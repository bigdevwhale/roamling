package app.roamling.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.PI
import kotlin.math.floor

/** A weather, as a stamp in the album. Order is the album's. */
enum class Sky(val ru: String, val en: String, val stamp: Int) {
    CLEAR("Ясно", "Clear", 0),
    CLOUDS("Облака", "Clouds", 1),
    RAIN("Ливень", "Rain", 2),
    DRIZZLE("Морось", "Drizzle", 3),
    FOG("Туман", "Fog", 4),
    WIND("Ветер", "Wind", 5),
    SNOW("Снег", "Snow", 6),
    BLIZZARD("Метель", "Blizzard", 7),
    FROST("Мороз", "Frost", 8),
    HEAT("Жара", "Heat", 9),
    STORM("Гроза", "Thunderstorm", 10),
    RAINBOW("Радуга", "Rainbow", 11);

    val title get() = tr(ru, en)
    /** Weathers that feed the Sky taste: anything but a plain day. */
    val notable get() = this != CLEAR && this != CLOUDS
}

/** The weather now and the next hours, from Open-Meteo. */
data class Weather(
    val at: Long,
    val code: Int,
    val temp: Double,
    val wind: Double,          // m/s
    val clouds: Int,           // %
    val isDay: Boolean,
    val sunrise: Long,
    val sunset: Long,
    /** Codes of the next hours, the first being the current hour. */
    val nextCodes: List<Int> = emptyList(),
    val nextTemps: List<Double> = emptyList(),
    /** Precipitation over the past two hours, mm. */
    val pastRain: Double = 0.0,
    val pastStorm: Boolean = false,
) {
    val sky: Sky get() = skyOf(code, temp, wind)
    val raining get() = code in 51..67 || code in 80..82
    val stormy get() = code >= 95

    companion object {
        fun skyOf(code: Int, temp: Double, wind: Double): Sky = when {
            code == 96 || code == 99 -> Sky.STORM
            code >= 95 -> Sky.STORM
            code in 71..77 || code in 85..86 -> if (wind >= 9) Sky.BLIZZARD else Sky.SNOW
            code in 51..57 -> Sky.DRIZZLE
            code in 61..67 || code in 80..82 -> Sky.RAIN
            code == 45 || code == 48 -> Sky.FOG
            wind >= 10 -> Sky.WIND
            temp <= -5 -> Sky.FROST
            temp >= 28 -> Sky.HEAT
            code <= 1 -> Sky.CLEAR
            else -> Sky.CLOUDS
        }
        fun hail(code: Int) = code == 96 || code == 99
    }
}

object WeatherApi {
    @Volatile private var cached: Weather? = null
    @Volatile private var cachedAt = Pair(0.0, 0.0)

    fun last(): Weather? = cached

    /** The weather at a point, cached for 15 minutes and 3 km; null without the network. */
    suspend fun at(lat: Double, lon: Double, force: Boolean = false): Weather? = withContext(Dispatchers.IO) {
        val c = cached
        if (!force && c != null && System.currentTimeMillis() - c.at < 15 * 60_000 &&
            app.roamling.geo.Grid.distance(lat, lon, cachedAt.first, cachedAt.second) < 3000) return@withContext c
        val url = String.format(Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code,wind_speed_10m,cloud_cover,is_day" +
                "&hourly=weather_code,temperature_2m,precipitation&past_hours=2&forecast_hours=12&daily=sunrise,sunset&forecast_days=1&wind_speed_unit=ms&timezone=auto&timeformat=unixtime", lat, lon)
        val w = runCatching {
            val con = URL(url).openConnection() as HttpURLConnection
            con.connectTimeout = 10_000; con.readTimeout = 15_000
            con.setRequestProperty("User-Agent", "Roamling (app.roamling; github.com/bigdevwhale/roamling)")
            val o = JSONObject(con.inputStream.bufferedReader().use { it.readText() })
            val cur = o.getJSONObject("current")
            val h = o.getJSONObject("hourly")
            val times = h.getJSONArray("time"); val codes = h.getJSONArray("weather_code"); val temps = h.getJSONArray("temperature_2m"); val prec = h.getJSONArray("precipitation")
            val now = System.currentTimeMillis() / 1000
            var past = 0.0; var storm = false
            val nc = ArrayList<Int>(); val nt = ArrayList<Double>()
            for (i in 0 until times.length()) {
                val t = times.getLong(i)
                if (t + 3600 <= now) { past += prec.optDouble(i, 0.0); if (codes.optInt(i) >= 95) storm = true }
                else { nc += codes.optInt(i); nt += temps.optDouble(i, 0.0) }
            }
            val d = o.getJSONObject("daily")
            Weather(System.currentTimeMillis(), cur.getInt("weather_code"), cur.getDouble("temperature_2m"), cur.getDouble("wind_speed_10m"), cur.optInt("cloud_cover"),
                cur.optInt("is_day", 1) == 1, d.getJSONArray("sunrise").getLong(0) * 1000, d.getJSONArray("sunset").getLong(0) * 1000, nc, nt, past, storm)
        }.getOrNull()
        if (w != null) { cached = w; cachedAt = lat to lon }
        w ?: c
    }

    /** For the demo: a weather made up on the spot. */
    fun fake(w: Weather) { cached = w }
}

/** The sky's clock: moon phase and the parts of the day, computed on the phone. */
object Heavens {
    /** Moon phase 0..1: 0 new, 0.5 full. */
    fun moon(ms: Long): Double {
        val days = (ms - 947182440000L) / 86_400_000.0 // new moon 2000-01-06 18:14 UTC
        val p = days / 29.530588853
        return p - floor(p)
    }

    fun fullMoon(ms: Long) = moon(ms).let { it in .45..0.55 }

    /** 0 new … 7 waning crescent, for a picture. */
    fun moonIcon(ms: Long) = (moon(ms) * 8 + .5).toInt() % 8

    /** A rough sunrise and sunset (local ms) when no weather has told them. */
    fun sun(ms: Long, lat: Double, lon: Double): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val n = date.dayOfYear
        val decl = 23.44 * Math.sin(2 * PI / 365 * (n - 81))
        val ha = Math.toDegrees(Math.acos((-Math.tan(Math.toRadians(lat)) * Math.tan(Math.toRadians(decl))).coerceIn(-1.0, 1.0)))
        val noonUtc = 12 - lon / 15
        val rise = (noonUtc - ha / 15) * 3_600_000; val set = (noonUtc + ha / 15) * 3_600_000
        val midnight = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        return (midnight + rise).toLong() to (midnight + set).toLong()
    }

    fun season(ms: Long, lat: Double): Int { // 0 winter, 1 spring, 2 summer, 3 autumn
        val m = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()).monthValue
        val north = when (m) { 12, 1, 2 -> 0; 3, 4, 5 -> 1; 6, 7, 8 -> 2; else -> 3 }
        return if (lat < 0) (north + 2) % 4 else north
    }

    /** How dark it is, 0 day … 1 night, with twilight between; for the scene's sky. */
    fun night(ms: Long, rise: Long, set: Long): Float {
        val tw = 50 * 60_000L
        return when {
            ms in rise + tw..set - tw -> 0f
            ms in rise - tw until rise + tw -> (1 - (ms - (rise - tw)) / (2f * tw)).coerceIn(0f, 1f)
            ms in set - tw until set + tw -> ((ms - (set - tw)) / (2f * tw)).coerceIn(0f, 1f)
            else -> 1f
        }
    }
}
