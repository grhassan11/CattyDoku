package com.threetech.catpuzzle.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.threetech.catpuzzle.ui.screens.home.HOME_PATTERN_ALPHA
import com.threetech.catpuzzle.ui.screens.home.patternHasX
import com.threetech.catpuzzle.ui.theme.GameColors

private val PatternCell = 64.dp

/**
 * The app's warm background: cream [GameColors.ScreenBackground] + faint board pattern. Shared by Home,
 * Settings and Streak (and, as its pattern layer, by the first world of the Home map).
 */
fun Modifier.homeBackground(): Modifier = background(GameColors.ScreenBackground).homePattern()

/** Faint board pattern: rounded squares with scattered X marks, drawn once per size (drawWithCache). No images. */
fun Modifier.homePattern(color: Color = GameColors.Wordmark): Modifier = drawWithCache {
    val draw = homePatternDrawer(color, size.width, size.height)
    onDrawBehind { draw(this) }
}

/** Builds the pattern draw lambda for a [width] x [height] px area. Call inside drawWithCache only. */
fun CacheDrawScope.homePatternDrawer(color: Color, width: Float, height: Float): DrawScope.() -> Unit {
    val cell = PatternCell.toPx()
    val gap = 4.dp.toPx()
    val side = cell - gap
    val corner = CornerRadius(12.dp.toPx())
    val line = Stroke(width = 1.5.dp.toPx())
    val xWidth = 3.dp.toPx()
    val faint = color.copy(alpha = HOME_PATTERN_ALPHA)
    val cols = (width / cell).toInt() + 2
    val rows = (height / cell).toInt() + 2
    return {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x = c * cell - cell / 2f
                val y = r * cell - cell / 2f
                drawRoundRect(faint, Offset(x, y), Size(side, side), corner, line)
                if (patternHasX(r, c)) {
                    val inset = side * 0.28f
                    drawLine(faint, Offset(x + inset, y + inset), Offset(x + side - inset, y + side - inset), xWidth, StrokeCap.Round)
                    drawLine(faint, Offset(x + side - inset, y + inset), Offset(x + inset, y + side - inset), xWidth, StrokeCap.Round)
                }
            }
        }
    }
}
