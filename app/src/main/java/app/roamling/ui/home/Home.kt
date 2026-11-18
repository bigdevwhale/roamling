package app.roamling.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.roamling.domain.District
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.roamling.BuildConfig
import app.roamling.data.Pet
import app.roamling.domain.Heavens
import app.roamling.domain.Rand
import app.roamling.domain.Shop
import app.roamling.domain.Sky
import app.roamling.domain.Weather
import app.roamling.domain.fmt
import app.roamling.domain.tr
import app.roamling.explore.Live
import app.roamling.geo.Grid
import app.roamling.ui.Data
import app.roamling.ui.Nav
import app.roamling.ui.Screen
import app.roamling.ui.art.Art
import app.roamling.ui.art.Ico
import app.roamling.ui.art.PetArt
import app.roamling.ui.art.Pic
import app.roamling.ui.art.Pose
import app.roamling.ui.art.nightFilter
import app.roamling.ui.components.Meter
import app.roamling.ui.components.card
import app.roamling.ui.components.tap
import app.roamling.ui.theme.C
import app.roamling.ui.theme.T
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private val still = BuildConfig.SHOTS

/** The rest stop and the den, side by side: swipe between them. */
@Composable
fun HomeScreen(d: Data, nav: Nav, live: Live) {
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val w = live.weather ?: app.roamling.domain.WeatherApi.last()
    val now = System.currentTimeMillis()
    val lat = live.fix?.lat ?: d.s.lastLat
    val (rise, set) = if (w != null && w.sunrise > 0) w.sunrise to w.sunset else Heavens.sun(now, lat, live.fix?.lon ?: d.s.lastLon)
    val night = Heavens.night(now, rise, set)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopLine(d, w, pager.currentPage) { scope.launch { pager.animateScrollToPage(it) } }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
            if (page == 0) RestStop(d, nav, w, night, now) else Den(d, nav, night)
        }
        Actions(d, nav, live)
    }
}

@Composable
private fun TopLine(d: Data, w: Weather?, page: Int, onPage: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // two tabs, so the den is found without knowing about the swipe
            Row(Modifier.clip(RoundedCornerShape(50)).background(C.track).border(1.5.dp, C.frame, RoundedCornerShape(50)).padding(3.dp)) {
                listOf(tr("Привал", "Rest stop") to "leaf", tr("Логово", "Den") to "door").forEachIndexed { i, (s, ic) ->
                    val on = i == page
                    Row(Modifier.clip(RoundedCornerShape(50)).background(if (on) C.card else androidx.compose.ui.graphics.Color.Transparent).clickable { onPage(i) }
                        .padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Ico(ic, 20.dp, dim = !on)
                        Text(s, style = T.smallB.copy(color = if (on) C.ink else C.mute), modifier = Modifier.padding(start = 5.dp))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (w != null) {
                Pic("stamp_${w.sky.stamp}", 24.dp)
                Text("${w.temp.roundToInt()}°", style = T.smallB, modifier = Modifier.padding(start = 4.dp, end = 10.dp))
            }
            Ico("cone", 22.dp)
            Text(fmt(d.cones), style = T.numS, modifier = Modifier.padding(start = 4.dp))
        }
        val goal = d.s.stepGoal
        ChapterLine(d)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico("boots", 18.dp)
            Meter(d.day.steps / goal.toFloat(), Modifier.weight(1f).padding(horizontal = 8.dp), color = if (d.day.steps >= goal) C.gold else C.acc, h = 7.dp)
            Text("${fmt(d.day.steps)} / ${fmt(goal)}", style = T.tiny)
        }
    }
}

/** The bench on the trail: the backpack's three on it, the eggs peeking out of the backpack, the sky as it is outside. */
@Composable
private fun RestStop(d: Data, nav: Nav, w: Weather?, night: Float, now: Long) {
    val ctx = LocalContext.current
    val bg = Art.get(ctx, "bench_bg")
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(0.dp))) {
        val W = maxWidth; val H = maxHeight
        // the picture fills the width and stands on the bottom; the sky above it is painted
        val ih = W * 1.5f
        val top = H - ih
        Sky(w, night, now, Modifier.fillMaxSize())
        if (bg != null) Canvas(Modifier.fillMaxSize()) {
            val y = size.height - size.width * 1.5f
            drawImage(bg, IntOffset.Zero, IntSize(bg.width, bg.height), IntOffset(0, y.roundToInt()), IntSize(size.width.roundToInt(), (size.width * 1.5f).roundToInt() + 3),
                filterQuality = FilterQuality.Low, colorFilter = if (night > .05f) nightFilter(night) else null)
        }
        fun x(f: Float) = W * f
        fun y(f: Float) = top + ih * f
        // eggs in the backpack
        d.eggs.take(3).forEachIndexed { i, e ->
            val s = W * .11f
            Box(Modifier.offset(x(.165f + i * .05f) - s / 2, y(.69f) - s).size(s).tap { nav.sheet = "team" }) { PetArt(e, s, phase = i * .3f) }
        }
        // the three on the bench: the leader in the middle
        // three seats far enough apart that nobody sits on anybody; the leader in the middle is drawn last, in front
        val seats = listOf(.525f to .23f, .325f to .19f, .725f to .19f)
        d.team.withIndex().sortedByDescending { it.index }.forEach { (i, p) ->
            val (fx, fs) = seats.getOrElse(i) { .5f to .2f }
            val s = W * fs
            val sleeping = night > .8f && i != 0
            Box(Modifier.offset(x(fx) - s / 2, y(.72f) - s).size(s).tap { nav.push(Screen.PetInfo(p.id)) }) {
                PetArt(p, s, pose = if (sleeping) Pose.SLEEP else Pose.IDLE, phase = i * .37f, flip = i == 2, night = night)
            }
        }
        // the Great Map, rolled up on the grass: the chronicle
        Column(Modifier.offset(x(.70f), y(.80f)).tap { nav.tab = 3 }, horizontalAlignment = Alignment.CenterHorizontally) {
            Pic("ic_map", W * .14f)
            Text(tr("Карта · ${d.letters}/12 ✉", "Map · ${d.letters}/12 ✉"), style = T.tiny.copy(color = Color(0xFF2E2618)),
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xDDFBF5E4)).padding(horizontal = 5.dp, vertical = 1.dp))
        }
        // the leader's wish in a bubble, now and then a word about the story instead
        var storyTurn by remember { androidx.compose.runtime.mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(Unit) { if (!BuildConfig.SHOTS) while (true) { kotlinx.coroutines.delay(9000); storyTurn = !storyTurn } }
        val story = remember(d.patches, d.letters, d.s.seals, d.s.finale) { storyLine(d) }
        if (d.leader != null) {
            val first = firstSteps(d)
            val text = first ?: if (storyTurn && story != null) story else if (d.callDone) tr("Сегодня было здорово!", "Today was lovely!") else d.call.title
            Box(Modifier.align(Alignment.TopCenter).padding(top = 12.dp).offset(y = (y(.70f) - W * .23f - 70.dp).coerceAtLeast(8.dp)).widthIn(max = 260.dp)
                .card().tap { if (storyTurn && story != null) nav.tab = 3 else nav.sheet = "call" }.padding(horizontal = 14.dp, vertical = 9.dp)) {
                Text(text, style = T.bodyB, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        // the signpost points the nose's way
        Box(Modifier.offset(x(.78f), y(.46f)).size(W * .2f, ih * .3f).tap { nav.sheet = "scent" })
        // the backpack itself opens the team
        Box(Modifier.offset(x(.1f), y(.66f)).size(W * .2f, ih * .14f).tap { nav.sheet = "team" })
        Weatherfx(w, Modifier.fillMaxSize())
        if (d.team.isEmpty()) Text(tr("Рюкзак пуст – возьмите кого-нибудь из логова", "The backpack is empty – take someone from the den"), style = T.bodyB.copy(color = Color.White),
            modifier = Modifier.align(Alignment.Center).padding(24.dp), textAlign = TextAlign.Center)
    }
}

/** The chapter in one line: the spirit, how far, the dwellers, the letters. Tap – the chronicle. */
@Composable
private fun ChapterLine(d: Data) {
    val home = d.home
    val text = when {
        d.s.finale > 0 -> tr("Хранитель с вами · печатей ${d.s.seals.size}", "The Keeper is with you · seals ${d.s.seals.size}")
        home == null -> tr("Глава 1 начнётся с первой прогулки", "Chapter 1 begins with the first walk")
        home in d.s.seals -> tr("Дом под печатью · печатей ${d.s.seals.size}", "Home is sealed · seals ${d.s.seals.size}")
        else -> { val dist = d.district(home); "${tr("Глава", "Chapter")} ${d.s.seals.size + 1} · ${dist.spirit.title} · ${(dist.share * 100).toInt()}/${(dist.needShare * 100).toInt()}% · ${dist.metCount}/${dist.needMet}" }
    }
    Row(Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(C.card2).clickable { app.roamling.ui.Start.goChronicle?.invoke() }.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Ico("map", 16.dp)
        Text(text, style = T.tiny.copy(color = C.ink), modifier = Modifier.weight(1f).padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("✉ ${d.letters}/12", style = T.tiny.copy(color = C.ink))
    }
}

/** The first steps, told by the leader until they are done. */
private fun firstSteps(d: Data): String? = when {
    d.walks.isEmpty() -> tr("Первый шаг: нажмём «Гулять» внизу – и выйдем в туман?", "First step: press «Walk» below – shall we go out into the fog?")
    d.patches < 10 -> tr("Давай откроем десяток клочков – туман тает, где мы идём.", "Let's clear ten patches – the fog lifts wherever we go.")
    d.pets.none { it.eggTaste.isNotEmpty() } -> tr("В тумане лежат яйца. Ищем на новых улицах!", "Eggs lie in the fog. Let's look on new streets!")
    d.finds.none { it.ref.startsWith("scent:") } -> tr("Я что-то чую! Пойдём по следу – стрелка в «Нюхе».", "I smell something! Let's follow the scent – see «Scent».")
    else -> null
}

/** A word from the leader about the story: the district's spirit, its dwellers, the letters, the Keeper. */
private fun storyLine(d: Data): String? {
    firstSteps(d)?.let { return it }
    if (d.s.finale > 0) return tr("Хранитель говорит: туман сегодня тёплый.", "The Keeper says the fog is warm today.")
    if (d.letters >= 3 && (System.currentTimeMillis() / 60_000 / 7 % 5).toInt() == 4) return tr("Там, в тумане, кто-то есть. Большой. Он не уходит.", "There's someone out in the fog. Someone big. It doesn't go away.")
    val k = d.home ?: return tr("Где-то в тумане спит Дух нашего района. Пойдём его искать?", "Somewhere in the fog our district's spirit is sleeping. Shall we look for it?")
    if (k in d.s.seals) return tr("Дух нашего района теперь узнаёт нас по шагам.", "Our district's spirit knows us by our steps now.")
    val dist = d.district(k)
    val pct = (dist.share * 100).toInt()
    val left = (dist.needMet - dist.metCount).coerceAtLeast(0)
    if (d.missing != null && !d.missedDone && (System.currentTimeMillis() / 60_000 / 7 % 3).toInt() == 0) {
        val sp = d.s.seals[d.missing]?.substringBefore('|')?.let { runCatching { app.roamling.domain.Spirit.valueOf(it) }.getOrNull() }
        if (sp != null) return tr("${sp.title} скучает. Заглянем к нему на этой неделе?", "${sp.en} misses us. Shall we drop by this week?")
    }
    return when ((System.currentTimeMillis() / 60_000 / 7 % 4).toInt()) {
        0 -> if (pct < 10) tr("Слышишь? В тумане спит ${dist.spirit.title.lowercase()}. Туман над районом совсем густой.", "Listen – ${dist.spirit.en} is asleep in the fog. It's thick over the district.")
            else tr("Мы прошли $pct% района из ${(dist.needShare * 100).toInt()}%. Ещё немного – и ${dist.spirit.title.lowercase()} проснётся.", "We've walked $pct% of the district. A bit more and ${dist.spirit.en} will wake.")
        1 -> if (left > 0) tr("В нашем районе живут ещё обитатели. Встретить бы хоть $left…", "More dwellers live in our district. If only we met $left more…")
            else tr("Обитателей мы встретили. Осталось пройти район.", "We've met the dwellers. Now to walk the district.")
        2 -> if (d.letters > 0) tr("Писем уже ${d.letters} из 12. Кто же их пишет?", "${d.letters} letters of 12 so far. Who writes them?") else null
        else -> tr("Картографы ждут: на Большой карте наш район ещё белый.", "The Cartographers are waiting: our district is still white on the Great Map.")
    }
}

/** The den under the old oak: everyone not in the backpack, and the things bought for it. */
@Composable
private fun Den(d: Data, nav: Nav, night: Float) {
    val ctx = LocalContext.current
    val bg = Art.get(ctx, "den_bg")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val W = maxWidth; val H = maxHeight
        val ih = W * 1.5f
        val top = H - ih
        Sky(null, night, System.currentTimeMillis(), Modifier.fillMaxSize())
        if (bg != null) Image(bg, null, Modifier.fillMaxWidth().height(ih + 2.dp).offset(y = top), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Low)
        fun x(f: Float) = W * f
        fun y(f: Float) = top + ih * f
        val decor = Shop.decor.filter { it.id in d.s.owned }
        DENSLOTS.zip(decor).forEach { (pos, th) ->
            val s = W * if (pos.third) .17f else .1f
            Box(Modifier.offset(x(pos.first) - s / 2, y(pos.second) - s).size(s).tap { nav.push(Screen.Shop) }) { Pic("decor_${th.id}", s) }
        }
        // the den's dwellers on the floor, in rows
        val pets = d.den.take(12)
        pets.forEachIndexed { i, p ->
            val row = i / 4; val col = i % 4
            val s = W * (.19f - row * .02f)
            val fx = .16f + col * .225f + (if (row % 2 == 1) .06f else 0f)
            val fy = listOf(.80f, .88f, .73f)[row]
            Box(Modifier.offset(x(fx) - s / 2, y(fy) - s).size(s).tap { nav.push(Screen.PetInfo(p.id)) }) {
                PetArt(p, s, pose = if (night > .7f) Pose.SLEEP else Pose.IDLE, phase = i * .23f, flip = col % 2 == 1)
            }
        }
        if (d.den.isEmpty()) Text(tr("Здесь отдыхают те, кто не в рюкзаке.", "Those not in the backpack rest here."), style = T.small.copy(color = Color(0xFFF1E3C4)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }
}

/** Places for the den's things: shelves (small) and the floor (big). x, foot y, big. */
private val DENSLOTS = listOf(
    Triple(.15f, .545f, false), Triple(.25f, .545f, false), Triple(.15f, .61f, false), Triple(.25f, .61f, false),
    Triple(.63f, .52f, false), Triple(.72f, .52f, false), Triple(.63f, .58f, false), Triple(.71f, .58f, false),
    Triple(.13f, .74f, true), Triple(.87f, .74f, true), Triple(.4f, .68f, true), Triple(.92f, .9f, true),
)

@Composable
private fun Actions(d: Data, nav: Nav, live: Live) {
    val sc = d.s.scent
    val here = live.fix
    val scentText = when {
        sc.day != d.today -> tr("Нюх проснётся на прогулке", "The nose wakes on a walk")
        sc.done -> tr("Найдено сегодня", "Found today")
        here != null && here.time > 0 -> "${fmt(Grid.distance(here.lat, here.lon, sc.lat, sc.lon).roundToInt())} ${tr("м", "m")}"
        else -> app.roamling.domain.bi(sc.place)
    }
    val today = app.roamling.domain.Quest.daily(d.today).count { questProgress(d, it) >= 1f }
    val week = app.roamling.domain.Quest.weekly(d.monday).count { questProgress(d, it) >= 1f }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionTile("compass", tr("Нюх", "Scent"), scentText, Modifier.weight(1f)) { nav.sheet = "scent" }
        ActionTile("scroll", tr("Задания", "Tasks"), tr("сегодня $today/3", "today $today/3"), Modifier.weight(1f)) { nav.sheet = "quests" }
        ActionTile("gift", tr("Лавка", "Shop"), tr("шишки", "cones"), Modifier.weight(1f)) { nav.push(Screen.Shop) }
    }
}

@Composable
private fun ActionTile(icon: String, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.card().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Ico(icon, 30.dp)
        Column(Modifier.padding(start = 6.dp)) {
            Text(title, style = T.smallB, maxLines = 1)
            Text(sub, style = T.tiny, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------- the sky

/** The sky behind a scene: the hour's colours, the sun or the moon, drifting clouds; grey in bad weather. */
@Composable
fun Sky(w: Weather?, night: Float, now: Long, modifier: Modifier) {
    val ctx = LocalContext.current
    val t by rememberInfiniteTransition(label = "sky").animateFloat(0f, 1f, infiniteRepeatable(tween(90_000, easing = LinearEasing), RepeatMode.Restart), label = "c")
    val time = if (still) .3f else t
    val clouds = (0..5).map { Art.get(ctx, "cloud_$it") }
    val grey = when (w?.sky) { Sky.RAIN, Sky.DRIZZLE, Sky.STORM, Sky.FOG, Sky.SNOW, Sky.BLIZZARD -> .7f; Sky.CLOUDS -> .35f; else -> 0f }
    val moonPhase = Heavens.moon(now)
    Canvas(modifier) {
        val day = listOf(Color(0xFF8CC8EE), Color(0xFFCFE9F5))
        val dusk = listOf(Color(0xFF6B6BA8), Color(0xFFF2A97E))
        val dark = listOf(Color(0xFF0E1530), Color(0xFF2A3560))
        fun mix(a: Color, b: Color, k: Float) = Color(a.red + (b.red - a.red) * k, a.green + (b.green - a.green) * k, a.blue + (b.blue - a.blue) * k)
        val twi = (1 - kotlin.math.abs(night - .5f) * 2).coerceIn(0f, 1f)
        var topC = if (night < .5f) mix(day[0], dusk[0], twi) else mix(dark[0], dusk[0], twi)
        var botC = if (night < .5f) mix(day[1], dusk[1], twi) else mix(dark[1], dusk[1], twi)
        val g = Color(0xFF9AA3AD); val gd = Color(0xFF39404A)
        topC = mix(topC, if (night > .5f) gd else g, grey); botC = mix(botC, if (night > .5f) gd else Color(0xFFC3C8CC), grey)
        drawRect(Brush.verticalGradient(listOf(topC, botC)))
        if (night > .6f && grey < .5f) {
            val r = Rand(7)
            repeat(60) { drawCircle(Color.White.copy(alpha = (.4f + r.next() * .6f) * night), 1.2f + r.next() * 1.5f, Offset(r.next() * size.width, r.next() * size.height * .45f)) }
            // the moon in its real phase
            val c = Offset(size.width * .78f, size.height * .12f); val rad = size.width * .06f
            drawCircle(Color(0xFFF6EFD2), rad, c)
            val lit = moonPhase
            val shift = ((lit - .5) * 4).toFloat().coerceIn(-2f, 2f)
            if (kotlin.math.abs(lit - .5) > .04) drawCircle(topC, rad * 1.02f, Offset(c.x + rad * shift, c.y))
        } else if (night < .5f && grey < .6f) {
            drawCircle(Color(0xFFFFE9A8).copy(alpha = .5f), size.width * .1f, Offset(size.width * .2f, size.height * .12f))
            drawCircle(Color(0xFFFFE18A), size.width * .065f, Offset(size.width * .2f, size.height * .12f))
        }
        val n = if (grey > .5f) 6 else if (grey > .2f) 4 else 2
        for (i in 0 until n) {
            val img = clouds[i % 6] ?: continue
            val cw = size.width * (.35f + (i % 3) * .1f); val ch = cw * img.height / img.width
            val x = ((time + i * .29f) % 1f) * (size.width + cw) - cw
            val y = size.height * (.04f + (i * .07f) % .3f)
            drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(x.roundToInt(), y.roundToInt()), IntSize(cw.roundToInt(), ch.roundToInt()),
                alpha = if (night > .5f) .45f else .9f, colorFilter = if (grey > .3f) androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF8E959E), androidx.compose.ui.graphics.BlendMode.Modulate) else null)
        }
    }
}

/** Rain, snow and fog over the whole scene. */
@Composable
fun Weatherfx(w: Weather?, modifier: Modifier) {
    val sky = w?.sky ?: return
    if (sky !in setOf(Sky.RAIN, Sky.DRIZZLE, Sky.STORM, Sky.SNOW, Sky.BLIZZARD, Sky.FOG)) return
    val t by rememberInfiniteTransition(label = "fx").animateFloat(0f, 1f, infiniteRepeatable(tween(if (sky == Sky.SNOW) 9000 else 1400, easing = LinearEasing), RepeatMode.Restart), label = "f")
    val time = if (still) .4f else t
    Canvas(modifier) {
        when (sky) {
            Sky.FOG -> { drawRect(Brush.verticalGradient(listOf(Color(0x00DDE2E6), Color(0x99DDE2E6), Color(0x66DDE2E6)))) }
            Sky.SNOW, Sky.BLIZZARD -> drops(time, 90, true, if (sky == Sky.BLIZZARD) .5f else .12f)
            else -> drops(time, if (sky == Sky.DRIZZLE) 50 else 110, false, .15f)
        }
    }
}

private fun DrawScope.drops(time: Float, n: Int, snow: Boolean, slant: Float) {
    val r = Rand(11)
    repeat(n) {
        val x0 = r.next(); val sp = .6f + r.next() * .6f; val ph = r.next()
        val k = (time * sp + ph) % 1f
        val y = k * size.height
        val x = (x0 * size.width + slant * y + (if (snow) sin((k * 6 + ph * 6) * PI).toFloat() * 8f else 0f)) % size.width
        if (snow) drawCircle(Color.White.copy(alpha = .85f), 2f + r.next() * 2.5f, Offset(x, y))
        else drawLine(Color(0x88CFE3F2), Offset(x, y), Offset(x + slant * 14, y + 14), 2f)
    }
}

/** A companion's portrait in a ring, for lists. */
@Composable
fun PetBadge(p: Pet, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(12.dp)).background(C.card2).border(1.5.dp, C.line, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        PetArt(p, size * .9f)
    }
}
