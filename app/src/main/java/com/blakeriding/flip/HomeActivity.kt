package com.blakeriding.flip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blakeriding.flip.ui.AppPickerDialog
import com.blakeriding.flip.ui.FlipTheme
import com.blakeriding.flip.ui.HomeActions
import com.blakeriding.flip.ui.HomeNav
import com.blakeriding.flip.ui.HomeUi
import com.blakeriding.flip.ui.ModernHome
import com.blakeriding.flip.ui.V3Phone
import com.blakeriding.flip.ui.rememberNow
import com.blakeriding.flip.ui.rememberPhoneStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The home screen. */
class HomeActivity : ComponentActivity() {
    private val nav = HomeNav()
    /** Bumped on every resume and app install/removal, to reload usage, apps and contacts. */
    private val refresh = mutableIntStateOf(0)

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = closeUp()
    }
    private val packagesChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refresh.intValue++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        enableEdgeToEdge()
        // The home screen never backs out to anything; back just steps back inside the launcher.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = nav.back()
        })
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        registerReceiver(packagesChanged, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        })

        val actions = HomeActions(
            open = { pkg -> openApp(this, pkg) },
            dial = { number -> dial(this, number) },
            settings = { startActivity(Intent(this, SettingsActivity::class.java)) },
        )

        setContent {
            FlipTheme {
                val state by Store.state.collectAsStateWithLifecycle()
                val tick = refresh.intValue
                val now = rememberNow()
                val phone = rememberPhoneStatus()
                val apps by produceState(emptyList<AppEntry>(), tick) { value = withContext(Dispatchers.IO) { Apps.list(this@HomeActivity) } }
                val contacts by produceState(emptyList<Contact>(), tick, state.settings.keypad) {
                    value = withContext(Dispatchers.IO) { loadContacts(this@HomeActivity) }
                }
                // Usage only matters for apps with rules; refresh it every minute while the home screen is up.
                val usage by produceState(emptyMap<String, Long>(), tick, now) {
                    while (true) {
                        value = withContext(Dispatchers.IO) { Usage.today(this@HomeActivity) }
                        delay(60_000)
                    }
                }
                val ui = HomeUi(state, usage, now, phone, apps, contacts)
                val look = state.settings.look

                LaunchedEffect(look) { setSystemBars(look == Look.MODERN) }
                if (look == Look.MODERN) ModernHome(ui, nav, actions) else V3Phone(ui, nav, actions, outer = look == Look.V3_OUTER)

                if (nav.picking >= 0) {
                    val slot = nav.picking
                    AppPickerDialog(
                        title = "Menu slot ${slot + 1}",
                        extra = {
                            if (state.settings.favorites.getOrNull(slot) != null) {
                                TextButton(onClick = { setFavorite(slot, null) }, Modifier.fillMaxWidth()) { Text("Empty this slot") }
                            }
                        },
                        onDismiss = { nav.picking = -1 },
                        onPick = { setFavorite(slot, it) },
                    )
                }
            }
        }
    }

    private fun setFavorite(slot: Int, pkg: String?) {
        Store.update { s ->
            val favs = s.settings.favorites.toMutableList().apply { while (size < 9) add(null) }
            favs[slot] = pkg
            s.copy(settings = s.settings.copy(favorites = favs))
        }
        nav.picking = -1
    }

    /** The V3 looks draw their own status bar, so hide the system one (swipe down to see it). */
    private fun setSystemBars(show: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (show) show(WindowInsetsCompat.Type.statusBars()) else hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    /** Pressing home while already home goes back to the idle screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        nav.home()
    }

    override fun onResume() {
        super.onResume()
        refresh.intValue++
    }

    override fun onStop() {
        super.onStop()
        nav.home()
    }

    private fun closeUp() {
        nav.home()
        nav.open = false
    }

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        unregisterReceiver(packagesChanged)
        super.onDestroy()
    }
}
