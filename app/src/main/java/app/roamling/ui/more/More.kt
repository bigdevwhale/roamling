package app.roamling.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roamling.container
import app.roamling.currentLanguage
import app.roamling.domain.Shop
import app.roamling.domain.fmt
import app.roamling.domain.tr
import app.roamling.explore.HealthSteps
import app.roamling.setLanguage
import app.roamling.ui.Data
import app.roamling.ui.Nav
import app.roamling.ui.art.Ico
import app.roamling.ui.art.Pic
import app.roamling.ui.components.Btn
import app.roamling.ui.components.ChipRow
import app.roamling.ui.components.Row2
import app.roamling.ui.components.Section
import app.roamling.ui.components.Segments
import app.roamling.ui.components.card
import app.roamling.ui.components.Page
import app.roamling.ui.theme.C
import app.roamling.ui.theme.T
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import app.roamling.domain.count
import app.roamling.domain.fmt1
import app.roamling.ui.components.Ghost

/** A thing on the shop's shelf. */
private class Ware(val pic: String, val title: String, val sub: String, val price: Int, val owned: Boolean, val note: String? = null, val buy: () -> Unit)

@Composable
fun ShopPage(d: Data, nav: Nav) {
    val c = LocalContext.current.container
    var tab by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    Page(tr("Лавка", "Shop"), { nav.pop() }) {
        app.roamling.ui.art.Banner("banner_shop", tr("Лавка у тропы", "The trail stall"), tr("Платят здесь шишками", "Pinecones are the coin here"), height = 112.dp) {
            Row(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(androidx.compose.ui.graphics.Color(0xE6FBF5E4)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Ico("cone", 22.dp); Text(fmt(d.cones), style = T.numS.copy(color = androidx.compose.ui.graphics.Color(0xFF2E2618)), modifier = Modifier.padding(start = 4.dp))
            }
        }
        Segments(listOf(tr("Прогулки", "Walks"), tr("Снаряжение", "Gear"), tr("Логово", "Den")), tab, Modifier.padding(top = 12.dp)) { tab = it }
        val wearer = d.pets.filter { it.gear.isNotEmpty() }.associateBy { it.gear }
        val wares = when (tab) {
            0 -> listOf(
                Ware("ic_moon", tr("Приманка", "Lure"), tr("Зовёт дикого, который может прийти сейчас", "Calls a wild one that may come now"), Shop.LURE, false, tr("есть: ${d.s.lures}", "have: ${d.s.lures}")) {
                    c.write { if (buyLure()) say(tr("Приманка в рюкзаке", "A lure in the backpack")) } },
                Ware("ic_stone", tr("Камень ветвления", "Branch stone"), tr("Меняет взрослую форму на другую", "Turns a grown form into another"), Shop.STONE, false, tr("есть: ${d.s.stones}", "have: ${d.s.stones}")) {
                    c.write { if (buyStone()) say(tr("Камень ветвления куплен", "Branch stone bought")) } },
                Ware("egg_green", tr("Третий карман", "A third pocket"), tr("Ещё одно место для яйца", "Room for one more egg"), Shop.POCKET, d.s.eggSlots >= 3) {
                    c.write { if (buyPocket()) say(tr("Теперь в рюкзаке три кармана", "The backpack has three pockets now")) } },
            )
            1 -> Shop.gear.map { th -> Ware("gear_${th.id}", th.name, th.help, th.price, th.id in d.s.owned, wearer[th.id]?.let { tr("носит ${it.name}", "worn by ${it.name}") }) {
                c.write { if (buy(th.id)) say(tr("Куплено: ${th.name}. Наденьте в карточке попутчика", "Bought: ${th.name}. Put it on in a companion's card")) } } }
            else -> Shop.decor.map { th -> Ware("decor_${th.id}", th.name, tr("встанет в логове", "goes into the den"), th.price, th.id in d.s.owned, if (th.id in d.s.owned) tr("в логове", "in the den") else null) {
                c.write { if (buy(th.id)) say(tr("Куплено: ${th.name}", "Bought: ${th.name}")) } } }
        }
        Text(when (tab) {
            0 -> tr("Шишки дают за новые клочки, нюх, зов дня и задания. За шаги – нет: шаги растят попутчиков.", "Cones come for new patches, scents, the day's wish and tasks. Not for steps: steps grow the companions.")
            1 -> tr("Снаряжение ускоряет рост в свою погоду. Надевается в карточке попутчика, одна вещь на одного.", "Gear speeds growth in its weather. Put it on in a companion's card, one piece each.")
            else -> tr("Вещи для логова под старым дубом. Свайп влево на привале – и они там.", "Things for the den under the old oak. Swipe left on the rest stop to see them.")
        }, style = T.small, modifier = Modifier.padding(top = 10.dp))
        wares.chunked(2).forEach { row ->
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { w -> WareCard(w, d.cones, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WareCard(w: Ware, cones: Int, modifier: Modifier) {
    Column(modifier.card().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(84.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(C.card2), contentAlignment = Alignment.Center) {
            Pic(w.pic, 70.dp)
        }
        Text(w.title, style = T.bodyB, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(w.sub, style = T.tiny, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.height(30.dp))
        if (w.note != null) Text(w.note, style = T.tiny.copy(color = C.acc), maxLines = 1)
        Spacer(Modifier.height(6.dp))
        if (w.owned) Text(tr("✓ есть", "✓ owned"), style = T.smallB.copy(color = C.ok), modifier = Modifier.padding(vertical = 8.dp))
        else Btn("${w.price}", Modifier.fillMaxWidth(), small = true, enabled = cones >= w.price, onClick = w.buy)
    }
}

@Composable
fun SettingsPage(d: Data, nav: Nav) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    val hcLauncher = androidx.activity.compose.rememberLauncherForActivityResult(HealthSteps.requestContract()) { granted ->
        scope.launch { c.settings.update { it.copy(health = HealthSteps.READ in granted) }; c.syncHealth() }
    }
    var tab by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    // moving to another phone: everything into one file, and back
    val exportFile = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        c.scope.launch {
            val ok = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val json = app.roamling.data.Backup.export(c.db, c.settings.now())
                    ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
                }
            }.isSuccess
            c.say(if (ok) tr("Прогресс сохранён в файл", "Progress saved to the file") else tr("Не удалось сохранить файл", "Couldn't save the file"))
        }
    }
    var pending by remember { mutableStateOf<app.roamling.data.Backup.Parsed?>(null) }
    val importFile = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        c.scope.launch {
            val parsed = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    app.roamling.data.Backup.parse(ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) })
                }
            }.getOrNull()
            if (parsed == null) c.say(tr("Это не файл Попутчиков", "That isn't a Roamling file")) else pending = parsed
        }
    }
    pending?.let { p ->
        app.roamling.ui.components.Popup({ pending = null }) {
            Text(tr("Загрузить прогресс?", "Load the progress?"), style = T.h2)
            Text(tr("В файле: ${count(p.pets.count { it.stage > 0 }, "попутчик", "попутчика", "попутчиков", "companion", "companions")}, " +
                count(p.walks.size, "прогулка", "прогулки", "прогулок", "walk", "walks") + ", ${fmt1(p.km)} км.",
                "In the file: ${count(p.pets.count { it.stage > 0 }, "companion", "companions", "companions", "companion", "companions")}, " +
                count(p.walks.size, "walk", "walks", "walks", "walk", "walks") + ", ${fmt1(p.km)} km."), style = T.body, modifier = Modifier.padding(top = 8.dp))
            Text(tr("Всё, что сейчас на этом телефоне, заменится прогрессом из файла.", "Everything on this phone now will be replaced by the file's progress."),
                style = T.small.copy(color = C.bad), modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ghost(tr("Отмена", "Cancel"), Modifier.weight(1f)) { pending = null }
                Btn(tr("Загрузить", "Load"), Modifier.weight(1f)) {
                    pending = null
                    c.scope.launch {
                        val ok = runCatching { c.walker.replaceAll { app.roamling.data.Backup.restore(c.db, c.settings, p) } }.getOrElse { false }
                        c.say(if (ok) tr("Прогресс загружен", "Progress loaded") else if (c.settings.now().walking) tr("Сначала закончите прогулку", "Finish the walk first") else tr("Не удалось загрузить файл", "Couldn't load the file"))
                    }
                }
            }
        }
    }
    Page(tr("Настройки", "Settings"), { nav.pop() }) {
        Segments(listOf(tr("Прогулки", "Walks"), tr("Вид", "Look"), tr("О приложении", "About")), tab, Modifier.padding(top = 4.dp)) { tab = it }
        when (tab) {
            0 -> {
                Section(tr("Цель шагов на день", "Daily step goal"))
                Text(tr("Считаются только шаги на прогулках. За цель – подарок в кармане рюкзака.", "Only steps on walks count. Reaching the goal puts a gift in the backpack pocket."), style = T.small)
                val goals = listOf(4000, 6000, 7000, 8000, 10000, 12000)
                ChipRow(goals.map { fmt(it) }, goals.indexOf(d.s.stepGoal).coerceAtLeast(0), Modifier.padding(top = 8.dp)) { i -> c.write { settings.update { it.copy(stepGoal = goals[i]) } } }
                Section(tr("Связи", "Connections"))
                Setting("heart", tr("Health Connect", "Health Connect"),
                    if (d.s.health) tr("Шаги прогулок берутся и с часов и браслетов", "Walk steps also come from watches and bands") else tr("Шаги прогулок с часов и браслетов", "Walk steps from watches and bands")) {
                    Toggle(d.s.health) {
                        when {
                            d.s.health -> c.write { settings.update { it.copy(health = false) }; syncHealth() }
                            c.health.available -> hcLauncher.launch(setOf(HealthSteps.READ))
                            c.health.installable -> runCatching { ctx.startActivity(c.health.storeIntent()) }
                            else -> c.say(tr("Health Connect на этом телефоне нет", "Health Connect isn't on this phone"))
                        }
                    }
                }
                Setting("door", tr("Дом", "Home"), if (d.s.homeLat != 0.0) tr("Отмечен – от него считается домашний район", "Marked – your home district counts from it") else tr("Отметьте, где живёте: от дома считается домашний район", "Mark where you live: your home district counts from it")) {
                    Btn(if (d.s.homeLat != 0.0) tr("Здесь", "Here") else tr("Отметить", "Mark"), small = true) {
                        val f = c.walker.live.value.fix
                        if (f == null || f.time <= 0) c.say(tr("Сначала откройте карту, чтобы найти вас", "Open the map first so you can be found"))
                        else c.write { settings.update { it.copy(homeLat = f.lat, homeLon = f.lon) }; say(tr("Дом отмечен", "Home marked")) }
                    }
                }
                Setting("gift", tr("Уведомления", "Notifications"), tr("Яйца, тени, радуга, последний шажок", "Eggs, shadows, rainbows, the last little step")) {
                    Toggle(d.s.notify) { c.write { settings.update { it.copy(notify = !it.notify) } } }
                }
                Section(tr("Перенос на другой телефон", "Moving to another phone"))
                Text(tr("Попутчики, яйца, прогулки, туман и шишки – в одном файле. На новом телефоне загрузите его здесь же.",
                    "Companions, eggs, walks, the fog and the cones – in one file. On the new phone, load it right here."), style = T.small)
                Setting("scroll", tr("Сохранить в файл", "Save to a file"), tr("Весь прогресс", "All the progress")) {
                    Btn(tr("Сохранить", "Save"), small = true) { exportFile.launch("roamling-${java.time.LocalDate.now()}.json") }
                }
                Setting("map", tr("Загрузить из файла", "Load from a file"), tr("Заменит то, что сейчас на телефоне", "Replaces what is on the phone now")) {
                    Btn(tr("Загрузить", "Load"), small = true) {
                        if (d.s.walking) c.say(tr("Сначала закончите прогулку", "Finish the walk first")) else importFile.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
                    }
                }
            }
            1 -> {
                Section(tr("Тема", "Theme"))
                val themes = listOf("system", "light", "dark")
                Segments(listOf(tr("Как в системе", "System"), tr("Светлая", "Light"), tr("Тёмная", "Dark")), themes.indexOf(d.s.theme).coerceAtLeast(0)) { i -> c.write { settings.update { it.copy(theme = themes[i]) } } }
                Section(tr("Язык", "Language"))
                val langs = listOf("", "ru", "en")
                Segments(listOf(tr("Как в системе", "System"), "Русский", "English"), langs.indexOf(currentLanguage()).coerceAtLeast(0)) { setLanguage(langs[it]) }
                Section(tr("Подсказки", "Hints"))
                Setting("scroll", tr("Подсказки на экранах", "Screen hints"), tr("Показать знакомство с экранами заново", "Show the screen introductions again")) {
                    Btn(tr("Показать", "Show"), small = true) { c.write { settings.update { it.copy(seen = emptySet()) }; say(tr("Подсказки вернутся", "Hints will come back")) } }
                }
            }
            else -> {
                Section(tr("Поддержать", "Support"))
                Text(tr("StillCraft держится на поддержке тех, кому нравятся эти приложения.", "StillCraft lives on the goodwill of people who like these apps."), style = T.small)
                Link("heart", tr("Поддержать разработку", "Support development"), null, Community.SUPPORT)
                Section("StillCraft")
                Link("scroll", tr("Сообщество в Telegram", "Telegram community"), tr("новости, обсуждения, другие приложения", "news, talk and the other apps"), Community.CHAT)
                Link("compass", tr("Сообщить об ошибке", "Report a bug"), tr("через бота StillCraft", "through the StillCraft bot"), Community.BUG)
                Link("leaf", tr("Предложить идею", "Suggest an idea"), null, Community.IDEA)
                Link("moon", tr("Следить за обновлениями", "Follow updates"), tr("бот напишет, когда выйдет новая версия", "the bot writes when a new version is out"), Community.FOLLOW)
                Link("door", tr("Все приложения StillCraft", "All StillCraft apps"), null, Community.SITE)
                Link("book", tr("Исходный код", "Source code"), "GitHub", Community.CODE)
                Section(tr("О приложении", "About"))
                Text(tr("Попутчики – ходилка с питомцами. Шаги и прогулки хранятся только на телефоне; в сеть уходят лишь запросы карты, погоды и OpenStreetMap.",
                    "Roamling – a walking game with companions. Steps and walks stay on the phone; only map tiles, weather and OpenStreetMap queries go online."), style = T.small)
                Text("v" + app.roamling.BuildConfig.VERSION_NAME, style = T.tiny, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.width(1.dp))
    }
}

/** A settings row in its own frame: an icon, what it is, a line under it, and its control at the end. */
@Composable
private fun Setting(icon: String, title: String, sub: String?, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp).card().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Ico(icon, 28.dp)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text(title, style = T.bodyB); if (sub != null) Text(sub, style = T.small) }
        control()
    }
}

/** An on/off switch drawn like a little lever. */
@Composable
private fun Toggle(on: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(width = 50.dp, height = 28.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(if (on) C.acc else C.track)
        .border(1.5.dp, C.frame, androidx.compose.foundation.shape.RoundedCornerShape(50)).clickable(onClick = onClick).padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.size(22.dp).clip(androidx.compose.foundation.shape.CircleShape).background(androidx.compose.ui.graphics.Color.White))
    }
}

/** Where the community lives: the StillCraft bot links carry the app's slug. */
object Community {
    private const val BOT = "https://t.me/ArcaneWhisperBot?start="
    const val SLUG = "roamling"
    const val BUG = "${BOT}bug_$SLUG"
    const val IDEA = "${BOT}wish_$SLUG"
    const val FOLLOW = "${BOT}f_$SLUG"
    const val CHAT = "https://t.me/+Q4gFEDk8O842OGVi"
    const val SUPPORT = "https://bigdevwhale.github.io/stillcraft/support/"
    const val SITE = "https://bigdevwhale.github.io/stillcraft/"
    const val CODE = "https://github.com/bigdevwhale/roamling"
}

@Composable
private fun Link(icon: String, title: String, sub: String?, url: String) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(top = 6.dp).card().clickable {
        runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
        Ico(icon, 30.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(title, style = T.bodyB); if (sub != null) Text(sub, style = T.small) }
        Text("›", style = T.h2.copy(color = C.mute))
    }
}
