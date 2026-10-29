package com.threetech.catpuzzle.domain.tutorial

/** Test helper: plays the script the way the gesture layer would report a correct run. */
object TutorialPlayer {

    fun correctActions(step: TutorialStep): List<TutorialAction> = when (step.expected) {
        ExpectedAction.CONTINUE -> listOf(TutorialAction.Continue)
        ExpectedAction.TAP_X -> step.targets.map { TutorialAction.Tap(it) }
        ExpectedAction.SWIPE_X -> listOf(TutorialAction.Drag(step.targets))
        // The resolver reports the first press of a double-tap as a Tap, then the DoubleTap.
        ExpectedAction.DOUBLE_TAP_CAT -> step.targets.flatMap { listOf(TutorialAction.Tap(it), TutorialAction.DoubleTap(it)) }
        ExpectedAction.HINT_BUTTON -> listOf(TutorialAction.UseHint)
        ExpectedAction.CAT_BUTTON -> listOf(TutorialAction.UseCatBooster)
    }

    /** Play the current step correctly; returns the state after it. */
    fun playStep(engine: TutorialEngine, state: TutorialState): TutorialState {
        var s = state
        for (a in correctActions(engine.step(state))) s = engine.apply(s, a).state
        return s
    }

    /** Play correctly until [stepIndex] is the current step (or the tutorial completed). */
    fun playUntil(engine: TutorialEngine, stepIndex: Int, from: TutorialState = TutorialState()): TutorialState {
        var s = from
        while (!s.completed && s.stepIndex < stepIndex) s = playStep(engine, s)
        return s
    }
}
