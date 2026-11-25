package app.roamling.ui.beasts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roamling.container
import app.roamling.data.Pet
import app.roamling.data.parseMap
import app.roamling.domain.Group
import app.roamling.domain.Growth
import app.roamling.domain.Sky
import app.roamling.domain.Species
import app.roamling.domain.Story
import app.roamling.domain.Taste
import app.roamling.domain.dayOf
import app.roamling.domain.fmt
import app.roamling.domain.fmt1
import app.roamling.domain.fmtDayYear
import app.roamling.domain.paceName
import app.roamling.domain.tr
import app.roamling.ui.Data
import app.roamling.ui.art.Art
import app.roamling.ui.art.Ico
import app.roamling.ui.art.Mini
import app.roamling.ui.art.PetArt
import app.roamling.ui.art.Pic
import app.roamling.ui.art.Pose
import app.roamling.ui.components.Meter
import app.roamling.ui.components.card
import app.roamling.ui.theme.C
import app.roamling.ui.theme.T
import kotlinx.coroutines.launch
import kotlin.math.sin

/** The world a companion belongs to: its grown form's taste, else what it ate most, else its kind. */
fun habitatOf(p: Pet, sp: Species): String {
    val t: Taste? = when {
        p.stage == 3 && p.form >= 0 -> sp.forms[p.form].taste
        p.stage == 2 -> parseMap(p.diet).maxByOrNull { it.value }?.key?.let { runCatching { Taste.valueOf(it) }.getOrNull() }
        else -> null
    }
    return "habitat_" + when (t) {
        Taste.GREEN -> "green"; Taste.WATER -> "water"; Taste.STONE -> "stone"; Taste.HEIGHT -> "height"; Taste.DUSK -> "dusk"; Taste.SKY -> "sky"
        null -> when (sp.group) { Group.WATER -> "water"; Group.STONE -> "stone"; Group.HEIGHT -> "height"; Group.TIME, Group.LEGEND -> "dusk"; Group.WEATHER -> "sky"; else -> "green" }
    }
}

/**
 * The companion in its own world. A tap makes it jump for joy with hearts flying up; the name and stage sit on a little tag.
 */
@Composable
fun PetHero(p: Pet, sp: Species, stage: String, pattedToday: Boolean, onSpecies: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.container
    val bg = Art.get(ctx, habitatOf(p, sp))
    val scope = rememberCoroutineScope()
    val joy = remember { Animatable(0f) }
    var pets by remember { mutableIntStateOf(0) }
    // a pat: it jumps for joy; the first one of the day warms its heart (#12)
    val onPat = {
        pets++
        scope.launch { joy.snapTo(0f); joy.animateTo(1f, tween(1400)) }
        if (!pattedToday && p.stage > 0) c.write { if (this.pat(p)) say(tr("${p.name} жмурится от радости · +¼ сердечка", "${p.name} beams with joy · +¼ heart")) }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)).background(C.card2)) {
        val W = maxWidth; val H = maxHeight
        if (bg != null) androidx.compose.foundation.Image(bg, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low)
        val s = W * .56f
        Box(Modifier.align(Alignment.BottomCenter).offset(y = -(H * .1f)).size(s).clickable(remember { MutableInteractionSource() }, null) { onPat() }) {
            PetArt(p, s, pose = if (joy.value in .01f..0.99f) Pose.CHEER else Pose.IDLE)
        }
        // hearts float up while it is happy
        if (joy.value in .01f..0.99f) Canvas(Modifier.fillMaxSize()) {
            val k = joy.value
            repeat(6) { i ->
                val x = size.width * (.35f + .3f * ((i * 37 + pets * 13) % 10) / 10f) + sin((k * 6 + i).toDouble()).toFloat() * 10f
                val y = size.height * (.7f - k * (.45f + i * .04f))
                val a = (1 - k).coerceIn(0f, 1f)
                val r = 7.dp.toPx()
                drawCircle(Color(0xFFE5484D).copy(alpha = a), r, Offset(x - r * .55f, y))
                drawCircle(Color(0xFFE5484D).copy(alpha = a), r, Offset(x + r * .55f, y))
                drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(x - r * 1.45f, y + r * .2f); lineTo(x, y + r * 1.9f); lineTo(x + r * 1.45f, y + r * .2f); close() }, Color(0xFFE5484D).copy(alpha = a))
            }
        }
        Row(Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(50)).background(Color(0xE6FBF5E4)).clickable(onClick = onSpecies)
            .padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${sp.name} · $stage", style = T.smallB.copy(color = Color(0xFF2E2618)))
            if (p.shiny) Text(" ✦", style = T.smallB.copy(color = C.gold))
        }
        if (p.gear.isNotEmpty()) Pic("gear_${p.gear}", 46.dp, Modifier.align(Alignment.TopEnd).padding(8.dp))
        // a real button, not a hint: people pressed the hint and nothing happened (#12)
        if (p.stage > 0) Row(Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(50))
            .background(if (pattedToday) Color(0x66000000) else Color(0xE6FBF5E4)).clickable { onPat() }.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Ico("heart", 16.dp)
            Text(if (pattedToday) tr("сегодня уже гладили", "patted today") else tr("Погладить", "Give a pat"),
                style = (if (pattedToday) T.tiny else T.smallB).copy(color = if (pattedToday) Color(0xFFFBF5E4) else Color(0xFF2E2618)), modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/**
 * Its way: baby → teen → the grown forms branching off. Where it stands is outlined; the form its diet leads to glows;
 * unknown forms are silhouettes. A teen's diet sits above as one coloured stripe.
 */
@Composable
fun EvolutionTree(p: Pet, sp: Species, d: Data) {
    val heading = if (p.stage == 2) Growth.formFor(p) else -1
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).card().padding(12.dp)) {
        Text(tr("Путь развития", "Its way"), style = T.h3)
        if (p.stage in 1..2) {
            val left = Growth.need(p.stage) - p.points
            Meter(Growth.progress(p), Modifier.padding(top = 6.dp), h = 7.dp)
            Text(tr("до ${if (p.stage == 1) "подростка" else "взрослого"} ~${fmt((left / Growth.PER_STEP).toInt())} шагов – новые клочки быстрее",
                "~${fmt((left / Growth.PER_STEP).toInt())} steps to ${if (p.stage == 1) "teen" else "grown"} – new patches are faster"), style = T.tiny)
        }
        if (p.stage >= 2) {
            val sh = Growth.dietShares(p)
            Row(Modifier.padding(top = 8.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(C.track)) {
                Taste.entries.forEach { t -> val v = sh[t] ?: 0f; if (v > 0f) Box(Modifier.weight(v).fillMaxSize().background(Color(0xFF000000 or t.color))) }
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Taste.entries.filter { (sh[it] ?: 0f) >= .05f }.sortedByDescending { sh[it] }.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) { Ico(t.icon, 14.dp); Text("${((sh[t] ?: 0f) * 100).toInt()}%", style = T.tiny, modifier = Modifier.padding(start = 2.dp)) }
                }
            }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Node(sp.id, 0, tr("Малыш", "Baby"), p.stage == 1, p.stage >= 1, false)
            Arrow()
            Node(sp.id, 1, tr("Подросток", "Teen"), p.stage == 2, p.stage >= 2 || "${sp.id}:t" in d.forms || d.pets.any { it.species == sp.id && it.stage >= 2 }, false)
            Arrow()
            // the branches
            Column(Modifier.weight(1f).drawBehind {
                val x = 2.dp.toPx()
                drawLine(C.line, Offset(x, size.height * .12f), Offset(x, size.height * .88f), 2.dp.toPx())
            }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sp.forms.forEachIndexed { i, f ->
                    val mine = p.stage == 3 && p.form == i
                    val seen = mine || "${sp.id}:$i" in d.forms
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.drawBehind {
                        drawLine(C.line, Offset(2.dp.toPx(), size.height / 2), Offset(10.dp.toPx(), size.height / 2), 2.dp.toPx())
                    }.padding(start = 10.dp)) {
                        val glow = i == heading
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(if (glow) C.goldSoft else C.card2)
                            .border(if (mine || glow) 2.dp else 1.dp, if (mine) C.acc else if (glow) C.gold else C.line, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            Mini(sp.id, 40.dp, dim = !seen, frame = sp.formFrame(i), silhouette = true)
                        }
                        Ico(f.taste.icon, 16.dp, Modifier.padding(start = 6.dp))
                        Text(if (seen) f.name else "???", style = if (mine) T.smallB else T.small.copy(color = C.ink), modifier = Modifier.padding(start = 4.dp).weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (glow) Text(tr("← сюда", "← here"), style = T.tiny.copy(color = C.gold))
                    }
                }
            }
        }
    }
}

@Composable
private fun Node(kind: String, frame: Int, label: String, now: Boolean, seen: Boolean, glow: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(if (glow) C.goldSoft else C.card2)
            .border(if (now) 2.dp else 1.dp, if (now) C.acc else C.line, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Mini(kind, 46.dp, dim = !seen, frame = frame, silhouette = true)
        }
        Text(label, style = T.tiny.copy(color = if (now) C.ink else C.mute), textAlign = TextAlign.Center)
    }
}

@Composable
private fun Arrow() = Text("›", style = T.h2.copy(color = C.mute), modifier = Modifier.padding(horizontal = 3.dp))

/** A companion's passport: where it was born, how long and how far together, its weather, its hearts. A stamp in the corner. */
@Composable
fun Passport(p: Pet, sp: Species, d: Data) {
    val born = if (p.lat == 0.0 && p.lon == 0.0) null else Story.sectorOf(p.lat, p.lon)
    val bornIn = when {
        born == null -> tr("пришёл из Долины", "came from the Valley")
        else -> {
            val sealed = d.s.seals[born]?.substringBefore('|')?.let { runCatching { app.roamling.domain.Spirit.valueOf(it) }.getOrNull() }
            val spirit = sealed ?: d.district(born).spirit
            "${spirit.title} · ${spirit.kind}"
        }
    }
    val days = (d.today - dayOf(p.born)).coerceAtLeast(0) + 1
    val weather = Growth.mark(p) ?: parseMap(p.skies).maxByOrNull { it.value }?.key?.let { runCatching { Sky.valueOf(it) }.getOrNull() }
    Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.fillMaxWidth().card(color = C.goldSoft).padding(14.dp)) {
            Text(tr("Паспорт попутчика", "Companion's passport").uppercase(), style = T.sec)
            Spacer(Modifier.height(8.dp))
            Line(tr("Родина", "Born"), bornIn)
            Line(tr("Вместе", "Together"), tr("$days ${app.roamling.domain.plural(days, "день", "дня", "дней")} · ${fmt1(p.meters / 1000)} км", "$days days · ${fmt1(p.meters / 1000)} km"))
            Line(tr("С", "Since"), fmtDayYear(dayOf(p.born)))
            Line(tr("Темп", "Pace"), paceName(sp.pace))
            Line(tr("Погода", "Weather"), weather?.title?.lowercase() ?: tr("ещё не выбрал", "not chosen yet"))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(10) { i -> Ico("heart", 16.dp, dim = i >= p.bond.toInt()) }
            }
        }
        if (weather != null) Pic("stamp_${weather.stamp}", 62.dp, Modifier.align(Alignment.TopEnd).padding(10.dp).rotate(8f))
    }
}

@Composable
private fun Line(k: String, v: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(k, style = T.small, modifier = Modifier.width(74.dp))
        Text(v, style = T.smallB, modifier = Modifier.padding(end = 70.dp))
    }
}
