package com.tm.meow.sudoku.puzzle.ui.components.sprite

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.ui.screens.game.rememberAnimationsEnabled
import kotlin.math.min
import kotlin.math.roundToInt

/** Source rectangle of one frame inside the atlas. */
data class FrameRect(val offset: IntOffset, val size: IntSize)

/**
 * Uniform grid atlas ([cols] x [rows] cells, reading order L-R then T-B) holding [frameCount] frames
 * played at [fps].
 */
data class SpriteSheet(
    @DrawableRes val res: Int,
    val cols: Int,
    val rows: Int,
    val frameCount: Int,
    val fps: Int,
) {
    /** Pure math. [index] wraps around [frameCount]; negatives wrap too. */
    fun frameRect(index: Int, atlasSize: IntSize): FrameRect {
        val i = index.mod(frameCount)
        val cw = atlasSize.width / cols
        val ch = atlasSize.height / rows
        return FrameRect(IntOffset((i % cols) * cw, (i / cols) * ch), IntSize(cw, ch))
    }

    val loopMillis: Int get() = frameCount * 1000 / fps
}

object CatSprites {
    /** Sitting-cat cheer loop (atlas 1416x768, 4x2 cells of 354x384). */
    val Cheer = SpriteSheet(R.drawable.cat_cheer_atlas, cols = 4, rows = 2, frameCount = 8, fps = 10)
}

/**
 * Plays [sheet] in a loop, bottom-centre aligned, aspect preserved. Frame index is read in the draw phase only
 * (no per-frame recomposition). Static frame 0 when [playing] is false or system animations are off.
 * Decorative unless [contentDescription] is given.
 */
@Composable
fun CatSprite(
    sheet: SpriteSheet,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    flipX: Boolean = false,
    playing: Boolean = true,
) {
    val atlas = ImageBitmap.imageResource(sheet.res)
    val atlasSize = remember(atlas) { IntSize(atlas.width, atlas.height) }
    val animate = playing && rememberAnimationsEnabled(key = sheet)
    val transition = rememberInfiniteTransition(label = "catSprite")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = sheet.frameCount.toFloat(),
        animationSpec = infiniteRepeatable(tween(sheet.loopMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "catSpriteFrame",
    )
    val semanticsMod = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier.clearAndSetSemantics { }
    }
    Canvas(modifier.then(semanticsMod)) {
        val index = if (animate) t.toInt().coerceAtMost(sheet.frameCount - 1) else 0
        val src = sheet.frameRect(index, atlasSize)
        val fit = min(size.width / src.size.width, size.height / src.size.height)
        val dstW = (src.size.width * fit).roundToInt()
        val dstH = (src.size.height * fit).roundToInt()
        val dstOffset = IntOffset(((size.width - dstW) / 2f).roundToInt(), (size.height - dstH).roundToInt())
        val draw = {
            drawImage(
                image = atlas,
                srcOffset = src.offset,
                srcSize = src.size,
                dstOffset = dstOffset,
                dstSize = IntSize(dstW, dstH),
                filterQuality = FilterQuality.High,
            )
        }
        if (flipX) scale(-1f, 1f, pivot = center) { draw() } else draw()
    }
}
