package com.threetech.catpuzzle.ui.screens.shop

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.domain.billing.BillingFailure
import com.threetech.catpuzzle.domain.billing.PurchaseEvent
import com.threetech.catpuzzle.domain.billing.ShopProduct
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.util.LegalLinks
import com.threetech.catpuzzle.util.StoreLinks
import kotlinx.coroutines.launch

/**
 * The only place that touches [ShopViewModel]: collects state + one-shot events, turns events into snackbar text, wires
 * the link seams (Terms / Privacy placeholders in [LegalLinks]; subscription management = Play subscriptions page), then
 * hands plain state and lambdas to the stateless [ShopScreen].
 * [initialSection] = pack section to land on (from a booster dialog "Get X in Store"); null = top.
 */
@Composable
fun ShopRoute(
    initialSection: Booster?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShopViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val linkFailed = stringResource(R.string.shop_link_failed)

    // Prices + purchase re-sync whenever the Shop is on screen (first show, back from the Play sheet).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onScreenStart() }

    // Message text resolved here (Composable context); the collector reads the latest lambda.
    val messageOf = messageResolver()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            launch { snackbarHostState.showSnackbar(messageOf(event)) }
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

    ShopScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        initialSection = initialSection,
        onBack = onBack,
        onRestore = viewModel::onRestore,
        onBuy = viewModel::onBuy,
        // SEAM: Play subscriptions page for our monthly sub (cancel / change payment method).
        onManageSubscription = {
            open(StoreLinks.manageSubscriptionUrl(context.packageName, ShopProduct.REMOVE_ADS_MONTHLY.id))
        },
        onTerms = { open(LegalLinks.TERMS_URL) },
        onPrivacy = { open(LegalLinks.PRIVACY_URL) },
        onRetry = viewModel::onScreenStart,
        modifier = modifier,
    )
}

/** Resolves every [PurchaseEvent] to display text. Captures the strings once per composition. */
@Composable
private fun messageResolver(): (PurchaseEvent) -> String {
    val cats = stringResource(R.string.shop_msg_cats_added)
    val hints = stringResource(R.string.shop_msg_hints_added)
    val mice = stringResource(R.string.shop_msg_mice_added)
    val adsRemoved = stringResource(R.string.shop_msg_ads_removed)
    val pending = stringResource(R.string.shop_msg_pending)
    val restored = stringResource(R.string.shop_msg_restored)
    val nothing = stringResource(R.string.shop_msg_nothing_to_restore)
    val canceled = stringResource(R.string.shop_err_canceled)
    val alreadyOwned = stringResource(R.string.shop_err_already_owned)
    val unavailable = stringResource(R.string.shop_err_unavailable)
    val network = stringResource(R.string.shop_err_network)
    val itemUnavailable = stringResource(R.string.shop_err_item_unavailable)
    val developer = stringResource(R.string.shop_err_developer)
    val generic = stringResource(R.string.shop_err_generic)
    return remember(
        cats, hints, mice, adsRemoved, pending, restored, nothing,
        canceled, alreadyOwned, unavailable, network, itemUnavailable, developer, generic,
    ) {
        { event ->
            when (event) {
                is PurchaseEvent.PackGranted -> {
                    val template = when (event.product.booster) {
                        Booster.CAT -> cats
                        Booster.HINT -> hints
                        else -> mice
                    }
                    String.format(template, event.product.amount)
                }
                is PurchaseEvent.AdsRemovalActive -> adsRemoved
                is PurchaseEvent.Pending -> pending
                PurchaseEvent.Restored -> restored
                PurchaseEvent.NothingToRestore -> nothing
                is PurchaseEvent.Failed -> when (event.failure) {
                    BillingFailure.USER_CANCELED -> canceled
                    BillingFailure.ALREADY_OWNED -> alreadyOwned
                    BillingFailure.SERVICE_DISCONNECTED,
                    BillingFailure.SERVICE_UNAVAILABLE,
                    BillingFailure.BILLING_UNAVAILABLE,
                    -> unavailable
                    BillingFailure.NETWORK -> network
                    BillingFailure.ITEM_UNAVAILABLE -> itemUnavailable
                    BillingFailure.DEVELOPER_ERROR -> developer
                    BillingFailure.ERROR -> generic
                }
            }
        }
    }
}
