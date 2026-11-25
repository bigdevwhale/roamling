package app.roamling

import android.app.Application
import android.content.Context
import app.roamling.data.Db
import app.roamling.data.Entry
import app.roamling.data.Pet
import app.roamling.data.Resident
import app.roamling.data.SettingsStore
import app.roamling.domain.Catalog
import app.roamling.domain.FogStore
import app.roamling.domain.Growth
import app.roamling.domain.Lang
import app.roamling.domain.Quest
import app.roamling.domain.Shop
import app.roamling.domain.Tastes
import app.roamling.domain.dayOf
import app.roamling.domain.tr
import app.roamling.domain.weekStart
import app.roamling.explore.HealthSteps
import app.roamling.explore.LocationFeed
import app.roamling.explore.StepCounter
import app.roamling.explore.Walker
import app.roamling.notify.Notes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

class AppContainer(context: Context) {
    val app: Context = context.applicationContext
    val db = Db.create(app)
    val dao = db.dao()
    val settings = SettingsStore(app)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val fog = FogStore()
    val walker = Walker(db, settings, fog, scope)
    val feed = LocationFeed(app, walker)
    val pedometer = StepCounter(app, walker)
    val health = HealthSteps(app)
    val news = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val lock = Mutex()

    fun write(block: suspend AppContainer.() -> Unit) { scope.launch { block() } }
    fun say(s: String) { news.tryEmit(s) }

    /** Health Connect, when it's chosen and allowed, counts the steps of walks too (watches, bands); never the rest of the day. */
    suspend fun syncHealth() {
        val s = settings.now()
        walker.walkSteps = if (s.health && health.available && health.allowed()) { a, b -> health.between(a, b) } else null
        walker.syncWalkSteps()
    }

    /** Listens to the location for a little while, so the dot, the weather and the district are known before any walk. */
    fun locateOnce(seconds: Long = 25) {
        if (!feed.permitted()) return
        scope.launch(kotlinx.coroutines.Dispatchers.Main) {
            feed.acquire()
            kotlinx.coroutines.delay(seconds * 1000)
            feed.release()
            walker.live.value.fix?.takeIf { it.time > 0 }?.let { f -> walker.weather(f.lat, f.lon, force = true) }
        }
    }

    /** The first companion, picked in onboarding. */
    suspend fun begin(species: String, name: String) = lock.withLock {
        val id = dao.insert(Pet(species = species, name = name, stage = 1, shiny = Random.nextInt(16) == 0))
        settings.update { it.copy(onboarded = true, team = listOf(id), told = it.told + "p:$id:1") }
    }

    /** Cones: earned in [Entry] rows, spent in negative ones. */
    suspend fun cones(): Int = dao.entryList().sumOf { it.cones }

    private suspend fun spend(ref: String, price: Int): Boolean {
        if (cones() < price) return false
        return dao.grant(Entry(ref, System.currentTimeMillis(), -price)) != -1L
    }

    suspend fun buy(id: String): Boolean = lock.withLock {
        val th = Shop.byId[id] ?: return@withLock false
        if (id in settings.now().owned || !spend("buy:$id", th.price)) return@withLock false
        settings.update { it.copy(owned = it.owned + id) }
        true
    }

    suspend fun buyLure(): Boolean = lock.withLock {
        if (!spend("lure:${System.currentTimeMillis()}", Shop.LURE)) return@withLock false
        settings.update { it.copy(lures = it.lures + 1) }; true
    }

    suspend fun buyBreeze(): Boolean = lock.withLock {
        if (!spend("breeze:${System.currentTimeMillis()}", Shop.BREEZE)) return@withLock false
        settings.update { it.copy(breezes = it.breezes + 1) }; true
    }

    /** A pat, once a day per companion, warms its heart a little (#12). False when it was patted today already. */
    suspend fun pat(p: Pet): Boolean = lock.withLock {
        if (p.stage <= 0) return@withLock false
        val now = System.currentTimeMillis()
        if (dao.grant(Entry("pat:${p.id}:${dayOf(now)}", now, 0)) == -1L) return@withLock false
        dao.pet(p.id)?.let { dao.update(it.copy(bond = (it.bond + Growth.PAT_BOND).coerceAtMost(10.0))) }
        true
    }

    /**
     * Lets a twin go to live where the walker stands (#7, #11): cones once, then a gift each week it is visited.
     * Only on a walk, only a twin, never the last one in the backpack. Null when done, else why not.
     */
    suspend fun release(p: Pet): String? = lock.withLock {
        val s = settings.now()
        val f = walker.live.value.fix
        when {
            !s.walking || f == null || f.time <= 0 -> return@withLock tr("Отпустить можно на прогулке: поселится там, где вы стоите", "You can let it go on a walk: it settles where you stand")
            p.stage <= 0 || s.residents.any { it.pet == p.id } -> return@withLock tr("Уже живёт на карте", "Already lives on the map")
            Growth.twinsOf(p, dao.petList().filter { o -> s.residents.none { it.pet == o.id } }).isEmpty() -> return@withLock tr("Отпустить можно только двойника", "Only a twin can be let go")
            s.team == listOf(p.id) -> return@withLock tr("Рюкзак не может остаться пустым", "The backpack can't be left empty")
        }
        val now = System.currentTimeMillis()
        val taste = Tastes.of(Tastes.patchOf(f!!.lat, f.lon))
        val home = taste == Growth.loves(p)
        dao.grant(Entry("release:${p.id}", now, Growth.RELEASE_CONES * p.stage))
        settings.update { it.copy(team = it.team - p.id, residents = it.residents + Resident(p.id, f.lat, f.lon, now, taste?.name.orEmpty(), home)) }
        null
    }

    /** Calls a resident back to the den. */
    suspend fun callHome(p: Pet) = settings.update { s -> s.copy(residents = s.residents.filter { it.pet != p.id }) }

    suspend fun buyStone(): Boolean = lock.withLock {
        if (!spend("stone:${System.currentTimeMillis()}", Shop.STONE)) return@withLock false
        settings.update { it.copy(stones = it.stones + 1) }; true
    }

    suspend fun buyPocket(): Boolean = lock.withLock {
        if (settings.now().eggSlots >= 3 || !spend("pocket", Shop.POCKET)) return@withLock false
        settings.update { it.copy(eggSlots = 3) }; true
    }

    /** A branch stone turns a grown companion into another of its forms. */
    suspend fun reform(p: Pet, form: Int): Boolean = lock.withLock {
        if (settings.now().stones <= 0 || p.stage != 3 || form == p.form || form !in Catalog[p.species].forms.indices) return@withLock false
        settings.update { it.copy(stones = it.stones - 1) }
        dao.update(p.copy(form = form)); true
    }

    suspend fun wear(p: Pet, gear: String) = lock.withLock {
        // one piece of gear is worn by one companion at a time
        if (gear.isNotEmpty()) dao.petList().filter { it.gear == gear && it.id != p.id }.forEach { dao.update(it.copy(gear = "")) }
        dao.update(p.copy(gear = gear))
    }

    suspend fun rename(p: Pet, name: String) = lock.withLock { dao.update(p.copy(name = name.trim().ifEmpty { p.name })) }

    /** Into the backpack: as leader ([slot] 0) or companion; the one there goes to the den. */
    suspend fun toTeam(id: Long, slot: Int) = settings.update { s ->
        val t = s.team.filter { it != id }.toMutableList()
        t.add(slot.coerceIn(0, t.size), id)
        s.copy(team = t.take(3), residents = s.residents.filter { it.pet != id })
    }

    suspend fun toDen(id: Long) = settings.update { s -> if (s.team.size <= 1) s else s.copy(team = s.team - id) }

    /** Trades a daily task for another for cones (#8). */
    suspend fun swapQuest(old: Quest, new: Quest): Boolean = lock.withLock {
        spend("swap:${dayOf(System.currentTimeMillis())}:${old.ref}>${new.ref}", Quest.SWAP)
    }

    /** A task's reward, once; [from] is its day, or the Monday of its week. */
    suspend fun claim(from: Long, q: Quest): Boolean = lock.withLock {
        if (dao.grant(Entry("quest:$from:${q.ref}", System.currentTimeMillis(), q.cones)) == -1L) return@withLock false
        settings.now().team.firstOrNull()?.let { id -> dao.pet(id)?.takeIf { it.stage in 1..2 }?.let { dao.update(it.copy(points = it.points + q.points)) } }
        walker.feedSteps(dayOf(System.currentTimeMillis()))
        true
    }

    fun thisMonday() = weekStart(dayOf(System.currentTimeMillis()))

    suspend fun told(key: String) = settings.update { it.copy(told = it.told + key) }
}

class RoamlingApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Lang.ru = resources.configuration.locales[0].language == "ru"
        Tastes.init(this)
        // Tiles come from openstreetmap.org, whose policy asks for an app-specific user agent.
        org.osmdroid.config.Configuration.getInstance().apply {
            userAgentValue = "Roamling/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})"
            osmdroidBasePath = java.io.File(cacheDir, "osmdroid")
            osmdroidTileCache = java.io.File(cacheDir, "osmdroid/tiles")
            tileFileSystemCacheMaxBytes = 300L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 250L * 1024 * 1024
        }
        container = AppContainer(this)
        container.walker.load()
        Notes.channels(this)
        container.scope.launch { container.syncHealth() }
    }
}

val Context.container: AppContainer get() = (applicationContext as RoamlingApp).container
