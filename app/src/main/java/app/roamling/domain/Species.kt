package app.roamling.domain

/** What a stretch of a walk tastes of. The first four come from the map, the last two from the hour and the sky. */
enum class Taste(val ru: String, val en: String, val color: Long, val icon: String) {
    GREEN("Зелень", "Green", 0x6E9F55, "leaf"),
    WATER("Вода", "Water", 0x4F8FC0, "drop"),
    STONE("Камень", "Stone", 0x8C8579, "stone"),
    HEIGHT("Высота", "Height", 0xA0784C, "peak"),
    DUSK("Сумрак", "Dusk", 0x6A5FA8, "moon"),
    SKY("Небо", "Sky", 0x5FA8B5, "cloud");

    val title get() = tr(ru, en)
}

enum class Group(val ru: String, val en: String) {
    STARTER("Стартовые", "Starters"),
    GREEN("Парк и лес", "Park and wood"),
    WATER("Вода", "Water"),
    STONE("Город", "Town"),
    HEIGHT("Высота", "Heights"),
    TIME("Время суток", "Time of day"),
    WEATHER("Погода", "Weather"),
    SEASON("Сезоны", "Seasons"),
    LEGEND("Легендарные", "Legendary");

    val title get() = tr(ru, en)
    /** Wild ones come by their conditions, the rest hatch from eggs. */
    val wild get() = this == TIME || this == WEATHER || this == SEASON || this == LEGEND
}

/** The condition a wild species comes in. */
enum class Wild(val ru: String, val en: String) {
    DAWN("Рассвет", "Sunrise"),
    GOLDEN("Золотой час", "Golden hour"),
    DUSK("Тёплые сумерки", "Warm dusk"),
    FULLMOON("Полнолуние, ночь, парк", "Full moon, night, park"),
    MIDNIGHT("После полуночи", "After midnight"),
    CLEAR_NIGHT("Ясная ночь", "Clear night"),
    RAIN("Дождь у воды", "Rain by water"),
    DRIZZLE("Морось", "Drizzle"),
    FOG("Туман", "Fog"),
    BLIZZARD("Метель", "Blizzard"),
    FROST("Мороз ниже −5", "Frost below −5"),
    WIND("Сильный ветер", "Strong wind"),
    HEAT("Жара за +28", "Heat above +28"),
    AFTER_STORM("Час после грозы", "The hour after a storm"),
    HAIL("Град", "Hail"),
    RAINBOW("Радуга", "Rainbow"),
    SPRING("Тёплая весна", "Warm spring"),
    POPLAR("Тополиный пух", "Poplar fluff"),
    AUTUMN("Листопад", "Leaf fall"),
    THAW("Оттепель", "Thaw"),
    HALF_DISTRICT("Половина района", "Half the district"),
    WORLD("40 075 км", "40,075 km");

    val title get() = tr(ru, en)
}

class Form(val taste: Taste, val ru: String, val en: String) { val name get() = tr(ru, en) }

class Species(
    val id: String, val ru: String, val en: String, val group: Group,
    /** 0 slow, 1 stroll, 2 brisk. */
    val pace: Int,
    val forms: List<Form>,
    val wild: Wild?,
    val aboutRu: String, val aboutEn: String,
) {
    val name get() = tr(ru, en)
    val about get() = tr(aboutRu, aboutEn)
    /** Sheet cell of the grown form [i]. */
    fun formFrame(i: Int) = 2 + i.coerceIn(0, forms.size - 1)
}

object Catalog {
    val byId = SPECIES.associateBy { it.id }
    operator fun get(id: String): Species = byId[id] ?: SPECIES.first()
    fun of(g: Group) = SPECIES.filter { it.group == g }
    val starters get() = of(Group.STARTER)
}

fun paceName(p: Int) = when (p) { 0 -> tr("неспешный", "slow"); 1 -> tr("прогулочный", "strolling"); else -> tr("бодрый", "brisk") }
