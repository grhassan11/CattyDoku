package com.tm.meow.sudoku.puzzle.domain.ads

/** Ad formats the app can show. [REWARDED] is never suppressed by a purchase. */
enum class AdFormat { NATIVE_HOME, NATIVE_GAME, INTERSTITIAL, APP_OPEN, REWARDED }

/**
 * The ONE early-level rule for rewarded prompts (Levels interstitials ignore it). App-open, Home native and the game native slots (Levels + Sudoku) have no level gate.
 * Early-level rule: levels 1..[AD_FREE_THROUGH_LEVEL] are ad-free, ads start at level [FIRST_AD_LEVEL]. Only [isAdFree] callers (rewarded prompt
 * preload) apply it; [adsAllowed] has no production caller.
 * Also holds the purchase rule ([isSuppressedByPurchase]). Pure, no clock, no Android. Callers pass the level they care about (level just completed / being played / highest
 * level reached from progress state). Level <= 0 (nothing played yet, or daily with no level) counts as ad-free.
 */
object AdFreePolicy {
    const val AD_FREE_THROUGH_LEVEL = 9
    const val FIRST_AD_LEVEL = AD_FREE_THROUGH_LEVEL + 1

    fun isAdFree(level: Int, throughLevel: Int = AD_FREE_THROUGH_LEVEL): Boolean = level <= throughLevel

    fun adsAllowed(level: Int, throughLevel: Int = AD_FREE_THROUGH_LEVEL): Boolean = !isAdFree(level, throughLevel)

    /**
     * The ONE purchase rule for every format: a bought "Remove ads" (active monthly OR forever) suppresses native (home + game slot),
     * interstitial and app-open. [AdFormat.REWARDED] is NEVER suppressed: it is opt-in value for the player, not an ad shown
     * to them. Callers must also skip preloading / loading a suppressed format.
     */
    fun isSuppressedByPurchase(format: AdFormat, adsRemoved: Boolean): Boolean =
        adsRemoved && format != AdFormat.REWARDED
}
