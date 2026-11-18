package app.roamling.domain

import app.roamling.geo.Grid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** A place the nose can lead to: a real thing from OpenStreetMap, and what may be found there. */
data class Place(val lat: Double, val lon: Double, val ru: String, val en: String, val item: String, val kind: String = "sparkle") { val name get() = tr(ru, en) }

/** Where the leader's nose points: a fountain, an old tree, a bridge, a lookout… 300–900 m away, out in the fog if it can be. */
object Places {
    private val ENDPOINTS = listOf("https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter", "https://maps.mail.ru/osm/tools/overpass/api/interpreter")

    private class Kind(val ru: String, val en: String, val items: List<String>, val pic: String)

    private fun kindOf(t: JSONObject): Kind? = when {
        t.optString("amenity") == "fountain" -> Kind("фонтан", "fountain", listOf("coin", "glass"), "fountain")
        t.optString("natural") == "tree" -> Kind("старое дерево", "old tree", listOf("acorn", "leaf", "chestnut", "cone"), "tree")
        t.optString("natural") == "spring" || t.optString("amenity") == "drinking_water" -> Kind("родник", "spring", listOf("shell", "pebble"), "spring")
        t.optString("tourism") == "viewpoint" -> Kind("смотровая", "lookout", listOf("feather", "crystal"), "lookout")
        t.optString("natural") == "peak" -> Kind("вершина", "hilltop", listOf("crystal", "feather"), "hilltop")
        t.optString("bridge") == "yes" -> Kind("мост", "bridge", listOf("pebble", "glass", "key"), "bridge")
        t.optString("historic").isNotEmpty() -> Kind("памятник", "monument", listOf("key", "horseshoe", "coin"), "monument")
        t.optString("amenity") == "place_of_worship" -> Kind("часовня", "chapel", listOf("bell", "key"), "chapel")
        t.optString("tourism") == "artwork" -> Kind("скульптура", "sculpture", listOf("cap", "coin"), "sculpture")
        t.optString("amenity") == "post_box" -> Kind("почтовый ящик", "post box", listOf("postcard"), "postbox")
        t.optString("leisure") == "garden" -> Kind("сад", "garden", listOf("lavender", "leaf"), "garden")
        t.optString("amenity") == "bench" -> Kind("скамейка", "bench", listOf("cap", "cone"), "bench")
        else -> null
    }

    /** Places within [radius] m, or null without the network. */
    suspend fun near(lat: Double, lon: Double, radius: Int = 900): List<Place>? = withContext(Dispatchers.IO) {
        val at = String.format(Locale.US, "%.5f,%.5f", lat, lon)
        val q = "[out:json][timeout:25];(" +
            "node(around:$radius,$at)[amenity~\"^(fountain|place_of_worship|drinking_water|post_box)$\"];" +
            "node(around:$radius,$at)[natural~\"^(tree|spring|peak)$\"][\"name\"];node(around:$radius,$at)[natural=tree][denotation];" +
            "node(around:$radius,$at)[tourism~\"^(viewpoint|artwork)$\"];node(around:$radius,$at)[historic];" +
            "way(around:$radius,$at)[bridge=yes][highway~\"^(footway|path|pedestrian|cycleway)$\"];way(around:$radius,$at)[leisure=garden];" +
            ");out center 200;"
        val body = "data=" + URLEncoder.encode(q, "UTF-8")
        val text = ENDPOINTS.firstNotNullOfOrNull { url ->
            runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 12_000; c.readTimeout = 30_000; c.requestMethod = "POST"; c.doOutput = true
                c.setRequestProperty("User-Agent", "Roamling (app.roamling; github.com/bigdevwhale/roamling)")
                c.outputStream.use { it.write(body.toByteArray()) }
                if (c.responseCode != 200) error("HTTP ${c.responseCode}")
                c.inputStream.bufferedReader().use { it.readText() }
            }.getOrNull()
        } ?: return@withContext null
        runCatching {
            val els = JSONObject(text).getJSONArray("elements")
            (0 until els.length()).mapNotNull { i ->
                val e = els.getJSONObject(i); val t = e.optJSONObject("tags") ?: return@mapNotNull null
                val k = kindOf(t) ?: return@mapNotNull null
                val ru = k.ru; val en = k.en; val items = k.items
                val la = e.optDouble("lat", e.optJSONObject("center")?.optDouble("lat") ?: Double.NaN)
                val lo = e.optDouble("lon", e.optJSONObject("center")?.optDouble("lon") ?: Double.NaN)
                if (la.isNaN()) null else {
                    val nm = t.optString("name")
                    Place(la, lo, if (nm.isNotEmpty()) "$ru «$nm»" else ru, if (nm.isNotEmpty()) "$en «$nm»" else en, items[Math.floorMod(hash("$la$lo"), items.size)], k.pic)
                }
            }
        }.getOrNull()
    }

    /**
     * Picks the day's place: 300–900 m away, preferring ones in the fog. Without the map, a spot in a random
     * direction with «something there».
     */
    fun pick(lat: Double, lon: Double, places: List<Place>?, seed: Int, explored: (Double, Double) -> Boolean): Place {
        val r = Rand(seed)
        val ok = places.orEmpty().filter { Grid.distance(lat, lon, it.lat, it.lon) in 300.0..950.0 }
        if (ok.isNotEmpty()) {
            val fog = ok.filter { !explored(it.lat, it.lon) }
            val pool = fog.ifEmpty { ok }
            return pool[r.int(pool.size)]
        }
        val (la, lo) = Grid.offset(lat, lon, 400.0 + r.next() * 400, r.next() * 360.0)
        val item = Finds.ids[r.int(Finds.ids.size)]
        return Place(la, lo, "что-то интересное", "something interesting", item, "sparkle")
    }
}
