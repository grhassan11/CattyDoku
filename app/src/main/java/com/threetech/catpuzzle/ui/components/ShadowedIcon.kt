package com.threetech.catpuzzle.ui.components

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val GreyscaleFilter: ColorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
private val ShadowFilter: ColorFilter = ColorFilter.tint(Color.Black)
private val CanBlur: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Icon with a drop shadow (Compose has none for vectors/bitmaps): a black-tinted copy of the same
 * painter drawn [shadowOffsetY] lower underneath. API 31+: the copy is blurred by [shadowBlur] (RenderEffect).
 * API 26-30: [blur] is a no-op there, so the copy is a plain offset silhouette (hard but low-alpha, reads fine at small sizes).
 * [greyed] = unearned look (alpha 0.5 + saturation 0, same as RateUsDialog); shadow follows the same alpha.
 * Reusable for any icon; decorative (no content description).
 */
@Composable
fun ShadowedIcon(
    @DrawableRes res: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    greyed: Boolean = false,
    shadowAlpha: Float = 0.35f,
    shadowOffsetY: Dp = 1.5.dp,
    shadowBlur: Dp = 1.5.dp,
) {
    val painter = painterResource(res)
    val imageAlpha = if (greyed) 0.5f else 1f
    val shadowMod = remember(shadowOffsetY, shadowBlur) {
        val base = Modifier.size(size).offset(y = shadowOffsetY)
        if (CanBlur) base.blur(shadowBlur, BlurredEdgeTreatment.Unbounded) else base
    }
    Box(modifier.size(size)) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = shadowMod,
            alpha = shadowAlpha * imageAlpha,
            colorFilter = ShadowFilter,
        )
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(size),
            alpha = imageAlpha,
            colorFilter = if (greyed) GreyscaleFilter else null,
        )
    }
}
