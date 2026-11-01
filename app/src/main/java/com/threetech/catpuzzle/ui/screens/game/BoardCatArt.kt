package com.threetech.catpuzzle.ui.screens.game

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.threetech.catpuzzle.domain.engine.CatVariants
import com.threetech.catpuzzle.domain.model.AnimalSet
import com.threetech.catpuzzle.ui.components.IconArt

/** Pure sampling math for downsampled cat decoding (unit tested). */
internal object CatBitmapSampling {
    /** Largest power-of-two sample size whose decoded edge is still >= [targetPx] (never below 1). */
    fun sampleSize(srcPx: Int, targetPx: Int): Int {
        if (srcPx <= 0 || targetPx <= 0) return 1
        var sample = 1
        while (srcPx / (sample * 2) >= targetPx) sample *= 2
        return sample
    }

    /** Distinct cat variants (0 until [CatVariants.UNIQUE]) used by [board], ascending. */
    fun variantsOf(board: BoardUi): List<Int> =
        board.cells.mapTo(sortedSetOf<Int>()) { it.catVariant.mod(CatVariants.UNIQUE) }.toList()
}

/**
 * Painters for ONLY the cat variants this board uses (a board has at most `size` regions), each decoded once at roughly
 * the on-screen cell size instead of the full 512 px source. Painter instances are stable between recompositions, so
 * unchanged cells can skip. Falls back to [painterResource] for a variant whose manual decode fails.
 * Result is indexed by variant (0 until [CatVariants.UNIQUE]); unused variants are null.
 */
@Composable
internal fun rememberBoardCatPainters(board: BoardUi, targetPx: Int): Array<Painter?> {
    val resources = LocalContext.current.resources
    val variants = remember(board) { CatBitmapSampling.variantsOf(board) }
    val decoded = remember(resources, variants, targetPx) {
        variants.map { v -> decodeCatPainter(resources, IconArt.placedCatFor(v), targetPx) }
    }
    val out = arrayOfNulls<Painter>(CatVariants.UNIQUE)
    variants.forEachIndexed { i, v ->
        out[v] = decoded[i] ?: painterResource(IconArt.placedCatFor(v))
    }
    return out
}

private fun decodeCatPainter(resources: Resources, @DrawableRes id: Int, targetPx: Int): Painter? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
    BitmapFactory.decodeResource(resources, id, bounds)
    val opts = BitmapFactory.Options().apply {
        inScaled = false
        inSampleSize = CatBitmapSampling.sampleSize(bounds.outWidth, targetPx)
    }
    BitmapFactory.decodeResource(resources, id, opts)?.let { BitmapPainter(it.asImageBitmap()) }
}.getOrNull()

/** Painters for every icon of boss-level [set], index-aligned with [IconArt.animalIcons]. Vector drawables, no decode. */
@Composable
internal fun rememberAnimalPainters(set: AnimalSet): List<Painter> =
    IconArt.animalIcons(set).map { painterResource(it) }
