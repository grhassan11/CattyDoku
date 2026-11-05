package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsEvent
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.analytics.NoopAnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.ads.AdDecision
import com.tm.meow.sudoku.puzzle.domain.ads.AdLogger
import com.tm.meow.sudoku.puzzle.domain.ads.AdPolicyConfig
import com.tm.meow.sudoku.puzzle.domain.ads.NoopAdLogger
import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.ads.AdShowResult
import com.tm.meow.sudoku.puzzle.domain.ads.AdTrigger
import com.tm.meow.sudoku.puzzle.domain.ads.AdsRepository
import com.tm.meow.sudoku.puzzle.domain.ads.InterstitialContext
import com.tm.meow.sudoku.puzzle.domain.ads.InterstitialPolicy
import com.tm.meow.sudoku.puzzle.domain.billing.AdsRemovedRepository
import com.tm.meow.sudoku.puzzle.domain.billing.NoAdsRemoved
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentRepository
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentState
import com.tm.meow.sudoku.puzzle.domain.repository.AppFlagsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Startup step: reset the persisted per-session ad counter (policy: sessionAdCount resets every process start),
 * gather consent (UMP), THEN initialise ads, only if consent allows. Order is the contract (consent before init).
 * Never throws: consent / ads problems must not block the app (fail open to "continue", ads stay off).
 */
class GatherConsentAndInitAds(
    private val consent: ConsentRepository,
    private val ads: AdsRepository,
    private val flags: AppFlagsRepository,
    private val tracker: AdSessionTracker? = null,
) {
    suspend operator fun invoke() {
        tracker?.startupGatherStarted = true
        safely { flags.update { it.copy(sessionAdCount = 0) } }
        safely { consent.gatherConsent() }
        if (consent.state.value.canRequestAds) safely { ads.initialize() }
    }
}

/**
 * Fallback for a launch that skips the in-app splash: after process death the nav back stack is restored (e.g. Home / Game)
 * and the splash, which normally runs [GatherConsentAndInitAds], is not on it. Runs the same startup step (UMP consent, then
 * ads init, then the consent-gated Remote Config fetch) unless it already started this process. Idempotent; never throws.
 * App Open stays gated: [AdSessionTracker.startFlowComplete] is set by Home and AppOpenPolicy also needs consent + SDK init.
 */
class EnsureConsentAndAdsInit(
    private val gather: GatherConsentAndInitAds,
    private val refreshRemoteConfig: RefreshRemoteConfig,
    private val tracker: AdSessionTracker,
) {
    /** True when this call ran the startup step, false when it had already started. */
    suspend operator fun invoke(): Boolean {
        if (tracker.startupGatherStarted) return false
        tracker.startupGatherStarted = true // claim before the first suspension: no second caller can slip in
        safely { gather() }
        safely { refreshRemoteConfig() }
        return true
    }
}

/** True while the UMP form is on screen. The splash stops its hard-cap clock meanwhile (the user is reading). */
class IsConsentFormShowing(private val tracker: AdSessionTracker) {
    operator fun invoke(): Boolean = tracker.consentFormShowing
}

class ObserveConsentState(private val consent: ConsentRepository) {
    operator fun invoke(): Flow<ConsentState> = consent.state
}

/** Settings "Privacy settings": reopen the UMP form; if that grants ads, bring the SDK up. */
class ShowPrivacyOptions(
    private val consent: ConsentRepository,
    private val ads: AdsRepository,
) {
    suspend operator fun invoke() {
        safely { consent.showPrivacyOptions() }
        if (consent.state.value.canRequestAds) safely { ads.initialize() }
    }
}

/** Count a completed (won) non-daily level (diagnostic win counter; Levels cadence is level-number based). */
class RecordLevelWon(private val tracker: AdSessionTracker) {
    operator fun invoke(isDaily: Boolean) {
        if (!isDaily) tracker.recordWin()
    }
}

/** Count a completed Sudoku win (own diagnostic counter; Levels counter untouched). */
class RecordSudokuWon(private val tracker: AdSessionTracker) {
    operator fun invoke() = tracker.recordSudokuWin()
}

/**
 * Decide with the pure [InterstitialPolicy], show if allowed, record. Returns true when an ad was shown.
 * Always returns (fail open): the caller continues navigation whatever happens.
 * [persistLevel] false (Sudoku, which has no level numbers) leaves the persisted Queens lastInterstitialLevel untouched.
 */
class ShowInterstitialIfAllowed(
    private val consent: ConsentRepository,
    private val ads: AdsRepository,
    private val tracker: AdSessionTracker,
    private val flags: AppFlagsRepository,
    private val config: AdPolicyConfig,
    private val adsRemoved: AdsRemovedRepository = NoAdsRemoved,
    private val logger: AdLogger = NoopAdLogger,
    private val analytics: AnalyticsLogger = NoopAnalyticsLogger,
) {
    suspend operator fun invoke(
        trigger: AdTrigger,
        level: Int,
        isDaily: Boolean = false,
        persistLevel: Boolean = true,
    ): Boolean {
        return try {
            val f = flags.flags.first()
            val ctx = InterstitialContext(
                trigger = trigger,
                canRequestAds = consent.state.value.canRequestAds,
                level = level,
                isDaily = isDaily,
                winsSinceLastAd = if (trigger == AdTrigger.SUDOKU_WON_CONTINUE || trigger == AdTrigger.SUDOKU_RESTART) tracker.sudokuWinsSinceLastAd else tracker.winsSinceLastAd,
                nowMs = tracker.now(),
                sessionStartMs = tracker.sessionStartMs,
                lastAdShownMs = tracker.lastAdShownMs,
                lastInterstitialLevel = f.lastInterstitialLevel,
                adsShownThisSession = tracker.adsShown,
                adsRemoved = adsRemoved.adsRemoved.value,
            )
            val decision = InterstitialPolicy.decide(ctx, config)
            val inputs = "trigger=${ctx.trigger} level=${ctx.level} daily=${ctx.isDaily} wins=${ctx.winsSinceLastAd} " +
                "sessionAgeMs=${ctx.nowMs - ctx.sessionStartMs} sinceLastAdMs=${ctx.lastAdShownMs?.let { ctx.nowMs - it }} " +
                "shownThisSession=${ctx.adsShownThisSession} lastInterstitialLevel=${ctx.lastInterstitialLevel} " +
                "consent=${ctx.canRequestAds} adsRemoved=${ctx.adsRemoved}"
            if (decision != AdDecision.Show) {
                logger.log("decide -> SKIP ${(decision as? AdDecision.Skip)?.reason} | $inputs")
                return false
            }
            logger.log("decide -> SHOW | $inputs")
            // Blocks app-open while the interstitial (and its load wait) is in flight.
            tracker.fullScreenAdShowing = true
            val result = try {
                ads.showIfReady(config.loadWaitTimeoutMs)
            } finally {
                tracker.fullScreenAdShowing = false
            }
            logger.log("showIfReady -> $result (level=$level)")
            analytics.log(AnalyticsEvent.AdInterstitial(if (result == AdShowResult.SHOWN) "shown" else "failed", trigger.name.lowercase()))
            if (result == AdShowResult.SHOWN) {
                tracker.recordShown()
                safely {
                    flags.update {
                        it.copy(
                            // Only a WIN ad marks the level as "had its ad". A Retry ad on a lost level must not block the later win ad.
                            lastInterstitialLevel = if (persistLevel && trigger == AdTrigger.LEVEL_WON_CONTINUE) level else it.lastInterstitialLevel,
                            sessionAdCount = it.sessionAdCount + 1,
                        )
                    }
                }
                true
            } else {
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            false
        }
    }
}

private suspend inline fun safely(block: () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        // fail open
    }
}
