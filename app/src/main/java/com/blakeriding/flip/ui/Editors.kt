package com.blakeriding.flip.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blakeriding.flip.ALL_DAYS
import com.blakeriding.flip.AppRule
import com.blakeriding.flip.Apps
import com.blakeriding.flip.Group
import com.blakeriding.flip.Window
import com.blakeriding.flip.fmtDuration
import com.blakeriding.flip.MINUTE_MS
import kotlin.math.roundToInt

/** Full-screen editor frame with a title, Save, and an optional Delete. */
@Composable
private fun EditorFrame(title: String, canSave: Boolean, onDismiss: () -> Unit, onSave: () -> Unit, onDelete: (() -> Unit)?, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cancel") }
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Button(onClick = onSave, enabled = canSave) { Text("Save") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                    content()
                    if (onDelete != null) {
                        Spacer(Modifier.height(24.dp))
                        OutlinedButton(onClick = onDelete, Modifier.fillMaxWidth()) { Text("Delete") }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            detail?.let { Hint(it) }
        }
        Switch(checked, onChange)
    }
}

/** Minutes slider snapped to 5. */
@Composable
private fun MinutesSlider(minutes: Int, range: IntRange, onChange: (Int) -> Unit) {
    Text(fmtDuration(minutes * MINUTE_MS) + " a day")
    Slider(
        minutes.toFloat(), { onChange(((it / 5).roundToInt() * 5).coerceIn(range)) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
    )
}

fun windowSummary(w: Window): String = "${fmtMinute(w.startMin)} – ${fmtMinute(w.endMin)}, ${daysSummary(w.days)}"

@Composable
fun RuleEditor(initial: AppRule, isNew: Boolean, notifAccess: Boolean, onDismiss: () -> Unit, onSave: (AppRule) -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    var rule by remember { mutableStateOf(initial) }
    var addingWindow by remember { mutableStateOf(false) }
    EditorFrame(Apps.label(ctx, rule.pkg), canSave = true, onDismiss, { onSave(rule) }, if (isNew) null else onDelete) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(rule.pkg, 48.dp)
            Spacer(Modifier.width(12.dp))
            Text(Apps.label(ctx, rule.pkg), style = MaterialTheme.typography.titleMedium)
        }
        SectionTitle("Blocking")
        SwitchRow("Always blocked", "Needs an unlock every time you open it", rule.always) { rule = rule.copy(always = it) }
        SwitchRow("Daily limit", "Locks for the rest of the day once used up", rule.dailyLimitMin != null) {
            rule = rule.copy(dailyLimitMin = if (it) 30 else null)
        }
        rule.dailyLimitMin?.let { m -> MinutesSlider(m, 5..240) { rule = rule.copy(dailyLimitMin = it) } }

        SectionTitle("Blocked hours")
        if (rule.schedules.isEmpty()) Hint("None. Add hours when this app is always locked, like 10 PM – 7 AM.")
        rule.schedules.forEachIndexed { i, w ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(windowSummary(w), Modifier.weight(1f))
                IconButton(onClick = { rule = rule.copy(schedules = rule.schedules.filterIndexed { j, _ -> j != i }) }) {
                    Icon(Icons.Default.Close, "Remove")
                }
            }
        }
        OutlinedButton(onClick = { addingWindow = true }) { Text("Add blocked hours") }

        SectionTitle("Notifications")
        SwitchRow(
            "Hide notifications while blocked",
            if (notifAccess) null else "Needs notification access (Flip settings → Setup)",
            rule.hideNotifs,
        ) { rule = rule.copy(hideNotifs = it) }

        if (!rule.always && rule.dailyLimitMin == null && rule.schedules.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Nothing is set, so this app won't be blocked.", color = MaterialTheme.colorScheme.error)
        }
    }
    if (addingWindow) WindowDialog(onDismiss = { addingWindow = false }) {
        rule = rule.copy(schedules = rule.schedules + it)
        addingWindow = false
    }
}

@Composable
private fun WindowDialog(onDismiss: () -> Unit, onAdd: (Window) -> Unit) {
    var w by remember { mutableStateOf(Window(22 * 60, 7 * 60, ALL_DAYS)) }
    var picking by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Blocked hours") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = "start" }, Modifier.fillMaxWidth()) { Text("From ${fmtMinute(w.startMin)}") }
                OutlinedButton(onClick = { picking = "end" }, Modifier.fillMaxWidth()) { Text("Until ${fmtMinute(w.endMin)}") }
                if (w.endMin <= w.startMin) Hint(if (w.endMin == w.startMin) "Blocks the whole day." else "Runs past midnight into the next morning.")
                Text("Starting on")
                DayPicker(w.days) { w = w.copy(days = it) }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(w) }, enabled = w.days.isNotEmpty()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    picking?.let { which ->
        TimePickerDialog(
            if (which == "start") "From" else "Until",
            if (which == "start") w.startMin else w.endMin,
            onDismiss = { picking = null },
        ) { m ->
            w = if (which == "start") w.copy(startMin = m) else w.copy(endMin = m)
            picking = null
        }
    }
}

@Composable
fun GroupEditor(initial: Group, isNew: Boolean, onDismiss: () -> Unit, onSave: (Group) -> Unit, onDelete: () -> Unit) {
    var g by remember { mutableStateOf(initial) }
    EditorFrame(if (isNew) "New group" else g.name, canSave = g.name.isNotBlank() && g.pkgs.isNotEmpty(), onDismiss, { onSave(g.copy(name = g.name.trim())) }, if (isNew) null else onDelete) {
        OutlinedTextField(g.name, { g = g.copy(name = it) }, label = { Text("Name (e.g. Social)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        SectionTitle("Shared daily limit")
        MinutesSlider(g.limitMin, 5..240) { g = g.copy(limitMin = it) }
        Hint("Time in any of these apps counts toward the limit. When it's used up, they all lock until midnight.")
        SectionTitle("Apps (${g.pkgs.size})")
        MultiAppPicker(g.pkgs) { g = g.copy(pkgs = it) }
    }
}
