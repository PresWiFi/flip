package com.blakeriding.flip

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blakeriding.flip.ui.AppIcon
import com.blakeriding.flip.ui.EmergencyEntry
import com.blakeriding.flip.ui.FlipTheme
import com.blakeriding.flip.ui.TypeCode
import com.blakeriding.flip.ui.describe
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Covers a blocked app. Unlock with a quick unlock, a tag, the typed code or the emergency code. */
class BlockActivity : ComponentActivity() {
    private val nfc = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private val pkg = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        enableEdgeToEdge()
        pkg.value = intent.getStringExtra(EXTRA_PKG).orEmpty()
        if (pkg.value.isEmpty()) {
            finish()
            return
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        setContent {
            FlipTheme {
                val p by pkg
                BlockScreen(p, nfc, onOpen = ::openUnlocked, onHome = ::goHome)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pkg.value = intent.getStringExtra(EXTRA_PKG).orEmpty()
    }

    override fun onResume() {
        super.onResume()
        startCardReader { nfc.tryEmit(it) }
    }

    override fun onPause() {
        stopCardReader()
        super.onPause()
    }

    private fun openUnlocked() {
        Apps.launchIntent(this, pkg.value)?.let { startActivity(it) }
        finish()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        private const val EXTRA_PKG = "pkg"

        fun start(context: Context, pkg: String) {
            context.startActivity(
                Intent(context, BlockActivity::class.java)
                    .putExtra(EXTRA_PKG, pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }
    }
}

private enum class Mode { CHOOSE, TAG, TYPE, EMERGENCY }

@Composable
internal fun BlockScreen(pkg: String, nfc: SharedFlow<String>, onOpen: () -> Unit, onHome: () -> Unit) {
    val ctx = LocalContext.current
    val state by Store.state.collectAsStateWithLifecycle()
    var mode by remember(pkg) { mutableStateOf(Mode.CHOOSE) }
    // Re-check every few seconds: a schedule can end or a limit reset while this is up.
    val first = remember(pkg) { verdictNow(ctx, pkg) }
    val verdict by produceState(first, pkg, state) {
        while (true) {
            value = verdictNow(ctx, pkg)
            delay(5_000)
        }
    }
    val label = remember(pkg) { Apps.label(ctx, pkg) }
    val s = state.settings

    fun unlock(method: UnlockMethod) {
        val now = nowZ()
        var ok = false
        Store.update { st -> st.unlock(pkg, method, now, label)?.also { ok = true } ?: st }
        if (ok) onOpen() else ctx.toast("No quick unlocks left today")
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            AppIcon(pkg, 72.dp)
            Text(label, style = MaterialTheme.typography.headlineMedium)
            Text(
                if (verdict.blocked) describe(verdict) else "Not blocked right now",
                color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))

            if (!verdict.blocked) {
                Button(onClick = onOpen, Modifier.fillMaxWidth().height(56.dp)) { Text("Open $label") }
                TextButton(onClick = onHome) { Text("Go home") }
                return@Column
            }

            when (mode) {
                Mode.CHOOSE -> {
                    val quick = state.quickLeft(nowZ())
                    Button(onClick = { unlock(UnlockMethod.QUICK) }, enabled = quick > 0, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(if (quick > 0) "Quick unlock: ${s.quickMin} min ($quick left today)" else "No quick unlocks left today")
                    }
                    if (state.tags.isNotEmpty()) {
                        OutlinedButton(onClick = { mode = Mode.TAG }, Modifier.fillMaxWidth()) { Text("Scan a tag: ${s.unlockMin} min") }
                    }
                    OutlinedButton(onClick = { mode = Mode.TYPE }, Modifier.fillMaxWidth()) { Text("Type a code: ${s.unlockMin} min") }
                    if (s.codeHash != null) {
                        TextButton(onClick = { mode = Mode.EMERGENCY }) { Text("Emergency code") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onHome, Modifier.fillMaxWidth()) { Text("Go home") }
                }
                Mode.TAG -> {
                    TagUnlock(state.tags, nfc) { unlock(UnlockMethod.TAG) }
                    TextButton(onClick = { mode = Mode.CHOOSE }) { Text("Back") }
                }
                Mode.TYPE -> {
                    Text("Type this code to unlock $label for ${s.unlockMin} minutes.", textAlign = TextAlign.Center)
                    TypeCode { unlock(UnlockMethod.CODE) }
                    TextButton(onClick = { mode = Mode.CHOOSE }) { Text("Back") }
                }
                Mode.EMERGENCY -> {
                    Text("The emergency code unlocks right away. It's logged.", textAlign = TextAlign.Center)
                    EmergencyEntry(s, "Unlock") { unlock(UnlockMethod.EMERGENCY) }
                    TextButton(onClick = { mode = Mode.CHOOSE }) { Text("Back") }
                }
            }
        }
    }
}

/** Unlocks when any saved tag is tapped (NFC) or scanned (barcode). */
@Composable
private fun TagUnlock(tags: List<Tag>, nfc: SharedFlow<String>, onMatch: () -> Unit) {
    val ctx = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    val nfcTags = tags.filter { it.kind == TagKind.NFC }
    val codes = tags.filter { it.kind == TagKind.BARCODE }
    if (nfcTags.isNotEmpty()) {
        Text("Hold your phone to one of your cards: ${nfcTags.joinToString { it.name }}", textAlign = TextAlign.Center)
        LaunchedEffect(nfcTags) {
            nfc.collect { uid -> if (nfcTags.any { it.value == uid }) onMatch() else error = "That's not one of your cards." }
        }
    }
    if (codes.isNotEmpty()) {
        Button(onClick = {
            GmsBarcodeScanning.getClient(ctx).startScan()
                .addOnSuccessListener { b -> if (codes.any { it.value == b.rawValue }) onMatch() else error = "That's not one of your codes." }
                .addOnFailureListener { error = "Scanner error: ${it.message}" }
        }, Modifier.fillMaxWidth().height(56.dp)) { Text("Scan a code: ${codes.joinToString { it.name }}") }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
