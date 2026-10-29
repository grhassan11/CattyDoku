package com.threetech.catpuzzle.ui.screens.rate

import android.widget.Toast
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.util.LegalLinks
import com.threetech.catpuzzle.util.StoreLinks

/**
 * Renders the Rate us dialog when [viewModel] says so and performs its one-shot follow-ups (store listing, low-star
 * toast). Place it as the LAST child of a full-screen Box so the scrim covers the screen below.
 * [onClosed] fires once each time the dialog goes from visible to hidden (submit, Not now, back): the Game route uses it
 * to continue to the next level.
 */
@Composable
fun RateUsHost(
    viewModel: RateUsViewModel,
    snackbarHostState: SnackbarHostState,
    onClosed: () -> Unit = {},
) {
    val visible by viewModel.visible.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val submitted = stringResource(R.string.rate_submitted)
    val linkFailed = stringResource(R.string.shop_link_failed)
    val latestOnClosed by rememberUpdatedState(onClosed)
    var wasVisible by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (wasVisible && !visible) latestOnClosed()
        wasVisible = visible
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                RateUsEvent.ShowSubmitted ->
                    Toast.makeText(context, submitted, Toast.LENGTH_SHORT).show()
                RateUsEvent.OpenStoreListing ->
                    if (!LegalLinks.open(context, StoreLinks.listingUrl(context.packageName))) {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(linkFailed)
                    }
            }
        }
    }

    if (visible) {
        RateUsDialog(onSubmit = viewModel::onSubmit, onDismiss = viewModel::onDismiss)
    }
}
