package com.threetech.catpuzzle.ui.screens.game

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.threetech.catpuzzle.domain.engine.IdleMeowPolicy
import com.threetech.catpuzzle.domain.engine.IdleNudgePolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.random.Random
import kotlin.math.sin

/**
 * Counts presses / releases anywhere on the game screen. The count is only ever read inside a snapshotFlow, so touching
 * it never recomposes anything.
 */
internal class InteractionClock {
    var count by mutableIntStateOf(0)
        private set

    fun touch() {
        count++
    }
}

/** Observes the Initial pass without consuming: gestures reach their real targets untouched. */
internal fun Modifier.trackInteractions(clock: InteractionClock): Modifier = pointerInput(clock) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type == PointerEventType.Press || event.type == PointerEventType.Release) clock.touch()
        }
    }
}

/**
 * Idle nudge flag. Runs the [IdleNudgePolicy] cycle (idle delay -> nudge -> rest -> repeat) while [eligible], restarting on
 * every interaction from [clock]. Off when ineligible or when the system animator scale is 0 (reduced motion).
 * Callers read the returned state lazily (inside a lambda) so flips never recompose the board.
 */
@Composable
internal fun rememberIdleNudge(eligible: Boolean, clock: InteractionClock): State<Boolean> {
    val nudging = remember { mutableStateOf(false) }
    val animationsOn = rememberAnimationsEnabled(eligible)
    val active = eligible && animationsOn
    LaunchedEffect(active, clock) {
        nudging.value = false
        if (!active) return@LaunchedEffect
        snapshotFlow { clock.count }.collectLatest {
            nudging.value = false
            while (true) {
                delay(IdleNudgePolicy.IDLE_DELAY_MS)
                nudging.value = true
                delay(IdleNudgePolicy.NUDGE_DURATION_MS)
                nudging.value = false
            }
        }
    }
    return nudging
}

/**
 * Idle meow: while [eligible], after the idle delay (when the nudge starts) rolls [IdleMeowPolicy] at random intervals and
 * calls [onMeow]. Any interaction from [clock] cancels and restarts the schedule. Last-meow time survives restarts so the
 * min gap holds across input resets.
 */
@Composable
internal fun RememberIdleMeow(eligible: Boolean, clock: InteractionClock, onMeow: () -> Unit) {
    val latest = rememberUpdatedState(onMeow)
    val lastMeowAt = remember { longArrayOf(-1L) }
    LaunchedEffect(eligible, clock) {
        if (!eligible) return@LaunchedEffect
        val random = Random.Default
        snapshotFlow { clock.count }.collectLatest {
            delay(IdleNudgePolicy.IDLE_DELAY_MS)
            while (true) {
                delay(IdleMeowPolicy.nextDelayMs(random))
                val now = SystemClock.elapsedRealtime()
                val last = lastMeowAt[0].takeIf { it >= 0L }
                if (IdleMeowPolicy.shouldMeow(random, now, last)) {
                    lastMeowAt[0] = now
                    latest.value()
                }
            }
        }
    }
}

/** False when the system animator duration scale is 0 (user disabled animations). Re-read when [key] changes. */
@Composable
private fun rememberAnimationsEnabled(key: Any?): Boolean {
    val context = LocalContext.current
    return remember(context, key) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        }.getOrDefault(true)
    }
}

private const val NUDGE_PERIOD_MS = 1_600
private const val WIGGLE_PORTION = 0.5f
private const val WIGGLE_DEGREES = 12f
private const val PULSE_SCALE = 0.14f

/**
 * Wraps a booster icon. While [active] it wiggles + pulses in a loop; the transform runs in graphicsLayer (draw phase),
 * so it neither recomposes nor relayouts, and the button's circle / drop shadow stay untouched. When [active] flips off
 * the icon snaps back to rest. Frame-by-frame the infinite transition only exists while nudging.
 */
@Composable
internal fun NudgeIcon(active: () -> Boolean, content: @Composable () -> Unit) {
    val phase: State<Float>? = if (active()) {
        rememberInfiniteTransition(label = "boosterNudge").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(NUDGE_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "boosterNudgePhase",
        )
    } else {
        null
    }
    Box(
        Modifier.graphicsLayer {
            val p = phase?.value ?: 0f
            if (p > 0f && p < WIGGLE_PORTION) {
                val u = p / WIGGLE_PORTION
                rotationZ = WIGGLE_DEGREES * sin(u * 4f * PI.toFloat()) * (1f - u)
                val s = 1f + PULSE_SCALE * sin(u * PI.toFloat())
                scaleX = s
                scaleY = s
            }
        },
        contentAlignment = Alignment.Center,
    ) { content() }
}
