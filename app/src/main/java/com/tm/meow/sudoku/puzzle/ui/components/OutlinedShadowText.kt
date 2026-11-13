package com.tm.meow.sudoku.puzzle.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tm.meow.sudoku.puzzle.ui.theme.FredokaFamily

/** Popup text look shared by the in-game praise words, "+N" popup and combo chip. */
internal object OutlinedShadowDefaults {
    val OutlineColor = Color.White
    val ShadowColor = Color(0x66000000) // black ~40 %
    val ShadowOffset = Offset(0f, 4f)
    const val ShadowBlur = 8f
}

/** Outline-then-fill style pair for drawing popup text into a canvas (stroke layer carries the drop shadow). */
internal fun outlinedTextStyles(
    base: TextStyle,
    outlineWidthPx: Float,
    outlineColor: Color = OutlinedShadowDefaults.OutlineColor,
): Pair<TextStyle, TextStyle> {
    val font = base.copy(fontFamily = FredokaFamily)
    val stroke = font.copy(
        color = outlineColor,
        drawStyle = Stroke(width = outlineWidthPx * 2f, join = StrokeJoin.Round), // inner half is covered by the fill
        shadow = Shadow(OutlinedShadowDefaults.ShadowColor, OutlinedShadowDefaults.ShadowOffset, OutlinedShadowDefaults.ShadowBlur),
    )
    val fill = font.copy(shadow = null, drawStyle = null)
    return stroke to fill
}

/**
 * Fredoka text with a white outline and a drop shadow that follows the outline. Two stacked Texts: stroke layer (white + shadow)
 * behind, fill layer on top. [outlineWidth] is the visible outline thickness. Only the fill layer is exposed to accessibility.
 */
@Composable
internal fun OutlinedShadowText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontSize: androidx.compose.ui.unit.TextUnit = 30.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    outlineWidth: Dp = 3.dp,
    outlineColor: Color = OutlinedShadowDefaults.OutlineColor,
) {
    val outlinePx = with(LocalDensity.current) { outlineWidth.toPx() }
    val (stroke, fill) = remember(color, fontSize, fontWeight, outlinePx, outlineColor) {
        outlinedTextStyles(TextStyle(color = color, fontSize = fontSize, fontWeight = fontWeight), outlinePx, outlineColor)
    }
    Box(modifier) {
        Text(text = text, style = stroke, modifier = Modifier.clearAndSetSemantics { })
        Text(text = text, style = fill)
    }
}
