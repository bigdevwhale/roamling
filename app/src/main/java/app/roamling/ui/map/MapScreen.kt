package app.roamling.ui.map

import androidx.compose.foundation.layout.navigationBarsPadding
import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.roamling.AppContainer
import app.roamling.BuildConfig
import app.roamling.MainActivity
import app.roamling.container
import app.roamling.data.parseMap
import app.roamling.domain.Catalog
import app.roamling.domain.Finds
import app.roamling.domain.Growth
import app.roamling.domain.Taste
import app.roamling.domain.fmt
import app.roamling.domain.fmt1
import app.roamling.domain.tr
import app.roamling.explore.Live
import app.roamling.explore.WalkService
import app.roamling.geo.Grid
import app.roamling.ui.Data
import app.roamling.ui.Nav
import app.roamling.ui.art.Creature
import app.roamling.ui.art.Ico
import app.roamling.ui.art.Look
import app.roamling.ui.art.Pic
import app.roamling.ui.components.Btn
import app.roamling.ui.components.Ghost
import app.roamling.ui.components.Popup
import app.roamling.ui.components.card
import app.roamling.ui.theme.C
import app.roamling.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import kotlin.math.roundToInt

/** The «Walk» button: asks for location (and steps, notifications) once, then starts a walk and opens the map. */
fun walkButton(c: AppContainer, nav: Nav, walking: Boolean) {
    nav.tab = 1
    if (walking) return
    val perms = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    MainActivity.ask(perms) {
        if (!c.feed.permitted()) { c.say(tr("Без геолокации туман не рассеется", "Without location the fog won't lift")); return@ask }
        c.feed.restart(); c.pedometer.refresh()
        c.scope.launch { c.walker.startWalk(); WalkService.start(c.app) }
    }
}

@Composable
fun MapScreen(d: Data, nav: Nav, live: Live) {
    val ctx = LocalContext.current
    val c = ctx.container
    val density = LocalDensity.current.density
    var follow by remember { mutableStateOf(true) }
    var pick by remember { mutableStateOf<Pick?>(null) }
    val overlay = remember { FogOverlay(ctx.applicationContext, c.fog, density, if (BuildConfig.DEBUG && !BuildConfig.DEMO) { la, lo -> c.walker.simulateTo(la, lo) } else null) { pick = it } }
    val dark = C.dark
    val map = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 4.0; maxZoomLevel = 19.5
            isHorizontalMapRepetitionEnabled = false; isVerticalMapRepetitionEnabled = false
            // two fingers turn the map once they plainly twist, as in Fogwalk (#3); the compass sets north back up
            overlays.add(TwistRotation(this))
            overlays.add(overlay)
            val f = live.fix
            if (f != null) { controller.setZoom(16.5); controller.setCenter(GeoPoint(f.lat, f.lon)) } else controller.setZoom(4.0)
            setOnTouchListener { _, e -> if (e.action == android.view.MotionEvent.ACTION_MOVE) follow = false; false }
        }
    }
    LaunchedEffect(dark) {
        map.overlayManager.tilesOverlay.setColorFilter(if (dark) nightTiles() else softTiles())
        map.overlayManager.tilesOverlay.loadingBackgroundColor = if (dark) 0xFF1A1E22.toInt() else 0xFFEDEFF0.toInt()
    }
    // the dot follows the phone while the map is open
    DisposableEffect(Unit) {
        val held = c.feed.permitted() || BuildConfig.DEMO
        if (held) c.feed.acquire()
        onDispose { if (held) c.feed.release() }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> when (e) { Lifecycle.Event.ON_RESUME -> map.onResume(); Lifecycle.Event.ON_PAUSE -> map.onPause(); else -> Unit } }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); map.onPause(); map.onDetach() }
    }
    // what kind each district around is: asked of OpenStreetMap for the 5 × 5 districts around the dot, once
    var kinds by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    val centre = live.fix?.let { app.roamling.domain.Story.sectorOf(it.lat, it.lon) }
    LaunchedEffect(centre, d.patches) {
        val c0 = centre ?: return@LaunchedEffect
        val around = (-2..2).flatMap { dx -> (-2..2).map { dy -> app.roamling.geo.Grid.key(app.roamling.geo.Grid.x(c0) + dx, app.roamling.geo.Grid.y(c0) + dy) } }
            .sortedBy { kotlin.math.abs(app.roamling.geo.Grid.x(it) - app.roamling.geo.Grid.x(c0)) + kotlin.math.abs(app.roamling.geo.Grid.y(it) - app.roamling.geo.Grid.y(c0)) }
        fun known() = around.mapNotNull { k -> app.roamling.domain.Story.spiritOf(k, d.patchKeys.filter { app.roamling.domain.Story.sectorOfPatch(it.first) == k }.map { it.second })?.let { k to it.ordinal } }.toMap()
        kinds = known()
        if (BuildConfig.DEMO) return@LaunchedEffect
        for (k in around) if (k !in kinds) { app.roamling.domain.Tastes.ensure(app.roamling.domain.Tastes.patchIn(k)); kinds = known() }
    }
    val sc = d.s.scent.takeIf { it.day == d.today && !it.done }
    val now = System.currentTimeMillis()
    overlay.scene = Scene(
        fix = live.fix?.takeIf { it.time > 0 || BuildConfig.DEMO }?.let { it.lat to it.lon }, trail = live.trail,
        leader = d.leader?.let { "pet_${it.species}_${Growth.frame(it)}" },
        scent = sc?.let { Triple(it.lat, it.lon, it.item) }, scentKind = sc?.kind ?: "sparkle", shadows = d.s.shadows.filter { it.until > now },
        rainbow = if (d.s.rainbowUntil > now) d.s.rainbowLat to d.s.rainbowLon else null,
        home = if (d.s.homeLat != 0.0) d.s.homeLat to d.s.homeLon else null, dark = dark, walking = d.s.walking, warm = d.s.finale > 0,
        seals = d.s.seals.mapValues { (_, v) -> runCatching { app.roamling.domain.Spirit.valueOf(v.substringBefore('|')).seal }.getOrDefault(4) },
        keeper = d.letters >= 3 && d.s.finale == 0L && (System.currentTimeMillis() / 60_000 % 10) < 3,
        homeDistrict = d.home, hereDistrict = live.fix?.let { app.roamling.domain.Story.sectorOf(it.lat, it.lon) }, kinds = kinds,
        residents = d.s.residents.mapNotNull { r -> d.byId[r.pet]?.let { p ->
            Dweller(p.id, r.lat, r.lon, "pet_${p.species}_${Growth.frame(p)}", d.entries.none { it.ref == "visit:${p.id}:${d.monday}" } && now - r.at >= Growth.SETTLE_MS) } },
    )
    LaunchedEffect(Unit) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { while (true) { map.invalidate(); delay(50) } } }
    val fix = live.fix
    var centered by remember { mutableStateOf(false) }
    LaunchedEffect(fix?.lat, fix?.lon, follow) {
        if (fix == null) return@LaunchedEffect
        while (map.width == 0) delay(50)
        if (!centered) { centered = true; map.controller.setZoom(if (BuildConfig.DEMO) 15.8 else 17.0); map.controller.setCenter(GeoPoint(fix.lat, fix.lon)) }
        else if (follow) map.controller.animateTo(GeoPoint(fix.lat, fix.lon))
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView({ map }, Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)) {
            RoundBtn("compass") { follow = true; map.mapOrientation = 0f; fix?.let { map.controller.animateTo(GeoPoint(it.lat, it.lon)) } }
            if (sc != null) RoundBtn("find_${sc.item}", pic = true) { follow = false; map.controller.animateTo(GeoPoint(sc.lat, sc.lon)) }
        }
        // a weak or wandering GPS is said plainly: nothing is counted until it sharpens (#2)
        live.fix?.takeIf { it.time > 0 && it.accuracy > app.roamling.explore.Track.MAX_ACCURACY }?.let { f ->
            Text(tr("Слабый GPS (±${f.accuracy.toInt()} м): туман и шаги не засчитываются, пока сигнал не станет точнее. Выйдите на открытое место.",
                "Weak GPS (±${f.accuracy.toInt()} m): fog and steps don't count until it sharpens. Step into the open."),
                style = T.small.copy(color = C.ink), modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).navigationBarsPadding().card().padding(horizontal = 12.dp, vertical = 8.dp))
        }
        Column(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp)) {
            live.fix?.takeIf { it.time > 0 || BuildConfig.DEMO }?.let { f ->
                val k = app.roamling.domain.Story.sectorOf(f.lat, f.lon)
                val dist = remember(k, d.patches, d.pets) { d.district(k) }
                Row(Modifier.padding(bottom = 6.dp).card().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val sealed = d.s.seals[k]?.substringBefore('|')?.let { runCatching { app.roamling.domain.Spirit.valueOf(it) }.getOrNull() }
                    if (sealed != null) Pic("seal_${sealed.seal}", 22.dp) else Ico("cloud", 20.dp)
                    val kindName = (sealed ?: kinds[k]?.let { app.roamling.domain.Spirit.entries[it] })?.kind
                    Text((kindName?.replaceFirstChar { it.uppercase() }?.plus(" · ") ?: "") + if (sealed != null) tr("под печатью", "sealed") else tr("${(dist.share * 100).toInt()}% · обитатели ${dist.metCount}/3", "${(dist.share * 100).toInt()}% · dwellers ${dist.metCount}/3"),
                        style = T.smallB, modifier = Modifier.padding(start = 6.dp))
                }
            }
            live.taste?.let { t -> TasteTag(t, Modifier) }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
            if (d.s.walking) WalkPanel(d, live) else Row(Modifier.fillMaxWidth().card().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Ico("boots", 34.dp)
                Text(tr("Нажмите «Гулять» – туман начнёт таять, а рюкзак – расти.", "Press «Walk» – the fog will lift and the backpack will grow."), style = T.small.copy(color = C.ink), modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
    pick?.let { p -> PickCard(p, d, live) { pick = null } }
}

@Composable
private fun RoundBtn(icon: String, pic: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.padding(bottom = 8.dp).size(46.dp).clip(CircleShape).background(C.card).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (pic) Pic(icon, 30.dp) else Ico(icon, 30.dp)
    }
}

@Composable
private fun TasteTag(t: Taste, modifier: Modifier) {
    Row(modifier.card().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Ico(t.icon, 20.dp)
        Text(tr("Здесь: ", "Here: ") + t.title.lowercase(), style = T.smallB, modifier = Modifier.padding(start = 6.dp))
    }
}

/** The walk as it goes: time, way, new patches, what the backpack ate; a lure; the end. */
@Composable
private fun WalkPanel(d: Data, live: Live) {
    val c = LocalContext.current.container
    val w = live.walk
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    Column(Modifier.fillMaxWidth().card().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val secs = if (w == null) 0 else ((System.currentTimeMillis() - w.start) / 1000).toInt().also { tick }
            Stat(clock(secs), tr("в пути", "out"), Modifier.weight(1f))
            Stat(fmt1((w?.meters ?: 0.0) / 1000), tr("км", "km"), Modifier.weight(1f))
            Stat(fmt(w?.patches ?: 0), tr("новых клочков", "new patches"), Modifier.weight(1f))
        }
        val eaten = live.eaten.ifEmpty { parseMap(w?.tastes.orEmpty()) }
        if (eaten.isNotEmpty()) {
            val total = eaten.values.sum().coerceAtLeast(1.0)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp).height(10.dp).clip(RoundedCornerShape(50))) {
                Taste.entries.forEach { t -> val v = eaten[t.name] ?: 0.0; if (v > 0) Box(Modifier.weight((v / total).toFloat()).fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF000000 or t.color))) }
            }
            Text(Taste.entries.filter { (eaten[it.name] ?: 0.0) > 0 }.joinToString(" · ") { "${it.title} ${fmt(((eaten[it.name] ?: 0.0)).roundToInt())} ${tr("м", "m")}" }, style = T.tiny, modifier = Modifier.padding(top = 4.dp))
        }
        val blowing = (d.s.breezeUntil - System.currentTimeMillis()).also { tick }
        if (blowing > 0) Text(tr("Ветерок дует ещё ${(blowing / 60_000 + 1)} мин – туман расходится вдвое шире", "The breeze blows ${(blowing / 60_000 + 1)} min more – the fog parts twice as wide"),
            style = T.tiny.copy(color = C.acc), modifier = Modifier.padding(top = 8.dp))
        // the backpack's things in a row of their own when there are two, so no label is cut short
        val both = d.s.breezes > 0 && d.s.lures > 0
        @Composable fun Things(m: (Float) -> Modifier) {
            if (d.s.breezes > 0) Ghost(tr("Ветерок · ${d.s.breezes}", "Breeze · ${d.s.breezes}"), m(1f), small = true) {
                c.write { if (walker.breeze()) say(tr("Ветерок выпущен: полчаса туман тает шире", "The breeze is out: the fog melts wider for half an hour")) }
            }
            if (d.s.lures > 0) Ghost(tr("Приманка · ${d.s.lures}", "Lure · ${d.s.lures}"), m(1f), small = true) {
                c.write { if (!walker.lure()) say(tr("Сейчас никто не придёт: не та погода и не тот час", "Nobody will come now: wrong weather, wrong hour")) }
            }
        }
        if (both) Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Things { Modifier.weight(it) } }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!both) Things { Modifier.weight(it) }
            Btn(tr("Закончить прогулку", "Finish the walk"), Modifier.weight(1.4f), small = true) { WalkService.stop(c.app) }
        }
    }
}

@Composable
private fun Stat(v: String, l: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v, style = T.num.copy(fontSize = T.num.fontSize * .85f), maxLines = 1)
        Text(l, style = T.tiny, maxLines = 1)
    }
}

@Composable
private fun PickCard(p: Pick, d: Data, live: Live, onClose: () -> Unit) {
    val here = live.fix
    fun dist(la: Double, lo: Double) = here?.let { "${fmt(Grid.distance(it.lat, it.lon, la, lo).roundToInt())} ${tr("м", "m")}" } ?: ""
    Popup(onClose) {
        when (p) {
            Pick.Scent -> {
                val sc = d.s.scent
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Pic("find_${sc.item}", 90.dp) }
                Text(app.roamling.domain.bi(sc.place).replaceFirstChar { it.uppercase() }, style = T.h2)
                Text(tr("Вожак чует здесь: ", "The leader smells here: ") + Finds.name(sc.item).lowercase() + ". " + dist(sc.lat, sc.lon), style = T.body)
            }
            is Pick.OfShadow -> {
                val sp = Catalog[p.s.species]
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Creature(sp.id, 0, 120.dp, look = Look.SHADOW) }
                Text(tr("Кто-то в тумане", "Someone in the fog"), style = T.h2)
                Text(tr("Подойдите ближе – и он пойдёт с вами. ", "Come closer and it will come along. ") + dist(p.s.lat, p.s.lon) +
                    tr(". Ждёт ещё ${((p.s.until - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)} мин.", ". Waits ${((p.s.until - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)} more min."), style = T.body)
            }
            is Pick.District -> {
                val dist = d.district(p.key)
                val sealed = d.s.seals[p.key]?.substringBefore('|')?.let { runCatching { app.roamling.domain.Spirit.valueOf(it) }.getOrNull() }
                val sp = sealed ?: dist.spirit
                val known = sealed != null || app.roamling.domain.Tastes.ofSector(p.key) != null
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Pic(app.roamling.ui.journal.spiritPic(sp, d.s.seals[p.key]), 120.dp) }
                Text(if (known) sp.kind.replaceFirstChar { it.uppercase() } else tr("Район ещё в тумане", "A district still in the fog"), style = T.h2)
                Text(if (!known) tr("Каким он окажется, станет ясно, когда карта узнает его улицы.", "What it is will show once the map learns its streets.")
                    else if (sealed != null) tr("Под печатью: ${sp.title}. ${sp.about}", "Sealed: ${sp.title}. ${sp.about}")
                    else tr("Здесь спит ${sp.title.lowercase()}. Пройдено клочков ${(dist.share * 100).toInt()}% из 50%, обитателей встречено ${dist.metCount} из 3.",
                        "${sp.title} sleeps here. Patches walked ${(dist.share * 100).toInt()}% of 50%, dwellers met ${dist.metCount} of 3."), style = T.body, modifier = Modifier.padding(top = 6.dp))
                if (known) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dist.residents.forEach { r -> app.roamling.ui.art.Mini(r.id, 48.dp, dim = r.id !in dist.met) }
                }
            }
            is Pick.OfResident -> {
                val pet = d.byId[p.pet]
                val r = d.residents[p.pet]
                if (pet != null && r != null) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { app.roamling.ui.art.PetArt(pet, 120.dp) }
                    Text(tr("${pet.name} живёт здесь", "${pet.name} lives here"), style = T.h2)
                    Text((if (d.entries.any { it.ref == "visit:${pet.id}:${d.monday}" }) tr("На этой неделе вы уже виделись. ", "You've met this week already. ")
                        else if (System.currentTimeMillis() - r.at < Growth.SETTLE_MS) tr("Обживается – первый подарок будет завтра. ", "Settling in – the first gift comes tomorrow. ")
                        else tr("Подойдите – вынесет подарок. ", "Come closer – it will bring a gift. ")) + dist(r.lat, r.lon), style = T.body)
                }
            }
            Pick.Rainbow -> {
                Text(tr("Конец радуги", "The rainbow's end"), style = T.h2)
                Text(tr("Там кристаллик – и, может быть, Радужник. ", "A crystal is there – and maybe a Rainbowkit. ") + dist(d.s.rainbowLat, d.s.rainbowLon), style = T.body)
            }
        }
        Btn(tr("Понятно", "OK"), Modifier.fillMaxWidth().padding(top = 14.dp)) { onClose() }
    }
}

private fun clock(t: Int) = if (t >= 3600) "%d:%02d:%02d".format(t / 3600, t / 60 % 60, t % 60) else "%d:%02d".format(t / 60, t % 60)

/** Night tiles: inverted, hue turned back so water stays blue, and dimmed (Fogwalk's). */
internal fun nightTiles(): android.graphics.ColorMatrixColorFilter {
    val invert = android.graphics.ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f))
    val hue = android.graphics.ColorMatrix(floatArrayOf(-0.574f, 1.43f, 0.144f, 0f, 0f, 0.426f, 0.43f, 0.144f, 0f, 0f, 0.426f, 1.43f, -0.856f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
    invert.postConcat(hue)
    val dim = android.graphics.ColorMatrix(); dim.setScale(0.86f, 0.8f, 0.68f, 1f)
    invert.postConcat(dim)
    return android.graphics.ColorMatrixColorFilter(invert)
}

/** Day tiles washed out to pale grey-white, so the districts' own colours carry the map (not Fogwalk's parchment). */
internal fun softTiles(): android.graphics.ColorMatrixColorFilter {
    val m = android.graphics.ColorMatrix(); m.setSaturation(0f)
    val light = android.graphics.ColorMatrix(floatArrayOf(.72f, 0f, 0f, 0f, 68f, 0f, .72f, 0f, 0f, 70f, 0f, 0f, .72f, 0f, 72f, 0f, 0f, 0f, 1f, 0f))
    m.postConcat(light)
    return android.graphics.ColorMatrixColorFilter(m)
}
