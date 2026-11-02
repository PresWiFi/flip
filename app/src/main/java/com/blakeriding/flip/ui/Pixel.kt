package com.blakeriding.flip.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// A 5x7 pixel font, like the one on old phone screens. Each glyph is 7 rows of 5 bits (high bit = left).

private val GLYPHS: Map<Char, IntArray> = mapOf(
    '0' to intArrayOf(0x0E, 0x11, 0x13, 0x15, 0x19, 0x11, 0x0E),
    '1' to intArrayOf(0x04, 0x0C, 0x04, 0x04, 0x04, 0x04, 0x0E),
    '2' to intArrayOf(0x0E, 0x11, 0x01, 0x02, 0x04, 0x08, 0x1F),
    '3' to intArrayOf(0x1F, 0x02, 0x04, 0x02, 0x01, 0x11, 0x0E),
    '4' to intArrayOf(0x02, 0x06, 0x0A, 0x12, 0x1F, 0x02, 0x02),
    '5' to intArrayOf(0x1F, 0x10, 0x1E, 0x01, 0x01, 0x11, 0x0E),
    '6' to intArrayOf(0x06, 0x08, 0x10, 0x1E, 0x11, 0x11, 0x0E),
    '7' to intArrayOf(0x1F, 0x01, 0x02, 0x04, 0x08, 0x08, 0x08),
    '8' to intArrayOf(0x0E, 0x11, 0x11, 0x0E, 0x11, 0x11, 0x0E),
    '9' to intArrayOf(0x0E, 0x11, 0x11, 0x0F, 0x01, 0x02, 0x0C),
    'A' to intArrayOf(0x0E, 0x11, 0x11, 0x1F, 0x11, 0x11, 0x11),
    'B' to intArrayOf(0x1E, 0x11, 0x11, 0x1E, 0x11, 0x11, 0x1E),
    'C' to intArrayOf(0x0E, 0x11, 0x10, 0x10, 0x10, 0x11, 0x0E),
    'D' to intArrayOf(0x1C, 0x12, 0x11, 0x11, 0x11, 0x12, 0x1C),
    'E' to intArrayOf(0x1F, 0x10, 0x10, 0x1E, 0x10, 0x10, 0x1F),
    'F' to intArrayOf(0x1F, 0x10, 0x10, 0x1E, 0x10, 0x10, 0x10),
    'G' to intArrayOf(0x0E, 0x11, 0x10, 0x17, 0x11, 0x11, 0x0F),
    'H' to intArrayOf(0x11, 0x11, 0x11, 0x1F, 0x11, 0x11, 0x11),
    'I' to intArrayOf(0x0E, 0x04, 0x04, 0x04, 0x04, 0x04, 0x0E),
    'J' to intArrayOf(0x07, 0x02, 0x02, 0x02, 0x02, 0x12, 0x0C),
    'K' to intArrayOf(0x11, 0x12, 0x14, 0x18, 0x14, 0x12, 0x11),
    'L' to intArrayOf(0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x1F),
    'M' to intArrayOf(0x11, 0x1B, 0x15, 0x15, 0x11, 0x11, 0x11),
    'N' to intArrayOf(0x11, 0x11, 0x19, 0x15, 0x13, 0x11, 0x11),
    'O' to intArrayOf(0x0E, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0E),
    'P' to intArrayOf(0x1E, 0x11, 0x11, 0x1E, 0x10, 0x10, 0x10),
    'Q' to intArrayOf(0x0E, 0x11, 0x11, 0x11, 0x15, 0x12, 0x0D),
    'R' to intArrayOf(0x1E, 0x11, 0x11, 0x1E, 0x14, 0x12, 0x11),
    'S' to intArrayOf(0x0F, 0x10, 0x10, 0x0E, 0x01, 0x01, 0x1E),
    'T' to intArrayOf(0x1F, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04),
    'U' to intArrayOf(0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0E),
    'V' to intArrayOf(0x11, 0x11, 0x11, 0x11, 0x11, 0x0A, 0x04),
    'W' to intArrayOf(0x11, 0x11, 0x11, 0x15, 0x15, 0x15, 0x0A),
    'X' to intArrayOf(0x11, 0x11, 0x0A, 0x04, 0x0A, 0x11, 0x11),
    'Y' to intArrayOf(0x11, 0x11, 0x11, 0x0A, 0x04, 0x04, 0x04),
    'Z' to intArrayOf(0x1F, 0x01, 0x02, 0x04, 0x08, 0x10, 0x1F),
    ' ' to intArrayOf(0, 0, 0, 0, 0, 0, 0),
    ':' to intArrayOf(0x00, 0x0C, 0x0C, 0x00, 0x0C, 0x0C, 0x00),
    '.' to intArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x0C, 0x0C),
    ',' to intArrayOf(0x00, 0x00, 0x00, 0x00, 0x0C, 0x04, 0x08),
    '-' to intArrayOf(0x00, 0x00, 0x00, 0x1F, 0x00, 0x00, 0x00),
    '/' to intArrayOf(0x00, 0x01, 0x02, 0x04, 0x08, 0x10, 0x00),
    '!' to intArrayOf(0x04, 0x04, 0x04, 0x04, 0x04, 0x00, 0x04),
    '?' to intArrayOf(0x0E, 0x11, 0x01, 0x02, 0x04, 0x00, 0x04),
    '\'' to intArrayOf(0x0C, 0x04, 0x08, 0x00, 0x00, 0x00, 0x00),
    '(' to intArrayOf(0x02, 0x04, 0x08, 0x08, 0x08, 0x04, 0x02),
    ')' to intArrayOf(0x08, 0x04, 0x02, 0x02, 0x02, 0x04, 0x08),
    '+' to intArrayOf(0x00, 0x04, 0x04, 0x1F, 0x04, 0x04, 0x00),
    '*' to intArrayOf(0x00, 0x04, 0x15, 0x0E, 0x15, 0x04, 0x00),
    '#' to intArrayOf(0x0A, 0x0A, 0x1F, 0x0A, 0x1F, 0x0A, 0x0A),
    '&' to intArrayOf(0x0C, 0x12, 0x14, 0x08, 0x15, 0x12, 0x0D),
    '%' to intArrayOf(0x18, 0x19, 0x02, 0x04, 0x08, 0x13, 0x03),
    '<' to intArrayOf(0x02, 0x04, 0x08, 0x10, 0x08, 0x04, 0x02),
    '>' to intArrayOf(0x08, 0x04, 0x02, 0x01, 0x02, 0x04, 0x08),
    '=' to intArrayOf(0x00, 0x00, 0x1F, 0x00, 0x1F, 0x00, 0x00),
    '_' to intArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x1F),
    '@' to intArrayOf(0x0E, 0x11, 0x01, 0x0D, 0x15, 0x15, 0x0E),
)

private fun glyph(c: Char): IntArray = GLYPHS[c.uppercaseChar()] ?: GLYPHS.getValue('?')

/** Text in the pixel font. [px] is the size of one font pixel; a character is 6 px wide with spacing. */
@Composable
fun PixelText(
    text: String,
    modifier: Modifier = Modifier,
    px: Dp = 2.dp,
    color: Color = Color.White,
    shadow: Color? = Color(0x99000000),
    maxChars: Int = Int.MAX_VALUE,
) {
    val shown = if (text.length > maxChars) text.take((maxChars - 1).coerceAtLeast(1)) + "." else text
    val w = px * (shown.length * 6 - 1).coerceAtLeast(1)
    Canvas(modifier.size(w, px * 7)) {
        val p = px.toPx()
        if (shadow != null) drawPixelText(shown, p, shadow, Offset(p * 0.5f, p * 0.5f))
        drawPixelText(shown, p, color, Offset.Zero)
    }
}

private fun DrawScope.drawPixelText(text: String, p: Float, color: Color, offset: Offset) {
    // A hair of gap between pixels gives the LCD grid look.
    val dot = Size(p * 0.88f, p * 0.88f)
    text.forEachIndexed { i, c ->
        val rows = glyph(c)
        for (y in 0 until 7) {
            val bits = rows[y]
            for (x in 0 until 5) {
                if (bits and (0x10 shr x) != 0) {
                    drawRect(color, Offset(offset.x + (i * 6 + x) * p, offset.y + y * p), dot)
                }
            }
        }
    }
}

// ---------- LCD parts ----------

val LcdBlue = Color(0xFF0A58C2)
val LcdText = Color.White
val Backlight = Color(0xFF6EC6FF)

/** The idle-screen wallpaper: deep blue with soft light bands. */
@Composable
fun V3Wallpaper(modifier: Modifier) {
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF001A4D), Color(0xFF0050B3), Color(0xFF1A8CFF))))
        val w = size.width
        val h = size.height
        for ((i, alpha) in listOf(0.18f, 0.12f, 0.08f).withIndex()) {
            val y = h * (0.45f + i * 0.14f)
            val path = Path().apply {
                moveTo(0f, y)
                cubicTo(w * 0.3f, y - h * 0.22f, w * 0.65f, y + h * 0.18f, w, y - h * 0.08f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(path, Color.White.copy(alpha = alpha))
        }
    }
}

/** Signal bars (0-4), small to tall. */
@Composable
fun SignalBars(level: Int, px: Dp = 2.dp) {
    Canvas(Modifier.size(px * 14, px * 8)) {
        val p = px.toPx()
        for (i in 0 until 5) {
            val h = (i + 2) * p * 1.15f
            val c = if (i <= level) LcdText else LcdText.copy(alpha = 0.25f)
            drawRect(c, Offset(i * 3 * p, size.height - h), Size(p * 2, h))
        }
    }
}

/** A battery with three bars. */
@Composable
fun BatteryIcon(percent: Int, charging: Boolean, px: Dp = 2.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (charging) {
            PixelText("+", px = px * 0.75f)
            Spacer(Modifier.width(px))
        }
        Canvas(Modifier.size(px * 13, px * 7)) {
            val p = px.toPx()
            // Tip on the left, like the V3.
            drawRect(LcdText, Offset(0f, p * 2), Size(p, p * 3))
            drawRect(LcdText, Offset(p, 0f), Size(p * 12, p * 7), style = androidx.compose.ui.graphics.drawscope.Stroke(p))
            val bars = when {
                percent >= 70 -> 3
                percent >= 35 -> 2
                percent >= 10 -> 1
                else -> 0
            }
            for (i in 0 until bars) drawRect(LcdText, Offset(p * (9 - i * 3.3f), p * 2), Size(p * 2.4f, p * 3))
        }
    }
}

/** A tiny padlock for locked apps. */
@Composable
fun PixelLock(px: Dp = 2.dp, color: Color = Color(0xFFFFC94A)) {
    Canvas(Modifier.size(px * 7, px * 8)) {
        val p = px.toPx()
        drawRect(color, Offset(p * 2, 0f), Size(p * 3, p))
        drawRect(color, Offset(p, p), Size(p, p * 3))
        drawRect(color, Offset(p * 5, p), Size(p, p * 3))
        drawRect(color, Offset(0f, p * 3), Size(p * 7, p * 5))
        drawRect(Color.Black, Offset(p * 3, p * 5), Size(p, p * 2))
    }
}
