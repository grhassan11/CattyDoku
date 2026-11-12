package com.tm.meow.sudoku.puzzle.ui.ads

import android.view.LayoutInflater
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.ads.GmaNativeAdHandle
import com.tm.meow.sudoku.puzzle.domain.ads.NativeAdSlotState
import com.tm.meow.sudoku.puzzle.ui.theme.GameColors

/**
 * Compact native ad row for the bottom of both game screens (Levels + Sudoku). Two-row bar: icon at start, title row
 * (Ad badge + headline) over a full-width CTA pill. FIXED height [GAME_NATIVE_AD_HEIGHT] (88dp), full width, so the board never resizes when the ad arrives, fails, or is hidden behind a dialog. The caller
 * applies the nav-bar inset and the clearance above the slot.
 *
 * [state] drives the content (owned by AdsViewModel, never destroyed here): Loading = shimmer skeleton, Loaded = the ad
 * ([R.layout.admob_native_banner] bound via [bind], shared with the Home bar), anything else = blank slot of the same height.
 * Policy: "Ad" badge always visible, AdChoices drawn by the SDK top-right (end padding keeps assets clear), icon / headline /
 * CTA registered with the NativeAdView, nothing overlaps an asset, CTA text is the advertiser's.
 */
/** Fixed slot height (56dp icon + paddings; title row + 40dp CTA). Skeleton + blank states share it. */
val GAME_NATIVE_AD_HEIGHT: Dp = 88.dp

@Composable
fun GameNativeAdSlot(state: NativeAdSlotState, modifier: Modifier = Modifier) {
    // Loaded -> keyed on the handle (new ad = fresh view); Loading -> skeleton; Hidden / Failed -> blank, same height.
    val phase: Any = if (state is NativeAdSlotState.Loaded) state.ad else state
    Crossfade(
        targetState = phase,
        modifier = modifier
            .fillMaxWidth()
            .height(GAME_NATIVE_AD_HEIGHT)
            .background(GameColors.ScreenBackground),
        animationSpec = tween(durationMillis = 200),
        label = "gameNativeAdSlot",
    ) { p ->
        when {
            p is GmaNativeAdHandle -> GameNativeAdRow(p)
            p is NativeAdSlotState.Loading -> GameNativeAdSkeleton()
            else -> Box(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun GameNativeAdRow(handle: GmaNativeAdHandle) {
    key(handle) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                LayoutInflater.from(context).inflate(R.layout.admob_native_banner, null, false) as NativeAdView
            },
            update = { view -> bind(view, handle.ad) },
        )
    }
}

/**
 * Loading placeholder: same container + background as the loaded row. Decorative only (no "Ad" badge, no text, not
 * clickable, hidden from accessibility). Blocks mimic badge, icon, headline, CTA pill; the shimmer sweep is shared with
 * the Home skeleton.
 */
@Composable
private fun GameNativeAdSkeleton() {
    val progress = rememberInfiniteTransition(label = "gameNativeAdShimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1300, easing = LinearEasing)),
        label = "gameNativeAdShimmerProgress",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(colorResource(R.color.native_ad_background))
            .clearAndSetSemantics { },
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colorResource(R.color.native_ad_top_stroke)))
        Row(
            Modifier.fillMaxSize().padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShimmerBlock(progress, Modifier.size(56.dp), 10.dp)
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Row(Modifier.fillMaxWidth().height(24.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ShimmerBlock(progress, Modifier.size(width = 22.dp, height = 20.dp), 4.dp)
                    ShimmerBlock(progress, Modifier.padding(start = 6.dp).weight(1f).height(14.dp), 7.dp)
                }
                ShimmerBlock(progress, Modifier.padding(top = 4.dp).fillMaxWidth().height(40.dp), 20.dp)
            }
        }
    }
}
