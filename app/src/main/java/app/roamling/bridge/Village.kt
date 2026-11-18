package app.roamling.bridge

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import app.roamling.BuildConfig
import app.roamling.RoamlingApp
import app.roamling.AppContainer
import app.roamling.domain.Growth
import app.roamling.domain.Lang
import app.roamling.domain.SPECIES
import app.roamling.domain.hash
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The bridge to Lingvale, the village where the pets of all the -ling apps live together.
 *
 * Lingvale asks through ContentResolver.call() and nobody else gets an answer: the caller must be Lingvale signed
 * with its release key (a debug or demo build also answers a Lingvale built with the same debug key). It only reads;
 * nothing here changes the app's data. Protocol v1, the same in every -ling app:
 *  - "hello"    → {v}
 *  - "snapshot" → {json}: pets, species, days of activity, the last week's events
 *  - "art"      → {bytes}: one picture from res/drawable-nodpi (pet_*, item_*, prop_*, *_bg)
 */
class VillageProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        if (!trusted(ctx)) throw SecurityException("Only Lingvale may ask")
        return when (method) {
            "hello" -> Bundle().apply { putInt("v", VERSION) }
            "snapshot" -> Bundle().apply { putString("json", runBlocking { snapshot(ctx, container(ctx)) }.toString()) }
            "art" -> art(ctx, arg)
            else -> null
        }
    }

    private fun trusted(ctx: Context): Boolean {
        val pkg = callingPackage ?: return false
        if (pkg != HUB && !pkg.startsWith("$HUB.")) return false
        val pm = ctx.packageManager
        if (HUB_CERTS.any { pm.hasSigningCertificate(pkg, it, PackageManager.CERT_INPUT_SHA256) }) return true
        return BuildConfig.DEBUG && pm.checkSignatures(pkg, ctx.packageName) == PackageManager.SIGNATURE_MATCH
    }

    private fun art(ctx: Context, name: String?): Bundle? {
        if (name == null || !ART.matches(name)) return null
        @Suppress("DiscouragedApi")
        val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
        if (id == 0) return null
        return Bundle().apply { putByteArray("bytes", ctx.resources.openRawResource(id).use { it.readBytes() }) }
    }

    /** The provider can be asked before the application has finished starting; wait for it a little. */
    private fun container(ctx: Context): AppContainer {
        repeat(100) {
            runCatching { return (ctx.applicationContext as RoamlingApp).container }
            Thread.sleep(30)
        }
        error("not ready")
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        const val VERSION = 1
        const val HUB = "app.lingvale"
        /** SHA-256 of Lingvale's release certificate. */
        private val HUB_CERTS = listOf(hex("CEF40A1786896D829CFA53115693AC9A53FC0349EAB84BAF64EFA648592D1ABF"))
        private val ART = Regex("""(pet|egg|find|decor|gear)_[a-z0-9_]+|[a-z]+_bg""")
        private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        private fun color(c: Long) = "#%06X".format(c and 0xFFFFFF)
        private fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

        /** Everything Lingvale shows of Roamling: the companions, the days walked, the last week's walks. */
        suspend fun snapshot(ctx: Context, c: AppContainer): JSONObject {
            val s = c.settings.now()
            val pets = c.dao.petList()
            val walks = c.dao.walkList()
            val days = c.dao.dayList()
            val today = LocalDate.now().toEpochDay()
            val petsJson = JSONArray()
            pets.forEach { p ->
                petsJson.put(JSONObject().put("id", p.id).put("species", p.species).put("name", p.name).put("stage", p.stage).put("progress", Growth.progress(p).toDouble())
                    .put("slot", s.team.indexOf(p.id)).put("shiny", p.shiny).put("found", p.born).put("hatched", p.born)
                    // The picture to draw: a grown companion is one of its species' forms (pet_<species>_2…5), not frame 2.
                    .put("frame", if (p.stage > 0) Growth.frame(p) else -1))
            }
            val speciesJson = JSONArray()
            SPECIES.forEach { k ->
                speciesJson.put(JSONObject().put("id", k.id).put("ru", k.ru).put("en", k.en).put("color", color(hash(k.id).toLong() and 0x7F7F7FL or 0x404040L))
                    .put("aboutRu", k.aboutRu).put("aboutEn", k.aboutEn).put("rare", k.group.wild))
            }
            val daysJson = JSONArray()
            days.filter { it.day > today - 400 && (it.steps > 0 || it.meters > 0) }.forEach { d ->
                val min = walks.filter { dayOf(it.start) == d.day }.sumOf { (it.end - it.start) / 60_000 }.toInt()
                daysJson.put(JSONObject().put("d", d.day).put("n", walks.count { dayOf(it.start) == d.day }.coerceAtLeast(if (d.steps > 0) 1 else 0)).put("min", min))
            }
            val eventsJson = JSONArray()
            walks.filter { dayOf(it.start) > today - 8 }.forEach { w ->
                val km = "%.1f".format(w.meters / 1000)
                eventsJson.put(JSONObject().put("at", w.start).put("min", ((w.end - w.start) / 60_000).toInt()).put("ru", "Прогулка: $km км, новых клочков ${w.patches}").put("en", "A walk: $km km, ${w.patches} new patches"))
            }
            return JSONObject()
                .put("v", VERSION).put("app", "roamling").put("version", BuildConfig.VERSION_NAME)
                .put("ru", Lang.ru).put("scene", "bench_bg")
                .put("pets", petsJson).put("species", speciesJson).put("days", daysJson).put("events", eventsJson)
        }
    }
}
