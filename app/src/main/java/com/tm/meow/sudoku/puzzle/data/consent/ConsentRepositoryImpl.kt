package com.tm.meow.sudoku.puzzle.data.consent

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.tm.meow.sudoku.puzzle.BuildConfig
import com.tm.meow.sudoku.puzzle.data.platform.CurrentActivityProvider
import com.tm.meow.sudoku.puzzle.di.IoDispatcher
import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentRepository
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentState
import com.tm.meow.sudoku.puzzle.domain.consent.TelemetryConsentPolicy
import com.tm.meow.sudoku.puzzle.domain.crash.CrashReporter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Google UMP wrapper (com.google.android.ump). Needs a foreground Activity (via [CurrentActivityProvider]).
 * Fails open: any error / timeout / missing activity returns normally; [state] then shows only what UMP already
 * knows (cached canRequestAds), so ads stay off unless consent really allows them.
 * Debug geography / test device come from BuildConfig and are honoured in debug builds only.
 */
@Singleton
class ConsentRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activities: CurrentActivityProvider,
    private val tracker: AdSessionTracker,
    private val crash: CrashReporter,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ConsentRepository {

    /** The "no foreground Activity" branch is logged once per process (it can repeat on every splash retry). */
    private val noActivityLogged = AtomicBoolean(false)

    private val info: ConsentInformation by lazy { UserMessagingPlatform.getConsentInformation(context) }

    private val _state = MutableStateFlow(ConsentState())
    override val state: StateFlow<ConsentState> = _state

    override suspend fun gatherConsent() {
        try {
            val activity = activities.current()
            if (activity == null) {
                if (noActivityLogged.compareAndSet(false, true)) {
                    crash.setCustomKey("consent_no_activity", true)
                    crash.log("consent.gather: no foreground Activity, UMP skipped (ads stay as cached)")
                }
                publish()
                return
            }
            withContext(Dispatchers.Main.immediate) {
                val updated = withTimeoutOrNull(UPDATE_TIMEOUT_MS) { requestInfoUpdate(activity) } ?: false
                if (updated) loadAndShowFormIfRequired(activity)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // fail open
            crash.recordNonFatal(t, "consent.gather")
        } finally {
            publish()
        }
    }

    override suspend fun showPrivacyOptions() {
        try {
            val activity = activities.current() ?: return
            withContext(Dispatchers.Main.immediate) {
                // Blocks app-open while the form is up.
                tracker.consentFormShowing = true
                try {
                    suspendCancellableCoroutine { cont ->
                        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
                            if (error != null) crash.log("consent.privacyOptions form error ${error.errorCode}")
                            if (cont.isActive) cont.resume(Unit)
                        }
                    }
                } finally {
                    tracker.consentFormShowing = false
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // fail open
            crash.recordNonFatal(t, "consent.privacyOptions")
        } finally {
            publish()
        }
    }

    /**
     * Reads UMP / SharedPreferences state on the IO dispatcher (first SharedPreferences load is disk I/O; callers are on Main).
     * NonCancellable: runs from `finally` blocks, so a cancelled caller still leaves the state published.
     */
    private suspend fun publish() {
        _state.value = withContext(NonCancellable + io) {
            try {
                val canRequestAds = info.canRequestAds()
                val purposes = readTcfPurposes()
                val optionsRequired = info.privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                val usRegulated = TelemetryConsentPolicy.usRegulatedNoTcf(optionsRequired, purposes)
                ConsentState(
                    canRequestAds = canRequestAds,
                    analyticsAllowed = TelemetryConsentPolicy.analyticsAllowed(canRequestAds, purposes),
                    adStorage = TelemetryConsentPolicy.adStorageAllowed(canRequestAds, purposes, usRegulated),
                    adUserData = TelemetryConsentPolicy.adUserDataAllowed(canRequestAds, purposes, usRegulated),
                    adPersonalization = TelemetryConsentPolicy.adPersonalizationAllowed(canRequestAds, purposes, usRegulated),
                    privacyOptionsRequired = optionsRequired,
                )
            } catch (t: Throwable) {
                crash.recordNonFatal(t, "consent.publish")
                ConsentState()
            }
        }
    }

    /** UMP writes the TCF string into the app's default SharedPreferences. Null = no TCF region / unreadable. */
    private fun readTcfPurposes(): String? = try {
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            .getString("IABTCF_PurposeConsents", null)
    } catch (t: Throwable) {
        null
    }

    private suspend fun requestInfoUpdate(activity: Activity): Boolean = suspendCancellableCoroutine { cont ->
        info.requestConsentInfoUpdate(
            activity,
            buildParams(activity),
            { if (cont.isActive) cont.resume(true) },
            { error ->
                crash.log("consent.infoUpdate failed ${error.errorCode}")
                if (cont.isActive) cont.resume(false)
            },
        )
    }

    private suspend fun loadAndShowFormIfRequired(activity: Activity) {
        tracker.consentFormShowing = true
        try {
            suspendCancellableCoroutine { cont ->
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) crash.log("consent.form failed ${error.errorCode}")
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        } finally {
            tracker.consentFormShowing = false
        }
    }

    private fun buildParams(activity: Activity): ConsentRequestParameters {
        val builder = ConsentRequestParameters.Builder()
        if (BuildConfig.DEBUG) {
            val geography = when (BuildConfig.UMP_DEBUG_GEOGRAPHY) {
                "EEA" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
                "NOT_EEA" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_NOT_EEA
                "US_STATE" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_REGULATED_US_STATE
                else -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_DISABLED
            }
            val debug = ConsentDebugSettings.Builder(activity).setDebugGeography(geography)
            if (BuildConfig.UMP_TEST_DEVICE_HASHED_ID.isNotBlank()) {
                debug.addTestDeviceHashedId(BuildConfig.UMP_TEST_DEVICE_HASHED_ID)
            }
            builder.setConsentDebugSettings(debug.build())
        }
        return builder.build()
    }

    private companion object {
        /** Info update only. The form is never time-boxed (user may read it). Past this, app continues, ads stay as UMP cached. */
        const val UPDATE_TIMEOUT_MS = 5_000L
    }
}
