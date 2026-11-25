package app.roamling.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** The day's scent: where the leader's nose points, and what waits there. */
data class Scent(val day: Long = 0, val lat: Double = 0.0, val lon: Double = 0.0, val place: String = "", val item: String = "", val done: Boolean = false, val n: Int = 0, val kind: String = "sparkle") {
    fun json() = JSONObject().put("day", day).put("lat", lat).put("lon", lon).put("place", place).put("item", item).put("done", done).put("n", n).put("kind", kind)
    companion object { fun of(o: JSONObject?) = if (o == null) Scent() else Scent(o.optLong("day"), o.optDouble("lat", 0.0), o.optDouble("lon", 0.0), o.optString("place"), o.optString("item"), o.optBoolean("done"), o.optInt("n"), o.optString("kind", "sparkle")) }
}

/** A wild one seen on the map: come close and it joins. */
/**
 * A companion let go to live at a place (#7, #11): it stays yours and in the bestiary, just not in the den.
 * [taste] is the place's taste when it settled; [home] is true when that is the taste it loves.
 */
data class Resident(val pet: Long, val lat: Double, val lon: Double, val at: Long, val taste: String = "", val home: Boolean = false) {
    fun json() = JSONObject().put("pet", pet).put("lat", lat).put("lon", lon).put("at", at).put("taste", taste).put("home", home)
    companion object { fun of(o: JSONObject) = Resident(o.optLong("pet"), o.optDouble("lat"), o.optDouble("lon"), o.optLong("at"), o.optString("taste"), o.optBoolean("home")) }
}

data class Shadow(val species: String = "", val lat: Double = 0.0, val lon: Double = 0.0, val until: Long = 0, val lured: Boolean = false) {
    fun json() = JSONObject().put("s", species).put("lat", lat).put("lon", lon).put("until", until).put("lured", lured)
    companion object { fun of(o: JSONObject) = Shadow(o.optString("s"), o.optDouble("lat"), o.optDouble("lon"), o.optLong("until"), o.optBoolean("lured")) }
}

data class Settings(
    val onboarded: Boolean = false,
    val theme: String = "system",
    /** Backpack: the leader first, then two companions (pet ids). */
    val team: List<Long> = emptyList(),
    /** Eggs warming in the backpack's pockets. */
    val eggs: List<Long> = emptyList(),
    val eggSlots: Int = 2,
    val stepGoal: Int = 7000,
    val homeLat: Double = 0.0,
    val homeLon: Double = 0.0,
    val lastLat: Double = 0.0,
    val lastLon: Double = 0.0,
    val walking: Boolean = false,
    val walkId: Long = 0,
    /** Things bought: gear, decor, the third pocket. */
    val owned: Set<String> = emptySet(),
    val lures: Int = 0,
    val stones: Int = 0,
    val scent: Scent = Scent(),
    val shadows: List<Shadow> = emptyList(),
    /** "day:species" of wild ones already met today. */
    val met: Set<String> = emptySet(),
    /** Rainbow's end until this moment, and where. */
    val rainbowUntil: Long = 0,
    val rainbowLat: Double = 0.0,
    val rainbowLon: Double = 0.0,
    /** When it last stormed, for the ozone hour. */
    val stormAt: Long = 0,
    val seen: Set<String> = emptySet(),
    /** News already told: "grow:<id>:<stage>", "hatch:<id>"… */
    val told: Set<String> = emptySet(),
    val health: Boolean = false,
    val pedometer: Boolean = true,
    val notify: Boolean = true,
    /** Town for the weather when no fix is known yet. */
    val decor: Map<String, Int> = emptyMap(),
    /** Closed chapters: district key → "SPIRIT|season|at". */
    val seals: Map<Long, String> = emptyMap(),
    /** When the Keeper of the fog came out, 0 before. */
    val finale: Long = 0,
    /** Bottled breezes in the backpack, and until when the one let out blows (#13). */
    val breezes: Int = 0,
    val breezeUntil: Long = 0,
    /** Twins let go to live at a place on the map (#7, #11). */
    val residents: List<Resident> = emptyList(),
) {
    fun json(): JSONObject = JSONObject().apply {
        put("onboarded", onboarded); put("theme", theme); put("team", JSONArray(team)); put("eggs", JSONArray(eggs)); put("eggSlots", eggSlots)
        put("stepGoal", stepGoal); put("homeLat", homeLat); put("homeLon", homeLon); put("lastLat", lastLat); put("lastLon", lastLon)
        put("walking", walking); put("walkId", walkId); put("owned", JSONArray(owned.toList())); put("lures", lures); put("stones", stones)
        put("scent", scent.json()); put("shadows", JSONArray(shadows.map { it.json() })); put("met", JSONArray(met.toList()))
        put("rainbowUntil", rainbowUntil); put("rainbowLat", rainbowLat); put("rainbowLon", rainbowLon); put("stormAt", stormAt)
        put("seen", JSONArray(seen.toList())); put("told", JSONArray(told.toList())); put("health", health); put("pedometer", pedometer); put("notify", notify)
        put("decor", JSONObject().apply { decor.forEach { (k, v) -> put(k, v) } })
        put("seals", JSONObject().apply { seals.forEach { (k, v) -> put(k.toString(), v) } }); put("finale", finale)
        put("breezes", breezes); put("breezeUntil", breezeUntil); put("residents", JSONArray(residents.map { it.json() }))
    }

    companion object {
        fun of(j: JSONObject): Settings {
            fun list(k: String) = j.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            fun longs(k: String) = j.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getLong(it) } } ?: emptyList()
            return Settings(
                j.optBoolean("onboarded"), j.optString("theme", "system"), longs("team"), longs("eggs"), j.optInt("eggSlots", 2),
                j.optInt("stepGoal", 7000), j.optDouble("homeLat", 0.0), j.optDouble("homeLon", 0.0), j.optDouble("lastLat", 0.0), j.optDouble("lastLon", 0.0),
                j.optBoolean("walking"), j.optLong("walkId"), list("owned").toSet(), j.optInt("lures"), j.optInt("stones"),
                Scent.of(j.optJSONObject("scent")), j.optJSONArray("shadows")?.let { a -> (0 until a.length()).map { Shadow.of(a.getJSONObject(it)) } } ?: emptyList(),
                list("met").toSet(), j.optLong("rainbowUntil"), j.optDouble("rainbowLat", 0.0), j.optDouble("rainbowLon", 0.0), j.optLong("stormAt"),
                list("seen").toSet(), list("told").toSet(), j.optBoolean("health"), j.optBoolean("pedometer", true), j.optBoolean("notify", true),
                j.optJSONObject("decor")?.let { o -> o.keys().asSequence().associateWith { o.getInt(it) } } ?: emptyMap(),
                j.optJSONObject("seals")?.let { o -> o.keys().asSequence().associate { it.toLong() to o.getString(it) } } ?: emptyMap(),
                j.optLong("finale"),
                j.optInt("breezes"), j.optLong("breezeUntil"),
                j.optJSONArray("residents")?.let { a -> (0 until a.length()).map { Resident.of(a.getJSONObject(it)) } } ?: emptyList(),
            )
        }
    }
}

private val Context.store by preferencesDataStore("roamling")
private val KEY = stringPreferencesKey("settings")

class SettingsStore(private val ctx: Context) {
    val settings: Flow<Settings> = ctx.store.data.map { p -> p[KEY]?.let { runCatching { Settings.of(JSONObject(it)) }.getOrNull() } ?: Settings() }
    suspend fun now(): Settings = settings.first()
    suspend fun update(f: (Settings) -> Settings) { ctx.store.edit { p ->
        val cur = p[KEY]?.let { runCatching { Settings.of(JSONObject(it)) }.getOrNull() } ?: Settings()
        p[KEY] = f(cur).json().toString() } }
}
