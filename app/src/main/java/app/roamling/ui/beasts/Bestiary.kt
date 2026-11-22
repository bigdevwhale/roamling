package app.roamling.ui.beasts

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roamling.container
import app.roamling.domain.Catalog
import app.roamling.domain.Group
import app.roamling.domain.Growth
import app.roamling.domain.SPECIES
import app.roamling.domain.Shop
import app.roamling.domain.Species
import app.roamling.domain.Taste
import app.roamling.domain.fmt
import app.roamling.domain.fmt1
import app.roamling.domain.fmtDayYear
import app.roamling.domain.dayOf
import app.roamling.domain.paceName
import app.roamling.domain.tr
import app.roamling.ui.Data
import app.roamling.ui.Nav
import app.roamling.ui.Screen
import app.roamling.ui.art.Creature
import app.roamling.ui.art.Ico
import app.roamling.ui.art.Mini
import app.roamling.ui.art.PetArt
import app.roamling.ui.art.Pic
import app.roamling.ui.art.Pose
import app.roamling.ui.components.Btn
import app.roamling.ui.components.Field
import app.roamling.ui.components.Ghost
import app.roamling.ui.components.Meter
import app.roamling.ui.components.Page
import app.roamling.ui.components.Popup
import app.roamling.ui.components.Section
import app.roamling.ui.components.Segments
import app.roamling.ui.components.card
import app.roamling.ui.home.PetRow
import app.roamling.ui.theme.C
import app.roamling.ui.theme.T

private val forms = SPECIES.sumOf { it.forms.size }

@Composable
fun BestiaryScreen(d: Data, nav: Nav) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        app.roamling.ui.art.Banner("banner_bestiary", tr("Бестиарий", "Bestiary"),
            tr("Видов ${d.met.size} из ${SPECIES.size} · форм ${d.forms.size} из $forms", "Species ${d.met.size} of ${SPECIES.size} · forms ${d.forms.size} of $forms"), Modifier.padding(top = 8.dp),
            stage = {
                // your own companions stand on the meadow: one of each species met, the most grown first
                val shown = d.pets.filter { it.stage > 0 }.sortedByDescending { it.stage }.distinctBy { it.species }.take(4)
                Row(Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
                    shown.forEachIndexed { i, p -> PetArt(p, if (i == 0) 70.dp else 56.dp, phase = i * .3f, flip = i % 2 == 1) }
                }
            })
        Segments(listOf(tr("Все виды", "All species"), tr("Мои · ${d.pets.count { it.stage > 0 }}", "Mine · ${d.pets.count { it.stage > 0 }}")), tab, Modifier.padding(top = 10.dp)) { tab = it }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (tab == 0) Group.entries.forEach { g ->
                val list = Catalog.of(g)
                Section(g.title + " · " + list.count { it.id in d.met } + "/" + list.size)
                list.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        row.forEach { sp -> SpeciesTile(sp, sp.id in d.met, Modifier.weight(1f)) { nav.push(Screen.SpeciesInfo(sp.id)) } }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                d.pets.filter { it.stage > 0 }.sortedWith(compareBy({ it.id !in d.s.team }, { -it.stage }, { it.id })).forEach { p ->
                    PetRow(p, onClick = { nav.push(Screen.PetInfo(p.id)) })
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SpeciesTile(sp: Species, met: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.card().clickable(onClick = onClick).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Mini(sp.id, 56.dp, dim = !met, silhouette = true)
        Text(if (met) sp.name else "???", style = T.tiny.copy(color = if (met) C.ink else C.mute), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

/** How a species is come by. */
fun howToMeet(sp: Species): String = when {
    sp.group == Group.STARTER -> tr("Стартовый: выбирается в начале; иногда попадается в любом яйце.", "A starter: picked at the start; now and then in any egg.")
    sp.wild != null -> tr("Дикий: приходит сам, когда ", "Wild: comes by itself when ") + sp.wild.title.lowercase() + tr(". Ищите тень на карте во время прогулки.", ". Look for a shadow on the map during a walk.")
    else -> {
        val t = when (sp.group) { Group.WATER -> Taste.WATER; Group.STONE -> Taste.STONE; Group.HEIGHT -> Taste.HEIGHT; else -> Taste.GREEN }
        tr("Из яйца: такие лежат в тумане на клочках со вкусом «${t.title}».", "From an egg: these lie in the fog on patches tasting of «${t.title}».")
    }
}

@Composable
fun SpeciesPage(id: String, d: Data, nav: Nav) {
    val sp = Catalog[id]
    val met = id in d.met
    val mine = d.pets.filter { it.species == id && it.stage > 0 }
    Page(if (met) sp.name else "???", { nav.pop() }) {
        Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(16.dp)).background(C.card2), contentAlignment = Alignment.Center) {
            Creature(sp.id, if (mine.isNotEmpty()) Growth.frame(mine.maxBy { it.stage }) else 0, 170.dp, look = if (met) app.roamling.ui.art.Look.FULL else app.roamling.ui.art.Look.SILHOUETTE)
        }
        Text(sp.group.title + " · " + tr("любит темп: ", "likes a pace: ") + paceName(sp.pace), style = T.small, modifier = Modifier.padding(top = 10.dp))
        if (met && sp.about.isNotEmpty()) Text(sp.about, style = T.body, modifier = Modifier.padding(top = 4.dp))
        Text(howToMeet(sp), style = T.body, modifier = Modifier.padding(top = 6.dp))
        Section(tr("Стадии", "Stages"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StageTile(sp.id, 0, tr("Малыш", "Baby"), null, met, Modifier.weight(1f))
            StageTile(sp.id, 1, tr("Подросток", "Teen"), null, mine.any { it.stage >= 2 }, Modifier.weight(1f))
        }
        Section(tr("Взрослые формы", "Grown forms"))
        Text(tr("Какой вырастет подросток, решает, где вы с ним гуляли.", "What a teen grows into depends on where you walked it."), style = T.small)
        sp.forms.chunked(2).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                row.forEachIndexed { k, f ->
                    val i = r * 2 + k
                    StageTile(sp.id, sp.formFrame(i), if ("${sp.id}:$i" in d.forms) f.name else "???", f.taste, "${sp.id}:$i" in d.forms, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (mine.isNotEmpty()) {
            Section(tr("Ваши", "Yours"))
            mine.forEach { p -> PetRow(p, onClick = { nav.push(Screen.PetInfo(p.id)) }) }
        }
    }
}

@Composable
private fun StageTile(kind: String, frame: Int, title: String, taste: Taste?, seen: Boolean, modifier: Modifier) {
    Column(modifier.card().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Mini(kind, 80.dp, dim = !seen, frame = frame, silhouette = true)
        Text(title, style = T.smallB, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (taste != null) Row(verticalAlignment = Alignment.CenterVertically) { Ico(taste.icon, 16.dp); Text(taste.title, style = T.tiny, modifier = Modifier.padding(start = 3.dp)) }
    }
}

@Composable
fun PetPage(id: Long, d: Data, nav: Nav) {
    val c = LocalContext.current.container
    val p = d.byId[id] ?: run { nav.pop(); return }
    val sp = Catalog[p.species]
    var rename by rememberSaveable { mutableStateOf(false) }
    var reform by rememberSaveable { mutableStateOf(false) }
    var gear by rememberSaveable { mutableStateOf(false) }
    val inTeam = p.id in d.s.team
    Page(p.name, { nav.pop() }, action = {
        if (p.stage > 0) app.roamling.ui.journal.ShareButton { nav.sheet = "cards:pet:${p.id}" }
        Text(tr("Имя", "Name"), style = T.smallB.copy(color = C.acc), modifier = Modifier.clickable { rename = true }.padding(12.dp)) }) {
        val stage = listOf(tr("Яйцо", "Egg"), tr("Малыш", "Baby"), tr("Подросток", "Teen"), sp.forms[p.form.coerceAtLeast(0)].name)[p.stage]
        PetHero(p, sp, stage) { nav.push(Screen.SpeciesInfo(sp.id)) }
        if (p.stage > 0) EvolutionTree(p, sp, d)
        Passport(p, sp, d)
        Section(tr("Рюкзак", "Backpack"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                d.s.team.firstOrNull() == p.id -> Text(tr("Вожак рюкзака", "Leads the backpack"), style = T.bodyB, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                inTeam -> { Btn(tr("Сделать вожаком", "Make leader"), Modifier.weight(1f), small = true) { c.write { toTeam(p.id, 0) } }
                    Ghost(tr("В логово", "To the den"), Modifier.weight(1f), small = true) { c.write { toDen(p.id) } } }
                p.stage > 0 -> { Btn(tr("В рюкзак", "Take along"), Modifier.weight(1f), small = true) { c.write { toTeam(p.id, if (d.team.size < 3) d.team.size else 2) } }
                    Ghost(tr("Вожаком", "As leader"), Modifier.weight(1f), small = true) { c.write { toTeam(p.id, 0) } } }
            }
        }
        if (p.stage > 0) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Ghost(if (p.gear.isEmpty()) tr("Надеть снаряжение", "Put on gear") else Shop.byId[p.gear]?.name ?: p.gear, Modifier.weight(1f), small = true) { gear = true }
            if (p.stage == 3 && sp.forms.size > 1) Ghost(tr("Камень ветвления · ${d.s.stones}", "Branch stone · ${d.s.stones}"), Modifier.weight(1f), small = true) { reform = true }
        }
    }
    if (rename) {
        var name by rememberSaveable { mutableStateOf(p.name) }
        Popup({ rename = false }) {
            Text(tr("Как зовут?", "What's its name?"), style = T.h2)
            Field(name, { name = it.take(20) }, tr("Имя", "Name"), Modifier.fillMaxWidth().padding(top = 10.dp))
            Btn(tr("Сохранить", "Save"), Modifier.fillMaxWidth().padding(top = 12.dp)) { c.write { rename(p, name) }; rename = false }
        }
    }
    if (gear) Popup({ gear = false }) {
        Text(tr("Снаряжение", "Gear"), style = T.h2)
        val owned = Shop.gear.filter { it.id in d.s.owned }
        if (owned.isEmpty()) Text(tr("Пока ничего нет – загляните в лавку.", "Nothing yet – have a look in the shop."), style = T.body, modifier = Modifier.padding(top = 8.dp))
        owned.forEach { th ->
            // who wears it now, so nobody is left bare in the frost by accident (#9)
            val on = d.pets.firstOrNull { it.gear == th.id && it.id != p.id }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).card().clickable {
                c.write { wear(p, if (p.gear == th.id) "" else th.id); if (on != null && p.gear != th.id) say(tr("${th.name} надет. ${on.name} теперь без него", "${th.name} is on. ${on.name} goes without it now")) }; gear = false
            }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Pic("gear_${th.id}", 40.dp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) { Text(th.name, style = T.bodyB); Text(th.help, style = T.small)
                    if (on != null) Text(tr("Сейчас носит ${on.name}", "${on.name} wears it now"), style = T.small.copy(color = C.gold)) }
                if (p.gear == th.id) Text("✓", style = T.h2.copy(color = C.ok))
            }
        }
    }
    if (reform) Popup({ reform = false }) {
        Text(tr("Камень ветвления", "Branch stone"), style = T.h2)
        Text(tr("Превращает взрослого в другую форму его вида. Камней: ${d.s.stones}. Купить можно в лавке.", "Turns a grown one into another form of its species. Stones: ${d.s.stones}. They're sold in the shop."), style = T.small)
        sp.forms.forEachIndexed { i, f ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).card().clickable(enabled = d.s.stones > 0 && i != p.form) { c.write { if (reform(p, i)) say(tr("Теперь – ${f.name}", "Now a ${f.name}")) }; reform = false }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Mini(sp.id, 52.dp, frame = sp.formFrame(i), dim = "${sp.id}:$i" !in d.forms && i != p.form)
                Text(f.name, style = T.bodyB, modifier = Modifier.weight(1f).padding(start = 10.dp))
                if (i == p.form) Text("✓", style = T.h2.copy(color = C.ok))
            }
        }
    }
}
