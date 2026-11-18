package app.roamling

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.roamling.data.DemoSeed
import app.roamling.data.Settings
import app.roamling.domain.Lang
import app.roamling.notify.Watch
import app.roamling.ui.Onboarding
import app.roamling.ui.Root
import app.roamling.ui.Start
import app.roamling.ui.theme.RoamTheme
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class MainActivity : AppCompatActivity() {
    private var then: (() -> Unit)? = null
    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { then?.invoke(); then = null }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEMO && BuildConfig.DEMO_LANG.isNotEmpty() && AppCompatDelegate.getApplicationLocales().toLanguageTags() != BuildConfig.DEMO_LANG) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(BuildConfig.DEMO_LANG)); return }
        Lang.ru = resources.configuration.locales[0].language == "ru"
        current = WeakReference(this)
        if (BuildConfig.DEMO) {
            container.scope.launch { DemoSeed.seedIfEmpty(this@MainActivity, container) }
            Start.tab = intent.getIntExtra("tab", 0); Start.open = intent.getStringExtra("open")
        }
        Watch.schedule(this)
        // a walk left on by a killed process carries on
        container.scope.launch { if (container.settings.now().walking && container.feed.permitted()) app.roamling.explore.WalkService.start(this@MainActivity) }
        setContent {
            val settings: Settings? by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val sys = isSystemInDarkTheme()
            val dark = if (BuildConfig.SHOTS) false else when (settings?.theme) { "dark" -> true; "light" -> false; else -> sys }
            LaunchedEffect(dark) {
                val t = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(statusBarStyle = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t), navigationBarStyle = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t))
            }
            RoamTheme(dark) { settings?.let { s -> if (s.onboarded) Root(s) else Onboarding() } }
        }
    }

    /** Debug builds: `adb shell am start -n app.roamling.dev/app.roamling.MainActivity --es sim "400,45"` walks 400 m at 45°. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (!BuildConfig.DEBUG || BuildConfig.DEMO) return
        val sim = intent.getStringExtra("sim") ?: return
        val (m, b) = sim.split(',').map { it.trim().toDouble() }
        val f = container.walker.live.value.fix ?: return
        val (la, lo) = app.roamling.geo.Grid.offset(f.lat, f.lon, m, b)
        container.walker.simulateTo(la, lo)
    }

    companion object {
        private var current: WeakReference<MainActivity>? = null

        /** Asks for [list] (those not granted yet), then runs [done] whatever the answer. */
        fun ask(list: List<String>, done: () -> Unit) {
            val a = current?.get() ?: return done()
            val missing = list.filter { androidx.core.content.ContextCompat.checkSelfPermission(a, it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
            if (missing.isEmpty()) return done()
            a.then = done
            a.perms.launch(missing.toTypedArray())
        }
    }
}

/** Switches the app language: "" follows the phone, "ru" or "en" pins it. */
fun setLanguage(tag: String) = AppCompatDelegate.setApplicationLocales(if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
fun currentLanguage(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')
