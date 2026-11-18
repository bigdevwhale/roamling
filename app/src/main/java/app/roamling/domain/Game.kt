package app.roamling.domain

import app.roamling.data.Pet
import app.roamling.data.parseMap
import kotlin.math.max

/** How companions grow. */
object Growth {
    /** Points for a step anywhere; a new patch is worth about five times its steps. */
    const val PER_STEP = 0.01
    const val FRESH_PATCH = 6.0
    const val FIND = 20.0
    const val CALL = 40.0
    const val QUEST = 60.0
    /** Companions get this share of what the leader gets. */
    const val SIDE_SHARE = 0.5
    /** Points to the next stage, from baby and from teen. */
    val NEXT = doubleArrayOf(0.0, 250.0, 900.0)
    /** In the favourite pace growth is faster. */
    const val PACE_BONUS = 1.5

    fun need(stage: Int): Double = NEXT.getOrElse(stage) { Double.MAX_VALUE }

    /** Progress to the next stage, 0..1 (1 when grown). */
    fun progress(p: Pet): Float = when (p.stage) {
        0 -> (1 - p.eggLeft / eggMeters(p.species).coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
        1, 2 -> (p.points / need(p.stage)).toFloat().coerceIn(0f, 1f)
        else -> 1f
    }

    /** Metres an egg wants to be carried. */
    fun eggMeters(species: String): Double = when (Catalog[species].group) {
        Group.LEGEND -> 1_000_000.0
        Group.STARTER -> 1_000.0
        else -> if (species.hashCode() % 3 == 0) 5_000.0 else 2_000.0
    }

    /** 0 slow, 1 stroll, 2 brisk, from metres per second. */
    fun pace(mps: Double): Int = when { mps < 1.0 -> 0; mps < 1.45 -> 1; else -> 2 }

    /** The grown form a teen is heading for by what it has eaten. */
    fun formFor(p: Pet): Int {
        val sp = Catalog[p.species]
        val diet = parseMap(p.diet)
        var best = 0; var score = -1.0
        sp.forms.forEachIndexed { i, f -> val v = diet[f.taste.name] ?: 0.0; if (v > score) { score = v; best = i } }
        return best
    }

    /** Diet shares for the bars, by taste. */
    fun dietShares(p: Pet): Map<Taste, Float> {
        val d = parseMap(p.diet); val sum = max(1.0, d.values.sum())
        return Taste.entries.associateWith { ((d[it.name] ?: 0.0) / sum).toFloat() }
    }

    /** The weather it walked in most (a notable one), for its mark. */
    fun mark(p: Pet): Sky? = parseMap(p.skies).filter { runCatching { Sky.valueOf(it.key).notable }.getOrDefault(false) && it.value >= 1500 }
        .maxByOrNull { it.value }?.let { Sky.valueOf(it.key) }

    fun frame(p: Pet): Int = when (p.stage) { 1 -> 0; 2 -> 1; else -> Catalog[p.species].formFrame(max(0, p.form)) }
}

/** What the hour, the sky and the place are like right now: the wild ones and the calls read this. */
data class Now(
    val ms: Long,
    val weather: Weather?,
    val rise: Long,
    val set: Long,
    val taste: Taste?,
    val lat: Double,
    val rainbow: Boolean,
    val stormAt: Long,
    val homeShare: Double,
    val totalKm: Double,
) {
    val hour get() = hourOf(ms)
    val sunUp get() = ms in rise..set
    val temp get() = weather?.temp ?: 15.0
    val season get() = Heavens.season(ms, lat)
    val month get() = java.time.Instant.ofEpochMilli(ms).atZone(zone).monthValue
    val sky get() = weather?.sky

    fun holds(w: Wild): Boolean = when (w) {
        Wild.DAWN -> ms in rise - 30 * 60_000..rise + 45 * 60_000
        Wild.GOLDEN -> ms in set - 60 * 60_000..set
        Wild.DUSK -> ms in set..set + 70 * 60_000 && temp >= 12
        Wild.FULLMOON -> Heavens.fullMoon(ms) && !sunUp && taste == Taste.GREEN
        Wild.MIDNIGHT -> hour in 0..3
        Wild.CLEAR_NIGHT -> !sunUp && (weather?.clouds ?: 100) < 25
        Wild.RAIN -> sky == Sky.RAIN && taste == Taste.WATER
        Wild.DRIZZLE -> sky == Sky.DRIZZLE
        Wild.FOG -> sky == Sky.FOG
        Wild.BLIZZARD -> sky == Sky.BLIZZARD
        Wild.FROST -> temp <= -5
        Wild.WIND -> (weather?.wind ?: 0.0) >= 10
        Wild.HEAT -> temp >= 28
        Wild.AFTER_STORM -> stormAt > 0 && ms - stormAt in 0..90 * 60_000 && weather?.stormy != true
        Wild.HAIL -> weather?.code?.let { Weather.hail(it) } == true
        Wild.RAINBOW -> rainbow
        Wild.SPRING -> season == 1 && temp >= 10
        Wild.POPLAR -> month == 6 && lat > 35
        Wild.AUTUMN -> season == 3 && month != 11
        Wild.THAW -> season == 0 && temp in 0.5..6.0
        Wild.HALF_DISTRICT -> homeShare >= .5
        Wild.WORLD -> totalKm >= 100
    }

    /** The wild ones that may come now. */
    fun wilds(): List<Species> = SPECIES.filter { it.wild != null && holds(it.wild) }

    /** The weather the walk is walked in, for the diet's Sky and the stamp. */
    fun stamp(): Sky? = if (rainbow) Sky.RAINBOW else sky
}

/** The day's wish of the leader. */
enum class Call(val ru: String, val en: String, val target: Double) {
    WATER("Хочу к воде", "Let's go to the water", 300.0),
    GREEN("Хочу в парк, в зелень", "Let's go somewhere green", 800.0),
    NEW("Пойдём новой улицей", "Let's take a new street", 5.0),
    HEIGHT("Хочу повыше – лестницы, холм", "Let's go up – steps, a hill", 200.0),
    FAR("Давай подальше, километра на три", "Let's go far, three kilometres", 3000.0),
    SUNSET("Давай встретим закат", "Let's meet the sunset", 1.0),
    DAWN("Пойдём на рассвете", "A walk at sunrise", 1.0),
    PUDDLES("Дождь кончится – пойдём мерить лужи", "When the rain stops, let's measure puddles", 1.0),
    STEPS("Давай дойдём до цели шагов", "Let's reach the step goal", 1.0);

    val title get() = tr(ru, en)

    companion object {
        /** The call for [day]: picked by the day, nudged by the forecast. */
        fun of(day: Long, w: Weather?): Call {
            if (w != null && w.raining && w.nextCodes.drop(1).take(4).any { it < 51 }) return PUDDLES
            val pool = listOf(WATER, GREEN, NEW, HEIGHT, FAR, SUNSET, NEW, GREEN, STEPS, WATER, DAWN)
            return pool[Math.floorMod(hash("call$day"), pool.size)]
        }
    }
}

/** A task: three a day and three a week. */
enum class QuestKind(val ru: String, val en: String) {
    PATCHES("Открыть %d новых клочков", "Clear %d new patches"),
    KM("Пройти %d км", "Walk %d km"),
    RAIN("Прогулка в дождь или снег", "A walk in rain or snow"),
    DAWN("Прогулка на рассвете или ночью", "A walk at sunrise or at night"),
    SCENTS("Дойти по нюху %d раз(а)", "Follow the scent %d time(s)"),
    DAYS("Гулять в %d разных дня", "Walk on %d different days"),
    WATER("%d м у воды", "%d m by the water"),
    GREEN("%d м в зелени", "%d m in green places"),
    MINUTES("Гулять %d минут", "Walk for %d minutes"),
    STEPS("%d шагов на прогулках", "%d steps on walks"),
    FAR("Одна прогулка на %d км", "One walk of %d km");
}

data class Quest(val kind: QuestKind, val target: Int, val daily: Boolean) {
    val ref get() = "${if (daily) "d" else "w"}:${kind.name}:$target"
    val title: String get() = when (kind) {
        QuestKind.SCENTS -> if (target == 1) tr("Дойти по нюху", "Follow the scent") else String.format(tr("Дойти по нюху %d раза", "Follow the scent %d times"), target)
        else -> String.format(tr(kind.ru, kind.en), target)
    }
    /** Cones and growth points for the leader. */
    val cones get() = if (daily) 10 else 30
    val points get() = if (daily) Growth.QUEST / 3 else Growth.QUEST

    companion object {
        /** Three tasks for the day [day]. */
        fun daily(day: Long): List<Quest> {
            val r = Rand(hash("daily$day"))
            val all = listOf(Quest(QuestKind.KM, 2 + r.int(2), true), Quest(QuestKind.PATCHES, 3 + r.int(4), true), Quest(QuestKind.SCENTS, 1, true),
                Quest(QuestKind.MINUTES, 20 + r.int(3) * 10, true), Quest(QuestKind.WATER, 200 + r.int(3) * 100, true), Quest(QuestKind.GREEN, 500 + r.int(3) * 250, true),
                Quest(QuestKind.STEPS, 3000 + r.int(3) * 1000, true))
            return all.sortedBy { hash(it.ref + day) }.take(3)
        }

        /** Three tasks for the week starting [monday]. */
        fun weekly(monday: Long): List<Quest> {
            val r = Rand(hash("quests$monday"))
            val all = listOf(Quest(QuestKind.PATCHES, 15 + r.int(4) * 5, false), Quest(QuestKind.KM, 8 + r.int(5) * 2, false), Quest(QuestKind.RAIN, 1, false),
                Quest(QuestKind.DAWN, 1, false), Quest(QuestKind.SCENTS, 2 + r.int(2), false), Quest(QuestKind.DAYS, 3 + r.int(2), false),
                Quest(QuestKind.WATER, 600 + r.int(4) * 200, false), Quest(QuestKind.GREEN, 1500 + r.int(4) * 500, false), Quest(QuestKind.FAR, 4 + r.int(3), false))
            return all.sortedBy { hash(it.ref + monday) }.take(3)
        }
    }
}

/** Little things found at the end of a scent, at a rainbow's end, as gifts. Order is the finds sheet's. */
object Finds {
    val ids = listOf("acorn", "coin", "pebble", "feather", "leaf", "key", "cone", "shell", "cap", "bell", "lavender", "glass", "crystal", "horseshoe", "chestnut", "postcard")
    private val ru = listOf("Жёлудь", "Старая монетка", "Речной камушек", "Перо", "Кленовый лист", "Медный ключик", "Шишка", "Ракушка улитки", "Крышечка", "Колокольчик",
        "Веточка лаванды", "Морское стёклышко", "Кристаллик", "Подкова", "Каштаны", "Открытка")
    private val en = listOf("Acorn", "Old coin", "River pebble", "Feather", "Maple leaf", "Brass key", "Pinecone", "Snail shell", "Bottle cap", "Little bell",
        "Lavender sprig", "Sea glass", "Crystal", "Horseshoe", "Chestnuts", "Postcard")
    fun name(id: String) = ids.indexOf(id).let { if (it < 0) id else tr(ru[it], en[it]) }

    /** Who in the Valley a find goes to: the other lings lost these together with the places. */
    fun recipient(id: String): String = when (id) {
        "coin", "horseshoe" -> tr("Пеннилингам в нору – на удачу", "to the Pennylings' burrow – for luck")
        "postcard", "key" -> tr("Фамильярам – история места для их дерева", "to the Familiars – a place's story for their tree")
        "bell", "cap" -> tr("Сонглингам – звук улицы", "to the Songlings – a sound of the street")
        "feather", "glass" -> tr("Пафлингам – шум ветра для чайного домика", "to the Pufflings – the wind's sound for the tea house")
        "crystal", "shell" -> tr("Инклингам – академия изучает Пелену", "to the Inklings – the academy studies the Veil")
        "pebble", "lavender" -> tr("Квестлингам – в таверне расскажут, откуда он", "to the Questlings – the tavern will tell where it came from")
        else -> tr("Вайлдлингам – их сторожка на самой опушке тумана", "to the Wildlings – their lodge stands at the very edge of the fog")
    }
}

/** Things the cones buy. */
object Shop {
    class Thing(val id: String, val ru: String, val en: String, val price: Int, val kind: Int, val helpRu: String = "", val helpEn: String = "", val sky: Set<Sky> = emptySet()) {
        val name get() = tr(ru, en)
        val help get() = tr(helpRu, helpEn)
    }
    const val GEAR = 0; const val DECOR = 1; const val OTHER = 2
    const val LURE = 30; const val STONE = 200; const val POCKET = 150
    /** Growth in the weather a piece of gear is for. */
    const val GEAR_BONUS = 1.3

    val gear = listOf(
        Thing("raincoat", "Дождевик", "Raincoat", 40, GEAR, "Рост ×1,3 в дождь и морось", "Growth ×1.3 in rain and drizzle", setOf(Sky.RAIN, Sky.DRIZZLE)),
        Thing("boots", "Резиновые сапожки", "Rubber boots", 40, GEAR, "Рост ×1,3 в ливень и после грозы", "Growth ×1.3 in rain and after storms", setOf(Sky.RAIN, Sky.STORM)),
        Thing("scarf", "Шарф", "Scarf", 35, GEAR, "Рост ×1,3 в мороз и ветер", "Growth ×1.3 in frost and wind", setOf(Sky.FROST, Sky.WIND)),
        Thing("sunhat", "Панамка", "Sun hat", 35, GEAR, "Рост ×1,3 в жару и в ясный день", "Growth ×1.3 in heat and on clear days", setOf(Sky.HEAT, Sky.CLEAR)),
        Thing("lantern", "Фонарик", "Lantern", 50, GEAR, "Нюх ночью дальше, рост ×1,3 в тумане", "Farther scent at night, growth ×1.3 in fog", setOf(Sky.FOG)),
        Thing("pack", "Рюкзачок", "Little backpack", 60, GEAR, "Находки по нюху – вдвойне", "Scent finds count twice"),
        Thing("hat", "Шапка с помпоном", "Pompom hat", 35, GEAR, "Рост ×1,3 в снег", "Growth ×1.3 in snow", setOf(Sky.SNOW)),
        Thing("umbrella", "Зонтик", "Umbrella", 45, GEAR, "Рост ×1,3 в грозу и метель", "Growth ×1.3 in storms and blizzards", setOf(Sky.STORM, Sky.BLIZZARD)),
        Thing("compass", "Компас", "Compass", 80, GEAR, "Нюх два раза в день", "Scent twice a day"),
        Thing("mittens", "Варежки", "Mittens", 35, GEAR, "Рост ×1,3 в мороз и метель", "Growth ×1.3 in frost and blizzard", setOf(Sky.FROST, Sky.BLIZZARD)),
        Thing("crown", "Венок", "Flower crown", 40, GEAR, "Сердечки растут быстрее", "Hearts grow faster"),
        Thing("glasses", "Очки от солнца", "Sunglasses", 35, GEAR, "Рост ×1,3 в ясный день", "Growth ×1.3 on clear days", setOf(Sky.CLEAR, Sky.HEAT)),
    )
    val decor = listOf("hammock" to ("Гамак-лист" to "Leaf hammock"), "maps" to ("Стопка карт" to "Pile of maps"), "shelf" to ("Полка с камушками" to "Pebble shelf"), "lamp" to ("Фонарь" to "Lantern stand"),
        "sticks" to ("Посохи" to "Walking sticks"), "cushion" to ("Моховая подушка" to "Moss cushion"), "frame" to ("Карта в рамке" to "Framed map"), "fireflies" to ("Банка светлячков" to "Firefly jar"),
        "teatable" to ("Чайный столик" to "Tea table"), "basket" to ("Корзина желудей" to "Acorn basket"), "garland" to ("Гирлянда из листьев" to "Leaf garland"), "globe" to ("Глобус" to "Globe"),
        "bootpots" to ("Сапоги с цветами" to "Flower boots"), "rug" to ("Плетёный коврик" to "Woven rug"), "books" to ("Книжная полка" to "Bookshelf"), "tent" to ("Шалаш из пледов" to "Blanket tent"))
        .mapIndexed { i, (id, n) -> Thing(id, n.first, n.second, 30 + (i % 4) * 15, DECOR) }
    val all = gear + decor
    val byId = all.associateBy { it.id }
}

/** Long ladders of deeds. */
object Deeds {
    class Ladder(val id: String, val ru: String, val en: String, val steps: List<Int>, val icon: String) { val title get() = tr(ru, en) }
    val ladders = listOf(
        Ladder("km", "Километры", "Kilometres", listOf(1, 5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000), "boots"),
        Ladder("patches", "Новые клочки", "New patches", listOf(10, 50, 100, 250, 500, 1000, 2500, 5000), "map"),
        Ladder("hatched", "Вылупились", "Hatched", listOf(1, 3, 5, 10, 20, 40), "paw"),
        Ladder("species", "Виды в бестиарии", "Species met", listOf(3, 5, 10, 20, 30, 40, 50), "book"),
        Ladder("forms", "Взрослые формы", "Grown forms", listOf(1, 5, 10, 25, 50, 100), "leaf"),
        Ladder("stamps", "Марки погоды", "Weather stamps", listOf(3, 6, 9, 12), "cloud"),
        Ladder("scents", "По нюху", "Scents followed", listOf(1, 10, 30, 100, 300), "compass"),
        Ladder("wild", "Дикие попутчики", "Wild companions", listOf(1, 5, 10, 20, 30), "moon"),
        Ladder("days", "Дни с прогулкой", "Days with a walk", listOf(7, 30, 100, 365, 1000), "heart"),
    )
}
