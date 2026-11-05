package com.tm.meow.sudoku.puzzle.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.ui.consent.PrivacyOptionsViewModel
import com.tm.meow.sudoku.puzzle.ui.screens.game.SettingToggle
import com.tm.meow.sudoku.puzzle.util.FeedbackEmail
import com.tm.meow.sudoku.puzzle.util.LegalLinks
import kotlinx.coroutines.launch

/**
 * The only place that touches [SettingsViewModel] / [PrivacyOptionsViewModel]: collects state, wires the feedback and
 * legal-link seams, then hands plain state and lambdas to the stateless [SettingsScreen].
 *
 * Also owns the notification permission gate for the two reminder toggles: they show OFF while the OS blocks
 * notifications (checked on every resume, persisted values are not rewritten), and turning one ON asks for
 * POST_NOTIFICATIONS first. A permanent denial (system shows no dialog) or app-level block gets a snackbar with an
 * "Open settings" action to the app notification settings.
 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onHowToPlay: () -> Unit,
    onAbout: () -> Unit,
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
    val blockedText = stringResource(R.string.settings_notifications_blocked)
    val openSettingsLabel = stringResource(R.string.settings_notifications_open)

    var notificationsOn by remember { mutableStateOf(notificationsEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        notificationsOn = notificationsEnabled(context)
        onPauseOrDispose { }
    }
    var pending by rememberSaveable { mutableStateOf<SettingToggle?>(null) }

    fun toast(text: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(text)
        }
    }

    fun offerSystemSettings() {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(blockedText, actionLabel = openSettingsLabel)
            if (result == SnackbarResult.ActionPerformed) openAppNotificationSettings(context)
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsOn = notificationsEnabled(context)
        val toggle = pending
        pending = null
        if (granted && notificationsOn) {
            if (toggle != null) viewModel.onSettingChanged(toggle, true)
        } else {
            // Denied: stays OFF. No rationale left = permanently denied (the system showed no dialog): point to settings.
            val activity = context.findActivity()
            val canAskAgain = !granted && activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
            if (granted || !canAskAgain) offerSystemSettings()
        }
    }

    fun onSettingChanged(toggle: SettingToggle, enabled: Boolean) {
        val isReminder = toggle == SettingToggle.STREAK_REMINDERS || toggle == SettingToggle.DAILY_REMINDERS ||
            toggle == SettingToggle.SUDOKU_REMINDERS
        if (!isReminder || !enabled || notificationsOn) {
            viewModel.onSettingChanged(toggle, enabled)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pending = toggle
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            offerSystemSettings()
        }
    }

    val shown = if (notificationsOn) {
        settings
    } else {
        settings.copy(streakReminders = false, dailyReminders = false, sudokuReminders = false)
    }

    SettingsScreen(
        settings = shown,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onSettingChanged = ::onSettingChanged,
        onFeedback = { if (!FeedbackEmail.open(context, feedbackSubject)) toast(noEmailApp) },
        onPrivacyPolicy = { if (!LegalLinks.open(context, LegalLinks.PRIVACY_URL)) toast(linkFailed) },
        onHowToPlay = onHowToPlay,
        onAbout = onAbout,
        modifier = modifier,
        onPrivacyOptions = if (privacyRequired) privacyViewModel::open else null,
    )
}

/** True when the OS lets the app post (API 33+: runtime permission; below: app-level switch). */
private fun notificationsEnabled(context: Context): Boolean = try {
    NotificationManagerCompat.from(context).areNotificationsEnabled()
} catch (t: Throwable) {
    false
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (t: Throwable) {
        // no handler: nothing to do
    }
}
