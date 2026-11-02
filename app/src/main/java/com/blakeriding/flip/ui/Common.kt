package com.blakeriding.flip.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blakeriding.flip.ALL_DAYS
import com.blakeriding.flip.AppEntry
import com.blakeriding.flip.Apps
import com.blakeriding.flip.LogKind
import com.blakeriding.flip.Reason
import com.blakeriding.flip.Settings
import com.blakeriding.flip.Store
import com.blakeriding.flip.Verdict
import com.blakeriding.flip.checkCode
import com.blakeriding.flip.fmtDuration
import com.blakeriding.flip.log
import com.blakeriding.flip.normalizeCode
import com.blakeriding.flip.nowMs
import com.blakeriding.flip.randomCode
import com.blakeriding.flip.zone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.random.Random

private val colors = darkColorScheme(
    primary = Color(0xFF4FB4FF),
    onPrimary = Color(0xFF00213D),
    secondary = Color(0xFF9CC9FF),
    tertiary = Color(0xFF7FE0B0),
    background = Color(0xFF0B1220),
    surface = Color(0xFF0B1220),
    surfaceVariant = Color(0xFF18233A),
    surfaceContainer = Color(0xFF121B2E),
    surfaceContainerHigh = Color(0xFF1B2740),
    surfaceContainerHighest = Color(0xFF22304C),
    error = Color(0xFFFF8A80),
)

@Composable
fun FlipTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = colors, content = content)

private val shortTime = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

fun fmtMinute(minuteOfDay: Int): String = LocalTime.of(minuteOfDay / 60 % 24, minuteOfDay % 60).format(shortTime)
fun fmtTime(epoch: Long): String = Instant.ofEpochMilli(epoch).atZone(zone()).format(shortTime)

private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")
private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun daysSummary(days: Set<Int>): String = when (days) {
    ALL_DAYS -> "Every day"
    setOf(1, 2, 3, 4, 5) -> "Weekdays"
    setOf(6, 7) -> "Weekends"
    else -> days.sorted().joinToString(" ") { DAY_NAMES[it - 1] }
}

/** One line on why an app is locked, or how long it has left. Empty for apps without rules. */
fun describe(v: Verdict): String = when (v.reason) {
    Reason.ALWAYS -> "Always blocked"
    Reason.SCHEDULE -> "Blocked until ${fmtTime(v.blockedUntil ?: 0)}"
    Reason.LIMIT -> "Daily limit used up"
    Reason.GROUP -> "${v.groupName} limit used up"
    null -> when {
        v.grantUntil != null -> "Unlocked, ${fmtDuration(v.grantUntil - nowMs())} left"
        v.leftMs != null -> "${fmtDuration(v.leftMs)} left today"
        else -> ""
    }
}

/** Very short status for small screens: "LOCKED", "12m", "". */
fun shortStatus(v: Verdict): String = when {
    v.blocked -> "LOCKED"
    v.grantUntil != null -> fmtDuration(v.grantUntil - nowMs())
    v.leftMs != null -> fmtDuration(v.leftMs)
    else -> ""
}

@Composable
fun AppIcon(pkg: String, size: Dp, modifier: Modifier = Modifier, pixel: Boolean = false) {
    val ctx = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bmp by produceState<ImageBitmap?>(null, pkg, px, pixel) {
        value = withContext(Dispatchers.IO) { Apps.icon(ctx, pkg, px, pixel) }
    }
    Box(modifier.size(size)) {
        bmp?.let { Image(it, null, Modifier.size(size), filterQuality = if (pixel) FilterQuality.None else FilterQuality.Medium) }
    }
}

@Composable
fun rememberApps(): List<AppEntry>? {
    val ctx = LocalContext.current
    val apps by produceState<List<AppEntry>?>(null) { value = withContext(Dispatchers.IO) { Apps.list(ctx) } }
    return apps
}

/** Pick one app. */
@Composable
fun AppPickerDialog(title: String, exclude: Set<String> = emptySet(), extra: (@Composable () -> Unit)? = null, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val apps = rememberApps()
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("Search") }, modifier = Modifier.fillMaxWidth())
                extra?.invoke()
                if (apps == null) Text("Loading…", Modifier.padding(16.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(apps.orEmpty().filter { it.pkg !in exclude && it.label.contains(query.trim(), ignoreCase = true) }, key = { it.pkg }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(app.pkg) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.pkg, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(app.label)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Pick several apps. */
@Composable
fun MultiAppPicker(selected: Set<String>, onChange: (Set<String>) -> Unit) {
    val apps = rememberApps()
    var query by remember { mutableStateOf("") }
    OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("Search apps") }, modifier = Modifier.fillMaxWidth())
    if (apps == null) Text("Loading…", Modifier.padding(16.dp))
    LazyColumn(Modifier.heightIn(max = 360.dp)) {
        // Chosen apps first so they're easy to review.
        val list = apps.orEmpty().filter { it.label.contains(query.trim(), ignoreCase = true) }.sortedBy { it.pkg !in selected }
        items(list, key = { it.pkg }) { app ->
            val on = app.pkg in selected
            Row(
                Modifier.fillMaxWidth().clickable { onChange(if (on) selected - app.pkg else selected + app.pkg) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(on, { onChange(if (on) selected - app.pkg else selected + app.pkg) })
                AppIcon(app.pkg, 32.dp)
                Spacer(Modifier.width(12.dp))
                Text(app.label)
            }
        }
    }
}

@Composable
fun DayPicker(days: Set<Int>, onChange: (Set<Int>) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (d in 1..7) {
            FilterChip(
                selected = d in days,
                onClick = { onChange(if (d in days) days - d else days + d) },
                label = { Text(DAY_LETTERS[d - 1]) },
                shape = CircleShape,
                modifier = Modifier.size(38.dp),
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(title: String, initialMinute: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinute / 60, initialMinute = initialMinute % 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Shows a random 24-character code; [onDone] runs once it's typed correctly. */
@Composable
fun TypeCode(onDone: () -> Unit) {
    val code = remember { randomCode(Random.Default) }
    var input by remember { mutableStateOf("") }
    Text(
        code.chunked(4).joinToString(" "), fontFamily = FontFamily.Monospace, fontSize = 22.sp,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = input,
        onValueChange = {
            input = it
            if (normalizeCode(it) == code) onDone()
        },
        singleLine = true,
        label = { Text("Type the code") },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
    Text("${normalizeCode(input).length} / ${code.length}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Field for the printed emergency code. Wrong codes are logged. */
@Composable
fun EmergencyEntry(settings: Settings, button: String, onOk: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    if (settings.codeHash == null) {
        Text("No emergency code is set up.")
        return
    }
    OutlinedTextField(
        code, { code = it; wrong = false }, singleLine = true, isError = wrong, label = { Text("Emergency code") },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
    if (wrong) Text("Wrong code", color = MaterialTheme.colorScheme.error)
    TextButton(onClick = {
        if (settings.checkCode(code)) onOk() else {
            wrong = true
            Store.update { it.log(LogKind.WRONG_CODE, nowMs()) }
        }
    }) { Text(button) }
}

/** Asks for the typed code (or the emergency code) before a change that makes Flip easier to get around. */
@Composable
fun LoosenDialog(reasons: List<String>, settings: Settings, onDismiss: () -> Unit, onOk: () -> Unit) {
    var emergency by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Protection is on") },
        text = {
            Column {
                Text("This change makes Flip easier to get around:")
                reasons.forEach { Text("• $it") }
                Spacer(Modifier.height(12.dp))
                if (emergency) EmergencyEntry(settings, "Unlock", onOk) else TypeCode(onOk)
                if (settings.codeHash != null) {
                    TextButton(onClick = { emergency = !emergency }) { Text(if (emergency) "Type a code instead" else "Use the emergency code") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
