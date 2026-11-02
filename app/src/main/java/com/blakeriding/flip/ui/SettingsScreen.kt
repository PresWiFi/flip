package com.blakeriding.flip.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.blakeriding.flip.AppRule
import com.blakeriding.flip.AppState
import com.blakeriding.flip.Apps
import com.blakeriding.flip.Group
import com.blakeriding.flip.KeypadMode
import com.blakeriding.flip.LogEvent
import com.blakeriding.flip.LogKind
import com.blakeriding.flip.Look
import com.blakeriding.flip.MINUTE_MS
import com.blakeriding.flip.Store
import com.blakeriding.flip.Tag
import com.blakeriding.flip.TagKind
import com.blakeriding.flip.Usage
import com.blakeriding.flip.V3Color
import com.blakeriding.flip.adminIntent
import com.blakeriding.flip.batteryIntent
import com.blakeriding.flip.fmtDuration
import com.blakeriding.flip.generateEmergencyCode
import com.blakeriding.flip.hasContacts
import com.blakeriding.flip.hasNfc
import com.blakeriding.flip.hasNotifAccess
import com.blakeriding.flip.hasUsageAccess
import com.blakeriding.flip.hashEmergencyCode
import com.blakeriding.flip.homeSettingsIntent
import com.blakeriding.flip.ignoresBatteryOptimizations
import com.blakeriding.flip.isAccessibilityEnabled
import com.blakeriding.flip.isAdminActive
import com.blakeriding.flip.isAttempt
import com.blakeriding.flip.isDefaultHome
import com.blakeriding.flip.isRandomUid
import com.blakeriding.flip.log
import com.blakeriding.flip.loosenings
import com.blakeriding.flip.newId
import com.blakeriding.flip.nfcOn
import com.blakeriding.flip.notifAccessIntent
import com.blakeriding.flip.nowMs
import com.blakeriding.flip.nowZ
import com.blakeriding.flip.quickLeft
import com.blakeriding.flip.randomCode
import com.blakeriding.flip.toast
import com.blakeriding.flip.usageAccessIntent
import com.blakeriding.flip.verdict
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/** A change waiting for the code, because it loosens the rules while protection is on. */
private class Pending(val reasons: List<String>, val change: (AppState) -> AppState)

@Composable
fun SettingsScreen(state: AppState, nfc: SharedFlow<String>, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val s = state.settings
    // Re-check permissions every time the screen comes back (after returning from system settings).
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { tick++ } }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    var pending by remember { mutableStateOf<Pending?>(null) }
    var newCode by remember { mutableStateOf<String?>(null) }
    var addingApp by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<Pair<AppRule, Boolean>?>(null) }
    var editingGroup by remember { mutableStateOf<Pair<Group, Boolean>?>(null) }
    var addingTag by remember { mutableStateOf(false) }

    /** Applies a change, or asks for the code first if it makes Flip easier to get around. */
    fun change(f: (AppState) -> AppState) {
        val old = Store.state.value
        val new = f(old)
        if (new == old) return
        val why = if (old.settings.protectionOn) loosenings(old, new) { Apps.label(ctx, it) } else emptyList()
        if (why.isEmpty()) Store.update(f) else pending = Pending(why, f)
    }

    fun settings(f: (com.blakeriding.flip.Settings) -> com.blakeriding.flip.Settings) = change { it.copy(settings = f(it.settings)) }

    val usage by produceState(emptyMap<String, Long>(), tick) { value = withContext(Dispatchers.IO) { Usage.today(ctx) } }
    val now = nowZ()

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Text("Flip", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 16.dp))

        @Suppress("UNUSED_EXPRESSION") tick // read so the checks below recompose on resume
        SectionTitle("Setup")
        Check("Home app", isDefaultHome(ctx), "Set Flip as the default home app") { ctx.startActivity(homeSettingsIntent()) }
        Check("Usage access", hasUsageAccess(ctx), "Needed to count time for daily limits") { ctx.startActivity(usageAccessIntent()) }
        Check("App blocker", isAccessibilityEnabled(ctx), "Accessibility: locks blocked apps however they're opened") {
            ctx.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        Check("Uninstall lock", isAdminActive(ctx), "Device admin: Android won't uninstall Flip while this is on") { ctx.startActivity(adminIntent(ctx)) }
        Check("Emergency code", s.codeHash != null, "Print it and keep it somewhere out of the way") { newCode = generateEmergencyCode(SecureRandom()) }
        Check("Battery: unrestricted", ignoresBatteryOptimizations(ctx), "OnePlus stops apps that aren't. Also set Battery → Unrestricted in app info.") {
            ctx.startActivity(batteryIntent(ctx))
        }
        Check("Notification access", hasNotifAccess(ctx), "Optional. Needed to hide notifications from blocked apps") { ctx.startActivity(notifAccessIntent()) }
        Check("Contacts", hasContacts(ctx), "Optional. Lets the keypad find contacts by T9") { contactsPermission.launch(Manifest.permission.READ_CONTACTS) }
        if (hasNfc(ctx)) Check("NFC on", nfcOn(ctx), "Needed for card tags") { ctx.startActivity(Intent(AndroidSettings.ACTION_NFC_SETTINGS)) }
        Hint("Finish setup before turning protection on: with it on, these system pages are blocked.")

        SectionTitle("Protection")
        val ready = isAdminActive(ctx) && isAccessibilityEnabled(ctx) && s.codeHash != null
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Protection on")
                Hint(
                    if (!ready && !s.protectionOn) "Finish app blocker, uninstall lock and emergency code first."
                    else "Blocks uninstalling, force-stopping and switching Flip off. Loosening any rule needs a code.",
                )
            }
            Switch(s.protectionOn, enabled = ready || s.protectionOn, onCheckedChange = { on -> settings { it.copy(protectionOn = on) } })
        }
        if (s.codeHash != null) {
            TextButton(onClick = {
                if (s.protectionOn) ctx.toast("Turn protection off to make a new code") else newCode = generateEmergencyCode(SecureRandom())
            }) { Text("Make a new emergency code") }
        }

        SectionTitle("Today")
        val quick = state.quickLeft(now)
        Text("Quick unlocks left: $quick of ${s.quickPerDay}")
        if (!hasUsageAccess(ctx)) Hint("Turn on usage access to see time used.")
        state.rules.filter { it.dailyLimitMin != null }.forEach { r ->
            UsageBar(Apps.label(ctx, r.pkg), usage[r.pkg] ?: 0L, r.dailyLimitMin!!)
        }
        state.groups.forEach { g -> UsageBar(g.name, g.pkgs.sumOf { usage[it] ?: 0L }, g.limitMin) }

        SectionTitle("Blocked apps")
        if (state.rules.isEmpty()) Hint("No apps yet. Add one to block it always, on a schedule, or after a daily limit.")
        state.rules.forEach { r ->
            val v = state.verdict(r.pkg, usage, now)
            Row(
                Modifier.fillMaxWidth().clickable { editingRule = r to false }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(r.pkg, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(Apps.label(ctx, r.pkg))
                    Hint(ruleSummary(r))
                }
                Text(describe(v), style = MaterialTheme.typography.bodySmall, color = if (v.blocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(onClick = { addingApp = true }) { Text("Add app") }

        SectionTitle("Groups")
        Hint("Several apps sharing one daily limit.")
        state.groups.forEach { g ->
            Row(Modifier.fillMaxWidth().clickable { editingGroup = g to false }.padding(vertical = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(g.name)
                    Hint("${g.pkgs.size} apps · ${fmtDuration(g.limitMin * MINUTE_MS)} a day: " + g.pkgs.joinToString { Apps.label(ctx, it) })
                }
            }
        }
        OutlinedButton(onClick = { editingGroup = Group(newId(), "", emptySet(), 45) to true }) { Text("Add group") }

        SectionTitle("Unlocking")
        Text("Scanning a tag, typing a code or the emergency code unlocks an app for ${s.unlockMin} minutes. Quick unlocks need nothing and last ${s.quickMin} minutes.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Quick unlocks per day", Modifier.weight(1f))
            TextButton(onClick = { settings { it.copy(quickPerDay = (it.quickPerDay - 1).coerceAtLeast(0)) } }) { Text("−") }
            Text("${s.quickPerDay}")
            TextButton(onClick = { settings { it.copy(quickPerDay = (it.quickPerDay + 1).coerceAtMost(10)) } }) { Text("+") }
        }

        SectionTitle("Look")
        Choice("Style", listOf(Look.V3 to "Razr V3", Look.V3_OUTER to "V3 + outer screen", Look.MODERN to "Modern"), s.look) { l -> settings { it.copy(look = l) } }
        if (s.look != Look.MODERN) {
            Choice("V3 color", listOf(V3Color.SILVER to "Silver", V3Color.BLACK to "Black", V3Color.PINK to "Pink"), s.v3Color) { c ->
                settings { it.copy(v3Color = c) }
            }
        }
        Choice("Keypad on the home screen", listOf(KeypadMode.T9 to "Dial + T9 search", KeypadMode.DIAL to "Dial only", KeypadMode.NONE to "None"), s.keypad) { k ->
            settings { it.copy(keypad = k) }
        }
        ToggleRow("24-hour clock", s.clock24h) { on -> settings { it.copy(clock24h = on) } }
        ToggleRow("Pixel-style app icons (V3 looks)", s.pixelIcons) { on -> settings { it.copy(pixelIcons = on) } }
        Hint("Main menu apps: open the menu and long-press a slot to change it.")

        SectionTitle("Tags")
        Hint("NFC cards/stickers and barcodes. Scanning any of them unlocks a blocked app. Keep them somewhere you'd have to get up for.")
        state.tags.forEach { tag ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${tag.name}  ", Modifier.weight(1f))
                Text(if (tag.kind == TagKind.NFC) "NFC" else "Barcode", color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { change { st -> st.copy(tags = st.tags.filterNot { it.id == tag.id }) } }) { Icon(Icons.Default.Close, "Delete tag") }
            }
        }
        OutlinedButton(onClick = { addingTag = true }) { Text("Add tag") }

        SectionTitle("Log")
        LogSection(state)
        Spacer(Modifier.height(48.dp))
    }

    pending?.let { p ->
        LoosenDialog(p.reasons, s, onDismiss = { pending = null }) {
            Store.update { st ->
                var n = p.change(st).log(LogKind.RULES_LOOSENED, nowMs(), p.reasons.joinToString("; "))
                if (st.settings.protectionOn && !n.settings.protectionOn) n = n.log(LogKind.PROTECTION_OFF, nowMs())
                n
            }
            pending = null
        }
    }
    newCode?.let { code ->
        NewCodeDialog(code, onClose = { newCode = null }) {
            val salt = randomCode(Random.Default, 16)
            Store.update { it.copy(settings = it.settings.copy(codeHash = hashEmergencyCode(code, salt), codeSalt = salt)) }
            newCode = null
        }
    }
    if (addingApp) {
        AppPickerDialog("Block an app", exclude = state.rules.map { it.pkg }.toSet(), onDismiss = { addingApp = false }) { pkg ->
            addingApp = false
            editingRule = AppRule(pkg, dailyLimitMin = 30) to true
        }
    }
    editingRule?.let { (rule, isNew) ->
        RuleEditor(
            rule, isNew, hasNotifAccess(ctx),
            onDismiss = { editingRule = null },
            onSave = { r ->
                editingRule = null
                change { st -> st.copy(rules = st.rules.filterNot { it.pkg == r.pkg } + r) }
            },
            onDelete = {
                editingRule = null
                change { st -> st.copy(rules = st.rules.filterNot { it.pkg == rule.pkg }) }
            },
        )
    }
    editingGroup?.let { (group, isNew) ->
        GroupEditor(
            group, isNew,
            onDismiss = { editingGroup = null },
            onSave = { g ->
                editingGroup = null
                change { st -> st.copy(groups = st.groups.filterNot { it.id == g.id } + g) }
            },
            onDelete = {
                editingGroup = null
                change { st -> st.copy(groups = st.groups.filterNot { it.id == group.id }) }
            },
        )
    }
    if (addingTag) AddTagDialog(nfc, onClose = { addingTag = false }) { tag ->
        addingTag = false
        change { st -> if (st.tags.any { it.kind == tag.kind && it.value == tag.value }) st else st.copy(tags = st.tags + tag) }
    }
}

private fun ruleSummary(r: AppRule): String = listOfNotNull(
    "Always blocked".takeIf { r.always },
    r.dailyLimitMin?.let { "${fmtDuration(it * MINUTE_MS)} a day" },
    r.schedules.takeIf { it.isNotEmpty() }?.joinToString { windowSummary(it) },
    "hides notifications".takeIf { r.hideNotifs },
).joinToString(" · ").ifEmpty { "Nothing set" }

@Composable
private fun UsageBar(name: String, usedMs: Long, limitMin: Int) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(name, Modifier.weight(1f))
            Text("${fmtDuration(usedMs)} of ${fmtDuration(limitMin * MINUTE_MS)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(progress = { (usedMs.toFloat() / (limitMin * MINUTE_MS)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun <T> Choice(title: String, options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    Text(title, Modifier.padding(top = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) -> FilterChip(selected = value == selected, onClick = { onPick(value) }, label = { Text(label) }) }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
private fun Check(title: String, ok: Boolean, detail: String, fix: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (ok) Icons.Default.CheckCircle else Icons.Default.Warning, null,
                tint = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title)
                Hint(detail)
            }
            if (!ok) TextButton(onClick = fix) { Text("Fix") }
        }
    }
}

@Composable
private fun NewCodeDialog(code: String, onClose: () -> Unit, onSaved: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Your emergency code") },
        text = {
            Column {
                Text(code, fontFamily = FontFamily.Monospace, fontSize = 28.sp)
                Spacer(Modifier.height(12.dp))
                Text("Write it down or print it, and keep it somewhere out of the way. It's only shown now. " +
                    "It unlocks a blocked app right away and approves rule changes.")
            }
        },
        confirmButton = { TextButton(onClick = onSaved) { Text("I've written it down") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

/** Register a new NFC card (by tapping it) or barcode (by scanning it). */
@Composable
private fun AddTagDialog(nfc: SharedFlow<String>, onClose: () -> Unit, onAdd: (Tag) -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var waitingForCard by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun add(k: TagKind, value: String) = onAdd(Tag(newId(), name.trim().ifBlank { if (k == TagKind.NFC) "Card" else "Barcode" }, k, value))

    if (waitingForCard) {
        LaunchedEffect(Unit) {
            nfc.collect { uid ->
                if (isRandomUid(uid)) error = "This card gives a different ID every tap, so it can't be used. Try another." else add(TagKind.NFC, uid)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Add tag") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. Garage)") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                if (waitingForCard) Text("Hold the card to the back of your phone…") else {
                    if (hasNfc(ctx)) OutlinedButton(onClick = { waitingForCard = true; error = null }, Modifier.fillMaxWidth()) { Text("Tap an NFC card") }
                    OutlinedButton(onClick = {
                        error = null
                        GmsBarcodeScanning.getClient(ctx).startScan()
                            .addOnSuccessListener { b -> b.rawValue?.let { add(TagKind.BARCODE, it) } ?: run { error = "Couldn't read that code" } }
                            .addOnFailureListener { error = "Scanner error: ${it.message}" }
                    }, Modifier.fillMaxWidth()) { Text("Scan a barcode or QR code") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

private val logTime = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a")

private fun LogEvent.text(): String = when (kind) {
    LogKind.UNLOCK_QUICK -> "Quick unlock: $detail"
    LogKind.UNLOCK_TAG -> "Unlocked with a tag: $detail"
    LogKind.UNLOCK_CODE -> "Unlocked with a typed code: $detail"
    LogKind.UNLOCK_EMERGENCY -> "Emergency code used: $detail"
    LogKind.BLOCKED_SCREEN -> "Blocked a settings screen ($detail)"
    LogKind.WRONG_CODE -> "Wrong emergency code"
    LogKind.RULES_LOOSENED -> "Rules loosened: $detail"
    LogKind.PROTECTION_OFF -> "Protection turned off"
    LogKind.ADMIN_OFF_ATTEMPT -> "Tried to turn off the uninstall lock"
    LogKind.ADMIN_OFF -> "Uninstall lock turned off"
    LogKind.GUARD_OFF -> "App blocker switched off"
    LogKind.FORCE_STOPPED -> "Flip was force-stopped"
}

@Composable
private fun LogSection(state: AppState) {
    var all by remember { mutableStateOf(false) }
    val events = state.log.asReversed()
    if (events.isEmpty()) {
        Hint("Nothing yet. Unlocks, blocked settings screens and wrong codes show up here.")
        return
    }
    (if (all) events else events.take(20)).forEach { e ->
        Column(Modifier.padding(vertical = 4.dp)) {
            Text(e.text(), color = if (e.kind.isAttempt) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Hint(Instant.ofEpochMilli(e.at).atZone(com.blakeriding.flip.zone()).format(logTime))
        }
    }
    if (events.size > 20) TextButton(onClick = { all = !all }) { Text(if (all) "Show less" else "Show all ${events.size}") }
}
