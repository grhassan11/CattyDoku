package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.repository.AppFlagsRepository
import com.threetech.catpuzzle.domain.startup.SdkLevelProvider
import com.threetech.catpuzzle.domain.startup.StartInputs
import com.threetech.catpuzzle.domain.startup.TermsPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** One-shot snapshot that drives the start flow (see StartFlow). */
class ReadStartInputs(
    private val flags: AppFlagsRepository,
    private val sdk: SdkLevelProvider,
) {
    suspend operator fun invoke(): StartInputs {
        val f = flags.flags.first()
        return StartInputs(
            termsAccepted = TermsPolicy.isAccepted(f),
            notificationPromptAsked = f.notificationPromptAsked,
            tutorialDone = f.tutorialDone,
            sdkInt = sdk.sdkInt(),
        )
    }
}

/** True when the CURRENT terms version is accepted (an older accepted version reads false = re-prompt). */
class ObserveTermsAccepted(private val flags: AppFlagsRepository) {
    operator fun invoke(): Flow<Boolean> = flags.flags.map { TermsPolicy.isAccepted(it) }.distinctUntilChanged()
}

/** Record acceptance of the current terms version. Idempotent. */
class AcceptTerms(
    private val flags: AppFlagsRepository,
    private val version: Int = TermsPolicy.CURRENT_VERSION,
) {
    suspend operator fun invoke() {
        flags.update { it.copy(termsAccepted = true, termsVersion = version) }
    }
}

/** Remember the notification pre-prompt was answered (allowed, denied or skipped): it shows once. Idempotent. */
class MarkNotificationPromptAsked(private val flags: AppFlagsRepository) {
    suspend operator fun invoke() {
        flags.update { it.copy(notificationPromptAsked = true) }
    }
}
