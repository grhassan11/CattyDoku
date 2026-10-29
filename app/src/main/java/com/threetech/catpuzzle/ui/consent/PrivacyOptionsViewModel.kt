package com.threetech.catpuzzle.ui.consent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threetech.catpuzzle.domain.usecase.ObserveConsentState
import com.threetech.catpuzzle.domain.usecase.ShowPrivacyOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Drives the Settings "Privacy settings" entry (Home + Game share it). Shown only when UMP requires it. */
@HiltViewModel
class PrivacyOptionsViewModel @Inject constructor(
    observeConsentState: ObserveConsentState,
    private val showPrivacyOptions: ShowPrivacyOptions,
) : ViewModel() {

    val required: StateFlow<Boolean> = observeConsentState()
        .map { it.privacyOptionsRequired }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun open() {
        viewModelScope.launch {
            try {
                showPrivacyOptions()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // fail open
            }
        }
    }
}
