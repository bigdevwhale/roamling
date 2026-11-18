package app.roamling.domain

import app.roamling.data.Pet
import app.roamling.data.Walk
import app.roamling.geo.Grid

/** The family of a district's spirit, by what the district tastes of most. */
enum class Spirit(val ru: String, val en: String, val seal: Int, val aboutRu: String, val aboutEn: String) {
    // the kind of district each spirit keeps, for the map
    GREEN("Лешачок-садовник", "The Gardener", 0, "Дух скверов и парков: в бороде мох, в ветвях – птичьи гнёзда.", "Spirit of squares and parks: moss in its beard, nests in its branches."),
    WATER("Выдра-мельничиха", "The Miller Otter", 1, "Дух набережных и прудов: крутит своё колесо, пока по берегу кто-то ходит.", "Spirit of quays and ponds: it turns its wheel while someone walks the bank."),
    STONE("Домовой улиц", "The Street Brownie", 2, "Дух дворов и мостовых: у него ключи от всех подъездов района.", "Spirit of yards and cobbles: it keeps the keys to every doorway of the district."),
    HEIGHT("Ветреник с колокольни", "The Belfry Wind", 3, "Дух холмов, лестниц и крыш: смотрит на район в подзорную трубу.", "Spirit of hills, stairs and roofs: it watches the district through a spyglass."),
    CROSS("Хозяйка перекрёстка", "The Crossroads Keeper", 4, "Дух мест, где всего понемногу: в карманах – чужие письма, в руке – фонарь.", "Spirit of places with a bit of everything: pockets full of letters, a lantern in hand.");

    val title get() = tr(ru, en)
    val about get() = tr(aboutRu, aboutEn)
    /** What the spirit says when it wakes: its piece of the Keeper's story. */
    val tale get() = when (this) {
        GREEN -> tr("Я его помню. Когда он уставал ждать, то засыпал у меня под сиренью. Я укрывал его листьями, а он мои аллеи – туманом. Так мы и берегли друг друга.",
            "I remember it. When it grew tired of waiting, it fell asleep under my lilac. I covered it with leaves, and it covered my alleys with fog. That's how we kept each other.")
        WATER -> tr("Он часто сидел у воды и смотрел на мост. Говорил: однажды по нему снова пройдут двое. Сегодня прошли.",
            "It often sat by the water and watched the bridge. It said: one day two will walk across it again. Today they did.")
        STONE -> tr("У меня ключи от всех подъездов, а его двери я так и не нашёл. Он жил на улице – на той самой тропе, где его оставили.",
            "I hold the keys to every doorway, yet I never found its door. It lived in the street – on the very path where it was left.")
        HEIGHT -> tr("С колокольни видно весь город. Каждый вечер я смотрел, как туман становится чуть плотнее. А теперь вижу, как он редеет – там, где идёте вы.",
            "From the belfry you can see the whole town. Every evening I watched the fog grow a little thicker. Now I see it thinning – wherever you walk.")
        CROSS -> tr("В моих карманах – письма, которые никто не забрал. Были там и его. Похоже, теперь они нашли адресата.",
            "My pockets hold letters nobody collected. Some of them were its. It seems they've found their reader at last.")
    }
    /** What the spirit leaves when it has missed you. */
    val gift get() = when (this) { GREEN -> "leaf"; WATER -> "shell"; STONE -> "key"; HEIGHT -> "feather"; CROSS -> "postcard" }
    /** What kind of district it is, in two words. */
    val kind get() = when (this) {
        GREEN -> tr("зелёный район", "green district"); WATER -> tr("у воды", "by the water"); STONE -> tr("дворы и мостовые", "yards and cobbles")
        HEIGHT -> tr("холмы и крыши", "hills and roofs"); CROSS -> tr("смешанный", "mixed")
    }
    /** One word for the map, and its icon. */
    val short get() = when (this) { GREEN -> tr("Зелень", "Green"); WATER -> tr("Вода", "Water"); STONE -> tr("Дворы", "Yards"); HEIGHT -> tr("Холмы", "Hills"); CROSS -> tr("Смешанный", "Mixed") }
    val icon get() = when (this) { GREEN -> "leaf"; WATER -> "drop"; STONE -> "stone"; HEIGHT -> "peak"; CROSS -> "compass" }
    /** Its colour on the map. */
    val color: Long get() = when (this) { GREEN -> 0xFF5E9A4A; WATER -> 0xFF3E86C0; STONE -> 0xFFB0603E; HEIGHT -> 0xFF6E7A8E; CROSS -> 0xFFD09A2E }
    val art get() = "spirit_" + when (this) { GREEN -> "green"; WATER -> "water"; STONE -> "stone"; HEIGHT -> "height"; CROSS -> "cross" }
}

/** A chapter of the story: one district (a zoom-14 square, about 1.5 km) of the walker's town. */
class District(val key: Long, val spirit: Spirit, val residents: List<Species>, val patches: Int, val met: Set<String>, val first: Boolean = false) {
    val share get() = patches / TOTAL.toFloat()
    val metCount get() = residents.count { it.id in met }
    /** What closes this chapter: the first one is lighter, so the first big moment comes in a week or two. */
    val needShare get() = if (first) FIRST_SHARE else SHARE
    val needMet get() = if (first) FIRST_MET else MET
    val ready get() = share >= needShare && metCount >= needMet

    companion object {
        /** Patches in a district: 16 × 16. */
        const val TOTAL = 256
        /** Half of a district's patches walked … */
        const val SHARE = .5f
        /** … and three of its dwellers met there close a chapter. */
        const val MET = 3
        const val FIRST_SHARE = .4f
        const val FIRST_MET = 2
    }
}

object Story {
    fun sectorOf(lat: Double, lon: Double) = Grid.sector(Grid.cell(lat, lon))
    fun sectorOfPatch(patch: Long): Long { val d = Tastes.PATCH_Z - Grid.SECTOR_Z; return Grid.key(Grid.x(patch) shr d, Grid.y(patch) shr d) }

    /** The spirit of a district: the taste most of its patches have; a mix when none leads. */
    fun spirit(sector: Long, patchTastes: List<String>): Spirit {
        val known = patchTastes.filter { it.isNotEmpty() }
        if (known.size < 6) return Spirit.CROSS
        val top = known.groupingBy { it }.eachCount().maxBy { it.value }
        if (top.value < known.size * .45) return Spirit.CROSS
        return when (top.key) { "GREEN" -> Spirit.GREEN; "WATER" -> Spirit.WATER; "HEIGHT" -> Spirit.HEIGHT; else -> Spirit.STONE }
    }

    /** Who lives in a district: four that hatch from its eggs, by its spirit, and one wild one of its weathers. Fixed per district. */
    fun residents(sector: Long, spirit: Spirit): List<Species> {
        val r = Rand(hash("res$sector"))
        val pool = when (spirit) {
            Spirit.GREEN -> Catalog.of(Group.GREEN)
            Spirit.WATER -> Catalog.of(Group.WATER)
            Spirit.STONE -> Catalog.of(Group.STONE)
            Spirit.HEIGHT -> Catalog.of(Group.HEIGHT) + Catalog.of(Group.STONE).take(4)
            Spirit.CROSS -> Catalog.of(Group.GREEN) + Catalog.of(Group.WATER) + Catalog.of(Group.STONE)
        }
        val eggs = pool.sortedBy { hash(it.id + sector) }.take(4)
        val wild = (Catalog.of(Group.TIME) + Catalog.of(Group.WEATHER)).filter { it.wild != Wild.RAINBOW }
        return eggs + wild[r.int(wild.size)]
    }

    /** The species met inside [sector]: hatched or joined there. */
    fun metIn(sector: Long, pets: List<Pet>): Set<String> =
        pets.filter { it.stage > 0 && (it.lat != 0.0 || it.lon != 0.0) && sectorOf(it.lat, it.lon) == sector }.map { it.species }.toSet()

    /** The spirit of a district from the map of the whole district when it is known, else from the patches walked. Null: not known yet. */
    fun spiritOf(sector: Long, walked: List<String>): Spirit? {
        val all = Tastes.ofSector(sector)
        return when {
            all != null -> spirit(sector, all)
            walked.count { it.isNotEmpty() } >= 6 -> spirit(sector, walked)
            else -> null
        }
    }

    fun district(sector: Long, patchKeys: List<Pair<Long, String>>, pets: List<Pet>, first: Boolean = false): District {
        val inside = patchKeys.filter { sectorOfPatch(it.first) == sector }
        val sp = spiritOf(sector, inside.map { it.second }) ?: Spirit.CROSS
        return District(sector, sp, residents(sector, sp), inside.size, metIn(sector, pets), first)
    }

    /** The home district: around the home mark, else the one walked most. */
    fun home(homeLat: Double, homeLon: Double, patchKeys: List<Long>): Long? =
        if (homeLat != 0.0 || homeLon != 0.0) sectorOf(homeLat, homeLon)
        else patchKeys.groupingBy { sectorOfPatch(it) }.eachCount().maxByOrNull { it.value }?.key

    // ------------------------------------------------------------------ the letters

    /** The milestones that each bring a letter, with when they happened (null: not yet). */
    /**
     * The twelve milestones that each bring a letter, with when they happened (null: not yet). All can be reached
     * without leaving the home district: the walks, the companions, a quarter of home walked, the first seal, 50 km, 20 walks.
     */
    fun milestones(walks: List<Walk>, pets: List<Pet>, scentAt: Long?, seals: Map<Long, String>, metCount: Int, homeQuarterAt: Long?): List<Long?> {
        val sorted = walks.sortedBy { it.start }
        var km = 0.0; var tenKm: Long? = null
        for (w in sorted) { km += w.meters / 1000; if (km >= 10 && tenKm == null) tenKm = w.end }
        val hatched = pets.filter { it.stage > 0 && it.eggTaste.isNotEmpty() }.minOfOrNull { it.born }
        val wild = pets.filter { it.stage > 0 && Catalog[it.species].group.wild }.minOfOrNull { it.born }
        val grown = pets.filter { it.stage == 3 && it.grownAt > 0 }.minOfOrNull { it.grownAt }
        val notable = sorted.firstOrNull { runCatching { Sky.valueOf(it.sky).notable }.getOrDefault(false) }?.end
        val tenSpecies = if (metCount >= 10) pets.filter { it.stage > 0 }.sortedBy { it.born }.distinctBy { it.species }.getOrNull(9)?.born else null
        val firstSeal = seals.values.mapNotNull { it.split('|').getOrNull(2)?.toLongOrNull() }.minOrNull()
        km = 0.0; var fiftyKm: Long? = null
        for (w in sorted) { km += w.meters / 1000; if (km >= 50 && fiftyKm == null) fiftyKm = w.end }
        val twentyWalks = sorted.getOrNull(19)?.end
        return listOf(sorted.firstOrNull()?.end, hatched, scentAt, wild, grown, tenKm, notable, tenSpecies, homeQuarterAt, firstSeal, fiftyKm, twentyWalks)
    }

    /** When a quarter of the home district had been walked: the 64th patch's first visit. */
    fun quarterAt(home: Long?, patches: List<Pair<Long, Long>>): Long? =
        if (home == null) null else patches.filter { sectorOfPatch(it.first) == home }.map { it.second }.sorted().getOrNull(District.TOTAL / 4 - 1)

    // ------------------------------------------------------------------ a sealed district that misses you

    /** This week's sealed district that misses you: one of them, by the week. */
    fun missing(monday: Long, seals: Collection<Long>): Long? = seals.sorted().takeIf { it.size > 0 }?.let { it[Math.floorMod(hash("miss$monday"), it.size)] }

    /** Patches of [sector] walked this week (from [monday]); five bring the spirit's gift. */
    fun walkedThisWeek(sector: Long, monday: Long, patches: List<Triple<Long, Long, Int>>): Int =
        patches.count { sectorOfPatch(it.first) == sector && it.second >= monday }

    const val MISS_PATCHES = 5

    /** How many letters have come: they arrive in the story's order, one per milestone reached. */
    fun lettersCome(milestones: List<Long?>): Int = milestones.count { it != null }

    val letters: List<Pair<String, String>> = listOf(
        "Если ты это читаешь – значит, ты снова гуляешь. Здравствуй. Ты меня не знаешь. Когда-то я ходил этими же улицами – и тоже не один." to
            "If you are reading this, you are out walking again. Hello. You don't know me. Once I walked these same streets – and not alone either.",
        "У нас тоже был первый малыш. Вылупился у моста и тут же чихнул – туман защекотал ему нос. Мы смеялись до самого дома." to
            "We had a first little one too. It hatched by the bridge and sneezed at the fog straight away. We laughed all the way home.",
        "Он всегда знал, куда идти: нос по ветру, хвост трубой. А я шёл следом. Так и повелось: он ищет, я запоминаю." to
            "It always knew where to go: nose to the wind, tail up. And I always followed. That's how it was – it sniffed, I remembered.",
        "Дикие приходят не к тем, кто за ними гоняется, а к тем, кто вышел в их погоду. Мой пришёл в метель. А ведь я чуть не остался дома." to
            "Wild ones don't come to those who chase them, but to those who go out in their weather. Mine came in a blizzard. I nearly stayed home that day.",
        "Когда он вырос, я понял: он стал похож на наши прогулки – на реку, на старые дворы, на вечерние улицы. Попутчики вырастают из дорог." to
            "When it grew up I understood: it looked like our walks. Like the river, the old yards, the evening. Companions grow out of roads.",
        "Мы прошли десять километров, потом сто. Потом я сбился со счёта. А он – нет. Попутчики помнят каждый шаг." to
            "We walked ten kilometres together, then a hundred. I stopped counting. It didn't. Companions remember every step.",
        "Как-то раз мы вышли под дождь. Город стал другим – тише и ярче. С тех пор я не боюсь непогоды: просто её мало кто видит." to
            "One day we went out in the rain. The town became different – quieter and brighter. Since then I'm not afraid of bad weather: few people ever see it.",
        "У нас были свои места: скамейка в сквере, мост, лестница к реке. Он всегда сворачивал к ним сам – будто проверял, всё ли на месте." to
            "We had our own places: a bench in the square, the bridge, the stairs down to the river. It always turned to them by itself – as if checking that all was still there.",
        "А потом стало некогда. Ничего плохого не случилось – я просто перестал выходить. А он остался на тропе." to
            "Then there was never any time. Nothing bad happened. I just stopped going out. And it stayed on the path.",
        "Он ждал. А чтобы наши места не растаяли, укрыл их собой: сквер, мост, лестницу к реке. Так и появился туман." to
            "It waited. So that our places would not fade, it covered them with itself: the square, the bridge, the stairs by the river. That is how the fog came.",
        "Туман – это его плащ. Он соткан из прогулок, на которые мы так и не вышли. Туман не прячет город – он его бережёт." to
            "The fog is its cloak. It is woven from every walk not taken. It doesn't hide the town – it keeps it safe.",
        "Вернёшь мой квартал – и он выйдет к тебе. Не бойся: он попросит только об одном. Иногда возвращайся туда, где вы уже были." to
            "If you bring my district back, it will come out to you. Don't be afraid: it will ask only one thing – go sometimes to the places you've already been.",
    )
    val letterTitles = listOf(
        "Здравствуй" to "Hello",
        "Первый малыш" to "The first little one",
        "Нос по ветру" to "Nose to the wind",
        "В метель" to "In the blizzard",
        "Из дорог" to "Out of the roads",
        "Каждый шаг" to "Every step",
        "Под дождём" to "In the rain",
        "Наши места" to "Our places",
        "Некогда" to "No time",
        "Он ждал" to "It waited",
        "Плащ" to "The cloak",
        "Только об одном" to "Just one thing",
    )
}
