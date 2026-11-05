package com.tm.meow.sudoku.puzzle.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.domain.model.AnimalSet

/**
 * User-supplied icon art (Design\Icons, see ASSET_LICENSES.txt). Which icon plays which role is
 * decided HERE, in one place. ic_cat_1..10, ic_animals_1..10, ic_dog_1..10 and ic_ocean_animal_1..10, ic_fish_life and ic_fishbone are WebPs (drawable-nodpi, 680 px for animals/dogs/ocean/fish); ic_yarn_ball, ic_mouse, and ic_mouse_locked are PNGs (drawable-nodpi, gradients
 * could not be converted); everything else (incl. the one-colour cat/dog silhouettes, tinted via ColorFilter) is a VectorDrawable.
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

    /** One-colour dog head used as a tinted silhouette (unsolved regions on Dogs boss levels). */
    @DrawableRes val DogSilhouette: Int = R.drawable.ic_dogs_flat_black

    /** A life (happy orange fish, WebP 680 px, drawable-nodpi). Render small via [DownsampledIconImage]. */
    @DrawableRes val FishLife: Int = R.drawable.ic_fish_life

    /** A lost life / mistake (sad grey fish skeleton, WebP 680 px, drawable-nodpi). Render via [DownsampledIconImage] (stat chip, HUD spent life). */
    @DrawableRes val FishBone: Int = R.drawable.ic_fishbone

    /** Sad crying cat face (WebP ~1254 px, drawable-nodpi, colour). ONLY for the Out of Lives / Out of Hearts hero ([LoseScreenLayout]). Render via [DownsampledIconImage], never tinted. */
    @DrawableRes val CatSad: Int = R.drawable.ic_cat_sad_level_failed

    /** Trophy (WebP, drawable-nodpi, colour). Render via [DownsampledIconImage], never tinted. */
    @DrawableRes val Trophy: Int = R.drawable.ic_trophy

    /** Remove-ads (WebP, drawable-nodpi, colour). Render via [DownsampledIconImage], never tinted. Used by the Home Remove Ads badge. */
    @DrawableRes val RemoveAds: Int = R.drawable.ic_remove_ads

    /** Store / shop (WebP, drawable-nodpi, colour). Render via [DownsampledIconImage], never tinted. */
    @DrawableRes val Store: Int = R.drawable.ic_store

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

    private val animals: List<Int> = listOf(
        R.drawable.ic_animals_1, R.drawable.ic_animals_2, R.drawable.ic_animals_3, R.drawable.ic_animals_4,
        R.drawable.ic_animals_5, R.drawable.ic_animals_6, R.drawable.ic_animals_7, R.drawable.ic_animals_8,
        R.drawable.ic_animals_9, R.drawable.ic_animals_10,
    )
    private val dogs: List<Int> = listOf(
        R.drawable.ic_dog_1, R.drawable.ic_dog_2, R.drawable.ic_dog_3, R.drawable.ic_dog_4, R.drawable.ic_dog_5,
        R.drawable.ic_dog_6, R.drawable.ic_dog_7, R.drawable.ic_dog_8, R.drawable.ic_dog_9, R.drawable.ic_dog_10,
    )
    private val ocean: List<Int> = listOf(
        R.drawable.ic_ocean_animal_1, R.drawable.ic_ocean_animal_2, R.drawable.ic_ocean_animal_3,
        R.drawable.ic_ocean_animal_4, R.drawable.ic_ocean_animal_5, R.drawable.ic_ocean_animal_6,
        R.drawable.ic_ocean_animal_7, R.drawable.ic_ocean_animal_8, R.drawable.ic_ocean_animal_9,
        R.drawable.ic_ocean_animal_10,
    )

    /** Boss-level piece art for [set] (sizes match [AnimalSet.count]; all WebPs). */
    fun animalIcons(set: AnimalSet): List<Int> = when (set) {
        AnimalSet.ANIMALS -> animals
        AnimalSet.DOGS -> dogs
        AnimalSet.OCEAN -> ocean
    }

    @DrawableRes
    fun animalFor(set: AnimalSet, index: Int): Int = animalIcons(set).let { it[index.mod(it.size)] }

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

/**
 * Icon for small slots from a large bitmap: decoded once at ~[size] (power-of-two downsample) instead of the full
 * 680 px source. Falls back to [painterResource] if the manual decode fails. No tint: art keeps its own colours.
 */
@Composable
fun DownsampledIconImage(@DrawableRes res: Int, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val resources = LocalContext.current.resources
    val targetPx = with(LocalDensity.current) { size.roundToPx() }
    val painter = remember(resources, res, targetPx) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
            BitmapFactory.decodeResource(resources, res, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetPx) sample *= 2
            val opts = BitmapFactory.Options().apply { inScaled = false; inSampleSize = sample }
            BitmapFactory.decodeResource(resources, res, opts)?.let { BitmapPainter(it.asImageBitmap()) }
        }.getOrNull()
    } ?: painterResource(res)
    Image(painter = painter, contentDescription = null, modifier = modifier.size(size))
}

/** Life fish: happy fish when [active], grey fishbone when spent. Never tinted. */
@Composable
fun FishLife(active: Boolean, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    DownsampledIconImage(if (active) IconArt.FishLife else IconArt.FishBone, modifier, size)
}

@Composable
fun HintBulb(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    IconImage(R.drawable.ic_hint, modifier, size)
}

@Composable
fun MouseIcon(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    IconImage(IconArt.Mouse, modifier, size)
}
