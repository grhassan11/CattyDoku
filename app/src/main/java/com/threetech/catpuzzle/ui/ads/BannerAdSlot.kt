package com.threetech.catpuzzle.ui.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.ads.GmaBannerAdHandle
import com.threetech.catpuzzle.domain.ads.BannerAdHandle
import com.threetech.catpuzzle.ui.theme.GameColors

/**
 * Sticky anchored-adaptive banner strip for the bottom of the game screen (above the navigation bar: the caller applies
 * the nav-bar inset). The slot has a FIXED height (the adaptive banner height for the screen width) whether or not [ad]
 * is loaded, so the board never resizes when the ad arrives, fails, or is hidden behind a dialog. Full width.
 * [ad] is owned by AdsViewModel; never destroyed here. Hosting detaches the banner view from any previous parent first.
 */
@Composable
fun BannerAdSlot(ad: BannerAdHandle?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val heightDp = remember(widthDp, context) {
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp).height
    }
    val description = stringResource(R.string.ad_slot_description)
    Box(
        modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .background(GameColors.ScreenBackground)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val handle = ad as? GmaBannerAdHandle
        if (handle != null) key(handle) { BannerHost(handle) }
    }
}

@Composable
private fun BannerHost(handle: GmaBannerAdHandle) {
    val context = LocalContext.current
    val container = remember(handle) { FrameLayout(context) }
    DisposableEffect(handle, container) {
        // Next-Gen BannerAd.getView requires an Activity; no Activity -> leave the fixed-height slot empty.
        val activity = context.findActivity() ?: return@DisposableEffect onDispose { }
        val view = handle.ad.getView(activity)
        (view.parent as? ViewGroup)?.removeView(view)
        container.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        onDispose { container.removeAllViews() }
    }
    AndroidView(factory = { container }, modifier = Modifier.fillMaxWidth())
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
