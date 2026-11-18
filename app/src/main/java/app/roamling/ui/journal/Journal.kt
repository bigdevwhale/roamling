package app.roamling.ui.journal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roamling.container
import app.roamling.data.Walk
import app.roamling.data.Waypoint
import app.roamling.data.parseMap
import app.roamling.domain.Deeds
import app.roamling.domain.Finds
import app.roamling.domain.Sky
import app.roamling.domain.Taste
import app.roamling.domain.dayOf
import app.roamling.domain.dayShort
import app.roamling.domain.dur
import app.roamling.domain.fmt
import app.roamling.domain.fmt1
import app.roamling.domain.fmtDayYear
import app.roamling.domain.hourOf
import app.roamling.domain.relDay
import app.roamling.domain.tr
import app.roamling.geo.Grid
import app.roamling.ui.Data
import app.roamling.ui.Nav
import app.roamling.ui.Screen
import app.roamling.ui.art.Ico
import app.roamling.ui.art.Pic
import app.roamling.ui.components.Bars
import app.roamling.ui.components.Empty
import app.roamling.ui.components.Meter
import app.roamling.ui.components.Page
import app.roamling.ui.components.PixTiles
import app.roamling.ui.components.Section
import app.roamling.ui.components.Segments
import app.roamling.ui.components.card
import app.roamling.ui.theme.C
import app.roamling.ui.theme.T
import kotlin.math.max
import kotlin.math.roundToInt

private fun skyOf(w: Walk) = runCatching { Sky.valueOf(w.sky) }.getOrNull()

@Composable
fun JournalScreen(d: Data, nav: Nav) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        app.roamling.ui.art.Banner("banner_journal", tr("Дневник", "Journal"),
            tr("${fmt1(d.km)} км · прогулок ${d.walks.size} · марок ${d.stamps.size}/12", "${fmt1(d.km)} km · ${d.walks.size} walks · ${d.stamps.size}/12 stamps"), Modifier.padding(top = 8.dp)) {
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xCCFBF5E4)).clickable { nav.sheet = "cards" }, contentAlignment = Alignment.Center) {
                    app.roamling.ui.components.Icon(app.roamling.ui.components.Icons.SHARE, Color(0xFF2E2618), 22.dp) }
                Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xCCFBF5E4)).clickable { nav.push(Screen.Settings) }, contentAlignment = Alignment.Center) {
                    app.roamling.ui.components.Icon(app.roamling.ui.components.Icons.GEAR, Color(0xFF2E2618), 24.dp) }
            }
        }
        Segments(listOf(tr("Летопись", "Chronicle"), tr("Прогулки", "Walks"), tr("Марки", "Stamps"), tr("Подвиги", "Deeds"), tr("Итоги", "Totals")), tab, Modifier.padding(top = 6.dp)) { tab = it }
        val c = androidx.compose.ui.platform.LocalContext.current.let { (it.applicationContext as app.roamling.RoamlingApp).container }
        val live by c.walker.live.collectAsState()
        val here = live.fix?.takeIf { it.time > 0 }?.let { app.roamling.domain.Story.sectorOf(it.lat, it.lon) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (tab) {
                0 -> Chronicle(d, here)
                1 -> Walks(d, nav)
                2 -> Stamps(d)
                3 -> DeedsList(d)
                else -> Totals(d)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Walks(d: Data, nav: Nav) {
    if (d.walks.isEmpty()) { Empty(tr("Прогулок ещё не было. Нажмите «Гулять» внизу.", "No walks yet. Press «Walk» below.")); return }
    var last = -1L
    d.walks.forEach { w ->
        val day = dayOf(w.start)
        if (day != last) { Section(relDay(day)); last = day }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).card().clickable { nav.push(Screen.WalkInfo(w.id)) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Pic("stamp_${(skyOf(w) ?: Sky.CLOUDS).stamp}", 46.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("${fmt1(w.meters / 1000)} ${tr("км", "km")} · ${dur(((w.end - w.start) / 1000).toInt())}", style = T.bodyB)
                Text(tr("новых клочков: ${w.patches}", "new patches: ${w.patches}") + (if (w.finds > 0) tr(" · находок: ${w.finds}", " · finds: ${w.finds}") else "") +
                    (skyOf(w)?.let { " · " + it.title.lowercase() } ?: ""), style = T.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TasteStripe(parseMap(w.tastes), Modifier.padding(top = 5.dp))
            }
        }
    }
}

@Composable
fun TasteStripe(m: Map<String, Double>, modifier: Modifier = Modifier) {
    val total = m.filterKeys { k -> Taste.entries.any { it.name == k } }.values.sum()
    if (total <= 0) return
    Row(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50))) {
        Taste.entries.forEach { t -> val v = m[t.name] ?: 0.0; if (v > 0) Box(Modifier.weight((v / total).toFloat()).fillMaxSize().background(Color(0xFF000000 or t.color))) }
    }
}

/** The stamp album: every weather walked in, the ones not yet met as empty places. */
@Composable
private fun Stamps(d: Data) {
    Text(tr("Каждая прогулка получает марку своей погоды. Соберите все двенадцать.", "Each walk gets a stamp of its weather. Collect all twelve."), style = T.small, modifier = Modifier.padding(top = 10.dp))
    Sky.entries.chunked(3).forEach { row ->
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { sky ->
                val n = d.stamps[sky] ?: 0
                Column(Modifier.weight(1f).card().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                        if (n > 0) Pic("stamp_${sky.stamp}", 84.dp) else Box(Modifier.size(70.dp).clip(RoundedCornerShape(6.dp)).background(C.track))
                    }
                    Text(sky.title, style = T.smallB, maxLines = 1)
                    Text(if (n > 0) "×$n" else "—", style = T.tiny)
                }
            }
        }
    }
}

/** Numbers for the ladders. */
fun deedValue(d: Data, id: String): Int = when (id) {
    "km" -> d.km.toInt()
    "patches" -> d.patches
    "hatched" -> d.pets.count { it.stage > 0 && !app.roamling.domain.Catalog[it.species].group.wild } - 1
    "species" -> d.met.size
    "forms" -> d.forms.size
    "stamps" -> d.stamps.size
    "scents" -> d.finds.count { it.ref.startsWith("scent:") && !it.ref.endsWith(":pack") }
    "wild" -> d.pets.count { it.stage > 0 && app.roamling.domain.Catalog[it.species].group.wild }
    "days" -> d.walks.map { dayOf(it.start) }.toSet().size
    else -> 0
}.coerceAtLeast(0)

@Composable
private fun DeedsList(d: Data) {
    Spacer(Modifier.height(8.dp))
    Deeds.ladders.forEach { l ->
        val v = deedValue(d, l.id)
        val done = l.steps.count { v >= it }
        val next = l.steps.firstOrNull { v < it }
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).card().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(l.icon, 36.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(l.title, style = T.bodyB, modifier = Modifier.weight(1f))
                    Text("★".repeat(done) + "☆".repeat(l.steps.size - done), style = T.tiny.copy(color = C.gold))
                }
                Meter(if (next == null) 1f else v / next.toFloat(), Modifier.padding(top = 5.dp), color = C.gold, h = 6.dp)
                Text(if (next == null) tr("Все ступени пройдены: ${fmt(v)}", "Every step done: ${fmt(v)}") else "${fmt(v)} / ${fmt(next)}", style = T.tiny, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun Totals(d: Data) {
    Spacer(Modifier.height(10.dp))
    val steps = d.days.sumOf { it.steps.toLong() }
    PixTiles(listOf(
        Triple(fmt1(d.km), tr("км прогулок", "km walked"), "boots"),
        Triple(fmt(steps), tr("шагов всего", "steps in all"), "paw"),
        Triple(fmt(d.patches), tr("клочков открыто", "patches cleared"), "map"),
        Triple(fmt(d.walks.size), tr("прогулок", "walks"), "scroll"),
        Triple(fmt(d.pets.count { it.stage > 0 }), tr("попутчиков", "companions"), "heart"),
        Triple(fmt(d.finds.size), tr("находок", "finds"), "gift"),
    ))
    Section(tr("Шаги за 14 дней", "Steps over 14 days"))
    val days = (13 downTo 0).map { d.today - it }
    Bars(days.map { day -> dayShort(java.time.LocalDate.ofEpochDay(day).dayOfWeek.value - 1) to (d.days.firstOrNull { it.day == day }?.steps ?: 0).toDouble() }, hi = 13)
    Section(tr("Что ели попутчики", "What the companions ate"))
    val eaten = d.walks.fold(HashMap<String, Double>()) { m, w -> parseMap(w.tastes).forEach { (k, v) -> m[k] = (m[k] ?: 0.0) + v }; m }
    TasteStripe(eaten)
    Taste.entries.forEach { t ->
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(t.icon, 20.dp); Text(t.title, style = T.smallB, modifier = Modifier.weight(1f).padding(start = 8.dp))
            Text("${fmt1((eaten[t.name] ?: 0.0) / 1000)} ${tr("км", "km")}", style = T.small)
        }
    }
    Section(tr("Когда гуляете", "When you walk"))
    val hours = IntArray(24); d.walks.forEach { hours[hourOf(it.start)]++ }
    Bars((0 until 24 step 2).map { h -> "$h" to (hours[h] + hours[h + 1]).toDouble() })
}

@Composable
fun WalkPage(id: Long, d: Data, nav: Nav) {
    val c = LocalContext.current.container
    val w = d.walks.firstOrNull { it.id == id } ?: run { nav.pop(); return }
    val route by produceState<List<Waypoint>>(emptyList(), id) { value = c.dao.route(id) }
    val finds = d.finds.filter { it.at in w.start..w.end }
    Page(fmtDayYear(dayOf(w.start)), { nav.pop() }, action = { ShareButton { nav.sheet = "cards:walk:${w.id}" } }) {
        Box(Modifier.fillMaxWidth().height(240.dp).card(), contentAlignment = Alignment.Center) {
            if (route.size >= 2) Route(route, Modifier.fillMaxSize().padding(16.dp)) else Empty(tr("Маршрута нет", "No route"))
            Pic("stamp_${(skyOf(w) ?: Sky.CLOUDS).stamp}", 60.dp, Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Row(Modifier.padding(top = 12.dp)) {
            listOf(fmt1(w.meters / 1000) to tr("км", "km"), dur(((w.end - w.start) / 1000).toInt()) to tr("в пути", "out"), fmt(w.patches) to tr("новых клочков", "new patches"))
                .forEach { (v, l) -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text(v, style = T.num); Text(l, style = T.tiny, textAlign = TextAlign.Center) } }
        }
        if (w.temp > -200) Text(tr("Погода: ", "Weather: ") + (skyOf(w)?.title?.lowercase() ?: "—") + ", ${w.temp.roundToInt()}°", style = T.small, modifier = Modifier.padding(top = 10.dp))
        Section(tr("Что ели", "What they ate"))
        val m = parseMap(w.tastes)
        TasteStripe(m)
        Taste.entries.filter { (m[it.name] ?: 0.0) > 0 }.forEach { t ->
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Ico(t.icon, 20.dp); Text(t.title, style = T.smallB, modifier = Modifier.weight(1f).padding(start = 8.dp)); Text("${fmt((m[t.name] ?: 0.0).roundToInt())} ${tr("м", "m")}", style = T.small)
            }
        }
        if (finds.isNotEmpty()) {
            Section(tr("Находки", "Finds"))
            finds.forEach { f -> Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) { Pic("find_${f.item}", 36.dp); Text(Finds.name(f.item), style = T.bodyB, modifier = Modifier.padding(start = 10.dp)) } }
        }
    }
}

/** A walk's line, fitted into the box with north up. */
@Composable
private fun Route(pts: List<Waypoint>, modifier: Modifier) {
    val ink = if (C.dark) C.gold else C.wood
    Canvas(modifier) {
        val xs = pts.map { Grid.mx(it.lon) }; val ys = pts.map { Grid.my(it.lat) }
        val x0 = xs.min(); val x1 = xs.max(); val y0 = ys.min(); val y1 = ys.max()
        val k = minOf(size.width / max(x1 - x0, 1e-12), size.height / max(y1 - y0, 1e-12)).toFloat()
        val ox = (size.width - (x1 - x0).toFloat() * k) / 2; val oy = (size.height - (y1 - y0).toFloat() * k) / 2
        fun o(i: Int) = Offset(ox + (xs[i] - x0).toFloat() * k, oy + (ys[i] - y0).toFloat() * k)
        val p = Path().apply { pts.indices.forEach { if (it == 0) moveTo(o(it).x, o(it).y) else lineTo(o(it).x, o(it).y) } }
        drawPath(p, ink, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Color(0xFF4F8A5B), 6.dp.toPx(), o(0)); drawCircle(Color(0xFFC4533A), 6.dp.toPx(), o(pts.lastIndex))
    }
}

/** The postcard button in a page's top bar. */
@Composable
fun ShareButton(onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        app.roamling.ui.components.Icon(app.roamling.ui.components.Icons.SHARE, C.ink, 22.dp)
    }
}
