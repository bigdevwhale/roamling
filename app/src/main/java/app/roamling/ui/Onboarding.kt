package app.roamling.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.roamling.container
import app.roamling.domain.Catalog
import app.roamling.domain.paceName
import app.roamling.domain.tr
import app.roamling.ui.art.Creature
import app.roamling.ui.art.Ico
import app.roamling.ui.art.Mini
import app.roamling.ui.art.Pose
import app.roamling.ui.components.Btn
import app.roamling.ui.components.Field
import app.roamling.ui.theme.C
import app.roamling.ui.theme.T

@Composable
fun Onboarding() {
    val c = LocalContext.current.container
    var step by rememberSaveable { mutableIntStateOf(0) }
    var cleared by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var pick by rememberSaveable { mutableIntStateOf(0) }
    var located by remember { mutableStateOf(c.feed.permitted()) }
    val starters = Catalog.starters
    var name by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
        if (step == 4) Scene("prologue_pick", Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)))
        Column(Modifier.fillMaxSize().then(if (step == 4) Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp) else Modifier)) {
            if (step == 0) {
                LivingValley(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(C.card2)) {
                    val sh = androidx.compose.ui.graphics.Shadow(androidx.compose.ui.graphics.Color(0x99000000), androidx.compose.ui.geometry.Offset(0f, 2f), 6f)
                    Column(Modifier.align(Alignment.Center).padding(horizontal = 22.dp)) {
                        Text(tr("Долина лингов", "The Valley of the lings"), style = T.title.copy(color = androidx.compose.ui.graphics.Color(0xFFFFF4DC), shadow = sh))
                        Text(tr("Здесь живут линги – маленькие существа, которые рождаются из внимания человека: из прочитанных страниц, песен, шагов. А вокруг Долины – Пелена: туман, в котором гаснут улицы, по которым никто не ходит. Ваш город – почти весь в тумане.",
                            "The lings live here – little creatures born of a person's attention: of pages read, songs, steps. Around the Valley lies the Veil: a fog in which streets nobody walks go dark. Your town is almost all fog."),
                            style = T.body.copy(color = androidx.compose.ui.graphics.Color(0xFFFFF4DC), shadow = sh), modifier = Modifier.padding(top = 10.dp))
                    }
                }
            } else if (step == 1) {
                Text(tr("Сотрите туман пальцем", "Wipe the fog away"), style = T.h2, modifier = Modifier.padding(top = 8.dp))
                Box(Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp).clip(RoundedCornerShape(14.dp))) {
                    FogScratch("prologue_veil", .4f, Modifier.fillMaxSize()) { cleared = it }
                }
                if (cleared < .4f) {
                    app.roamling.ui.components.Meter(cleared / .4f, Modifier.padding(top = 12.dp), h = 8.dp)
                    Text(tr("Открыто ${(cleared * 100).toInt()}% из 40%", "Cleared ${(cleared * 100).toInt()}% of 40%"), style = T.small, modifier = Modifier.padding(top = 4.dp))
                } else {
                    Text(tr("Дух района проснулся и поставил печать!", "The district's spirit woke up and set its seal!"), style = T.h3, modifier = Modifier.padding(top = 12.dp))
                    Text(tr("Так и будет в игре: вы гуляете – туман тает, Духи районов просыпаются, город вспоминает себя. А кто укрыл его туманом – расскажут письма на тропах.",
                        "That's the game: you walk – the fog lifts, district spirits wake, the town remembers itself. And who covered it in fog – the letters on the paths will tell."),
                        style = T.body, modifier = Modifier.padding(top = 6.dp))
                }
            } else if (step == 2) {
                CreatureReveal()
            } else if (step == 3) {
                LocationPage(located)
            } else {
                val sh = androidx.compose.ui.graphics.Shadow(androidx.compose.ui.graphics.Color(0x99000000), androidx.compose.ui.geometry.Offset(0f, 2f), 6f)
                Text(tr("Кто пойдёт с вами в туман?", "Who goes into the fog with you?"), style = T.h2.copy(color = androidx.compose.ui.graphics.Color(0xFFFFF4DC), shadow = sh), modifier = Modifier.padding(top = 120.dp, start = 4.dp))
                Text(tr("Попутчик растёт от прогулок. Без него в туман не выйти.", "A companion grows as you walk. Without one you can't go into the fog."),
                    style = T.small.copy(color = androidx.compose.ui.graphics.Color(0xFFFFF4DC), shadow = sh), modifier = Modifier.padding(start = 4.dp))
                starters.forEachIndexed { i, sp ->
                    val on = i == pick
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(if (on) C.accSoft.copy(alpha = .95f) else C.card.copy(alpha = .93f))
                        .border(2.dp, if (on) C.acc else C.line, RoundedCornerShape(12.dp)).clickable { pick = i }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (on) Creature(sp.id, 0, 84.dp, pose = Pose.CHEER) else Mini(sp.id, 84.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(sp.name, style = T.h3)
                            Text(sp.about, style = T.small.copy(color = C.ink))
                            Text(tr("Темп: ", "Pace: ") + paceName(sp.pace) + " · " + sp.forms.joinToString(", ") { it.taste.title.lowercase() }, style = T.tiny, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
                Field(name, { name = it.take(20) }, tr("Имя (можно потом)", "Name (can wait)"), Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(12.dp)).background(C.card.copy(alpha = .93f)))
            }
        }
        }
        Spacer(Modifier.height(10.dp))
        Btn(when { step == 1 && cleared < .4f -> tr("Пропустить", "Skip"); step == 3 && !located -> tr("Разрешить", "Allow"); step < 4 -> tr("Дальше", "Next"); else -> tr("В путь", "Let's go") }, Modifier.fillMaxWidth(),
            color = if (step == 1 && cleared < .4f) C.track else C.acc, ink = if (step == 1 && cleared < .4f) C.ink else C.accInk) {
            if (step == 3 && !located) {
                // the location is asked for here, at the start: without it the fog can't lift, and there's no weather or sky
                val perms = buildList {
                    add(android.Manifest.permission.ACCESS_FINE_LOCATION); add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (android.os.Build.VERSION.SDK_INT >= 29) add(android.Manifest.permission.ACTIVITY_RECOGNITION)
                    if (android.os.Build.VERSION.SDK_INT >= 33) add(android.Manifest.permission.POST_NOTIFICATIONS)
                }
                app.roamling.MainActivity.ask(perms) {
                    located = c.feed.permitted()
                    if (located) { c.feed.restart(); c.locateOnce(); step++ }
                }
            } else if (step < 4) step++ else {
                val sp = starters[pick]
                c.write { begin(sp.id, name.trim().ifEmpty { sp.name }) }
            }
        }
        Text(tr("Всё хранится только на телефоне.", "Everything stays on the phone."), style = T.tiny, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center)
    }
}

/**
 * Who lives in the fog: twelve puffs; a tap brings out a real creature of the game. Twelve picked to show the range –
 * park, water, town, hills, night, weather, a legend.
 */
@Composable
private fun ColumnScope.CreatureReveal() {
    val shown = remember { listOf("acorn", "splash", "lanternmoth", "tiledrake", "moonhare", "fogling", "ivy", "quayotter", "eaglet", "rainbow", "dovefox", "fogkeeper") }
    var open by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(Unit) { while (open < shown.size) { kotlinx.coroutines.delay(260); open++ } }
    Text(tr("Кто живёт в тумане?", "Who lives in the fog?"), style = T.h2, modifier = Modifier.padding(top = 8.dp))
    Column(Modifier.weight(1f).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.chunked(3).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { k, id ->
                    val i = r * 3 + k
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(C.card2), contentAlignment = Alignment.Center) {
                        val s = minOf(maxWidth, maxHeight) * .92f
                        if (i < open) Creature(id, 2, s, pose = Pose.IDLE, phase = i * .17f) else app.roamling.ui.art.Mystery(id, s)
                    }
                }
            }
        }
    }
    Text(tr("${SPECIES_COUNT} видов и почти 200 взрослых форм. Каждый вырастает по-своему – смотря где вы гуляли. А дикие приходят сами – только в свою погоду.",
        "${SPECIES_COUNT} species and nearly 200 grown forms. Each grows its own way – by where you walked. And wild ones come by themselves – only in their weather."),
        style = T.body, modifier = Modifier.padding(top = 10.dp))
}

private val SPECIES_COUNT get() = app.roamling.domain.SPECIES.size

/** Where you walk: why the location is needed, in three short lines. */
@Composable
private fun ColumnScope.LocationPage(located: Boolean) {
    Text(tr("Где вы гуляете?", "Where do you walk?"), style = T.h2, modifier = Modifier.padding(top = 8.dp))
    Scene("prologue_where", Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp).clip(RoundedCornerShape(14.dp)))
    Point("map", tr("Туман тает там, где вы идёте.", "The fog lifts where you walk."))
    Point("cloud", tr("Погода и небо – как за окном: дождь, снег, закат.", "Weather and sky as outside: rain, snow, sunset."))
    Point("door", tr("Ваш район – первая глава. Где вы, знает только телефон.", "Your district is chapter one. Only the phone knows where you are."))
    if (located) Text(tr("Готово – вы на карте.", "Done – you're on the map."), style = T.smallB.copy(color = C.ok), modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Point(icon: String, text: String) {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        app.roamling.ui.art.Ico(icon, 26.dp)
        Text(text, style = T.body, modifier = Modifier.padding(start = 10.dp))
    }
}

/** A picture filling its box, cropped to fit. */
@Composable
private fun Scene(name: String, modifier: Modifier) {
    val img = app.roamling.ui.art.Art.get(LocalContext.current, name)
    androidx.compose.foundation.Canvas(modifier.background(C.card2)) { if (img != null) drawCropped(img) }
}
