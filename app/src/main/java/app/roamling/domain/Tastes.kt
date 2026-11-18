package app.roamling.domain

import android.content.Context
import app.roamling.geo.Grid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * What each patch of the map tastes of, from OpenStreetMap. A district (a zoom-14 tile, about 1.5 km) is asked for
 * once: its parks and woods, water, steps, bridges and lookouts, and its houses. Every patch (zoom 18, about 100 m)
 * gets the taste most of it has; houses packed close make Stone. Kept in files/tastes, a byte per patch.
 */
object Tastes {
    const val PATCH_Z = 18
    private const val SIDE = 1 shl (PATCH_Z - Grid.SECTOR_Z) // patches per district side: 16
    private val ENDPOINTS = listOf("https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter", "https://maps.mail.ru/osm/tools/overpass/api/interpreter")
    private val mem = HashMap<Long, ByteArray>()
    private val asking = HashSet<Long>()
    private val lock = Mutex()
    lateinit var dir: File

    fun init(ctx: Context) { dir = File(ctx.filesDir, "tastes").apply { mkdirs() } }

    fun patchOf(lat: Double, lon: Double): Long = Grid.parent(Grid.cell(lat, lon), PATCH_Z)

    /** The taste of a patch if its district is known, else null (and the district is asked for in the background by [ensure]). */
    @Synchronized
    fun of(patch: Long): Taste? {
        val sector = sectorOf(patch)
        val b = mem[sector] ?: load(sector)?.also { mem[sector] = it } ?: return null
        val i = index(patch)
        return when (b[i].toInt()) { 1 -> Taste.GREEN; 2 -> Taste.WATER; 3 -> Taste.STONE; 4 -> Taste.HEIGHT; else -> null }
    }

    /** The tastes of all 256 patches of a district, or null while OpenStreetMap hasn't been asked about it. */
    @Synchronized
    fun ofSector(sector: Long): List<String>? {
        val b = mem[sector] ?: load(sector)?.also { mem[sector] = it } ?: return null
        return b.map { when (it.toInt()) { 1 -> "GREEN"; 2 -> "WATER"; 3 -> "STONE"; 4 -> "HEIGHT"; else -> "" } }
    }

    /** A patch inside [sector] (its corner), to ask about the district. */
    fun patchIn(sector: Long): Long { val d = PATCH_Z - Grid.SECTOR_Z; return Grid.key(Grid.x(sector) shl d, Grid.y(sector) shl d) }

    fun known(patch: Long): Boolean = synchronized(this) { mem.containsKey(sectorOf(patch)) || File(dir, "${sectorOf(patch)}.t").exists() }

    private fun sectorOf(patch: Long): Long { val d = PATCH_Z - Grid.SECTOR_Z; return Grid.key(Grid.x(patch) shr d, Grid.y(patch) shr d) }
    private fun index(patch: Long): Int = (Grid.y(patch) and (SIDE - 1)) * SIDE + (Grid.x(patch) and (SIDE - 1))
    private fun load(sector: Long): ByteArray? = File(dir, "$sector.t").takeIf { it.exists() }?.readBytes()?.takeIf { it.size == SIDE * SIDE }

    /** Asks OpenStreetMap for the district of [patch] unless it is known. */
    suspend fun ensure(patch: Long) {
        val sector = sectorOf(patch)
        lock.withLock { if (known(patch) || !asking.add(sector)) return }
        try {
            val bytes = fetch(sector) ?: return
            File(dir, "$sector.t").writeBytes(bytes)
            synchronized(this) { mem[sector] = bytes }
        } finally { lock.withLock { asking.remove(sector) } }
    }

    /** For the demo and tests: a district's tastes set directly. */
    fun put(sector: Long, bytes: ByteArray) { synchronized(this) { mem[sector] = bytes } }

    private class Shape(val kind: Int, val pts: List<Pair<Double, Double>>, val area: Boolean) {
        val s = pts.minOf { it.first }; val n = pts.maxOf { it.first }; val w = pts.minOf { it.second }; val e = pts.maxOf { it.second }
    }

    private suspend fun fetch(sector: Long): ByteArray? = withContext(Dispatchers.IO) {
        val z = Grid.SECTOR_Z; val n = 1 shl z
        val x = Grid.x(sector); val y = Grid.y(sector)
        val north = Grid.lat(y.toDouble() / n); val south = Grid.lat((y + 1.0) / n)
        val west = Grid.lon(x.toDouble() / n); val east = Grid.lon((x + 1.0) / n)
        val bb = String.format(Locale.US, "%.5f,%.5f,%.5f,%.5f", south, west, north, east)
        val q = "[out:json][timeout:40];(" +
            "way[leisure~\"^(park|garden|nature_reserve|playground|dog_park)$\"]($bb);rel[leisure~\"^(park|garden|nature_reserve)$\"]($bb);" +
            "way[landuse~\"^(forest|grass|meadow|recreation_ground|village_green|allotments|orchard|cemetery)$\"]($bb);rel[landuse~\"^(forest|meadow)$\"]($bb);" +
            "way[natural~\"^(wood|scrub|grassland|heath)$\"]($bb);rel[natural=wood]($bb);" +
            "way[natural~\"^(water|wetland|beach)$\"]($bb);rel[natural=water]($bb);way[waterway~\"^(river|canal|stream|riverbank)$\"]($bb);" +
            "way[highway=steps]($bb);way[bridge=yes][highway]($bb);node[natural=peak]($bb);node[tourism=viewpoint]($bb);" +
            ");out geom;way[building]($bb);out center;"
        val body = "data=" + URLEncoder.encode(q, "UTF-8")
        val text = ENDPOINTS.firstNotNullOfOrNull { url ->
            runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 15_000; c.readTimeout = 60_000; c.requestMethod = "POST"; c.doOutput = true
                c.setRequestProperty("User-Agent", "Roamling (app.roamling; github.com/bigdevwhale/roamling)")
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.outputStream.use { it.write(body.toByteArray()) }
                if (c.responseCode != 200) error("HTTP ${c.responseCode}")
                c.inputStream.bufferedReader().use { it.readText() }
            }.getOrNull()
        } ?: return@withContext null
        runCatching { classify(sector, JSONObject(text)) }.getOrNull()
    }

    /** Turns OpenStreetMap's answer into a byte per patch. */
    fun classify(sector: Long, o: JSONObject): ByteArray {
        val shapes = ArrayList<Shape>()
        val houses = IntArray(SIDE * SIDE)
        val els = o.getJSONArray("elements")
        val d = PATCH_Z - Grid.SECTOR_Z
        val x0 = Grid.x(sector) shl d; val y0 = Grid.y(sector) shl d
        val pn = 1 shl PATCH_Z
        fun patchIdx(lat: Double, lon: Double): Int {
            val px = (Grid.mx(lon) * pn).toInt() - x0; val py = (Grid.my(lat) * pn).toInt() - y0
            return if (px in 0 until SIDE && py in 0 until SIDE) py * SIDE + px else -1
        }
        for (i in 0 until els.length()) {
            val e = els.getJSONObject(i)
            val tags = e.optJSONObject("tags") ?: JSONObject()
            if (tags.has("building")) {
                val c = e.optJSONObject("center") ?: continue
                val k = patchIdx(c.getDouble("lat"), c.getDouble("lon")); if (k >= 0) houses[k]++
                continue
            }
            val kind = when {
                tags.optString("natural") in setOf("water", "wetland", "beach") || tags.has("waterway") -> 2
                tags.optString("highway") == "steps" || tags.optString("bridge") == "yes" || tags.optString("natural") == "peak" || tags.optString("tourism") == "viewpoint" -> 4
                else -> 1
            }
            fun ring(a: org.json.JSONArray) = (0 until a.length()).map { a.getJSONObject(it).let { p -> p.getDouble("lat") to p.getDouble("lon") } }
            when (e.optString("type")) {
                "node" -> shapes += Shape(kind, listOf(e.getDouble("lat") to e.getDouble("lon")), false)
                "way" -> e.optJSONArray("geometry")?.takeIf { it.length() > 0 }?.let { g -> val pts = ring(g); shapes += Shape(kind, pts, kind != 4 && !tags.has("waterway") && pts.size > 3 && pts.first() == pts.last()) }
                "relation" -> e.optJSONArray("members")?.let { m -> for (j in 0 until m.length()) { val mm = m.getJSONObject(j); if (mm.optString("role") == "outer") mm.optJSONArray("geometry")?.takeIf { it.length() > 2 }?.let { shapes += Shape(kind, ring(it), true) } } }
            }
        }
        val out = ByteArray(SIDE * SIDE)
        val score = Array(SIDE * SIDE) { DoubleArray(5) }
        // area shapes: sample a 3×3 grid in each patch
        for (py in 0 until SIDE) for (px in 0 until SIDE) {
            for (sy in 0..2) for (sx in 0..2) {
                val lat = Grid.lat((y0 + py + (sy + .5) / 3) / pn); val lon = Grid.lon((x0 + px + (sx + .5) / 3) / pn)
                for (s in shapes) if (s.area && inBox(s, lat, lon) && inside(s.pts, lat, lon)) score[py * SIDE + px][s.kind] += 1.0
            }
        }
        // lines and points: each point (and the points along a line) marks its patch
        for (s in shapes) if (!s.area) {
            val pts = ArrayList<Pair<Double, Double>>()
            s.pts.zipWithNext().forEach { (a, b) -> val n = (Grid.distance(a.first, a.second, b.first, b.second) / 25).toInt().coerceAtLeast(1); for (t in 0..n) pts += (a.first + (b.first - a.first) * t / n) to (a.second + (b.second - a.second) * t / n) }
            if (s.pts.size == 1) pts += s.pts[0]
            pts.map { patchIdx(it.first, it.second) }.filter { it >= 0 }.toSet().forEach { score[it][s.kind] += if (s.kind == 4) 4.0 else 3.0 }
        }
        for (k in 0 until SIDE * SIDE) {
            score[k][3] = if (houses[k] >= 10) 3.0 + houses[k] / 10.0 else 0.0
            val best = (1..4).maxBy { score[k][it] }
            out[k] = if (score[k][best] >= 2.0) best.toByte() else 0
        }
        return out
    }

    private fun inBox(s: Shape, lat: Double, lon: Double) = lat in s.s..s.n && lon in s.w..s.e

    private fun inside(pts: List<Pair<Double, Double>>, lat: Double, lon: Double): Boolean {
        var c = false; var j = pts.size - 1
        for (i in pts.indices) {
            val (yi, xi) = pts[i]; val (yj, xj) = pts[j]
            if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) c = !c
            j = i
        }
        return c
    }
}
