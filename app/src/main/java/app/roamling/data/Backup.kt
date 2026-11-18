package app.roamling.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

/**
 * All the progress as one JSON file, to move to a new phone: the companions and eggs, the walks with their routes,
 * the fog, the patches, the days, the finds, the cones and the settings. Loading a file replaces what is on the phone.
 */
object Backup {
    const val APP = "roamling"
    const val VERSION = 1

    suspend fun export(db: Db, s: Settings): String {
        val dao = db.dao()
        val o = JSONObject().put("app", APP).put("version", VERSION).put("at", System.currentTimeMillis())
        o.put("settings", s.copy(walking = false, walkId = 0).json())
        o.put("pets", JSONArray().apply {
            dao.petList().forEach { p ->
                put(JSONObject().put("id", p.id).put("species", p.species).put("name", p.name).put("stage", p.stage).put("points", p.points)
                    .put("diet", p.diet).put("form", p.form).put("shiny", p.shiny).put("born", p.born).put("eggLeft", p.eggLeft).put("eggTaste", p.eggTaste)
                    .put("lat", p.lat).put("lon", p.lon).put("bond", p.bond).put("meters", p.meters).put("skies", p.skies).put("gear", p.gear).put("grownAt", p.grownAt))
            }
        })
        // routes ride along with their walks as flat at, lat, lon triples
        val routes = dao.allWaypoints().groupBy { it.walk }
        o.put("walks", JSONArray().apply {
            dao.walkList().forEach { w ->
                val route = JSONArray()
                routes[w.id].orEmpty().forEach { p -> route.put(p.at).put(round(p.lat)).put(round(p.lon)) }
                put(JSONObject().put("id", w.id).put("start", w.start).put("end", w.end).put("meters", w.meters).put("steps", w.steps).put("patches", w.patches)
                    .put("tastes", w.tastes).put("sky", w.sky).put("temp", w.temp).put("finds", w.finds).put("lat", w.lat).put("lon", w.lon).put("route", route))
            }
        })
        o.put("days", JSONArray().apply {
            dao.dayList().forEach { d -> put(JSONArray().put(d.day).put(d.steps).put(d.meters).put(d.patches).put(d.fed).put(d.gift)) }
        })
        o.put("finds", JSONArray().apply {
            dao.findList().forEach { f -> put(JSONObject().put("item", f.item).put("at", f.at).put("lat", f.lat).put("lon", f.lon).put("place", f.place).put("ref", f.ref)) }
        })
        o.put("entries", JSONArray().apply { dao.entryList().forEach { e -> put(JSONArray().put(e.ref).put(e.at).put(e.cones)) } })
        o.put("patches", JSONArray().apply {
            dao.patchList().forEach { p -> put(JSONArray().put(p.key).put(p.first).put(p.last).put(p.days).put(p.lastDay).put(p.taste)) }
        })
        // the fog is the bulk: only the cleared cells' keys
        o.put("cells", JSONArray().apply { dao.cellIds().forEach { put(it) } })
        return o.toString()
    }

    /** What a file holds, read in full before anything on the phone is touched. */
    class Parsed(val settings: Settings, val pets: List<Pet>, val walks: List<Walk>, val route: List<Waypoint>, val days: List<Day>,
                 val finds: List<Find>, val entries: List<Entry>, val patches: List<Patch>, val cells: List<Long>) {
        val km get() = walks.sumOf { it.meters } / 1000
    }

    /** Throws if the file is not a Roamling backup. */
    fun parse(text: String): Parsed {
        val o = JSONObject(text)
        require(o.optString("app") == APP) { "not a Roamling file" }
        fun arr(k: String) = o.optJSONArray(k) ?: JSONArray()
        val pets = arr("pets").let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { p ->
            Pet(p.getLong("id"), p.getString("species"), p.optString("name"), p.getInt("stage"), p.optDouble("points", 0.0), p.optString("diet"), p.optInt("form", -1),
                p.optBoolean("shiny"), p.optLong("born"), p.optDouble("eggLeft", 0.0), p.optString("eggTaste"), p.optDouble("lat", 0.0), p.optDouble("lon", 0.0),
                p.optDouble("bond", 0.0), p.optDouble("meters", 0.0), p.optString("skies"), p.optString("gear"), p.optLong("grownAt"))
        } } }
        val route = ArrayList<Waypoint>()
        val walks = arr("walks").let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { w ->
            val id = w.getLong("id")
            w.optJSONArray("route")?.let { r -> for (j in 0 until r.length() / 3) route += Waypoint(id, r.getLong(j * 3), r.getDouble(j * 3 + 1), r.getDouble(j * 3 + 2)) }
            Walk(id, w.getLong("start"), w.getLong("end"), w.optDouble("meters", 0.0), w.optInt("steps"), w.optInt("patches"), w.optString("tastes"), w.optString("sky"),
                w.optDouble("temp", -999.0), w.optInt("finds"), w.optDouble("lat", 0.0), w.optDouble("lon", 0.0))
        } } }
        val days = arr("days").let { a -> (0 until a.length()).map { i -> a.getJSONArray(i).let { d ->
            Day(d.getLong(0), d.getInt(1), d.getDouble(2), d.getInt(3), d.getInt(4), d.getBoolean(5)) } } }
        val finds = arr("finds").let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { f ->
            Find(item = f.getString("item"), at = f.getLong("at"), lat = f.optDouble("lat", 0.0), lon = f.optDouble("lon", 0.0), place = f.optString("place"), ref = f.optString("ref")) } } }
        val entries = arr("entries").let { a -> (0 until a.length()).map { i -> a.getJSONArray(i).let { e -> Entry(e.getString(0), e.getLong(1), e.getInt(2)) } } }
        val patches = arr("patches").let { a -> (0 until a.length()).map { i -> a.getJSONArray(i).let { p ->
            Patch(p.getLong(0), p.getLong(1), p.getLong(2), p.getInt(3), p.getLong(4), p.optString(5)) } } }
        val cells = arr("cells").let { a -> (0 until a.length()).map { a.getLong(it) } }
        val s = Settings.of(o.getJSONObject("settings")).let { s ->
            // only what the file has: the backpack and the pockets point at its own companions
            val ids = pets.map { it.id }.toSet()
            s.copy(onboarded = true, walking = false, walkId = 0, team = s.team.filter { it in ids }, eggs = s.eggs.filter { it in ids })
        }
        return Parsed(s, pets, walks, route, days, finds, entries, patches, cells)
    }

    /** Replaces everything on the phone with [p]. */
    suspend fun restore(db: Db, store: SettingsStore, p: Parsed) {
        db.clearAllTables()
        val dao = db.dao()
        db.withTransaction {
            p.pets.forEach { dao.insert(it) }
            p.walks.forEach { dao.insert(it) }
            p.route.chunked(2000).forEach { dao.insertWaypoints(it) }
            p.days.forEach { dao.upsert(it) }
            p.finds.forEach { dao.insert(it) }
            p.entries.forEach { dao.grant(it) }
            p.patches.chunked(2000).forEach { dao.upsertPatches(it) }
            val now = System.currentTimeMillis()
            p.cells.chunked(5000).forEach { c -> dao.insertCells(c.map { Cell(it, now) }) }
        }
        store.update { p.settings }
    }

    private fun round(v: Double) = Math.round(v * 1e6) / 1e6
}
