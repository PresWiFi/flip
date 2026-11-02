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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
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

// ---------- Screen ----------

@Composable
internal fun Lcd(ui: HomeUi, nav: HomeNav, actions: HomeActions) {
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
                onMenu = { nav.menu() },
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
    val list = rememberLazyListState()
    LaunchedEffect(nav.sel) {
        val visible = list.layoutInfo.visibleItemsInfo
        if (visible.isNotEmpty() && (nav.sel < visible.first().index || nav.sel > visible.last().index)) list.animateScrollToItem(nav.sel)
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list) {
            itemsIndexed(results) { i, m ->
                val v = m.pkg?.let { ui.verdict(it) }
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (i == nav.sel) Color(0x55FFFFFF) else Color.Transparent)
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
        ) {
            val pkg = ui.settings.favorites.getOrNull(nav.sel)
            PixelText(pkg?.let { p -> ui.apps.find { it.pkg == p }?.label } ?: "Main Menu", px = 1.75.dp, maxChars = 20)
        }
        for (row in 0 until 3) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (col in 0 until 3) {
                    val slot = row * 3 + col
                    MenuCell(ui, ui.settings.favorites.getOrNull(slot), slot, slot == nav.sel, Modifier.weight(1f).fillMaxHeight(), onOpen = actions.open) {
                        nav.picking = slot
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuCell(ui: HomeUi, pkg: String?, slot: Int, selected: Boolean, modifier: Modifier, onOpen: (String) -> Unit, onPick: () -> Unit) {
    val src = androidx.compose.runtime.remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Column(
        modifier
            .padding(3.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (pressed || selected) Color(0x40FFFFFF) else Color.Transparent)
            .border(1.5.dp, if (selected) Color.White else Color.Transparent, RoundedCornerShape(6.dp))
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

// ---------- Closed (outer screen) ----------

@Composable
private fun OuterScreen(ui: HomeUi, onOpen: () -> Unit) {
    val f = finish(ui.settings.v3Color)
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .statusBarsPadding().navigationBarsPadding().padding(6.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(Brush.verticalGradient(f.frame))
            .clickable(onClick = onOpen)
            .pointerInput(Unit) { detectVerticalDragGestures { _, drag -> if (drag < -12) onOpen() } },
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(0.22f))
            // Camera lens.
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF3A4A66), Color(0xFF0A0D12))))
                    .border(3.dp, f.frame[2], CircleShape),
            )
            Spacer(Modifier.height(22.dp))
            // The small outside display.
            Box(
                Modifier.width(190.dp).height(120.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color.Black).border(2.dp, f.groove, RoundedCornerShape(12.dp)).padding(10.dp),
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
            Spacer(Modifier.weight(0.65f))
            PixelText("Tap to open", px = 1.5.dp, color = f.groove, shadow = null)
            Spacer(Modifier.height(24.dp))
        }
    }
}
