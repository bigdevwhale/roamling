package app.roamling

import android.graphics.Bitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.roamling.data.DemoSeed
import app.roamling.domain.Lang
import app.roamling.ui.Onboarding
import app.roamling.ui.Root
import app.roamling.ui.Start
import app.roamling.ui.theme.RoamTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The demo's screens drawn to PNGs in app/build/art/screens (run with `-Prender`). The map (osmdroid) is left out. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ru-w393dp-h852dp-xxhdpi")
class RenderScreens {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val out = File(System.getProperty("render.out") ?: "build/art", "screens").apply { mkdirs() }
    private val dark = System.getProperty("render.dark") == "1"

    private fun shot(name: String) {
        repeat(6) { rule.mainClock.advanceTimeBy(400); Thread.sleep(150); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
        val v = rule.activity.window.decorView
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { v.draw(android.graphics.Canvas(b)) }
        File(out, "${if (dark) "d-" else ""}$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun render() {
        Lang.ru = System.getProperty("render.lang") != "en"
        val c = ApplicationProvider.getApplicationContext<RoamlingApp>().container
        var i by mutableIntStateOf(-1)
        rule.mainClock.autoAdvance = false
        rule.setContent { RoamTheme(dark) { key(i) { if (i == -2) Onboarding() else { val s by c.settings.settings.collectAsState(null); s?.let { st -> if (i >= 0 && st.onboarded) Root(st) } } } } }
        rule.runOnUiThread { i = -2 }; shot("0-onboarding")
        runBlocking { DemoSeed.seedIfEmpty(rule.activity, c) }
        val lead = runBlocking { c.settings.now().team }
        val shots = listOf(
            "1-home" to (0 to null), "2-bestiary" to (2 to null), "3-journal" to (3 to null),
            "4-pet" to (0 to "pet:${lead[0]}"), "4b-teen" to (0 to "pet:${lead[1]}"), "5-species" to (2 to "species:rustle"),
            "6-team" to (0 to "team"), "7-scent" to (0 to "scent"), "8-quests" to (0 to "quests"), "9-shop" to (0 to "shop"),
            "10-settings" to (0 to "settings"), "11-walk" to (3 to "walk:"), "12-call" to (0 to "call"),
        )
        for ((n, sh) in shots.withIndex()) {
            Start.tab = sh.second.first; Start.open = sh.second.second
            rule.runOnUiThread { i = n }
            shot(sh.first)
        }
    }
}
