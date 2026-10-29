package com.threetech.catpuzzle.ui.screens.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.threetech.catpuzzle.ui.components.LocalAudioController
import com.threetech.catpuzzle.ui.components.MouseIcon
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Mouse booster travel animation (UI only). The X marks are already in the game state (applied, saved and charged
 * by the ViewModel); this overlay only flies a mouse sprite from its booster button over each target tile, calls
 * [onReveal] as it lands on each one (the screen then un-hides that tile's X), and finally flies back.
 *
 * Input is blocked by the ViewModel while the run is active. [onDone] fires only when the whole sequence finished;
 * if the composition leaves mid-run the effect is cancelled and the ViewModel clears the run itself
 * (app stop / new load), so the applied state is never lost.
 *
 * Overlay hit-testing: no pointer modifiers, so touches fall through to the screen below.
 */
@Composable
internal fun MouseTravelOverlay(
    run: MouseRunUi,
    boardSize: Int,
    boardGeo: State<BoardGeo?>,
    buttonBounds: State<Rect?>,
    sfxOn: Boolean,
    onReveal: (Int) -> Unit,
    onDone: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val audio = LocalAudioController.current
    val origin = remember { mutableStateOf(Offset.Zero) }
    val pos = remember(run.id) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scale = remember(run.id) { Animatable(1f) }
    val visible = remember(run.id) { mutableStateOf(false) }
    val revealCb = rememberUpdatedState(onReveal)
    val doneCb = rememberUpdatedState(onDone)
    val sfx = rememberUpdatedState(sfxOn)
    val iconPx = with(density) { BOOSTER_ICON_DP.dp.toPx() }

    LaunchedEffect(run.id) {
        withFrameNanos { } // first layout pass done: overlay origin is known
        val geo = boardGeo.value
        val btn = buttonBounds.value
        if (geo == null || btn == null) {
            // No geometry (should not happen): skip the flight, show everything, release input.
            revealCb.value(run.cells.size)
            doneCb.value(run.id)
            return@LaunchedEffect
        }
        val m = geo.metrics
        val pad = with(density) { m.cardPaddingDp.dp.toPx() }
        val cell = with(density) { m.cellDp.dp.toPx() }
        val pitch = cell + with(density) { m.gapDp.dp.toPx() }
        val start = btn.center - origin.value
        val tileScale = (cell * 0.85f / iconPx).coerceAtLeast(1f)

        pos.snapTo(start)
        scale.snapTo(1f)
        visible.value = true
        delay(80)
        for ((i, p) in run.cells.withIndex()) {
            val target = Offset(
                geo.bounds.left + pad + p.col * pitch + cell / 2f,
                geo.bounds.top + pad + p.row * pitch + cell / 2f,
            ) - origin.value
            coroutineScope {
                launch { scale.animateTo(tileScale, tween(HOP_MS, easing = FastOutSlowInEasing)) }
                pos.animateTo(target, tween(HOP_MS, easing = FastOutSlowInEasing))
            }
            revealCb.value(i + 1)
            if (sfx.value) audio?.playSfx(SfxKind.CROSS)
            delay(DWELL_MS)
        }
        coroutineScope {
            launch { scale.animateTo(1f, tween(RETURN_MS, easing = FastOutSlowInEasing)) }
            pos.animateTo(start, tween(RETURN_MS, easing = FastOutSlowInEasing))
        }
        visible.value = false
        doneCb.value(run.id)
    }

    Box(modifier.fillMaxSize().onGloballyPositioned { origin.value = it.positionInRoot() }) {
        if (visible.value) {
            MouseIcon(
                Modifier.graphicsLayer {
                    val half = iconPx / 2f
                    translationX = pos.value.x - half
                    translationY = pos.value.y - half
                    scaleX = scale.value
                    scaleY = scale.value
                },
                size = BOOSTER_ICON_DP.dp,
            )
        }
    }
}

private const val HOP_MS = 320
private const val DWELL_MS = 110L
private const val RETURN_MS = 420
