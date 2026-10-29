package com.threetech.catpuzzle.domain.rating

import com.threetech.catpuzzle.domain.model.AppFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateUsPolicyTest {

    private fun prompt(
        highest: Int = 5,
        flags: AppFlags = AppFlags(),
        startFlowComplete: Boolean = true,
        adShowing: Boolean = false,
        consentShowing: Boolean = false,
        msSinceAd: Long? = null,
    ) = RateUsPolicy.shouldPrompt(highest, flags, startFlowComplete, adShowing, consentShowing, msSinceAd)

    @Test
    fun dueOnceLevel5IsWon() {
        assertFalse(prompt(highest = 0))
        assertFalse(prompt(highest = 4))
        assertTrue(prompt(highest = 5))
        assertTrue(prompt(highest = 30))
    }

    @Test
    fun neverAgainOnceShown() {
        assertFalse(prompt(flags = AppFlags(rateUsShown = true)))
        assertFalse(prompt(flags = AppFlags(rateUsShown = true, rateUsDismissedCount = 1)))
    }

    @Test
    fun neverAgainOnceRated() {
        assertFalse(prompt(flags = AppFlags(rateUsRated = true)))
    }

    @Test
    fun heldBackBeforeStartFlowCompletes() {
        assertFalse(prompt(startFlowComplete = false))
    }

    @Test
    fun heldBackWhileFullScreenAdOrConsentFormShows() {
        assertFalse(prompt(adShowing = true))
        assertFalse(prompt(consentShowing = true))
    }

    @Test
    fun heldBackRightAfterAnAdThenAllowed() {
        assertFalse(prompt(msSinceAd = 0L))
        assertFalse(prompt(msSinceAd = RateUsPolicy.MIN_GAP_AFTER_AD_MS - 1))
        assertTrue(prompt(msSinceAd = RateUsPolicy.MIN_GAP_AFTER_AD_MS))
        assertTrue(prompt(msSinceAd = null))
    }

    @Test
    fun starsRouteToStoreListingOrSubmittedLow() {
        assertEquals(RateAction.SUBMITTED_LOW, RateUsPolicy.actionFor(1))
        assertEquals(RateAction.SUBMITTED_LOW, RateUsPolicy.actionFor(2))
        assertEquals(RateAction.SUBMITTED_LOW, RateUsPolicy.actionFor(3))
        assertEquals(RateAction.STORE_LISTING, RateUsPolicy.actionFor(4))
        assertEquals(RateAction.STORE_LISTING, RateUsPolicy.actionFor(5))
    }

    @Test
    fun outOfRangeStarsAreClamped() {
        assertEquals(RateAction.SUBMITTED_LOW, RateUsPolicy.actionFor(0))
        assertEquals(RateAction.SUBMITTED_LOW, RateUsPolicy.actionFor(-3))
        assertEquals(RateAction.STORE_LISTING, RateUsPolicy.actionFor(9))
    }
}
