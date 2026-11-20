package app.roamling.ui

import androidx.compose.foundation.layout.Box
import app.roamling.ui.theme.C
import app.roamling.ui.components.card
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.roamling.container
import app.roamling.domain.tr
import app.roamling.ui.art.Ico
import app.roamling.ui.components.Btn
import app.roamling.ui.components.Sheet
import app.roamling.ui.theme.T

/** A first-visit introduction: what a screen is for and how to use it. */
class Intro(val id: String, val icon: String, val title: String, val lines: List<Pair<String, String>>)

private fun ru(r: String, e: String) = tr(r, e)

/** The introduction for what is on screen now, or null. */
fun introFor(top: Screen?, tab: Int, sheet: String?, walking: Boolean): Intro? = when {
    sheet == "team" -> Intro("i.team", "paw", ru("Рюкзак", "The backpack"), listOf(
        "heart" to ru("В рюкзаке трое: вожак и два спутника. Вожак получает весь рост, спутники – по половине.", "Three ride in the backpack: the leader and two companions. The leader gets all the growth, the companions half each."),
        "gift" to ru("В карманах греются яйца: вылупятся, когда пронесёте их нужные километры.", "Eggs warm in the pockets: they hatch once carried far enough."),
        "door" to ru("Остальные ждут в логове. Меняйте команду когда хотите – никто не обижается.", "The rest wait in the den. Change the team any time – nobody minds.")))
    sheet == "scent" -> Intro("i.scent", "compass", ru("Нюх", "The nose"), listOf(
        "compass" to ru("Раз в день вожак берёт след к настоящему месту рядом: фонтан, старое дерево, мост, смотровая.", "Once a day the leader picks up a scent to a real place nearby: a fountain, an old tree, a bridge, a lookout."),
        "map" to ru("След виден на карте. Подойдите на 35 м – там находка, шишки и рост.", "The scent shows on the map. Come within 35 m for a find, cones and growth."),
        "heart" to ru("С компасом или пятью сердечками вожака – два следа в день.", "With a compass, or a leader with five hearts, it's two scents a day.")))
    sheet == "quests" -> Intro("i.quests", "scroll", ru("Задания", "Tasks"), listOf(
        "scroll" to ru("Каждый день – три небольших задания, каждую неделю – три побольше.", "Every day three small tasks, every week three bigger ones."),
        "cone" to ru("Выполненное забирается кнопкой: шишки и рост вожаку.", "Claim a finished one with the button: cones and growth for the leader."),
        "leaf" to ru("Задания не сгорают со штрафом – просто сменяются новыми.", "Tasks don't burn with a penalty – they're simply replaced by new ones.")))
    sheet != null -> null
    top is Screen.PetInfo -> Intro("i.pet", "paw", ru("Карточка попутчика", "A companion's card"), listOf(
        "leaf" to ru("Малыш → подросток → взрослый. Полоска показывает, сколько до следующей стадии.", "Baby → teen → grown. The bar shows how far the next stage is."),
        "drop" to ru("Подросток ест вкусы мест, где вы гуляете. Чего больше в рационе – такой взрослой формой он станет.", "A teen eats the tastes of the places you walk. Whatever leads in its diet decides its grown form."),
        "heart" to ru("Сердечки растут на прогулках и от зова дня. Снаряжение ускоряет рост в свою погоду.", "Hearts grow on walks and from the day's wish. Gear speeds growth in its weather.")))
    top is Screen.SpeciesInfo -> Intro("i.species", "book", ru("Страница вида", "A species page"), listOf(
        "book" to ru("Все стадии и взрослые формы вида. Неизвестные – тенью.", "Every stage and grown form of the species. Unknown ones as shadows."),
        "cloud" to ru("Внизу сказано, как его встретить: из какого яйца или в какую погоду он приходит сам.", "Below it says how to meet it: which egg, or which weather it comes in by itself.")))
    top is Screen.Shop -> Intro("i.shop", "cone", ru("Лавка", "The shop"), listOf(
        "cone" to ru("Шишки дают за новые клочки, нюх, зов дня и задания. За шаги – нет.", "Cones come for new patches, scents, the day's wish and tasks. Not for steps."),
        "gift" to ru("Снаряжение ускоряет рост в свою погоду, приманка зовёт дикого, камень меняет взрослую форму.", "Gear speeds growth in its weather, a lure calls a wild one, a stone changes a grown form.")))
    top is Screen.WalkInfo -> Intro("i.walkpage", "map", ru("Прогулка", "A walk"), listOf(
        "map" to ru("Маршрут, марка погоды, сколько нового открыто и что съели попутчики.", "The route, the weather stamp, how much new ground and what the companions ate.")))
    top != null -> null
    tab == 0 -> Intro("i.rest", "door", ru("Привал", "The rest stop"), listOf(
        "paw" to ru("На лавочке – ваш рюкзак: вожак и два спутника. Над вожаком – его желание на сегодня.", "On the bench: the leader and two companions. Above the leader – its wish for today."),
        "map" to ru("Строка вверху – ваша глава: сколько пройдено и сколько писем.", "The line at the top is your chapter: how far you've got and how many letters."),
        "door" to ru("«Логово» вверху – там отдыхают остальные.", "«Den» at the top – where the rest relax.")))
    tab == 1 && walking -> Intro("i.walking", "boots", ru("Прогулка идёт", "Walk under way"), listOf(
        "map" to ru("Туман тает вокруг вас. Попутчики растут от прогулок, и лучше всего их кормят новые места: клочок (~100 м), где вы ещё не были, сытнее, чем шаги по знакомым улицам, раз в пять.", "The fog lifts around you. Companions grow from walks, and new places feed them best: a patch (~100 m) you've never been to is about five times richer than steps on familiar streets."),
        "leaf" to ru("Вверху – вкус места под ногами. Внизу – что съели попутчики за прогулку.", "At the top – the taste of the ground underfoot. Below – what the companions ate on this walk."),
        "moon" to ru("Тени на карте – дикие попутчики. Подойдите – и они пойдут с вами.", "Shadows on the map are wild ones. Come close and they'll join you."),
        "boots" to ru("Можно убрать телефон: прогулка идёт и с выключенным экраном.", "You can pocket the phone: the walk goes on with the screen off.")))
    tab == 1 -> Intro("i.map", "map", ru("Карта", "The map"), listOf(
        "cloud" to ru("Туман тает только на прогулке – нажмите «Гулять» внизу.", "The fog lifts only on a walk – press «Walk» below."),
        "leaf" to ru("Город поделён на районы: у каждого свой цвет, тип и спящий Дух. Тапните значок района.", "The town is split into districts, each with its colour, kind and sleeping spirit. Tap a district's badge."),
        "compass" to ru("Ещё на карте – след нюха и тени диких попутчиков.", "The map also shows the scent's goal and wild shadows.")))
    tab == 2 -> Intro("i.best", "book", ru("Бестиарий", "The bestiary"), listOf(
        "book" to ru("${app.roamling.domain.SPECIES.size} видов и почти двести взрослых форм. Встреченные – в цвете, остальные скрыты туманом.", "${app.roamling.domain.SPECIES.size} species and nearly two hundred grown forms. Met ones in colour, the rest hidden in fog."),
        "gift" to ru("Обычные вылупляются из яиц, найденных в тумане. Дикие приходят сами – в свою погоду и свой час.", "Common ones hatch from eggs found in the fog. Wild ones come by themselves – in their weather and hour."),
        "paw" to ru("Вкладка «Мои» – все ваши попутчики.", "The «Mine» tab lists all your companions.")))
    tab == 3 -> Intro("i.journal", "scroll", ru("Дневник", "The journal"), listOf(
        "book" to ru("Летопись – главная цель: районы города как главы, Духи мест и письма Первого путника.", "The chronicle is the main goal: the town's districts as chapters, the spirits of places and the First Walker's letters."),
        "map" to ru("Прогулки – каждая с маршрутом и маркой погоды.", "Walks – each with its route and weather stamp."),
        "cloud" to ru("Марки – альбом из двенадцати погод. Соберите все.", "Stamps – an album of twelve weathers. Collect them all."),
        "heart" to ru("Подвиги – длинные лестницы, итоги – цифры и графики. Шестерёнка – настройки.", "Deeds are long ladders, totals are numbers and charts. The gear is settings.")))
    else -> null
}

/**
 * A first-visit hint that doesn't cover the page: a small card, no dimming, one line at a time.
 * «›» shows the next line, «Понятно» puts it away for good. [top] puts it under the status bar (when a sheet fills the bottom).
 */
@Composable
fun BoxScope.IntroTip(i: Intro, top: Boolean, aboveDock: Boolean, topPad: androidx.compose.ui.unit.Dp = 8.dp) {
    val c = LocalContext.current.container
    var k by androidx.compose.runtime.saveable.rememberSaveable(i.id) { androidx.compose.runtime.mutableIntStateOf(0) }
    fun done() = c.write { settings.update { it.copy(seen = it.seen + i.id) } }
    val (ic, text) = i.lines[k.coerceIn(0, i.lines.lastIndex)]
    Row(Modifier.align(if (top) Alignment.TopCenter else Alignment.BottomCenter)
        .then(if (top) Modifier.statusBarsPadding().padding(top = topPad) else Modifier.navigationBarsPadding().padding(bottom = if (aboveDock) 96.dp else 12.dp))
        .padding(horizontal = 12.dp).fillMaxWidth().card(color = C.card).padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Ico(ic, 26.dp)
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(i.title + if (i.lines.size > 1) "  ${k + 1}/${i.lines.size}" else "", style = T.tiny)
            Text(text, style = T.small.copy(color = C.ink))
        }
        if (k < i.lines.lastIndex) Text("›", style = T.h2.copy(color = C.acc), modifier = Modifier.clickable { k++ }.padding(horizontal = 10.dp, vertical = 4.dp))
        else Text(tr("Понятно", "Got it"), style = T.smallB.copy(color = C.acc), modifier = Modifier.clickable { done() }.padding(horizontal = 8.dp, vertical = 6.dp))
    }
}
