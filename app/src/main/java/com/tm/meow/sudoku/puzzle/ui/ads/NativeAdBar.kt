package com.tm.meow.sudoku.puzzle.ui.ads

import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import com.tm.meow.sudoku.puzzle.ui.screens.game.rememberAnimationsEnabled
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tm.meow.sudoku.puzzle.domain.ads.NativeAdSlotState
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.ads.GmaNativeAdHandle
import com.tm.meow.sudoku.puzzle.domain.ads.NativeAdHandle

/**
 * Edge-to-edge native ad strip (res/layout/admob_native_hundred.xml, 112dp content) for the bottom of Home.
 * Compose has no NativeAdView, so the layout is inflated inside an [AndroidView]. The ad background fills the full
 * width and runs BEHIND the navigation bar; the ad content sits above the nav-bar inset.
 * Renders nothing when [ad] is not a GMA handle. The ad is owned by the caller (HomeAdsViewModel); never destroyed here.
 * Policy: "Ad" attribution badge always visible, AdChoices drawn by the SDK top-right, headline / body / icon / CTA all
 * registered with the NativeAdView (impressions + clicks), nothing overlaps an asset, CTA text is the advertiser's.
 */
@Composable
fun NativeAdSlot(state: NativeAdSlotState, modifier: Modifier = Modifier) {
    if (!state.reservesSlot) return
    // Keyed on the handle (null = skeleton): skeleton -> ad fades over 200 ms; both layers are the same height.
    Crossfade(
        targetState = (state as? NativeAdSlotState.Loaded)?.ad,
        modifier = modifier,
        animationSpec = tween(durationMillis = 200),
        label = "nativeAdSlot",
    ) { ad ->
        if (ad != null) NativeAdBar(ad) else NativeAdSkeleton()
    }
}

/**
 * Loading placeholder for the Home native slot: same full-width container, background, nav-bar inset and 112dp content
 * height as [NativeAdBar]. Decorative only: no "Ad" badge, no text, not clickable, hidden from accessibility.
 * Blocks mimic icon square, headline, body, CTA pill; a soft highlight sweeps across them (drawn in the draw phase, no
 * recomposition per frame; the platform animator scale 0 freezes it).
 */
@Composable
private fun NativeAdSkeleton(modifier: Modifier = Modifier) {
    val progress = rememberShimmerProgress("nativeAdShimmer")
    Column(
        modifier
            .fillMaxWidth()
            .background(colorResource(R.color.native_ad_background))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .clearAndSetSemantics { },
    ) {
        Box(Modifier.fillMaxWidth().height(SKELETON_HEIGHT)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colorResource(R.color.native_ad_top_stroke)))
            Box(Modifier.fillMaxSize().padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 4.dp)) {
                ShimmerBlock(progress, Modifier.size(50.dp), 8.dp)
                ShimmerBlock(
                    progress,
                    Modifier.padding(start = 66.dp, top = 6.dp).fillMaxWidth(0.55f).height(16.dp),
                    8.dp,
                )
                ShimmerBlock(
                    progress,
                    Modifier.padding(start = 66.dp, top = 35.dp).fillMaxWidth(0.8f).height(10.dp),
                    5.dp,
                )
                ShimmerBlock(progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(44.dp), 22.dp)
            }
        }
    }
}

/**
 * Shared shimmer sweep 0..1 (1300 ms loop). Honors the platform animator scale 0 and the app's "Reduce effects" setting
 * ([rememberAnimationsEnabled]): when off, a fixed mid value = static placeholder (still visible, no motion).
 */
@Composable
internal fun rememberShimmerProgress(label: String): State<Float> {
    if (!rememberAnimationsEnabled(null)) return remember { mutableFloatStateOf(0.5f) }
    return rememberInfiniteTransition(label = label).animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1300, easing = LinearEasing)),
        label = "${label}Progress",
    )
}

@Composable
internal fun ShimmerBlock(progress: State<Float>, modifier: Modifier, corner: Dp) {
    Box(
        modifier.drawBehind {
            val w = size.width
            val x = -w + 2f * w * progress.value
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(SkeletonBase, SkeletonHighlight, SkeletonBase),
                    start = Offset(x, 0f),
                    end = Offset(x + w, 0f),
                ),
                cornerRadius = CornerRadius(corner.toPx()),
            )
        },
    )
}

private val SKELETON_HEIGHT = 112.dp
internal val SkeletonBase = Color(0xFFE9E9EB)
internal val SkeletonHighlight = Color(0xFFF6F6F7)

@Composable
fun NativeAdBar(ad: NativeAdHandle, modifier: Modifier = Modifier) {
    val handle = ad as? GmaNativeAdHandle ?: return
    key(handle) {
        Column(
            modifier
                .fillMaxWidth()
                .background(colorResource(R.color.native_ad_background))
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { context ->
                    LayoutInflater.from(context).inflate(R.layout.admob_native_hundred, null, false) as NativeAdView
                },
                update = { view -> bind(view, handle.ad) },
            )
        }
    }
}

/** Binds [ad] to the inflated layout (shared by Home [NativeAdBar] and the game-screen [GameNativeAdSlot]; missing asset views are skipped). */
internal fun bind(view: NativeAdView, ad: NativeAd) {
    // update{} can run again (recomposition); register once per ad.
    if (view.tag === ad) return

    val icon = view.findViewById<ImageView?>(R.id.ad_app_icon)
    val headline = view.findViewById<TextView?>(R.id.ad_headline)
    val body = view.findViewById<TextView?>(R.id.ad_body)
    val cta = view.findViewById<TextView?>(R.id.ad_call_to_action)

    headline?.text = ad.headline

    val bodyText = ad.body
    body?.text = bodyText
    body?.visibility = if (bodyText.isNullOrEmpty()) View.GONE else View.VISIBLE

    val iconDrawable = ad.icon?.drawable
    icon?.setImageDrawable(iconDrawable)
    icon?.visibility = if (iconDrawable == null) View.INVISIBLE else View.VISIBLE

    val ctaText = ad.callToAction
    cta?.text = ctaText
    cta?.visibility = if (ctaText.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE

    view.headlineView = headline
    view.bodyView = body
    view.callToActionView = cta
    view.iconView = icon
    // No MediaView in this layout. The "Ad" badge (ad_attribute_txtv) is static text, always visible.
    view.registerNativeAd(ad, null)
    view.tag = ad
}
