package com.blakeriding.flip.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blakeriding.flip.Look
import com.blakeriding.flip.V3Color
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

// The open V3: aluminium body, screen with earpiece above, barrel hinge, and a flat etched keypad sheet
// with blue backlit symbols and a round 5-way navigation key.

/** Colours for one V3 finish. */
class Finish(
    /** Body, top to bottom. */
    val frame: List<Color>,
    /** The keypad sheet. */
    val plate: List<Color>,
    /** Etched line, and the lit edge beside it. */
    val groove: Color,
    val grooveLight: Color,
    /** Backlit symbols and their glow. */
    val label: Color,
    val glow: Color,
)

fun finish(c: V3Color): Finish = when (c) {
    V3Color.SILVER -> Finish(
        frame = listOf(Color(0xFFDDE1E6), Color(0xFFB4BAC2), Color(0xFF8F96A0)),
        plate = listOf(Color(0xFFCBD0D7), Color(0xFFAEB4BD)),
        groove = Color(0xFF6A717C), grooveLight = Color(0xFFEFF1F4),
        label = Color(0xFF1477F0), glow = Color(0xFF5DB4FF),
    )
    V3Color.BLACK -> Finish(
        frame = listOf(Color(0xFF45484E), Color(0xFF26282C), Color(0xFF17181B)),
        plate = listOf(Color(0xFF2E3136), Color(0xFF1C1E22)),
        groove = Color(0xFF050506), grooveLight = Color(0xFF4A4E55),
        label = Color(0xFF6EC6FF), glow = Color(0xFF2E9BFF),
    )
    V3Color.PINK -> Finish(
        frame = listOf(Color(0xFFF6C3D8), Color(0xFFE08DB2), Color(0xFFC46C94)),
        plate = listOf(Color(0xFFEDB2CB), Color(0xFFD48DAD)),
        groove = Color(0xFF9A4F71), grooveLight = Color(0xFFFBDDEA),
        label = Color(0xFF1477F0), glow = Color(0xFF5DB4FF),
    )
}

private fun Finish.text(size: Int, lit: Boolean) =
    TextStyle(color = label, fontSize = size.sp, fontWeight = FontWeight.Normal, shadow = Shadow(glow, Offset.Zero, if (lit) 22f else 9f))

private val SendGreen = Color(0xFF22B24C)
private val EndRed = Color(0xFFE0443A)

@Composable
internal fun OpenPhone(ui: HomeUi, nav: HomeNav, actions: HomeActions) {
    val f = finish(ui.settings.v3Color)
    val digitsOn = ui.keypadOn(nav.screen)
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().padding(horizontal = 6.dp)) {
        // Top half: earpiece slot, screen.
        Column(
            Modifier.fillMaxWidth().weight(if (digitsOn) 0.47f else 0.76f)
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                .background(Brush.verticalGradient(f.frame))
                .brushed()
                .padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(70.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF151619)))
            Spacer(Modifier.height(9.dp))
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp)).background(Color.Black)
                    .border(1.5.dp, f.groove, RoundedCornerShape(10.dp))
                    .padding(9.dp).clip(RoundedCornerShape(3.dp)),
            ) { Lcd(ui, nav, actions) }
            Spacer(Modifier.height(12.dp))
        }
        Hinge(f)
        // Bottom half: the keypad sheet set into the body, with a curved chin and the mic hole.
        Column(
            Modifier.fillMaxWidth().weight(if (digitsOn) 0.53f else 0.24f)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 40.dp, bottomEnd = 40.dp))
                .background(Brush.verticalGradient(f.frame))
                .padding(start = 9.dp, end = 9.dp, top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val sheet = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 34.dp, bottomEnd = 34.dp)
            Column(
                Modifier.weight(1f).fillMaxWidth().clip(sheet)
                    .background(Brush.verticalGradient(f.plate)).brushed()
                    .border(1.dp, f.groove, sheet),
            ) {
                NavKeys(f, ui, nav, actions, Modifier.weight(if (digitsOn) 2.15f else 1f))
                if (digitsOn) {
                    Etch(f)
                    DigitKeys(f, Modifier.weight(4f)) { c ->
                        if (nav.screen == Screen.MENU && c in '1'..'9') {
                            ui.settings.favorites.getOrNull(c - '1')?.let(actions.open)
                        } else {
                            if (nav.screen == Screen.MENU) nav.screen = Screen.IDLE
                            nav.type(c)
                        }
                    }
                }
            }
            Box(Modifier.padding(vertical = 7.dp).size(5.dp).clip(CircleShape).background(Color(0xFF202125)))
        }
    }
}

/** Faint horizontal brushing, like the V3's aluminium. */
private fun Modifier.brushed(): Modifier = drawBehind {
    val step = 3.dp.toPx()
    var y = 0f
    var i = 0
    while (y < size.height) {
        val a = if (i % 3 == 0) 0.05f else 0.025f
        drawLine(Color.White.copy(alpha = a), Offset(0f, y), Offset(size.width, y), 1f)
        y += step
        i++
    }
}

/** The barrel hinge across the middle. */
@Composable
private fun Hinge(f: Finish) {
    val dark = Color(0xFF0E0F11)
    Box(
        Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Brush.verticalGradient(listOf(dark, f.frame[2], f.frame[0], f.frame[1], f.frame[2], dark)))
            .drawBehind {
                // The barrel is in three parts; the outer two belong to the top half.
                for (x in listOf(size.width * 0.2f, size.width * 0.8f)) {
                    drawLine(dark, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                }
            },
    )
}

/** An etched line across the sheet. */
@Composable
private fun Etch(f: Finish) {
    Box(Modifier.fillMaxWidth().height(2.dp).drawBehind { etchH(f, 0f, 0f, size.width) })
}

private fun DrawScope.etchH(f: Finish, y: Float, x0: Float, x1: Float) {
    drawLine(f.groove, Offset(x0, y), Offset(x1, y), 1.2.dp.toPx())
    drawLine(f.grooveLight, Offset(x0, y + 1.2.dp.toPx()), Offset(x1, y + 1.2.dp.toPx()), 0.8.dp.toPx())
}

private fun DrawScope.etchV(f: Finish, x: Float, y0: Float, y1: Float) {
    drawLine(f.groove, Offset(x, y0), Offset(x, y1), 1.2.dp.toPx())
    drawLine(f.grooveLight, Offset(x + 1.2.dp.toPx(), y0), Offset(x + 1.2.dp.toPx(), y1), 0.8.dp.toPx())
}

/** One key: just an area of the sheet, marked by etched lines on its right and/or bottom edge. */
@Composable
private fun FlatKey(
    f: Finish,
    modifier: Modifier,
    right: Boolean = false,
    bottom: Boolean = false,
    onLong: (() -> Unit)? = null,
    onClick: () -> Unit,
    label: @Composable (lit: Boolean) -> Unit,
) {
    val view = LocalView.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Box(
        modifier
            .drawBehind {
                if (pressed) drawRect(Color.Black.copy(alpha = 0.10f))
                if (right) etchV(f, size.width - 1.dp.toPx(), size.height * 0.12f, size.height * 0.88f)
                if (bottom) etchH(f, size.height - 1.dp.toPx(), size.width * 0.06f, size.width * 0.94f)
            }
            .combinedClickable(
                interactionSource = src, indication = null,
                onLongClick = onLong?.let { l -> { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); l() } },
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) { label(pressed) }
}

// ---------- Top section: soft keys, menu, search/clear, send/end, 5-way ring ----------

enum class Dir { UP, DOWN, LEFT, RIGHT, CENTER }

@Composable
private fun NavKeys(f: Finish, ui: HomeUi, nav: HomeNav, actions: HomeActions, modifier: Modifier) {
    val idle = nav.screen == Screen.IDLE && nav.digits.isEmpty()
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            FlatKey(f, Modifier.weight(1f).fillMaxWidth(), bottom = true, onClick = { if (idle) nav.screen = Screen.SEARCH else nav.back() }) { SoftDash(f, it) }
            FlatKey(f, Modifier.weight(1f).fillMaxWidth(), bottom = true, onClick = { nav.screen = Screen.SEARCH; nav.sel = 0 }) {
                Icon(Icons.Default.Search, "Search", tint = f.label, modifier = Modifier.size(if (it) 24.dp else 22.dp))
            }
            FlatKey(f, Modifier.weight(1f).fillMaxWidth(), onClick = { actions.dial(nav.digits) }) {
                Icon(Icons.Default.Call, "Call", tint = SendGreen, modifier = Modifier.size(24.dp))
            }
        }
        Column(Modifier.weight(1.35f).fillMaxHeight()) {
            FlatKey(f, Modifier.weight(0.75f).fillMaxWidth(), onClick = { nav.menu() }) { MenuGlyph(f.label, if (it) 3.dp else 2.6.dp) }
            NavRing(f, Modifier.weight(2.25f).fillMaxWidth()) { ring(it, ui, nav, actions) }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            FlatKey(f, Modifier.weight(1f).fillMaxWidth(), bottom = true, onClick = actions.settings) { SoftDash(f, it) }
            FlatKey(f, Modifier.weight(1f).fillMaxWidth(), bottom = true, onLong = { nav.home() }, onClick = {
                if (nav.digits.isNotEmpty()) nav.erase() else nav.back()
            }) { Text("C", style = f.text(22, it)) }
            FlatKey(f, Modifier.weight(1f).fillMaxWidth(), onClick = {
                nav.home()
                if (ui.settings.look == Look.V3_OUTER) nav.open = false
            }) {
                Icon(Icons.Default.Call, "End", tint = EndRed, modifier = Modifier.size(24.dp).rotate(135f))
            }
        }
    }
}

/** What the 5-way key does: move the highlight in the menu or search results, and open with the center. */
private fun ring(d: Dir, ui: HomeUi, nav: HomeNav, actions: HomeActions) {
    when {
        nav.screen == Screen.MENU -> {
            val s = nav.sel
            when (d) {
                Dir.UP -> nav.sel = (s + 6) % 9
                Dir.DOWN -> nav.sel = (s + 3) % 9
                Dir.LEFT -> nav.sel = s / 3 * 3 + (s % 3 + 2) % 3
                Dir.RIGHT -> nav.sel = s / 3 * 3 + (s % 3 + 1) % 3
                Dir.CENTER -> ui.settings.favorites.getOrNull(s)?.let(actions.open) ?: run { nav.picking = s }
            }
        }
        nav.digits.isNotEmpty() && ui.searching(nav.screen) -> {
            val results = t9Results(nav.digits, ui.apps, ui.contacts)
            if (results.isEmpty()) return
            when (d) {
                Dir.UP -> nav.sel = (nav.sel - 1).coerceAtLeast(0)
                Dir.DOWN -> nav.sel = (nav.sel + 1).coerceAtMost(results.lastIndex)
                Dir.CENTER -> results.getOrNull(nav.sel)?.let { m -> if (m.pkg != null) actions.open(m.pkg) else m.contact?.let { actions.dial(it.number) } }
                else -> {}
            }
        }
        // On the idle screen the center key opens the main menu, like the V3.
        d == Dir.CENTER -> nav.menu()
    }
}

private fun dirAt(pos: Offset, size: IntSize): Dir {
    val dx = pos.x - size.width / 2f
    val dy = pos.y - size.height / 2f
    val r = min(size.width, size.height) / 2f * RING
    return when {
        hypot(dx, dy) < r * INNER -> Dir.CENTER
        abs(dx) > abs(dy) -> if (dx > 0) Dir.RIGHT else Dir.LEFT
        else -> if (dy > 0) Dir.DOWN else Dir.UP
    }
}

private const val RING = 0.9f
private const val INNER = 0.42f

/** The round navigation key: an etched ring with arrows and a center select button. */
@Composable
private fun NavRing(f: Finish, modifier: Modifier, onPress: (Dir) -> Unit) {
    val view = LocalView.current
    var pressed by remember { mutableStateOf<Dir?>(null) }
    Canvas(
        modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = { pos ->
                    pressed = dirAt(pos, size)
                    tryAwaitRelease()
                    pressed = null
                },
                onTap = { pos ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onPress(dirAt(pos, size))
                },
            )
        },
    ) {
        val r = min(size.width, size.height) / 2f * RING
        val ri = r * INNER
        val c = center
        val line = 1.2.dp.toPx()
        // Slight dish: a soft shade inside the ring.
        drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.0f), Color.Black.copy(alpha = 0.08f)), c, r), r, c)
        pressed?.let { d ->
            if (d == Dir.CENTER) {
                drawCircle(Color.Black.copy(alpha = 0.12f), ri, c)
            } else {
                val start = when (d) { Dir.RIGHT -> -45f; Dir.DOWN -> 45f; Dir.LEFT -> 135f; else -> 225f }
                drawArc(Color.Black.copy(alpha = 0.12f), start, 90f, true, Offset(c.x - r, c.y - r), Size(r * 2, r * 2))
            }
        }
        for ((radius, w) in listOf(r to line * 1.3f, ri to line)) {
            drawCircle(f.grooveLight, radius, c + Offset(0f, line), style = Stroke(w * 0.8f))
            drawCircle(f.groove, radius, c, style = Stroke(w))
        }
        // Arrows, backlit.
        val arrowAt = (r + ri) / 2f
        val a = r * 0.11f
        for ((i, d) in listOf(Dir.UP, Dir.RIGHT, Dir.DOWN, Dir.LEFT).withIndex()) {
            rotate(i * 90f, c) {
                val tip = Offset(c.x, c.y - arrowAt - a * 0.6f)
                val path = Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(tip.x - a, tip.y + a * 1.1f)
                    lineTo(tip.x + a, tip.y + a * 1.1f)
                    close()
                }
                if (pressed == d) drawPath(path, f.glow.copy(alpha = 0.6f), style = Stroke(a * 0.6f))
                drawPath(path, f.label)
            }
        }
        // The select button's mark: a small backlit dot.
        drawCircle(f.glow.copy(alpha = if (pressed == Dir.CENTER) 0.7f else 0.35f), ri * 0.32f, c)
        drawCircle(f.label, ri * 0.18f, c)
    }
}

@Composable
private fun SoftDash(f: Finish, lit: Boolean) {
    Box(
        Modifier.width(34.dp).height(if (lit) 5.dp else 4.dp).clip(RoundedCornerShape(2.dp)).background(f.label)
            .drawBehind { drawRect(f.glow.copy(alpha = 0.4f)) },
    )
}

// ---------- Digits ----------

private val DIGITS = listOf("1" to "", "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "*" to "", "0" to "+", "#" to "")

@Composable
private fun DigitKeys(f: Finish, modifier: Modifier, onDigit: (Char) -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        DIGITS.chunked(3).forEachIndexed { row, keys ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                keys.forEachIndexed { col, (d, letters) ->
                    FlatKey(
                        f, Modifier.weight(1f).fillMaxHeight(), right = col < 2, bottom = row < 3,
                        onLong = if (d == "0") ({ onDigit('+') }) else null,
                        onClick = { onDigit(d[0]) },
                    ) { lit ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(d, style = f.text(27, lit))
                            if (letters.isNotEmpty()) {
                                Spacer(Modifier.width(5.dp))
                                Text(letters, style = f.text(10, lit))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The menu key symbol: a small 3x3 grid. */
@Composable
internal fun MenuGlyph(color: Color, px: Dp) {
    Canvas(Modifier.size(px * 7, px * 7)) {
        val p = px.toPx()
        for (y in 0 until 3) for (x in 0 until 3) drawRect(color, Offset(x * 3 * p, y * 3 * p), Size(p * 1.6f, p * 1.6f))
    }
}
