package com.threetech.catpuzzle.data.consent

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.threetech.catpuzzle.BuildConfig
import com.threetech.catpuzzle.data.platform.CurrentActivityProvider
import com.threetech.catpuzzle.domain.ads.AdSessionTracker
import com.threetech.catpuzzle.domain.consent.ConsentRepository
import com.threetech.catpuzzle.domain.consent.ConsentState
import com.threetech.catpuzzle.domain.consent.TelemetryConsentPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
) : ConsentRepository {

    private val info: ConsentInformation by lazy { UserMessagingPlatform.getConsentInformation(context) }

    private val _state = MutableStateFlow(ConsentState())
    override val state: StateFlow<ConsentState> = _state

    override suspend fun gatherConsent() {
        try {
            val activity = activities.current()
            if (activity == null) {
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
                        UserMessagingPlatform.showPrivacyOptionsForm(activity) { _ ->
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
        } finally {
            publish()
        }
    }

    private fun publish() {
        _state.value = try {
            val canRequestAds = info.canRequestAds()
            ConsentState(
                canRequestAds = canRequestAds,
                analyticsAllowed = TelemetryConsentPolicy.analyticsAllowed(canRequestAds, readTcfPurposes()),
                privacyOptionsRequired = info.privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED,
            )
        } catch (t: Throwable) {
            ConsentState()
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
            { _ -> if (cont.isActive) cont.resume(false) },
        )
    }

    private suspend fun loadAndShowFormIfRequired(activity: Activity) {
        tracker.consentFormShowing = true
        try {
            suspendCancellableCoroutine { cont ->
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
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
