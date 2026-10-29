package com.threetech.catpuzzle.domain.startup

import com.threetech.catpuzzle.domain.model.AppFlags

/** Which Terms/Privacy version the app currently requires. Bump [CURRENT_VERSION] to re-prompt everyone. */
object TermsPolicy {
    const val CURRENT_VERSION = 1

    fun isAccepted(flags: AppFlags, currentVersion: Int = CURRENT_VERSION): Boolean =
        flags.termsAccepted && flags.termsVersion >= currentVersion
}
