package app.roamling.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.roamling.container
import app.roamling.data.Day
import app.roamling.data.Entry
import app.roamling.data.Find
import app.roamling.data.Pet
import app.roamling.data.Settings
import app.roamling.data.Walk
import app.roamling.domain.Call
import app.roamling.domain.Rand
import app.roamling.domain.Sky
import app.roamling.domain.WeatherApi
import app.roamling.domain.dayOf
import app.roamling.domain.tr
import app.roamling.domain.weekStart
import app.roamling.explore.Live
import app.roamling.ui.art.Ico
import app.roamling.ui.theme.C
import app.roamling.ui.theme.Pixel
import app.roamling.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine

/** Where a demo/screenshot launch opens (set from the intent in demo builds only). */
object Start { var tab = 0; var open: String? = null; var goChronicle: (() -> Unit)? = null }

sealed class Screen {
    data class PetInfo(val id: Long) : Screen()
    data class SpeciesInfo(val id: String) : Screen()
    data class WalkInfo(val id: Long) : Screen()
    data object Shop : Screen()
    data object Settings : Screen()
}

class Nav {
    var tab by mutableIntStateOf(0)
    val stack = mutableStateListOf<Screen>()
    /** An open bottom sheet by name: team, scent, quests, call, walk. */
    var sheet by mutableStateOf<String?>(null)
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
}

private data class Rows(val pets: List<Pet>, val walks: List<Walk>, val days: List<Day>, val finds: List<Find>, val entries: List<Entry>)

/** Everything the screens read, worked out once per change. */
class Data(val s: Settings, val pets: List<Pet>, val walks: List<Walk>, val days: List<Day>, val finds: List<Find>, val entries: List<Entry>, val patchRows: List<app.roamling.data.Patch>) {
    val patches = patchRows.size
    val patchKeys by lazy { patchRows.map { it.key to it.taste } }
    val home: Long? by lazy { app.roamling.domain.Story.home(s.homeLat, s.homeLon, patchRows.map { it.key }) }
    fun district(sector: Long) = app.roamling.domain.Story.district(sector, patchKeys, pets, first = s.seals.isEmpty())
    /** The sealed district that misses you this week, and how many of its patches were walked since Monday. */
    val missing: Long? by lazy { app.roamling.domain.Story.missing(monday, s.seals.keys) }
    val missedDone get() = entries.any { it.ref == "miss:$monday" }
    val missWalked: Int by lazy { missing?.let { k -> app.roamling.domain.Story.walkedThisWeek(k, monday, patchRows.map { Triple(it.key, it.lastDay, it.days) }) } ?: 0 }
    /** Letters come one per milestone, in the story's order. */
    val letters: Int by lazy {
        app.roamling.domain.Story.lettersCome(app.roamling.domain.Story.milestones(walks, pets, finds.filter { it.ref.startsWith("scent:") }.minOfOrNull { it.at }, s.seals, met.size,
            app.roamling.domain.Story.quarterAt(home, patchRows.map { it.key to it.first })))
    }
    val today = dayOf(System.currentTimeMillis())
    val byId = pets.associateBy { it.id }
    val team: List<Pet> = s.team.mapNotNull { byId[it] }
    val eggs: List<Pet> = s.eggs.mapNotNull { byId[it] }.filter { it.stage == 0 }
    val den: List<Pet> = pets.filter { it.stage > 0 && it.id !in s.team }
    val leader: Pet? = team.firstOrNull()
    val cones = entries.sumOf { it.cones }
    val day: Day = days.lastOrNull { it.day == today } ?: Day(today)
    val call: Call = Call.of(today, WeatherApi.last())
    /** Today's three tasks, with the swaps made (#8). */
    val dailyQuests: List<app.roamling.domain.Quest> by lazy { app.roamling.domain.Quest.daily(today, app.roamling.domain.Quest.swapsOf(today, entries.map { it.ref })) }
    val callDone = entries.any { it.ref == "call:$today" }
    val km = walks.sumOf { it.meters } / 1000
    val monday = weekStart(today)
    /** Species met: hatched, joined or grown. */
    val met: Set<String> = pets.filter { it.stage > 0 }.map { it.species }.toSet()
    /** Grown forms seen, "species:form". */
    val forms: Set<String> = pets.filter { it.stage == 3 && it.form >= 0 }.map { "${it.species}:${it.form}" }.toSet()
    val stamps: Map<Sky, Int> = walks.mapNotNull { w -> runCatching { Sky.valueOf(w.sky) }.getOrNull() }.groupingBy { it }.eachCount()
}

@Composable
fun Root(s: Settings) {
    val c = LocalContext.current.container
    val rows by remember { combine(c.dao.pets(), c.dao.walks(), c.dao.days(), c.dao.finds(), c.dao.entries()) { p, w, d, f, e -> Rows(p, w, d, f, e) } }.collectAsState(initial = null)
    val patchRows by remember { c.dao.patches() }.collectAsState(initial = emptyList())
    val r = rows ?: return
    val data = remember(s, r, patchRows) { Data(s, r.pets, r.walks, r.days, r.finds, r.entries, patchRows) }
    val live by c.walker.live.collectAsState()
    val nav = remember { Nav().apply { tab = Start.tab } }
    Start.goChronicle = { nav.tab = 3 }
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { c.news.collect { note = it; delay(2600); note = null } }
    LaunchedEffect(Unit) {
        when (val o = Start.open) {
            null -> {}
            "shop" -> nav.push(Screen.Shop); "settings" -> nav.push(Screen.Settings)
            "team", "scent", "quests", "call" -> nav.sheet = o
            else -> when {
                o.startsWith("pet:") -> nav.push(Screen.PetInfo(o.drop(4).toLongOrNull() ?: data.leader?.id ?: 0))
                o.startsWith("species:") -> nav.push(Screen.SpeciesInfo(o.drop(8)))
                o.startsWith("walk:") -> data.walks.firstOrNull()?.let { nav.push(Screen.WalkInfo(it.id)) }
                o.startsWith("cards") -> nav.sheet = o
            }
        }
        Start.open = null
    }
    // the steps of the day become growth whenever the app is looked at
    LaunchedEffect(Unit) { c.walker.storyCheck(); c.syncHealth(); c.walker.feedSteps(data.today); val f = c.walker.live.value.fix; if (f != null && f.time > 0) c.walker.weather(f.lat, f.lon) else if (s.lastLat != 0.0) c.walker.weather(s.lastLat, s.lastLon) }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (nav.tab) {
                    0 -> app.roamling.ui.home.HomeScreen(data, nav, live)
                    1 -> app.roamling.ui.map.MapScreen(data, nav, live)
                    2 -> app.roamling.ui.beasts.BestiaryScreen(data, nav)
                    else -> app.roamling.ui.journal.JournalScreen(data, nav)
                }
            }
            Dock(nav.tab, s.walking, { nav.tab = it }) { app.roamling.ui.map.walkButton(c, nav, s.walking) }
        }
        for (scr in nav.stack) Box(Modifier.fillMaxSize()) {
            when (scr) {
                is Screen.PetInfo -> app.roamling.ui.beasts.PetPage(scr.id, data, nav)
                is Screen.SpeciesInfo -> app.roamling.ui.beasts.SpeciesPage(scr.id, data, nav)
                is Screen.WalkInfo -> app.roamling.ui.journal.WalkPage(scr.id, data, nav)
                Screen.Shop -> app.roamling.ui.more.ShopPage(data, nav)
                Screen.Settings -> app.roamling.ui.more.SettingsPage(data, nav)
            }
        }
        when (nav.sheet) {
            "team" -> app.roamling.ui.home.TeamSheet(data, nav)
            "scent" -> app.roamling.ui.home.ScentSheet(data, nav, live)
            "quests" -> app.roamling.ui.home.QuestSheet(data, nav)
            "call" -> app.roamling.ui.home.CallSheet(data, nav)
        }
        nav.sheet?.takeIf { it.startsWith("cards") }?.let { k -> app.roamling.ui.share.ShareSheet(k.removePrefix("cards").removePrefix(":"), data) { nav.sheet = null } }
        val shots = app.roamling.BuildConfig.SHOTS
        val news = if (shots) null else app.roamling.ui.home.nextNews(data)
        if (nav.stack.isEmpty() && nav.sheet == null && news != null) app.roamling.ui.home.NewsSheet(news, data, nav)
        else if (!shots && "all" !in s.seen) introFor(nav.stack.lastOrNull(), nav.tab, nav.sheet, s.walking)?.takeIf { it.id !in s.seen }?.let { IntroTip(it, top = nav.sheet != null || (nav.stack.isEmpty() && nav.tab == 1), aboveDock = nav.stack.isEmpty(),
            // on the map the bottom holds the walk panel and the top the district line: the hint goes just below that line
            topPad = if (nav.sheet == null && nav.stack.isEmpty() && nav.tab == 1) 104.dp else 8.dp) }
        AnimatedVisibility(note != null, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp, start = 18.dp, end = 18.dp), enter = fadeIn(), exit = fadeOut()) {
            Text(note ?: "", style = T.bodyB.copy(color = C.bg), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.ink).padding(horizontal = 16.dp, vertical = 11.dp))
        }
    }
}
