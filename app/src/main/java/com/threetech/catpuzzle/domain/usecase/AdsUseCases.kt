package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.ads.AdDecision
import com.threetech.catpuzzle.domain.ads.AdLogger
import com.threetech.catpuzzle.domain.ads.AdPolicyConfig
import com.threetech.catpuzzle.domain.ads.NoopAdLogger
import com.threetech.catpuzzle.domain.ads.AdSessionTracker
import com.threetech.catpuzzle.domain.ads.AdShowResult
import com.threetech.catpuzzle.domain.ads.AdTrigger
import com.threetech.catpuzzle.domain.ads.AdsRepository
import com.threetech.catpuzzle.domain.ads.InterstitialContext
import com.threetech.catpuzzle.domain.ads.InterstitialPolicy
import com.threetech.catpuzzle.domain.billing.AdsRemovedRepository
import com.threetech.catpuzzle.domain.billing.NoAdsRemoved
import com.threetech.catpuzzle.domain.consent.ConsentRepository
import com.threetech.catpuzzle.domain.consent.ConsentState
import com.threetech.catpuzzle.domain.repository.AppFlagsRepository
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
) {
    suspend operator fun invoke() {
        safely { flags.update { it.copy(sessionAdCount = 0) } }
        safely { consent.gatherConsent() }
        if (consent.state.value.canRequestAds) safely { ads.initialize() }
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

/** Count a completed (won) non-daily level toward the every-Nth-win rule. */
class RecordLevelWon(private val tracker: AdSessionTracker) {
    operator fun invoke(isDaily: Boolean) {
        if (!isDaily) tracker.recordWin()
    }
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
                winsSinceLastAd = tracker.winsSinceLastAd,
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
            if (result == AdShowResult.SHOWN) {
                tracker.recordShown()
                safely {
                    flags.update {
                        it.copy(
                            lastInterstitialLevel = if (persistLevel) level else it.lastInterstitialLevel,
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
