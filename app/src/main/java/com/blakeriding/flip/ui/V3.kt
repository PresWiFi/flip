package com.blakeriding.flip.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blakeriding.flip.Look
import com.blakeriding.flip.quickLeft

/** What the launcher can do, for the looks to call. */
class HomeActions(
    val open: (String) -> Unit,
    val dial: (String) -> Unit,
    val settings: () -> Unit,
)

private val MetalTop = Color(0xFF4A505A)
private val MetalBottom = Color(0xFF23272E)
private val Groove = Color(0xFF0E1013)
private val Glow = TextStyle(color = Backlight, shadow = Shadow(Backlight, Offset.Zero, 14f), fontWeight = FontWeight.Medium)

/** The open V3: screen on top, hinge, etched metal keypad below. With [outer], it starts closed. */
@Composable
fun V3Phone(ui: HomeUi, nav: HomeNav, actions: HomeActions, outer: Boolean) {
    AnimatedContent(
        targetState = outer && !nav.open,
        transitionSpec = { (fadeIn() + slideInVertically { it / 6 }) togetherWith fadeOut() },
        label = "flip",
    ) { closed ->
        if (closed) OuterScreen(ui) { nav.open = true } else OpenPhone(ui, nav, actions)
    }
}

@Composable
private fun OpenPhone(ui: HomeUi, nav: HomeNav, actions: HomeActions) {
    val digitsOn = ui.keypadOn(nav.screen)
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF2B3038), Color(0xFF15181D))))
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        // Top half: the screen in its bezel.
        Box(Modifier.fillMaxWidth().weight(if (digitsOn) 0.5f else 0.8f).padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 10.dp)) {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Color.Black).padding(7.dp)
                    .clip(RoundedCornerShape(6.dp)),
            ) { Lcd(ui, nav, actions) }
        }
        Hinge()
        // Bottom half: keypad plate.
        Column(
            Modifier.fillMaxWidth().weight(if (digitsOn) 0.5f else 0.2f).padding(horizontal = 14.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(MetalTop, MetalBottom)))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp))
                .padding(10.dp),
        ) {
            NavKeys(ui, nav, actions, Modifier.weight(if (digitsOn) 2f else 1f))
            if (digitsOn) DigitKeys(Modifier.weight(4f)) { c ->
                if (nav.screen == Screen.MENU && c in '1'..'9') {
                    ui.settings.favorites.getOrNull(c - '1')?.let(actions.open)
                } else {
                    if (nav.screen == Screen.MENU) nav.screen = Screen.IDLE
                    nav.type(c)
                }
            }
        }
    }
}

@Composable
private fun Hinge() {
    Box(
        Modifier.fillMaxWidth().height(16.dp).padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF111316), Color(0xFF5A606A), Color(0xFF16181C)))),
    )
}

// ---------- Screen ----------

@Composable
private fun Lcd(ui: HomeUi, nav: HomeNav, actions: HomeActions) {
    Box(Modifier.fillMaxSize()) {
        V3Wallpaper(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            StatusBar(ui)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    nav.screen == Screen.MENU -> MenuGrid(ui, nav, actions)
                    nav.digits.isNotEmpty() && ui.searching(nav.screen) -> Results(ui, nav, actions)
                    nav.digits.isNotEmpty() -> BigNumber(nav.digits)
                    nav.screen == Screen.SEARCH -> SearchPrompt()
                    else -> IdleClock(ui)
                }
            }
            SoftBar(
                left = if (nav.screen == Screen.IDLE && nav.digits.isEmpty()) "Search" else "Back",
                right = "Flip",
                onLeft = { if (nav.screen == Screen.IDLE && nav.digits.isEmpty()) nav.screen = Screen.SEARCH else nav.back() },
                onMenu = { nav.screen = if (nav.screen == Screen.MENU) Screen.IDLE else Screen.MENU },
                onRight = actions.settings,
            )
        }
    }
}

@Composable
private fun StatusBar(ui: HomeUi) {
    Row(
        Modifier.fillMaxWidth().background(Color(0x55000000)).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SignalBars(ui.phone.signal, 1.5.dp)
        Spacer(Modifier.weight(1f))
        PixelText(ui.timeText() + ui.amPm().lowercase().take(1), px = 1.5.dp)
        Spacer(Modifier.weight(1f))
        BatteryIcon(ui.phone.battery, ui.phone.charging, 1.5.dp)
    }
}

@Composable
private fun IdleClock(ui: HomeUi) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.Bottom) {
            PixelText(ui.timeText(), px = 6.dp)
            if (ui.amPm().isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                PixelText(ui.amPm(), px = 2.5.dp)
            }
        }
        Spacer(Modifier.height(10.dp))
        PixelText(ui.dateText(), px = 2.dp)
        if (ui.state.rules.isNotEmpty() || ui.state.groups.isNotEmpty()) {
            val n = ui.state.quickLeft(ui.now)
            val left = "$n quick unlock${if (n == 1) "" else "s"} left"
            Spacer(Modifier.height(14.dp))
            PixelText(left, px = 1.25.dp, color = Color(0xCCFFFFFF))
        }
    }
}

@Composable
private fun SearchPrompt() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        PixelText("Search", px = 3.dp)
        Spacer(Modifier.height(8.dp))
        PixelText("Spell with the keypad", px = 1.5.dp)
        PixelText("4-6-7 = Instagram", px = 1.5.dp, color = Color(0xCCFFFFFF))
    }
}

@Composable
private fun BigNumber(digits: String) {
    Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.BottomEnd) {
        Column(horizontalAlignment = Alignment.End) {
            digits.chunked(11).forEach { PixelText(it, px = 3.5.dp) }
        }
    }
}

@Composable
private fun Results(ui: HomeUi, nav: HomeNav, actions: HomeActions) {
    val results = t9Results(nav.digits, ui.apps, ui.contacts)
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(results) { i, m ->
                val v = m.pkg?.let { ui.verdict(it) }
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (i == 0) Color(0x55FFFFFF) else Color.Transparent)
                        .clickable { if (m.pkg != null) actions.open(m.pkg) else m.contact?.let { actions.dial(it.number) } }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (m.pkg != null) AppIcon(m.pkg, 20.dp, pixel = ui.settings.pixelIcons) else PixelText("@", px = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    PixelText(m.label, px = 1.75.dp, maxChars = 16, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.weight(1f))
                    if (v?.blocked == true) PixelLock(1.5.dp) else v?.let { shortStatus(it) }?.takeIf { it.isNotEmpty() }?.let {
                        PixelText(it, px = 1.25.dp, color = Color(0xFFFFE08A))
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Color(0x66000000)).padding(6.dp),
            horizontalArrangement = Arrangement.End,
        ) { PixelText(nav.digits.takeLast(14), px = 2.5.dp) }
    }
}

@Composable
private fun MenuGrid(ui: HomeUi, nav: HomeNav, actions: HomeActions) {
    Column(Modifier.fillMaxSize().background(Color(0x33001133))) {
        Box(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF3A8DFF), Color(0xFF0A3E9A)))).padding(5.dp),
            contentAlignment = Alignment.Center,
        ) { PixelText("Main Menu", px = 1.75.dp) }
        for (row in 0 until 3) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (col in 0 until 3) {
                    val slot = row * 3 + col
                    MenuCell(ui, ui.settings.favorites.getOrNull(slot), slot, Modifier.weight(1f).fillMaxHeight(), onOpen = actions.open) {
                        nav.picking = slot
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuCell(ui: HomeUi, pkg: String?, slot: Int, modifier: Modifier, onOpen: (String) -> Unit, onPick: () -> Unit) {
    val src = androidx.compose.runtime.remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Column(
        modifier
            .padding(3.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (pressed) Color(0x66FFC94A) else Color.Transparent)
            .combinedClickable(interactionSource = src, indication = null, onLongClick = onPick) { if (pkg != null) onOpen(pkg) else onPick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (pkg == null) {
            PixelText("+", px = 4.dp, color = Color(0x88FFFFFF))
            Spacer(Modifier.height(4.dp))
            PixelText("${slot + 1} empty", px = 1.25.dp, color = Color(0x88FFFFFF))
            return@Column
        }
        val v = ui.verdict(pkg)
        Box {
            AppIcon(pkg, 40.dp, pixel = ui.settings.pixelIcons)
            if (v.blocked) Box(Modifier.align(Alignment.BottomEnd)) { PixelLock(1.75.dp) }
        }
        Spacer(Modifier.height(4.dp))
        val label = ui.apps.find { it.pkg == pkg }?.label ?: pkg.substringAfterLast('.')
        PixelText(label, px = 1.25.dp, maxChars = 11)
        val status = shortStatus(v)
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            PixelText(status, px = 1.dp, color = Color(0xFFFFE08A))
        }
    }
}

@Composable
private fun SoftBar(left: String, right: String, onLeft: () -> Unit, onMenu: () -> Unit, onRight: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xCC0A2A66), Color(0xEE021233)))).padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).clickable(onClick = onLeft).padding(start = 8.dp)) { PixelText(left, px = 1.75.dp) }
        Box(Modifier.clickable(onClick = onMenu).padding(horizontal = 10.dp)) { MenuGlyph(Color.White, 2.dp) }
        Box(Modifier.weight(1f).clickable(onClick = onRight).padding(end = 8.dp), contentAlignment = Alignment.CenterEnd) {
            PixelText(right, px = 1.75.dp)
        }
    }
}

/** The menu key symbol: a small 3x3 grid. */
@Composable
private fun MenuGlyph(color: Color, px: Dp) {
    Canvas(Modifier.size(px * 7, px * 7)) {
        val p = px.toPx()
        for (y in 0 until 3) for (x in 0 until 3) drawRect(color, Offset(x * 3 * p, y * 3 * p), Size(p * 1.6f, p * 1.6f))
    }
}

// ---------- Keypad ----------

@Composable
private fun NavKeys(ui: HomeUi, nav: HomeNav, actions: HomeActions, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Key(Modifier.weight(1f), onClick = { if (nav.screen == Screen.IDLE && nav.digits.isEmpty()) nav.screen = Screen.SEARCH else nav.back() }) { SoftDash() }
            Key(Modifier.weight(1f), onClick = { nav.screen = if (nav.screen == Screen.MENU) Screen.IDLE else Screen.MENU }) { MenuGlyph(Backlight, 2.5.dp) }
            Key(Modifier.weight(1f), onClick = actions.settings) { SoftDash() }
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Key(Modifier.weight(1f), onClick = { actions.dial(nav.digits) }) {
                Icon(Icons.Default.Call, "Call", tint = Color(0xFF5CFF8A), modifier = Modifier.size(22.dp))
            }
            Key(Modifier.weight(1f), onClick = { nav.erase() }, onLong = { nav.digits = "" }) { Text("C", style = Glow, fontSize = 20.sp) }
            Key(Modifier.weight(1f), onClick = {
                nav.home()
                if (ui.settings.look == Look.V3_OUTER) nav.open = false
            }) {
                Icon(Icons.Default.Call, "End", tint = Color(0xFFFF5C5C), modifier = Modifier.size(22.dp).rotate(135f))
            }
        }
    }
}

private val DIGITS = listOf("1" to "", "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "*" to "", "0" to "+", "#" to "")

@Composable
private fun DigitKeys(modifier: Modifier, onDigit: (Char) -> Unit) {
    Column(modifier.fillMaxWidth()) {
        for (row in DIGITS.chunked(3)) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for ((d, letters) in row) {
                    Key(Modifier.weight(1f), onClick = { onDigit(d[0]) }, onLong = if (d == "0") ({ onDigit('+') }) else null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(d, style = Glow, fontSize = 24.sp)
                            if (letters.isNotEmpty()) {
                                Spacer(Modifier.width(5.dp))
                                Text(letters, style = Glow, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One etched key: a groove around it and a blue backlit label that brightens when pressed. */
@Composable
private fun RowScope.Key(modifier: Modifier, onClick: () -> Unit, onLong: (() -> Unit)? = null, label: @Composable () -> Unit) {
    val view = LocalView.current
    val src = androidx.compose.runtime.remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Box(
        modifier.fillMaxHeight().padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (pressed) Brush.verticalGradient(listOf(Color(0xFF2E4A66), Color(0xFF1E2C3C)))
                else Brush.verticalGradient(listOf(Color(0xFF444A54), Color(0xFF2C3139))),
            )
            .border(1.dp, Groove, RoundedCornerShape(6.dp))
            .combinedClickable(
                interactionSource = src, indication = null,
                onLongClick = onLong?.let { f -> { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); f() } },
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) { label() }
}

@Composable
private fun SoftDash() {
    Box(Modifier.width(28.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Backlight))
}

// ---------- Closed (outer screen) ----------

@Composable
private fun OuterScreen(ui: HomeUi, onOpen: () -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF3A3F47), Color(0xFF1A1D22), Color(0xFF2A2E35))))
            .clickable(onClick = onOpen)
            .pointerInput(Unit) { detectVerticalDragGestures { _, drag -> if (drag < -12) onOpen() } }
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(0.35f))
            // The small outside display.
            Box(
                Modifier.width(190.dp).height(120.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color.Black).border(2.dp, Color(0xFF55595F), RoundedCornerShape(12.dp)).padding(10.dp),
            ) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SignalBars(ui.phone.signal, 1.25.dp)
                        Spacer(Modifier.weight(1f))
                        BatteryIcon(ui.phone.battery, ui.phone.charging, 1.25.dp)
                    }
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.Bottom) {
                        PixelText(ui.timeText(), px = 4.dp, color = Color(0xFFBFE3FF), shadow = null)
                        if (ui.amPm().isNotEmpty()) {
                            Spacer(Modifier.width(4.dp))
                            PixelText(ui.amPm(), px = 1.75.dp, color = Color(0xFFBFE3FF), shadow = null)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    PixelText(ui.dateText(), px = 1.5.dp, color = Color(0xFF8FB8D8), shadow = null)
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(28.dp))
            // Camera lens.
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF3A4A66), Color(0xFF0A0D12))))
                    .border(3.dp, Color(0xFF5A6068), CircleShape),
            )
            Spacer(Modifier.weight(0.65f))
            PixelText("Tap to open", px = 1.5.dp, color = Color(0x88FFFFFF), shadow = null)
            Spacer(Modifier.height(24.dp))
        }
    }
}
