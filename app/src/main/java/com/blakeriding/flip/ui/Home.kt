package com.blakeriding.flip.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.telephony.TelephonyManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.blakeriding.flip.AppEntry
import com.blakeriding.flip.AppState
import com.blakeriding.flip.Contact
import com.blakeriding.flip.KeypadMode
import com.blakeriding.flip.MINUTE_MS
import com.blakeriding.flip.Verdict
import com.blakeriding.flip.hasRules
import com.blakeriding.flip.nowZ
import com.blakeriding.flip.t9
import com.blakeriding.flip.t9Rank
import com.blakeriding.flip.verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Screen { IDLE, MENU, SEARCH }

/** Where you are in the launcher. Lives in the activity so the home button and screen-off can reset it. */
class HomeNav {
    var screen by mutableStateOf(Screen.IDLE)
    var digits by mutableStateOf("")
    /** V3 + outer screen look: false = closed, showing the small outer display. */
    var open by mutableStateOf(false)
    /** Main-menu slot being changed (long-press), or -1. */
    var picking by mutableStateOf(-1)
    /** Highlighted item for the navigation ring: a menu slot or a search result. */
    var sel by mutableIntStateOf(0)

    fun home() {
        screen = Screen.IDLE
        digits = ""
        picking = -1
        sel = 0
    }

    fun back() {
        when {
            digits.isNotEmpty() -> digits = ""
            screen != Screen.IDLE -> screen = Screen.IDLE
        }
        sel = 0
    }

    fun menu() {
        screen = if (screen == Screen.MENU) Screen.IDLE else Screen.MENU
        sel = 0
    }

    fun type(c: Char) {
        if (digits.length < 32) digits += c
        sel = 0
    }

    fun erase() {
        digits = digits.dropLast(1)
        sel = 0
    }
}

/** What the launcher draws: state plus things read from the phone. */
class HomeUi(
    val state: AppState,
    val usage: Map<String, Long>,
    val now: ZonedDateTime,
    val phone: PhoneStatus,
    val apps: List<AppEntry>,
    val contacts: List<Contact>,
) {
    val settings get() = state.settings

    fun verdict(pkg: String): Verdict = if (state.hasRules(pkg)) state.verdict(pkg, usage, now) else Verdict.FREE

    /** Keypad shown on [screen]. The search screen always has one. */
    fun keypadOn(screen: Screen): Boolean = screen == Screen.SEARCH || settings.keypad != KeypadMode.NONE

    /** Typing searches apps and contacts (not just dials). */
    fun searching(screen: Screen): Boolean = screen == Screen.SEARCH || settings.keypad == KeypadMode.T9

    fun timeText(): String = now.format(DateTimeFormatter.ofPattern(if (settings.clock24h) "H:mm" else "h:mm"))
    fun amPm(): String = if (settings.clock24h) "" else now.format(DateTimeFormatter.ofPattern("a", Locale.US))
    fun dateText(): String = now.format(DateTimeFormatter.ofPattern("EEE MMM d", Locale.US))
}

/** A T9 search result: an app or a contact. */
data class Match(val label: String, val pkg: String? = null, val contact: Contact? = null)

fun t9Results(digits: String, apps: List<AppEntry>, contacts: List<Contact>, limit: Int = 30): List<Match> {
    if (digits.isEmpty() || digits.any { it !in '0'..'9' }) return emptyList()
    val appHits = apps.mapNotNull { a -> t9Rank(digits, a.label)?.let { it to Match(a.label, pkg = a.pkg) } }
    val contactHits = contacts.mapNotNull { c ->
        val rank = t9Rank(digits, c.name) ?: if (digits.length >= 3 && digits in t9(c.number)) 2 else null
        rank?.let { it to Match(c.name, contact = c) }
    }
    return (appHits.sortedWith(compareBy({ it.first }, { it.second.label.lowercase() })) +
        contactHits.sortedWith(compareBy({ it.first }, { it.second.label.lowercase() })))
        .map { it.second }.take(limit)
}

data class PhoneStatus(val battery: Int = 100, val charging: Boolean = false, val signal: Int = 4)

private fun readPhoneStatus(context: Context): PhoneStatus {
    val b = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val plugged = (b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    val signal = try {
        context.getSystemService(TelephonyManager::class.java).signalStrength?.level ?: 0
    } catch (_: Exception) {
        0
    }
    return PhoneStatus(if (level < 0) 100 else level * 100 / scale.coerceAtLeast(1), plugged, signal)
}

@Composable
fun rememberPhoneStatus(): PhoneStatus {
    val ctx = LocalContext.current
    val s by produceState(PhoneStatus()) {
        while (true) {
            value = withContext(Dispatchers.IO) { readPhoneStatus(ctx) }
            delay(30_000)
        }
    }
    return s
}

/** The time, updated at the start of each minute. */
@Composable
fun rememberNow(): ZonedDateTime {
    val now by produceState(nowZ()) {
        while (true) {
            value = nowZ()
            val ms = System.currentTimeMillis()
            delay(MINUTE_MS - ms % MINUTE_MS + 50)
        }
    }
    return now
}
