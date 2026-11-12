package com.tm.meow.sudoku.puzzle.ui.screens.game

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tm.meow.sudoku.puzzle.R

@StringRes
internal fun PraiseKind.stringRes(): Int = when (this) {
    PraiseKind.NICE -> R.string.praise_nice
    PraiseKind.PURRFECT -> R.string.praise_purrfect
    PraiseKind.MEOWVELOUS -> R.string.praise_meowvelous
    PraiseKind.CLAWSOME -> R.string.praise_clawsome
    PraiseKind.CLEAR -> R.string.praise_clear
}

/** One linear 0..1 clock for the praise word. Same token-gated pattern as [PlacementFx] / [WrongMoveFx]. */
@Stable
internal class PraiseFx {
    private val t = Animatable(0f)
    private var runToken by mutableIntStateOf(0)

    suspend fun run(token: Int) {
        t.snapTo(0f)
        runToken = token
        t.animateTo(1f, tween(DURATION_MS, easing = LinearEasing))
    }

    fun time(token: Int): Float = if (runToken == token) t.value else 0f

    companion object {
        const val DURATION_MS = 900
    }
}

private val PraiseStyle = TextStyle(
    color = Color(0xFFFFB300),
    fontSize = 30.sp,
    fontWeight = FontWeight.ExtraBold,
    shadow = Shadow(Color(0xAA3E2723), Offset(0f, 4f), 8f),
)

/**
 * Short praise word near the top of the board. [token] is the placement token the [praise] belongs to: each NEW token plays once
 * (900 ms: scale-in with overshoot, hold, fade out), a token already present at first composition never plays (rotation /
 * re-entry). Skipped when system animations are off. The envelope is read in graphicsLayer, so playing never recomposes the screen.
 */
@Composable
internal fun PraiseOverlay(praise: PraiseKind?, token: Int, modifier: Modifier = Modifier) {
    val animationsOn = rememberAnimationsEnabled(null)
    val fx = remember { PraiseFx() }
    var doneToken by remember { mutableIntStateOf(token) }
    var shown by remember { mutableStateOf<PraiseKind?>(null) }
    LaunchedEffect(token) {
        if (token == doneToken) return@LaunchedEffect
        if (praise != null && animationsOn) {
            shown = praise
            fx.run(token)
        }
        doneToken = token
    }
    val kind = shown ?: return
    Box(modifier) {
        Text(
            text = stringResource(kind.stringRes()),
            style = PraiseStyle,
            modifier = Modifier.graphicsLayer {
                val p = fx.time(token)
                if (p <= 0f || p >= 1f) {
                    alpha = 0f
                } else {
                    alpha = when {
                        p < 0.15f -> p / 0.15f
                        p < 0.65f -> 1f
                        else -> 1f - (p - 0.65f) / 0.35f
                    }
                    // Scale-in 0.6 -> 1.12 -> 1.0 over the first 30 %, then a slow drift up.
                    val s = when {
                        p < 0.2f -> 0.6f + 0.52f * (p / 0.2f)
                        p < 0.3f -> 1.12f - 0.12f * ((p - 0.2f) / 0.1f)
                        else -> 1f
                    }
                    scaleX = s
                    scaleY = s
                    translationY = -p * 14.dp.toPx()
                }
            },
        )
    }
}
