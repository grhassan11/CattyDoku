package com.threetech.catpuzzle.ui.screens.notification

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * The only place that touches [NotificationPromptViewModel]. Owns the system permission launcher
 * (needs an Activity result registry). The step is only reached on API 33+ (StartFlow), so
 * POST_NOTIFICATIONS is a real runtime permission here. Granted or denied, the answer is remembered.
 */
@Composable
fun NotificationPromptRoute(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationPromptViewModel = hiltViewModel(),
) {
    val currentOnFinished by rememberUpdatedState(onFinished)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onAnswered()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                NotificationPromptEvent.Finished -> currentOnFinished()
            }
        }
    }

    NotificationPromptScreen(
        onAllow = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        onNotNow = viewModel::onAnswered,
        modifier = modifier,
    )
}
