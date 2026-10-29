package com.threetech.catpuzzle.domain.startup

/**
 * Post-splash startup steps, in canonical order (ordinal = order). The in-app splash always comes first
 * and is not a step: it hands over to [StartFlow.first].
 *
 * (system splash -> in-app splash [UMP consent + MobileAds.initialize run here, every launch] -> WELCOME -> NOTIFICATION -> TUTORIAL -> HOME).
 * Consent is not a step: the splash gates on it (see SplashTiming / SplashViewModel), so it precedes every step here.
 */
enum class StartStep { WELCOME, NOTIFICATION, TUTORIAL, HOME }

/** Snapshot of what is already done, read once per process. */
data class StartInputs(
    val termsAccepted: Boolean,
    val notificationPromptAsked: Boolean,
    val tutorialDone: Boolean,
    val sdkInt: Int,
)

/** Android API level source (domain stays free of android.os.Build). */
fun interface SdkLevelProvider {
    fun sdkInt(): Int
}

/** Pure start-flow resolver. */
object StartFlow {
    /** POST_NOTIFICATIONS is a runtime permission from API 33 (Android 13). */
    const val NOTIFICATION_MIN_SDK = 33

    fun required(step: StartStep, inputs: StartInputs): Boolean = when (step) {
        StartStep.WELCOME -> !inputs.termsAccepted
        StartStep.NOTIFICATION -> !inputs.notificationPromptAsked && inputs.sdkInt >= NOTIFICATION_MIN_SDK
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
