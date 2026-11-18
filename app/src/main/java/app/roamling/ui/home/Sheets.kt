package app.roamling.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.roamling.container
import app.roamling.data.Pet
import app.roamling.data.parseMap
import app.roamling.domain.Catalog
import app.roamling.domain.Finds
import app.roamling.domain.Growth
import app.roamling.domain.Quest
import app.roamling.domain.QuestKind
import app.roamling.domain.Sky
import app.roamling.domain.Taste
import app.roamling.domain.dayOf
import app.roamling.domain.fmt
import app.roamling.domain.fmt1
import app.roamling.domain.hourOf
import app.roamling.domain.tr
import app.roamling.explore.Live
import app.roamling.geo.Grid
import app.roamling.ui.Data
import app.roamling.ui.Nav
import app.roamling.ui.Screen
import app.roamling.ui.art.Ico
import app.roamling.ui.art.PetArt
import app.roamling.ui.art.Pic
import app.roamling.ui.art.Pose
import app.roamling.ui.components.Btn
import app.roamling.ui.components.Ghost
import app.roamling.ui.components.Meter
import app.roamling.ui.components.Section
import app.roamling.ui.components.Sheet
import app.roamling.ui.components.card
import app.roamling.ui.theme.C
import app.roamling.ui.theme.T
import kotlin.math.roundToInt

private fun stageName(p: Pet) = when (p.stage) { 0 -> tr("яйцо", "egg"); 1 -> tr("малыш", "baby"); 2 -> tr("подросток", "teen"); else -> tr("взрослый", "grown") }

/** One companion in a list: picture, name, stage and how far to the next. */
@Composable
fun PetRow(p: Pet, sub: String? = null, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).card().clickable(onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PetBadge(p, 54.dp)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(if (p.stage == 0) tr("Яйцо", "Egg") else p.name, style = T.bodyB)
            Text(sub ?: if (p.stage == 0) tr("ещё ${fmt1(p.eggLeft / 1000)} км в рюкзаке", "${fmt1(p.eggLeft / 1000)} km more in the backpack")
                else "${Catalog[p.species].name} · ${stageName(p)}", style = T.small)
            if (p.stage < 3) Meter(Growth.progress(p), Modifier.padding(top = 4.dp), h = 5.dp)
        }
        trailing()
    }
}

@Composable
fun TeamSheet(d: Data, nav: Nav) {
    val c = LocalContext.current.container
    Sheet({ nav.sheet = null }) {
        Text(tr("Рюкзак", "Backpack"), style = T.h2)
        Text(tr("Вожак получает весь рост, двое спутников – по половине. Яйца греются в карманах, пока вы идёте.",
            "The leader gets all the growth, the two companions half each. Eggs warm in the pockets as you walk."), style = T.small)
        Section(tr("В рюкзаке", "In the backpack"))
        d.team.forEachIndexed { i, p ->
            PetRow(p, (if (i == 0) tr("Вожак · ", "Leader · ") else tr("Спутник · ", "Companion · ")) + Catalog[p.species].name, { nav.sheet = null; nav.push(Screen.PetInfo(p.id)) }) {
                if (i > 0) Ghost(tr("Вожак", "Lead"), small = true) { c.write { toTeam(p.id, 0) } }
            }
        }
        Section(tr("Карманы: яйца ${d.eggs.size}/${d.s.eggSlots}", "Pockets: eggs ${d.eggs.size}/${d.s.eggSlots}"))
        if (d.eggs.isEmpty()) Text(tr("Яйца лежат в тумане – на клочках, где вы ещё не были.", "Eggs lie in the fog – in patches you haven't been to."), style = T.small)
        d.eggs.forEach { e -> PetRow(e, onClick = {}) }
        if (d.den.isNotEmpty()) {
            Section(tr("В логове", "In the den"))
            d.den.forEach { p ->
                PetRow(p, onClick = { nav.sheet = null; nav.push(Screen.PetInfo(p.id)) }) {
                    Ghost(if (d.team.size < 3) tr("В рюкзак", "Take") else tr("Сменить", "Swap"), small = true) {
                        c.write { if (d.team.size < 3) toTeam(p.id, d.team.size) else toTeam(p.id, 2) }
                    }
                }
            }
        }
    }
}

private fun compass(b: Double) = (if (app.roamling.domain.Lang.ru) listOf("север", "северо-восток", "восток", "юго-восток", "юг", "юго-запад", "запад", "северо-запад")
    else listOf("north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"))[(((b + 22.5) % 360) / 45).toInt()]

@Composable
fun ScentSheet(d: Data, nav: Nav, live: Live) {
    val sc = d.s.scent
    Sheet({ nav.sheet = null }) {
        Text(tr("Нюх", "The nose"), style = T.h2)
        Text(tr("Раз в день вожак берёт след: что-то настоящее рядом – фонтан, старое дерево, мост, смотровая. Дойдите – там находка.",
            "Once a day the leader picks up a scent: something real nearby – a fountain, an old tree, a bridge, a lookout. Get there for a find."), style = T.small)
        Spacer(Modifier.height(14.dp))
        if (sc.day != d.today) {
            Text(tr("След появится, когда начнётся прогулка.", "The scent appears once a walk begins."), style = T.bodyB)
        } else {
            val here = live.fix?.takeIf { it.time > 0 }
            Row(Modifier.fillMaxWidth().card().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    if (here != null && !sc.done) {
                        val b = Grid.bearing(here.lat, here.lon, sc.lat, sc.lon)
                        Text("➤", style = T.big.copy(color = C.acc), modifier = Modifier.rotate((b - 90).toFloat()))
                    } else Pic("place_${sc.kind}", 60.dp)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(app.roamling.domain.bi(sc.place).replaceFirstChar { it.uppercase() }, style = T.bodyB)
                    if (!sc.done) Row(verticalAlignment = Alignment.CenterVertically) { Pic("find_${sc.item}", 22.dp); Text(tr("там ждёт: ", "waiting there: ") + Finds.name(sc.item).lowercase(), style = T.small, modifier = Modifier.padding(start = 4.dp)) }
                    if (sc.done) Text(tr("Найдено: ", "Found: ") + Finds.name(sc.item), style = T.small)
                    else if (here != null) {
                        val m = Grid.distance(here.lat, here.lon, sc.lat, sc.lon)
                        Text("${fmt(m.roundToInt())} ${tr("м", "m")}, ${tr("на", "to the")} ${compass(Grid.bearing(here.lat, here.lon, sc.lat, sc.lon))}", style = T.small)
                    }
                }
            }
            Btn(tr("Показать на карте", "Show on the map"), Modifier.fillMaxWidth().padding(top = 14.dp)) { nav.sheet = null; nav.tab = 1 }
        }
    }
}

/** The days a task counts: its day, or its week. */
fun questRange(d: Data, q: Quest): LongRange = if (q.daily) d.today..d.today else d.monday..d.monday + 6

/** How far a task is, 0..1. */
fun questProgress(d: Data, q: Quest): Float {
    val range = questRange(d, q)
    val walks = d.walks.filter { dayOf(it.start) in range }
    val v: Double = when (q.kind) {
        QuestKind.PATCHES -> d.days.filter { it.day in range }.sumOf { it.patches }.toDouble()
        QuestKind.KM -> walks.sumOf { it.meters } / 1000
        QuestKind.RAIN -> if (walks.any { it.sky in setOf(Sky.RAIN.name, Sky.DRIZZLE.name, Sky.SNOW.name, Sky.BLIZZARD.name, Sky.STORM.name) }) 1.0 else 0.0
        QuestKind.DAWN -> if (walks.any { hourOf(it.start) < 7 || hourOf(it.start) >= 22 }) 1.0 else 0.0
        QuestKind.SCENTS -> d.finds.count { it.ref.startsWith("scent:") && !it.ref.endsWith(":pack") && dayOf(it.at) in range }.toDouble()
        QuestKind.DAYS -> walks.map { dayOf(it.start) }.toSet().size.toDouble()
        QuestKind.WATER -> walks.sumOf { parseMap(it.tastes)[Taste.WATER.name] ?: 0.0 }
        QuestKind.GREEN -> walks.sumOf { parseMap(it.tastes)[Taste.GREEN.name] ?: 0.0 }
        QuestKind.MINUTES -> walks.sumOf { (it.end - it.start) / 60_000.0 }
        QuestKind.STEPS -> walks.sumOf { it.steps }.toDouble()
        QuestKind.FAR -> (walks.maxOfOrNull { it.meters } ?: 0.0) / 1000
    }
    return (v / q.target).toFloat().coerceIn(0f, 1f)
}

fun questClaimed(d: Data, q: Quest) = d.entries.any { it.ref == "quest:${questRange(d, q).first}:${q.ref}" }

@Composable
fun QuestSheet(d: Data, nav: Nav) {
    val c = LocalContext.current.container
    var tab by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    Sheet({ nav.sheet = null }) {
        Text(tr("Задания", "Tasks"), style = T.h2)
        app.roamling.ui.components.Segments(listOf(tr("Сегодня", "Today"), tr("Неделя", "This week")), tab, Modifier.padding(top = 8.dp)) { tab = it }
        Text(if (tab == 0) tr("Три задания на день: по 10 шишек и немного роста вожаку. Завтра будут новые.", "Three tasks a day: 10 cones each and a little growth for the leader. New ones tomorrow.")
            else tr("Три задания на неделю: по 30 шишек и рывок роста вожаку. В понедельник появятся новые.", "Three tasks a week: 30 cones each and a growth boost for the leader. New ones on Monday."),
            style = T.small, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(8.dp))
        (if (tab == 0) Quest.daily(d.today) else Quest.weekly(d.monday)).forEach { q ->
            val pr = questProgress(d, q)
            val claimed = questClaimed(d, q)
            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).card().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ico("scroll", 28.dp)
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(q.title, style = T.bodyB)
                        Text("+${q.cones} " + tr("шишек", "cones"), style = T.tiny)
                    }
                    when {
                        claimed -> Text("✓", style = T.h2.copy(color = C.ok))
                        pr >= 1f -> Btn(tr("Забрать", "Claim"), small = true) { c.write { if (claim(questRange(d, q).first, q)) say(tr("+${q.cones} шишек", "+${q.cones} cones")) } }
                    }
                }
                Meter(pr, Modifier.padding(top = 8.dp), color = if (pr >= 1f) C.gold else C.acc, h = 6.dp)
            }
        }
    }
}

@Composable
fun CallSheet(d: Data, nav: Nav) {
    Sheet({ nav.sheet = null }) {
        d.leader?.let { p -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PetArt(p, 150.dp, pose = if (d.callDone) Pose.CHEER else Pose.IDLE) } }
        Text(tr("Зов дня", "Today's wish"), style = T.h2, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text("«${d.call.title}»", style = T.h3, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.Center)
        Text(if (d.callDone) tr("Исполнено! Вожак получил рывок роста и сердечко.", "Done! The leader got a growth boost and a heart.")
            else tr("Исполните – вожак вырастет рывком, а сердечко привязанности прибавится. Не выйдет – ничего страшного.",
                "Do it and the leader grows in a leap and gains a heart. If not, no harm done."), style = T.small, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center)
        Btn(tr("Хорошо", "OK"), Modifier.fillMaxWidth().padding(top = 16.dp)) { nav.sheet = null }
    }
}

// ---------------------------------------------------------------- news

class News(val key: String, val pet: Pet?, val title: String, val text: String, val find: String? = null, val pic: String? = null, val letter: Int = -1)

/** What happened and hasn't been shown yet: hatched, grown, joined, an egg found. */
fun nextNews(d: Data): News? {
    if (d.s.finale > 0 && "finale" !in d.s.told) return News("finale", d.pets.firstOrNull { it.species == "fogkeeper" }, tr("Хранитель тумана", "The Keeper of the fog"),
        tr("Он вышел из тумана к вашему привалу – тот самый первый попутчик. «Ходи иногда туда, где мы уже были», – просит он. Туман вокруг стал тёплым.",
            "It came out of the fog to your rest stop – that very first companion. «Go sometimes to the places we've been», it asks. The fog around has grown warm."))
    d.s.seals.entries.firstOrNull { "s:${it.key}" !in d.s.told }?.let { (k, v) ->
        val parts = v.split('|'); val sp = runCatching { app.roamling.domain.Spirit.valueOf(parts[0]) }.getOrDefault(app.roamling.domain.Spirit.CROSS)
        val season = parts.getOrNull(1)?.toIntOrNull() ?: 2
        return News("s:$k", null, tr("Район проснулся!", "A district woke up!"), tr("${sp.title} вышел из тумана и поставил печать на Большую карту Долины. И сказал: «${sp.tale}»", "${sp.title} came out of the fog and sealed the Valley's Great Map. And said: «${sp.tale}»"),
            pic = "${sp.art}_${1 + season.let { (it + 3) % 4 }}")
    }
    (0 until d.letters).firstOrNull { "l:$it" !in d.s.told }?.let { i ->
        return News("l:$i", null, tr("Письмо Первого путника", "A letter from the First Walker") + " · ${i + 1}/12", tr(app.roamling.domain.Story.letters[i].first, app.roamling.domain.Story.letters[i].second), find = "postcard", letter = i)
    }
    for (p in d.pets.sortedBy { it.born }) {
        val sp = Catalog[p.species]
        if (p.stage == 0) { if ("e:${p.id}" !in d.s.told) return News("e:${p.id}", p, tr("Яйцо!", "An egg!"), tr("В тумане нашлось яйцо. Оно в кармане рюкзака – пройдите с ним ${fmt1(p.eggLeft / 1000)} км, и оно вылупится.", "An egg turned up in the fog. It's in a backpack pocket – carry it ${fmt1(p.eggLeft / 1000)} km and it will hatch.")); continue }
        for (st in 1..p.stage) {
            if ("p:${p.id}:$st" in d.s.told) continue
            return when (st) {
                1 -> if (sp.group.wild) News("p:${p.id}:1", p, tr("Новый попутчик!", "A new companion!"), tr("${sp.name} пошёл с вами. ${sp.about}", "${sp.name} came along. ${sp.about}"))
                    else News("p:${p.id}:1", p, tr("Вылупился!", "Hatched!"), tr("Из яйца – ${sp.name}. ${if (p.shiny) "Да ещё и искрящийся – такие редкость! " else ""}Назовём его ${p.name}?", "Out of the egg – a ${sp.name}. ${if (p.shiny) "A sparkling one, too – they are rare! " else ""}Shall we call it ${p.name}?"))
                2 -> News("p:${p.id}:2", p, tr("${p.name} подрос", "${p.name} has grown"), tr("Теперь это подросток. Он ест то, где вы гуляете: от этого зависит, каким он вырастет.", "A teen now. It eats where you walk – and that decides what it will grow into."))
                else -> News("p:${p.id}:3", p, tr("${p.name} вырос!", "${p.name} is grown!"), tr("Взрослая форма – ${sp.forms[p.form.coerceAtLeast(0)].name}: ${sp.forms[p.form.coerceAtLeast(0)].taste.title.lowercase()} в рационе взяла верх.", "Grown form – ${sp.forms[p.form.coerceAtLeast(0)].name}: ${sp.forms[p.form.coerceAtLeast(0)].taste.title.lowercase()} won in its diet."))
            }
        }
    }
    d.finds.firstOrNull { "f:${it.id}" !in d.s.told && System.currentTimeMillis() - it.at < 86_400_000 && !it.ref.endsWith(":pack") }?.let { f ->
        val place = app.roamling.domain.bi(f.place)
        return News("f:${f.id}", null, Finds.name(f.item), (when { f.place == "gift" -> tr("Подарок за цель шагов.", "A gift for the step goal."); f.ref.startsWith("miss:") -> tr("Подарок: $place.", "A gift: $place."); else -> tr("Нашлось у места: $place.", "Found by: $place.") }) +
            " " + tr("Уйдёт ", "It goes ") + Finds.recipient(f.item) + ".", f.item)
    }
    return null
}

@Composable
fun NewsSheet(n: News, d: Data, nav: Nav) {
    val c = LocalContext.current.container
    Sheet({ c.write { told(n.key) } }) {
        Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)).background(C.card2), contentAlignment = Alignment.Center) {
            when {
                n.pet != null -> PetArt(n.pet, 180.dp, pose = Pose.CHEER)
                n.pic != null -> Pic(n.pic, 190.dp)
                n.find != null -> Pic("find_${n.find}", 120.dp)
            }
        }
        Text(n.title, style = T.h2, modifier = Modifier.fillMaxWidth().padding(top = 14.dp), textAlign = TextAlign.Center)
        Text(n.text, style = T.body, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.Center)
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (n.pet != null && n.pet.stage > 0) Ghost(tr("Подробнее", "Details"), Modifier.weight(1f)) { c.write { told(n.key) }; nav.push(Screen.PetInfo(n.pet.id)) }
            Btn(if (n.letter >= 0) tr("Сложить письмо", "Fold the letter") else tr("Ура!", "Hooray!"), Modifier.weight(1f)) { c.write { told(n.key) } }
        }
        Spacer(Modifier.width(1.dp))
    }
}
