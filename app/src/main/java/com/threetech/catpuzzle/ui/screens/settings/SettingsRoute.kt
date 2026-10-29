package com.threetech.catpuzzle.ui.screens.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.threetech.catpuzzle.BuildConfig
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.ui.consent.PrivacyOptionsViewModel
import com.threetech.catpuzzle.util.FeedbackEmail
import com.threetech.catpuzzle.util.LegalLinks
import kotlinx.coroutines.launch

/**
 * The only place that touches [SettingsViewModel] / [PrivacyOptionsViewModel]: collects state, wires the feedback and
 * legal-link seams, then hands plain state and lambdas to the stateless [SettingsScreen].
 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    privacyViewModel: PrivacyOptionsViewModel = hiltViewModel(),
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val privacyRequired by privacyViewModel.required.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val feedbackSubject = stringResource(R.string.settings_feedback_subject, stringResource(R.string.app_name))
    val noEmailApp = stringResource(R.string.settings_feedback_no_app)
    val linkFailed = stringResource(R.string.shop_link_failed)

    fun toast(text: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(text)
        }
    }

    SettingsScreen(
        settings = settings,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onSettingChanged = viewModel::onSettingChanged,
        onFeedback = { if (!FeedbackEmail.open(context, feedbackSubject)) toast(noEmailApp) },
        onPrivacyPolicy = { if (!LegalLinks.open(context, LegalLinks.PRIVACY_URL)) toast(linkFailed) },
        versionName = BuildConfig.VERSION_NAME,
        modifier = modifier,
        onPrivacyOptions = if (privacyRequired) privacyViewModel::open else null,
    )
}
