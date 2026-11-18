package app.roamling.explore

import app.roamling.data.Cell
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import app.roamling.data.Day
import app.roamling.data.Db
import app.roamling.data.Entry
import app.roamling.data.Find
import app.roamling.data.Patch
import app.roamling.data.Pet
import app.roamling.data.Scent
import app.roamling.data.Settings
import app.roamling.data.SettingsStore
import app.roamling.data.Shadow
import app.roamling.data.Walk
import app.roamling.data.Waypoint
import app.roamling.data.pack
import app.roamling.data.parseMap
import app.roamling.data.plus
import app.roamling.domain.Call
import app.roamling.domain.Catalog
import app.roamling.domain.FogStore
import app.roamling.domain.Group
import app.roamling.domain.Growth
import app.roamling.domain.Heavens
import app.roamling.domain.Now
import app.roamling.domain.Places
import app.roamling.domain.Shop
import app.roamling.domain.Sky
import app.roamling.domain.Taste
import app.roamling.domain.Tastes
import app.roamling.domain.Weather
import app.roamling.domain.WeatherApi
import app.roamling.domain.Wild
import app.roamling.domain.dayOf
import app.roamling.domain.hash
import app.roamling.domain.Rand
import app.roamling.geo.Grid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** A position from GPS (or the demo). */
data class Fix(
    val lat: Double,
    val lon: Double,
    val accuracy: Float,
    val time: Long,
    val bearing: Float? = null,
    val simulated: Boolean = false,
    /** False for network positions, which are shown but never walked on. */
    val gps: Boolean = true,
    val speed: Float? = null,
)

/** What the map and the walk panel show live. */
data class Live(
    val fix: Fix? = null,
    val walk: Walk? = null,
    val trail: List<Pair<Double, Double>> = emptyList(),
    val tooFast: Boolean = false,
    /** The taste of the patch underfoot. */
    val taste: Taste? = null,
    val weather: Weather? = null,
    /** Metres eaten per taste on this walk. */
    val eaten: Map<String, Double> = emptyMap(),
)

sealed interface Event {
    data class Egg(val pet: Pet) : Event
    data class Hatched(val pet: Pet) : Event
    data class Grew(val pet: Pet) : Event
    data class Joined(val pet: Pet) : Event
    data class Found(val find: Find) : Event
    data class ShadowNear(val species: String, val meters: Int) : Event
    data class CallDone(val call: Call) : Event
    data object Rainbow : Event
    data object Gift : Event
    data class Chapter(val sector: Long, val spirit: app.roamling.domain.Spirit) : Event
    data object Finale : Event
}

/**
 * Turns positions into food for the companions. Fixes run one at a time on a single worker, as in Fogwalk:
 * cleaned by [Track], the way from the last point filled in, the fog cleared around it, and every patch
 * (zoom 18, about 100 m) passed tasted. Only while a walk is on; otherwise a fix only moves the dot.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Walker(private val db: Db, private val settings: SettingsStore, val fog: FogStore, private val scope: CoroutineScope) {
    private val worker = Dispatchers.Default.limitedParallelism(1)
    /**
     * One thing at a time, for real: the single worker alone lets a fix that waits on the database be overtaken by the next
     * one, and a burst of fixes then finds the same pocket free and the same patch new. Re-entered calls pass straight through.
     */
    private val lock = Mutex()
    private class Held : AbstractCoroutineContextElement(Held) { companion object : CoroutineContext.Key<Held> }
    private suspend fun <T> serial(block: suspend () -> T): T =
        if (coroutineContext[Held] != null) block()
        else { ready.await(); withContext(worker + Held()) { lock.withLock { block() } } }
    private val ready = CompletableDeferred<Unit>()
    private val _live = MutableStateFlow(Live())
    val live: StateFlow<Live> = _live
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 32)
    val events: SharedFlow<Event> = _events

    private val track = Track()
    private var walk: Walk? = null
    private var routed = -1L
    private var lastWeather = 0L
    private var lastWild = 0L
    /** Metres walked under each sky on this walk, for its stamp. */
    private val skyMeters = HashMap<String, Double>()
    private var heading = 0.0
    /** Steps between two moments from Health Connect, when it is chosen; null otherwise. */
    @Volatile var walkSteps: (suspend (Long, Long) -> Long?)? = null
    private var stepsSyncedAt = 0L

    fun load() {
        scope.launch(worker + Held()) { lock.withLock {
            val dao = db.dao()
            dao.cellIds().forEach { fog.add(it) }
            fog.changed()
            val s = settings.now()
            if (s.walking) walk = dao.walkList().firstOrNull { it.id == s.walkId }
            if (s.lastLat != 0.0 || s.lastLon != 0.0) _live.update { it.copy(fix = Fix(s.lastLat, s.lastLon, 9999f, 0), walk = walk) }
            ready.complete(Unit)
        } }
    }

    suspend fun awaitReady() = ready.await()

    /**
     * Swaps all the progress for a backup's: no fix runs meanwhile, and afterwards the fog and the walk are read anew.
     * Refused (false) during a walk.
     */
    suspend fun replaceAll(swap: suspend () -> Unit): Boolean = serial {
        if (settings.now().walking) return@serial false
        val old = db.dao().cellIds().toLongArray()
        swap()
        fog.removeAll(old)
        db.dao().cellIds().forEach { fog.add(it) }
        fog.changed()
        walk = null; skyMeters.clear()
        _live.update { it.copy(walk = null, trail = emptyList(), eaten = emptyMap()) }
        true
    }

    fun submit(fix: Fix) { scope.launch { serial { process(fix) } } }

    /** Debug builds: walks from the dot to a point at a stroll, a fix every 8 m, as if the phone went there. */
    fun simulateTo(lat: Double, lon: Double) {
        val from = _live.value.fix ?: Fix(lat, lon, 5f, System.currentTimeMillis() - 1000, simulated = true)
        val d = Grid.distance(from.lat, from.lon, lat, lon)
        val n = (d / FILL_STEP).toInt().coerceAtLeast(1)
        val t0 = max(System.currentTimeMillis(), from.time + 1)
        for (i in 1..n) {
            val k = i.toDouble() / n
            submit(Fix(from.lat + (lat - from.lat) * k, from.lon + (lon - from.lon) * k, 5f, t0 + (i * FILL_STEP / 1.3 * 1000).toLong(), simulated = true))
        }
    }

    fun pedometerRestarted() = track.pedometerRestarted()
    fun stepped(at: Long) = track.stepped(at)

    // ------------------------------------------------------------------ walks

    /** The «Walk» button: a new walk begins. */
    suspend fun startWalk(): Walk = serial {
        ready.await()
        val now = System.currentTimeMillis()
        val f = _live.value.fix
        var w = Walk(start = now, end = now, lat = f?.lat ?: 0.0, lon = f?.lon ?: 0.0)
        w = w.copy(id = db.dao().insert(w))
        walk = w; skyMeters.clear()
        settings.update { it.copy(walking = true, walkId = w.id) }
        _live.update { it.copy(walk = w, trail = emptyList(), eaten = emptyMap()) }
        f?.takeIf { it.time > 0 }?.let { daily(it.lat, it.lon) }
        w
    }

    /** The walk ends: its weather stamp is set; a walk of nothing is dropped. */
    suspend fun stopWalk() = serial {
        if (walk != null) syncWalkSteps()
        val w = walk ?: db.dao().walkList().firstOrNull { it.id == settings.now().walkId }
        if (w != null) {
            if (w.meters < 30 && w.patches == 0) db.dao().deleteWalk(w.id)
            else {
                val stamp = skyMeters.filter { runCatching { Sky.valueOf(it.key).notable }.getOrDefault(false) }.maxByOrNull { it.value }?.key
                    ?: skyMeters.maxByOrNull { it.value }?.key ?: WeatherApi.last()?.sky?.name.orEmpty()
                db.dao().update(w.copy(sky = stamp, end = max(w.end, System.currentTimeMillis())))
            }
        }
        walk = null
        settings.update { it.copy(walking = false, walkId = 0) }
        _live.update { it.copy(walk = null, trail = emptyList()) }
    }

    // ------------------------------------------------------------------ fixes

    private suspend fun process(f: Fix) {
        val prev = _live.value.fix
        if (prev != null && prev.time > 0 && f.time <= prev.time && !f.simulated) return
        val step = track.add(f)
        if (step == null) {
            if (!track.hasGps(f.time)) _live.update { if (prev == null || prev.time <= 0 || f.accuracy <= prev.accuracy) it.copy(fix = f) else it }
            return
        }
        val patchHere = Tastes.patchOf(step.lat, step.lon)
        _live.update { it.copy(fix = f.copy(lat = step.lat, lon = step.lon), tooFast = step.fast, taste = Tastes.of(patchHere)) }
        if (!Tastes.known(patchHere)) scope.launch { Tastes.ensure(patchHere) }
        val s = settings.now()
        if (f.time - lastWeather > 10 * 60_000) { lastWeather = f.time; scope.launch { weather(step.lat, step.lon) } }
        if (s.walking && walkSteps != null && f.time - stepsSyncedAt > 2 * 60_000) { stepsSyncedAt = f.time; scope.launch { syncWalkSteps() } }
        var w = walk
        if (!s.walking || w == null || step.fast) {
            if (f.time - savedAt > 120_000) { savedAt = f.time; settings.update { it.copy(lastLat = step.lat, lastLon = step.lon) } }
            return
        }
        if (step.bearing != null) heading = step.bearing.toDouble()
        val from = step.from
        val moved = step.meters
        val points = ArrayList<Pair<Double, Double>>()
        if (from == null) points += step.lat to step.lon
        else {
            val n = ceil(moved / FILL_STEP).toInt().coerceAtLeast(1)
            for (i in 1..n) { val t = i.toDouble() / n; points += (from.first + (step.lat - from.first) * t) to (from.second + (step.lon - from.second) * t) }
            heading = Grid.bearing(from.first, from.second, step.lat, step.lon)
        }

        val dao = db.dao()
        val day = dayOf(f.time)
        // the fog
        val freshCells = ArrayList<Long>()
        for ((la, lo) in points) Grid.around(la, lo, REVEAL_M) { if (fog.add(it)) freshCells += it }
        if (freshCells.isNotEmpty()) { dao.insertCells(freshCells.map { Cell(it, f.time) }); fog.changed() }

        // the patches: new ones are fresh food, old ones on a new day grow familiar
        val weather = WeatherApi.last()
        val (rise, set) = sun(f.time, step.lat, step.lon, weather)
        var fresh = 0
        var familiar = 0
        var newToday = 0
        val newPatches = ArrayList<Long>()
        for (k in points.map { Tastes.patchOf(it.first, it.second) }.toSet()) {
            val old = dao.patch(k)
            val taste = Tastes.of(k)?.name.orEmpty()
            if (old == null) { dao.upsert(Patch(k, f.time, f.time, 1, day, taste)); fresh++; newPatches += k }
            else if (old.lastDay != day) { newToday++; dao.upsert(old.copy(last = f.time, days = old.days + 1, lastDay = day, taste = old.taste.ifEmpty { taste })); if (old.days + 1 >= 5) familiar++ }
        }

        // what this stretch tastes of
        val eaten = HashMap<String, Double>()
        if (moved > 0) {
            Tastes.of(patchHere)?.let { eaten[it.name] = moved }
            val dusk = f.time !in rise + 20 * 60_000..set - 20 * 60_000
            if (dusk) eaten[Taste.DUSK.name] = moved
            val sky = rainbowSky(f.time) ?: weather?.sky
            if (sky != null) { skyMeters[sky.name] = (skyMeters[sky.name] ?: 0.0) + moved; if (sky.notable) eaten[Taste.SKY.name] = moved }
        }

        // the walk
        w = w.copy(end = f.time, meters = w.meters + moved, patches = w.patches + fresh,
            tastes = eaten.entries.fold(parseMap(w.tastes)) { m, (k, v) -> m.plus(k, v) }.pack(), temp = weather?.temp ?: w.temp)
        if (w.lat == 0.0) w = w.copy(lat = step.lat, lon = step.lon)
        dao.update(w); walk = w
        if (from != null) {
            val route = ArrayList<Waypoint>(2)
            if (routed != w.id) route += Waypoint(w.id, f.time - 1, from.first, from.second)
            route += Waypoint(w.id, f.time, step.lat, step.lon)
            dao.insertWaypoints(route); routed = w.id
        }
        val row = dao.day(day) ?: Day(day)
        if (moved > 0 || fresh > 0) dao.upsert(row.copy(meters = row.meters + moved, patches = row.patches + fresh))
        _live.update { l -> l.copy(walk = w, trail = if (from != null) (l.trail.ifEmpty { listOf(from) } + points).takeLast(4000) else l.trail,
            eaten = eaten.entries.fold(l.eaten) { m, (k, v) -> m.plus(k, v) }) }

        // the companions
        val pace = Growth.pace(if (w.end > w.start + 60_000) w.meters / ((w.end - w.start) / 1000.0) else 1.2)
        if (moved > 0 || fresh > 0) feed(s, Growth.FRESH_PATCH * fresh, eaten, moved, pace, rainbowSky(f.time) ?: weather?.sky, if (familiar > 0) .05 * familiar else 0.0)
        for (k in newPatches) maybeEgg(k, step.lat, step.lon, f.time)
        if (fresh > 0 || familiar > 0 || newToday > 0) storyCheck(app.roamling.domain.Story.sectorOfPatch(patchHere))

        // the nose, the wild ones, the rainbow, the call
        val now2 = settings.now()
        scentCheck(now2, points, f.time)
        wildCheck(now2, points, f.time, step.lat, step.lon, Tastes.of(patchHere), rise, set)
        callCheck(day, f.time, rise, set)
        if (f.time - savedAt > 60_000) { savedAt = f.time; settings.update { it.copy(lastLat = step.lat, lastLon = step.lon) } }
    }

    /** Positions are saved at most once a minute. */
    @Volatile private var savedAt = 0L

    private fun sun(ms: Long, lat: Double, lon: Double, w: Weather?): Pair<Long, Long> =
        if (w != null && w.sunrise > 0 && dayOf(w.sunrise) == dayOf(ms)) w.sunrise to w.sunset else Heavens.sun(ms, lat, lon)

    private suspend fun rainbowSky(ms: Long): Sky? = if (settings.now().rainbowUntil > ms) Sky.RAINBOW else null

    // ------------------------------------------------------------------ growth

    /**
     * Growth for the backpack: [points] to the leader in full and half to the companions, more in the favourite pace
     * and in the weather their gear is for. Teens also eat the tastes; eggs warm by the metre.
     */
    private suspend fun feed(s: Settings, points: Double, eaten: Map<String, Double>, moved: Double, pace: Int, sky: Sky?, bond: Double) {
        val dao = db.dao()
        val team = s.team.mapNotNull { dao.pet(it) }
        team.forEachIndexed { i, p0 ->
            if (p0.stage == 0) return@forEachIndexed
            val share = if (i == 0) 1.0 else Growth.SIDE_SHARE
            val sp = Catalog[p0.species]
            val gear = Shop.byId[p0.gear]
            var k = share * (if (sp.pace == pace) Growth.PACE_BONUS else 1.0)
            if (gear != null && sky != null && sky in gear.sky) k *= Shop.GEAR_BONUS
            var p = p0.copy(points = p0.points + points * k, meters = p0.meters + moved,
                bond = (p0.bond + bond * (if (p0.gear == "crown") 1.5 else 1.0) + moved / 20_000.0 * share).coerceAtMost(10.0),
                skies = if (sky != null && moved > 0) parseMap(p0.skies).plus(sky.name, moved).pack() else p0.skies)
            if (p.stage == 2) p = p.copy(diet = eaten.entries.fold(parseMap(p.diet)) { m, (t, v) -> m.plus(t, v * share) }.pack())
            p = grow(p)
            if (p != p0) dao.update(p)
        }
        // eggs in the pockets
        for (id in s.eggs) {
            val e = dao.pet(id) ?: continue
            if (e.stage != 0 || moved <= 0) continue
            val left = e.eggLeft - moved
            if (left > 0) dao.update(e.copy(eggLeft = left)) else hatch(e)
        }
    }

    /** Next stages while the points reach. */
    private fun grow(p0: Pet): Pet {
        var p = p0
        while (p.stage in 1..2 && p.points >= Growth.need(p.stage)) {
            p = p.copy(points = p.points - Growth.need(p.stage), stage = p.stage + 1)
            if (p.stage == 3) p = p.copy(form = Growth.formFor(p), grownAt = System.currentTimeMillis())
            _events.tryEmit(Event.Grew(p))
        }
        return p
    }

    private suspend fun hatch(e: Pet) {
        val p = e.copy(stage = 1, eggLeft = 0.0, born = System.currentTimeMillis(), shiny = Random.nextInt(16) == 0)
        db.dao().update(p)
        settings.update { s ->
            val eggs = s.eggs - p.id
            if (s.team.size < 3) s.copy(eggs = eggs, team = s.team + p.id) else s.copy(eggs = eggs)
        }
        _events.tryEmit(Event.Hatched(p))
    }

    /** Points to the backpack for steps not yet counted on [day]. */
    suspend fun feedSteps(day: Long) = serial {
        ready.await()
        val dao = db.dao()
        val row = dao.day(day) ?: return@serial
        if (row.steps <= row.fed) return@serial
        val n = row.steps - row.fed
        dao.upsert(row.copy(fed = row.steps))
        val s = settings.now()
        // only a walk's steps come here; its metres already warm the eggs
        feed(s, n * Growth.PER_STEP, emptyMap(), 0.0, 1, null, 0.0)
        val goal = s.stepGoal
        if (row.steps >= goal && !row.gift) {
            val now = System.currentTimeMillis()
            dao.upsert(dao.day(day)!!.copy(gift = true))
            if (dao.grant(Entry("gift:$day", now, 3)) != -1L) {
                val item = app.roamling.domain.Finds.ids[Math.floorMod(hash("gift$day"), 16)]
                dao.insert(Find(item = item, at = now, lat = s.lastLat, lon = s.lastLon, place = "gift", ref = "gift:$day"))
                _events.tryEmit(Event.Gift)
            }
        }
        callCheck(day, System.currentTimeMillis(), 0, 0)
    }

    /** Steps from the pedometer: only a walk's steps count – to the walk and its day. */
    fun addSteps(n: Int, at: Long) {
        scope.launch { serial {
            val w = walk ?: return@serial
            walkStepsTo(w.steps + n, at)
        } }
    }

    /** Sets the walk's steps to [total] if that is more, adding the difference to the day and to growth. */
    private suspend fun walkStepsTo(total: Int, at: Long) {
        val w = walk ?: return
        if (total <= w.steps) return
        val dao = db.dao()
        val day = dayOf(at)
        val row = dao.day(day) ?: Day(day)
        dao.upsert(row.copy(steps = row.steps + (total - w.steps)))
        val nw = w.copy(steps = total)
        dao.update(nw); walk = nw; _live.update { it.copy(walk = nw) }
        scope.launch { feedSteps(day) }
    }

    /**
     * The walk's steps from Health Connect, when it is chosen: what a watch or a band counted between the walk's start
     * and now. The larger of it and the phone's own count stands, so nothing is counted twice.
     */
    suspend fun syncWalkSteps() = serial {
        val w = walk ?: return@serial
        val read = walkSteps ?: return@serial
        val now = System.currentTimeMillis()
        stepsSyncedAt = now
        val n = read(w.start, now) ?: return@serial
        walkStepsTo(min(n, 70_000L).toInt(), now)
    }


    // ------------------------------------------------------------------ eggs, the nose, the wild

    /** About one new patch in fifteen hides an egg, of the patch's kind. */
    private suspend fun maybeEgg(patch: Long, lat: Double, lon: Double, at: Long) {
        if (Math.floorMod(hash("egg$patch"), 15) != 0) return
        val s = settings.now()
        if (s.eggs.size >= s.eggSlots) return
        val taste = Tastes.of(patch)
        val group = when (taste) { Taste.WATER -> Group.WATER; Taste.STONE -> Group.STONE; Taste.HEIGHT -> Group.HEIGHT; else -> Group.GREEN }
        val r = Rand(hash("eggkind$patch"))
        // most eggs of a district hatch into its own dwellers
        val sector = app.roamling.domain.Story.sectorOfPatch(patch)
        val dwellers = app.roamling.domain.Story.residents(sector, app.roamling.domain.Story.spirit(sector, db.dao().patchList().filter { app.roamling.domain.Story.sectorOfPatch(it.key) == sector }.map { it.taste }))
            .filter { !it.group.wild }
        val roll = r.next()
        val pool = when { roll < .1f -> app.roamling.domain.Catalog.starters; roll < .75f && dwellers.isNotEmpty() -> dwellers; else -> app.roamling.domain.Catalog.of(group) }
        val sp = pool[r.int(pool.size)]
        val egg = Pet(species = sp.id, name = names(sp.id), stage = 0, eggLeft = Growth.eggMeters(sp.id), eggTaste = (taste ?: Taste.GREEN).name, lat = lat, lon = lon, born = at)
        val id = db.dao().insert(egg)
        settings.update { it.copy(eggs = it.eggs + id) }
        _events.tryEmit(Event.Egg(egg.copy(id = id)))
    }

    /** The day's scent, set at the first fix of a walk. */
    private suspend fun daily(lat: Double, lon: Double) {
        val s = settings.now()
        val today = dayOf(System.currentTimeMillis())
        if (s.scent.day == today && (!s.scent.done || s.scent.n >= scentsPerDay(s))) return
        val places = Places.near(lat, lon)
        val pick = Places.pick(lat, lon, places, hash("scent$today${s.scent.n}")) { la, lo -> fog.exploredAround(la, lo, 40.0) > 0 }
        val n = if (s.scent.day == today) s.scent.n else 0
        settings.update { it.copy(scent = Scent(today, pick.lat, pick.lon, "${pick.ru}|${pick.en}", pick.item, false, n, pick.kind)) }
    }

    private suspend fun scentsPerDay(s: Settings): Int {
        val team = s.team.mapNotNull { db.dao().pet(it) }
        return if (team.any { it.gear == "compass" } || (team.firstOrNull()?.bond ?: 0.0) >= 5) 2 else 1
    }

    private suspend fun scentCheck(s: Settings, points: List<Pair<Double, Double>>, at: Long) {
        val sc = s.scent
        if (sc.day != dayOf(at)) { points.lastOrNull()?.let { daily(it.first, it.second) }; return }
        if (sc.done) { if (sc.n < scentsPerDay(s)) points.lastOrNull()?.let { daily(it.first, it.second) }; return }
        if (points.none { Grid.distance(it.first, it.second, sc.lat, sc.lon) <= REACH_M }) return
        val dao = db.dao()
        val find = Find(item = sc.item, at = at, lat = sc.lat, lon = sc.lon, place = sc.place, ref = "scent:${sc.day}:${sc.n}")
        val fid = dao.insert(find)
        val team = s.team.mapNotNull { dao.pet(it) }
        val twice = team.any { it.gear == "pack" }
        if (twice) dao.insert(find.copy(ref = find.ref + ":pack"))
        dao.grant(Entry("scent:${sc.day}:${sc.n}", at, 8))
        settings.update { it.copy(scent = it.scent.copy(done = true, n = it.scent.n + 1)) }
        feed(settings.now(), Growth.FIND, emptyMap(), 0.0, -1, null, .3)
        walk?.let { w -> val nw = w.copy(finds = w.finds + 1); dao.update(nw); walk = nw }
        _events.tryEmit(Event.Found(find.copy(id = fid)))
    }

    private suspend fun wildCheck(s: Settings, points: List<Pair<Double, Double>>, at: Long, lat: Double, lon: Double, taste: Taste?, rise: Long, set: Long) {
        val dao = db.dao()
        val day = dayOf(at)
        // reaching a shadow
        val live = s.shadows.filter { it.until > at }
        val reached = live.firstOrNull { sh -> points.any { Grid.distance(it.first, it.second, sh.lat, sh.lon) <= REACH_M + 10 } }
        if (reached != null) {
            val p = Pet(species = reached.species, name = names(reached.species), stage = 1, lat = reached.lat, lon = reached.lon, shiny = Random.nextInt(16) == 0, born = at)
            val id = dao.insert(p)
            settings.update { st -> st.copy(shadows = st.shadows - reached, met = st.met + "$day:${reached.species}", team = if (st.team.size < 3) st.team + id else st.team) }
            _events.tryEmit(Event.Joined(p.copy(id = id)))
            return
        }
        if (live.size != s.shadows.size) settings.update { it.copy(shadows = live) }
        // the rainbow's end gives a find
        if (s.rainbowUntil > at && points.any { Grid.distance(it.first, it.second, s.rainbowLat, s.rainbowLon) <= REACH_M } && dao.grant(Entry("rainbow:$day", at, 10)) != -1L) {
            val f = Find(item = "crystal", at = at, lat = s.rainbowLat, lon = s.rainbowLon, place = "конец радуги|the rainbow's end", ref = "rainbow:$day")
            _events.tryEmit(Event.Found(f.copy(id = dao.insert(f))))
        }
        // a new one shows up, now and then
        if (live.isNotEmpty() || at - lastWild < 90_000) return
        lastWild = at
        val now = nowAt(at, lat, taste, rise, set)
        val cands = now.wilds().filter { it.group != Group.LEGEND && "$day:${it.id}" !in s.met }
        if (cands.isEmpty()) return
        // the more rarely a weather comes, the surer its guest
        if (Random.nextFloat() > .55f) return
        spawn(cands[Random.nextInt(cands.size)].id, lat, lon, at, false)
    }

    private suspend fun spawn(species: String, lat: Double, lon: Double, at: Long, lured: Boolean) {
        val dist = if (lured) 70.0 else 150.0 + Random.nextDouble() * 120
        val (la, lo) = Grid.offset(lat, lon, dist, heading + Random.nextDouble(-35.0, 35.0))
        settings.update { it.copy(shadows = it.shadows + Shadow(species, la, lo, at + 25 * 60_000, lured)) }
        _events.tryEmit(Event.ShadowNear(species, dist.toInt()))
    }

    /** A lure: one of the wild ones that may come now shows up close by. False when none may come. */
    suspend fun lure(): Boolean = serial {
        val s = settings.now()
        val f = _live.value.fix ?: return@serial false
        if (s.lures <= 0 || !s.walking) return@serial false
        val at = System.currentTimeMillis()
        val (rise, set) = sun(at, f.lat, f.lon, WeatherApi.last())
        val cands = nowAt(at, f.lat, Tastes.of(Tastes.patchOf(f.lat, f.lon)), rise, set).wilds().filter { it.group != Group.LEGEND }
        if (cands.isEmpty()) return@serial false
        settings.update { it.copy(lures = it.lures - 1) }
        spawn(cands.random().id, f.lat, f.lon, at, true)
        true
    }

    suspend fun nowAt(at: Long, lat: Double, taste: Taste?, rise: Long, set: Long): Now {
        val s = settings.now()
        val home = if (s.homeLat != 0.0) Grid.sector(Grid.cell(s.homeLat, s.homeLon)) else fog.sectors().let { (k, c) -> if (k.isEmpty()) 0L else k[c.indices.maxBy { c[it] }] }
        val km = db.dao().walkList().sumOf { it.meters } / 1000
        return Now(at, WeatherApi.last(), rise, set, taste, lat, s.rainbowUntil > at, s.stormAt, if (home == 0L) 0.0 else fog.sectorShare(home), km)
    }

    // ------------------------------------------------------------------ the story

    /**
     * Chapters and the finale. A district wakes its spirit when half its patches are walked and three of its dwellers were
     * met there; the spirit seals it on the Great Map. The globe egg comes at 100 km. When the home district is sealed and
     * all twelve letters have come, the Keeper of the fog comes out.
     */
    suspend fun storyCheck(sector: Long? = null) = serial {
        ready.await()
        val dao = db.dao()
        val s = settings.now()
        val now = System.currentTimeMillis()
        val patches = dao.patchList()
        val pets = dao.petList()
        // a sealed district that misses you this week: five of its patches walked bring its spirit's gift
        val monday = app.roamling.domain.weekStart(dayOf(now))
        val missing = app.roamling.domain.Story.missing(monday, s.seals.keys)
        if (missing != null) {
            val sector = missing
            if (
                app.roamling.domain.Story.walkedThisWeek(sector, monday, patches.map { Triple(it.key, it.lastDay, it.days) }) >= app.roamling.domain.Story.MISS_PATCHES &&
                dao.grant(Entry("miss:$monday", now, 15)) != -1L) {
                val sp = runCatching { app.roamling.domain.Spirit.valueOf(s.seals[sector]!!.substringBefore('|')) }.getOrDefault(app.roamling.domain.Spirit.CROSS)
                val f = Find(item = sp.gift, at = now, lat = s.lastLat, lon = s.lastLon, place = "${sp.ru} скучал по вам|${sp.en} missed you", ref = "miss:$monday")
                val fid = dao.insert(f)
                s.team.forEach { id -> dao.pet(id)?.let { p -> if (p.stage > 0) dao.update(p.copy(bond = (p.bond + 1).coerceAtMost(10.0))) } }
                _events.tryEmit(Event.Found(f.copy(id = fid)))
            }
        }
        if (sector != null && sector !in s.seals) {
            val d = app.roamling.domain.Story.district(sector, patches.map { it.key to it.taste }, pets, first = s.seals.isEmpty())
            if (d.ready) {
                val season = Heavens.season(now, s.lastLat)
                settings.update { it.copy(seals = it.seals + (sector to "${d.spirit.name}|$season|$now")) }
                dao.grant(Entry("seal:$sector", now, 50))
                _events.tryEmit(Event.Chapter(sector, d.spirit))
            }
        }
        // the globe egg, for the long road
        val km = dao.walkList().sumOf { it.meters } / 1000
        val s2 = settings.now()
        if (km >= 100 && pets.none { it.species == "globe" } && s2.eggs.size < s2.eggSlots) {
            val id = dao.insert(Pet(species = "globe", name = names("globe"), stage = 0, eggLeft = Growth.eggMeters("globe"), eggTaste = "GLOBE", lat = s2.lastLat, lon = s2.lastLon, born = now))
            settings.update { it.copy(eggs = it.eggs + id) }
        }
        // the finale
        if (s2.finale == 0L) {
            val home = app.roamling.domain.Story.home(s2.homeLat, s2.homeLon, patches.map { it.key })
            val scentAt = dao.findList().filter { it.ref.startsWith("scent:") }.minOfOrNull { it.at }
            val met = pets.filter { it.stage > 0 }.map { it.species }.toSet().size
            val quarter = app.roamling.domain.Story.quarterAt(home, patches.map { it.key to it.first })
            val letters = app.roamling.domain.Story.lettersCome(app.roamling.domain.Story.milestones(dao.walkList(), pets, scentAt, s2.seals, met, quarter))
            if (home != null && home in s2.seals && letters >= 12) {
                val id = if (pets.none { it.species == "fogkeeper" }) dao.insert(Pet(species = "fogkeeper", name = app.roamling.domain.tr("Хранитель", "Keeper"), stage = 1,
                    lat = s2.homeLat, lon = s2.homeLon, born = now)) else null
                settings.update { it.copy(finale = now, team = if (id != null && it.team.size < 3) it.team + id else it.team) }
                _events.tryEmit(Event.Finale)
            }
        }
    }

    // ------------------------------------------------------------------ weather and the call

    /** New weather: storms remembered, a rainbow when the sun comes out after rain. */
    suspend fun weather(lat: Double, lon: Double, force: Boolean = false): Weather? {
        val w = WeatherApi.at(lat, lon, force) ?: return null
        _live.update { it.copy(weather = w) }
        val now = System.currentTimeMillis()
        val s = settings.now()
        if (w.stormy) settings.update { it.copy(stormAt = now) }
        else if (w.pastStorm && s.stormAt < now - 3 * 3_600_000) settings.update { it.copy(stormAt = now - 30 * 60_000) }
        if (w.pastRain >= .2 && !w.raining && w.isDay && w.clouds < 85 && s.rainbowUntil < now - 6 * 3_600_000) {
            val (la, lo) = Grid.offset(lat, lon, 220.0 + Random.nextDouble() * 120, Random.nextDouble() * 360)
            settings.update { it.copy(rainbowUntil = now + 40 * 60_000, rainbowLat = la, rainbowLon = lo) }
            _events.tryEmit(Event.Rainbow)
        }
        // a hail or a storm passing becomes a wild chance at once
        if (Weather.hail(w.code)) lastWild = 0
        return w
    }

    /** Pays the day's call once it is done. */
    private suspend fun callCheck(day: Long, at: Long, rise: Long, set: Long) {
        val dao = db.dao()
        if (dao.entryList().any { it.ref == "call:$day" }) return
        val call = Call.of(day, WeatherApi.last())
        val walks = dao.walkList().filter { dayOf(it.start) == day }
        fun eaten(t: Taste) = walks.sumOf { parseMap(it.tastes)[t.name] ?: 0.0 }
        val row = dao.day(day)
        val done = when (call) {
            Call.WATER -> eaten(Taste.WATER) >= call.target
            Call.GREEN -> eaten(Taste.GREEN) >= call.target
            Call.HEIGHT -> eaten(Taste.HEIGHT) >= call.target
            Call.NEW -> (row?.patches ?: 0) >= call.target
            Call.FAR -> walks.any { it.meters >= call.target }
            Call.SUNSET -> set > 0 && walks.any { set in it.start - 30 * 60_000..it.end + 30 * 60_000 }
            Call.DAWN -> rise > 0 && walks.any { rise in it.start - 45 * 60_000..it.end + 45 * 60_000 }
            Call.PUDDLES -> walks.any { it.sky == Sky.RAIN.name || it.sky == Sky.DRIZZLE.name } || (walk != null && (WeatherApi.last()?.pastRain ?: 0.0) > 0.1)
            Call.STEPS -> (row?.steps ?: 0) >= settings.now().stepGoal
        }
        if (!done) return
        if (dao.grant(Entry("call:$day", at, 5)) == -1L) return
        val s = settings.now()
        s.team.firstOrNull()?.let { id -> dao.pet(id)?.let { p -> if (p.stage > 0) dao.update(grow(p.copy(points = p.points + Growth.CALL, bond = (p.bond + 1).coerceAtMost(10.0)))) } }
        _events.tryEmit(Event.CallDone(call))
    }

    companion object {
        const val REVEAL_M = 25.0
        const val MAX_SPEED = 8.5
        const val FILL_STEP = 8.0
        const val REACH_M = 35.0

        /** A name for a newcomer, from its species' list. */
        fun names(species: String): String {
            val sp = Catalog[species]
            val pool = if (app.roamling.domain.Lang.ru) listOf("Тим", "Буся", "Луня", "Ёся", "Пых", "Мика", "Тоша", "Фиса", "Кузя", "Ласка", "Шустрик", "Пуговка", "Сеня", "Нюша", "Дымка", "Боня")
            else listOf("Tim", "Bun", "Luna", "Pip", "Puff", "Mika", "Toby", "Fizz", "Pebble", "Hazel", "Scoot", "Button", "Sunny", "Nutmeg", "Misty", "Bonnie")
            return pool[Random.nextInt(pool.size)].takeIf { Random.nextInt(4) != 0 } ?: sp.name
        }
    }
}
