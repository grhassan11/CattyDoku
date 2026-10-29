package com.threetech.catpuzzle.ui.screens.welcome

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.util.LegalLinks
import kotlinx.coroutines.launch

/** The only place that touches [WelcomeViewModel]. [onAccepted] runs after `terms_accepted` is saved. */
@Composable
fun WelcomeRoute(
    onAccepted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val linkFailed = stringResource(R.string.welcome_link_failed)
    val currentOnAccepted by rememberUpdatedState(onAccepted)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                WelcomeEvent.Accepted -> currentOnAccepted()
            }
        }
    }

    fun open(url: String) {
        if (!LegalLinks.open(context, url)) {
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(linkFailed)
            }
        }
    }

    WelcomeScreen(
        onAccept = viewModel::onAccept,
        onOpenTerms = { open(LegalLinks.TERMS_URL) },
        onOpenPrivacy = { open(LegalLinks.PRIVACY_URL) },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}
