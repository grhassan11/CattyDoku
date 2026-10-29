package com.threetech.catpuzzle.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.threetech.catpuzzle.R

/**
 * User-supplied icon art (Design\Icons, see ASSET_LICENSES.txt). Which icon plays which role is
 * decided HERE, in one place. ic_cat_1..10, ic_yarn_ball, ic_mouse, ic_mouse_locked and ic_fish_bone_level_failed are PNGs (drawable-nodpi, gradients
 * could not be converted); everything else is a VectorDrawable.
 * Decorative: content descriptions live on the clickable / semantic parent.
 */
object IconArt {
    /**
     * Placed-cat art, indexed by CatVariants variant (size must equal CatVariants.UNIQUE).
     * ic_cat_black_flat is NOT used: flat one-colour silhouette, no face, reads badly on region colours.
     */
    private val placedCats: List<Int> = listOf(
        R.drawable.ic_cat_1,
        R.drawable.ic_cat_2,
        R.drawable.ic_cat_3,
        R.drawable.ic_cat_4,
        R.drawable.ic_cat_5,
        R.drawable.ic_cat_6,
        R.drawable.ic_cat_7,
        R.drawable.ic_cat_8,
        R.drawable.ic_cat_9,
        R.drawable.ic_cat_10,
    )

    /** Fixed cat for decorative spots (rules diagram, cat booster button, splash, tutorial). */
    @DrawableRes val PlacedCat: Int = R.drawable.ic_cat_5

    /** Mouse booster art (PNG, gradient SVG). */
    @DrawableRes val Mouse: Int = R.drawable.ic_mouse

    /** Greyed mouse booster art for the locked state (PNG, gradient SVG). Lock badge is drawn by the caller. */
    @DrawableRes val MouseLocked: Int = R.drawable.ic_mouse_locked

    /** Rate Us star (VectorDrawable, single gradient). Unselected state is greyed at the use site. */
    @DrawableRes val StarIsland: Int = R.drawable.ic_star_island

    @DrawableRes
    fun placedCatFor(variant: Int): Int = placedCats[variant.mod(placedCats.size)]

    /** One-colour cat head used as a tinted silhouette (unsolved regions, locked booster). */
    @DrawableRes val CatSilhouette: Int = R.drawable.ic_cat_black_flat

    /** Lose-dialog hero: big fish skeleton (PNG, gradient SVG). */
    @DrawableRes val LevelFailed: Int = R.drawable.ic_fish_bone_level_failed

    /** Former lose-dialog hero (sad cat); no longer used by the lose dialog. */
    @DrawableRes val SadCat: Int = R.drawable.ic_cat_4

    /** Win-dialog mascots; rotate by level so repeat wins feel less static. */
    private val mascots: List<Int> = listOf(
        R.drawable.ic_cat_1,
        R.drawable.ic_cat_2,
        R.drawable.ic_cat_3,
        R.drawable.ic_cat_4,
        R.drawable.ic_cat_5,
        R.drawable.ic_cat_6,
        R.drawable.ic_cat_7,
        R.drawable.ic_cat_8,
        R.drawable.ic_cat_9,
        R.drawable.ic_cat_10,
    )

    @DrawableRes
    fun mascotFor(level: Int): Int = mascots[(level - 1).mod(mascots.size)]
}

@Composable
fun IconImage(
    @DrawableRes res: Int,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color? = null,
) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = tint?.let { ColorFilter.tint(it) },
    )
}

/** Cat head. Pass [silhouetteTint] for the flat one-colour version, [variant] for a specific cat. */
@Composable
fun CatIcon(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    silhouetteTint: Color? = null,
    variant: Int = -1,
) {
    if (silhouetteTint != null) {
        IconImage(IconArt.CatSilhouette, modifier, size, silhouetteTint)
    } else {
        IconImage(if (variant >= 0) IconArt.placedCatFor(variant) else IconArt.PlacedCat, modifier, size)
    }
}

/** Life fish: live fish when [active], bone when spent. */
@Composable
fun FishLife(active: Boolean, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    IconImage(if (active) R.drawable.ic_fish_live else R.drawable.ic_fish_bone, modifier, size)
}

@Composable
fun HintBulb(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    IconImage(R.drawable.ic_hint, modifier, size)
}

@Composable
fun MouseIcon(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    IconImage(IconArt.Mouse, modifier, size)
}
