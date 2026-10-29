package com.threetech.catpuzzle.domain.startup

import com.threetech.catpuzzle.domain.model.AppFlags
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermsPolicyTest {

    @Test
    fun defaultFlagsNotAccepted() {
        assertFalse(TermsPolicy.isAccepted(AppFlags()))
    }

    @Test
    fun acceptedCurrentVersion() {
        assertTrue(TermsPolicy.isAccepted(AppFlags(termsAccepted = true, termsVersion = TermsPolicy.CURRENT_VERSION)))
    }

    @Test
    fun flagWithoutVersionReprompts() {
        assertFalse(TermsPolicy.isAccepted(AppFlags(termsAccepted = true, termsVersion = 0)))
    }

    @Test
    fun versionBumpReprompts() {
        val flags = AppFlags(termsAccepted = true, termsVersion = 1)
        assertTrue(TermsPolicy.isAccepted(flags, currentVersion = 1))
        assertFalse(TermsPolicy.isAccepted(flags, currentVersion = 2))
    }

    @Test
    fun newerStoredVersionStaysAccepted() {
        assertTrue(TermsPolicy.isAccepted(AppFlags(termsAccepted = true, termsVersion = 3), currentVersion = 2))
    }
}
