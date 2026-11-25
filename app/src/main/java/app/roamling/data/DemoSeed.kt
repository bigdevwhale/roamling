package app.roamling.data

import android.content.Context
import app.roamling.AppContainer
import app.roamling.domain.Finds
import app.roamling.domain.Growth
import app.roamling.domain.Sky
import app.roamling.domain.Taste
import app.roamling.domain.Tastes
import app.roamling.domain.Weather
import app.roamling.domain.WeatherApi
import app.roamling.domain.dayOf
import app.roamling.domain.hash
import app.roamling.domain.tr
import app.roamling.explore.Walker
import app.roamling.geo.Grid
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Screenshot data for `-Pdemo` builds: two months of walks along real Prague streets (Fogwalk's demo_walks.json),
 * a backpack of three, eggs, a den full of companions, finds, stamps and cones.
 */
object DemoSeed {
    var here: Pair<Double, Double> = 0.0 to 0.0
        private set

    suspend fun seedIfEmpty(context: Context, c: AppContainer) {
        val json = runCatching { context.assets.open("demo_walks.json").use { it.readBytes().decodeToString() } }.getOrNull() ?: return
        val o = JSONObject(json)
        here = o.getDouble("lat") to o.getDouble("lon")
        val now = System.currentTimeMillis()
        WeatherApi.fake(Weather(now, 2, 17.0, 3.0, 30, true, now - 9 * 3_600_000L, now + 3 * 3_600_000L, List(12) { 2 }, List(12) { 17.0 }))
        val dao = c.dao
        if (dao.petList().isNotEmpty()) return
        val walks = o.getJSONArray("walks")
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val rnd = Random(7)
        // made-up tastes for the districts walked, so the diet bars and stripes have colour
        val skies = listOf(Sky.CLEAR, Sky.CLOUDS, Sky.RAIN, Sky.CLEAR, Sky.FOG, Sky.CLOUDS, Sky.DRIZZLE, Sky.WIND, Sky.CLEAR, Sky.SNOW, Sky.HEAT, Sky.FROST)
        val seen = HashSet<Long>()
        val patches = HashMap<Long, Patch>()
        val dayRows = HashMap<Long, Day>()
        for (i in 0 until walks.length()) {
            val line = walks.getJSONArray(i)
            val day = today.minusDays(((walks.length() - 1 - i) * 60L / walks.length())).toEpochDay()
            var start = LocalDate.ofEpochDay(day).atTime(7 + rnd.nextInt(13), rnd.nextInt(60)).atZone(zone).toInstant().toEpochMilli()
            if (day == today.toEpochDay()) start = minOf(start, now - 3 * 3_600_000L)
            var meters = 0.0
            val tastes = HashMap<String, Double>()
            val cells = ArrayList<Cell>()
            val route = ArrayList<Waypoint>()
            var prev: Pair<Double, Double>? = null
            var fresh = 0
            for (j in 0 until line.length()) {
                val p = line.getJSONArray(j)
                val pt = p.getDouble(0) to p.getDouble(1)
                route += Waypoint(0, start + j * 9_000L, pt.first, pt.second)
                val pts = ArrayList<Pair<Double, Double>>()
                prev?.let { a ->
                    val d = Grid.distance(a.first, a.second, pt.first, pt.second); meters += d
                    val n = (d / Walker.FILL_STEP).toInt().coerceAtLeast(1)
                    for (k in 1..n) pts += (a.first + (pt.first - a.first) * k / n) to (a.second + (pt.second - a.second) * k / n)
                    val t = demoTaste(Tastes.patchOf(pt.first, pt.second))
                    if (t != null) tastes[t.name] = (tastes[t.name] ?: 0.0) + d
                } ?: pts.add(pt)
                pts.forEach { (la, lo) -> Grid.around(la, lo, Walker.REVEAL_M) { if (seen.add(it)) cells += Cell(it, start) } }
                pts.forEach { (la, lo) ->
                    val k = Tastes.patchOf(la, lo)
                    val old = patches[k]
                    if (old == null) { patches[k] = Patch(k, start, start, 1, day, demoTaste(k)?.name.orEmpty()); fresh++ }
                    else if (old.lastDay != day) patches[k] = old.copy(last = start, days = old.days + 1, lastDay = day)
                }
                prev = pt
            }
            val sky = skies[i % skies.size]
            if (sky.notable) tastes[Taste.SKY.name] = meters * .4
            if (i % 4 == 0) tastes[Taste.DUSK.name] = meters * .5
            val end = start + (meters / 1.25 * 1000).toLong()
            val wid = dao.insert(Walk(start = start, end = end, meters = meters, steps = (meters / .72).toInt(), patches = fresh, tastes = tastes.pack(), sky = sky.name,
                temp = 4.0 + (i % 9) * 2.5, finds = if (i % 3 == 0) 1 else 0, lat = route.first().lat, lon = route.first().lon))
            dao.insertWaypoints(route.map { it.copy(walk = wid) })
            dao.insertCells(cells)
            val row = dayRows[day] ?: Day(day)
            dayRows[day] = row.copy(meters = row.meters + meters, patches = row.patches + fresh, steps = row.steps + (meters / .72).toInt() + 2500 + rnd.nextInt(3000))
            if (i % 3 == 0) {
                val item = Finds.ids[i % Finds.ids.size]
                dao.insert(Find(item = item, at = end, lat = route.last().lat, lon = route.last().lon, place = "фонтан|fountain", ref = "scent:$day:0"))
            }
        }
        dao.upsertPatches(patches.values.toList())
        // the days around the walks, with steps
        for (k in 0..60) {
            val day = today.minusDays(k.toLong()).toEpochDay()
            val row = dayRows[day] ?: Day(day, steps = 3000 + rnd.nextInt(5000))
            dao.upsert(row.copy(fed = row.steps, steps = if (k == 0) 5840 else row.steps, gift = row.steps >= 7000))
        }
        // the companions
        fun pet(species: String, nameRu: String, nameEn: String, stage: Int, form: Int = -1, days: Int = 30, diet: Map<Taste, Double> = emptyMap(), shiny: Boolean = false, bond: Double = 4.0, gear: String = "") =
            Pet(species = species, name = tr(nameRu, nameEn), stage = stage, form = form, shiny = shiny, bond = bond, gear = gear,
                points = if (stage in 1..2) Growth.need(stage) * (.35 + rnd.nextDouble() * .5) else 0.0,
                diet = diet.mapKeys { it.key.name }.pack(), born = now - days * 86_400_000L, meters = 4000.0 + rnd.nextInt(60000), lat = here.first, lon = here.second,
                skies = "RAIN:2400;CLEAR:9000")
        val team = listOf(
            pet("rustle", "Шуршик", "Rustle", 3, 0, 58, mapOf(Taste.GREEN to 9000.0, Taste.WATER to 3000.0, Taste.STONE to 2500.0), bond = 8.0, gear = "raincoat"),
            pet("compass", "Стрелка", "Needle", 2, -1, 25, mapOf(Taste.STONE to 4200.0, Taste.GREEN to 2100.0, Taste.DUSK to 1600.0), bond = 5.0),
            pet("moonhare", "Луня", "Luna", 1, -1, 4, bond = 2.0),
        )
        val ids = team.map { dao.insert(it) }
        val den = listOf(
            pet("acorn", "Жолудь", "Nutty", 3, 0, 50), pet("splash", "Плюх", "Splash", 3, 1, 47), pet("lamp", "Фитиль", "Wick", 2, -1, 20, mapOf(Taste.STONE to 2000.0)),
            pet("plod", "Топа", "Toppy", 3, 2, 55, shiny = true), pet("puddle", "Капля", "Drop", 1, -1, 6), pet("fern", "Завиток", "Curl", 2, -1, 16, mapOf(Taste.GREEN to 1800.0)),
            pet("pigeon", "Гуля", "Coo", 3, 1, 40), pet("dawn", "Зоря", "Dawny", 3, 0, 33), pet("roofcat", "Мурза", "Tiles", 1, -1, 3), pet("eaglet", "Гордей", "Scout", 3, 2, 38),
            pet("fogling", "Морок", "Haze", 3, 1, 28), pet("graffiti", "Клякса", "Splotch", 2, -1, 12, mapOf(Taste.STONE to 900.0, Taste.DUSK to 700.0)),
            // a twin of Nutty's, to show what twins are for (#7)
            pet("acorn", "Желудёк", "Nutlet", 2, -1, 9, mapOf(Taste.WATER to 1200.0, Taste.GREEN to 500.0)),
        )
        den.forEach { dao.insert(it) }
        val eggs = listOf(Pet(species = "beaver", name = "", stage = 0, eggLeft = 1300.0, eggTaste = "WATER", born = now - 86_400_000L),
            Pet(species = "cobble", name = "", stage = 0, eggLeft = 4200.0, eggTaste = "STONE", born = now - 3_600_000L)).map { dao.insert(it) }
        dao.grant(Entry("demo:start", now - 50 * 86_400_000L, 380))
        dao.grant(Entry("buy:raincoat", now - 20 * 86_400_000L, -40))
        val all = dao.petList()
        // one chapter already closed: the district the walks went through most
        val walked = patches.keys.groupingBy { app.roamling.domain.Story.sectorOfPatch(it) }.eachCount().maxByOrNull { it.value }?.key
        val seals = if (walked != null) mapOf(walked to "STONE|3|${now - 5 * 86_400_000L}") else emptyMap()
        val told = all.flatMap { p -> (1..p.stage).map { "p:${p.id}:$it" } + "e:${p.id}" }.toSet() + dao.findList().map { "f:${it.id}" } +
            seals.keys.map { "s:$it" } + (0 until 12).map { "l:$it" }
        val todayDay = dayOf(now)
        val (sla, slo) = Grid.offset(here.first, here.second, 520.0, 60.0)
        c.settings.update {
            it.copy(onboarded = true, team = ids, eggs = eggs, owned = setOf("raincoat", "scarf", "lamp", "maps", "fireflies", "shelf", "cushion", "globe", "garland"),
                lures = 2, stones = 1, told = told, lastLat = here.first, lastLon = here.second, homeLat = here.first + .004, homeLon = here.second - .006,
                scent = Scent(todayDay, sla, slo, "старое дерево «Липа у моста»|old tree «Lime by the bridge»", "acorn", false, 0, "tree"),
                shadows = listOf(Shadow("drizzle", here.first + .0012, here.second + .0009, now + 20 * 60_000)),
                seen = setOf("all"), seals = seals)
        }
        c.fog.changed()
        c.walker.load()
    }

    /** A made-up taste for a demo patch, steady for the same patch. */
    private fun demoTaste(patch: Long): Taste? = when (Math.floorMod(hash("t$patch"), 9)) {
        0, 1, 2 -> Taste.GREEN; 3, 4 -> Taste.STONE; 5 -> Taste.WATER; 6 -> Taste.HEIGHT; else -> null
    }
}
