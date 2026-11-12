package com.tm.meow.sudoku.puzzle.domain.startup

/**
 * Post-splash startup steps, in canonical order (ordinal = order). The in-app splash always comes first
 * and is not a step: it hands over to [StartFlow.first].
 *
 * (system splash -> in-app splash [UMP consent + MobileAds.initialize run here, every launch] -> TUTORIAL -> HOME).
 * Consent is not a step: the splash gates on it (see SplashTiming / SplashViewModel), so it precedes every step here.
 * The notification permission is not a step either: Home asks once on first entry (see ShouldAskNotificationPermission).
 */
enum class StartStep { TUTORIAL, HOME }

/** Snapshot of what is already done, read once per process. */
data class StartInputs(
    val tutorialDone: Boolean,
)

/** Android API level source (domain stays free of android.os.Build). */
fun interface SdkLevelProvider {
    fun sdkInt(): Int
}

/** Pure start-flow resolver. */
object StartFlow {
    fun required(step: StartStep, inputs: StartInputs): Boolean = when (step) {
        StartStep.TUTORIAL -> !inputs.tutorialDone
        StartStep.HOME -> true
    }

    /** Remaining steps in order; always ends with [StartStep.HOME]. */
    fun steps(inputs: StartInputs): List<StartStep> = StartStep.entries.filter { required(it, inputs) }

    fun first(inputs: StartInputs): StartStep = steps(inputs).first()

    /** Next step after [current] finished (the inputs snapshot may predate [current]'s completion). */
    fun after(current: StartStep, inputs: StartInputs): StartStep =
        steps(inputs).firstOrNull { it.ordinal > current.ordinal } ?: StartStep.HOME
}
