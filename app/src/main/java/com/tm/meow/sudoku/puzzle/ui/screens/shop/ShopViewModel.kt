package com.tm.meow.sudoku.puzzle.ui.screens.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsEvent
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.analytics.NoopAnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.billing.CatalogState
import com.tm.meow.sudoku.puzzle.domain.billing.PurchaseEvent
import com.tm.meow.sudoku.puzzle.domain.usecase.BuyProduct
import com.tm.meow.sudoku.puzzle.domain.usecase.LoadShopCatalog
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveBoosterBalances
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveEntitlement
import com.tm.meow.sudoku.puzzle.domain.usecase.ObservePurchaseEvents
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveShopCatalog
import com.tm.meow.sudoku.puzzle.domain.usecase.RestorePurchases
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shop state + events. Use cases only: no Activity, no BillingClient. The Play sheet is opened by the data layer on the
 * current foreground Activity (CurrentActivityProvider), so nothing Android reaches this class.
 * One-shot messages (granted / pending / failed / restored) arrive as [PurchaseEvent] through a Channel.
 */
@HiltViewModel
class ShopViewModel @Inject constructor(
    private val observeCatalog: ObserveShopCatalog,
    observeEntitlement: ObserveEntitlement,
    observeBalances: ObserveBoosterBalances,
    private val loadCatalog: LoadShopCatalog,
    private val buyProduct: BuyProduct,
    private val restorePurchases: RestorePurchases,
    observeEvents: ObservePurchaseEvents,
    savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val analytics: AnalyticsLogger = NoopAnalyticsLogger,
) : ViewModel() {

    private val busy = MutableStateFlow(false)

    val state: StateFlow<ShopUiState> = combine(
        observeCatalog(),
        observeEntitlement(),
        observeBalances(),
        busy,
    ) { catalog, entitlement, balances, isBusy ->
        ShopUiMapper.map(catalog, entitlement, balances, isBusy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ShopUiMapper.initial())

    private val _events = Channel<PurchaseEvent>(Channel.BUFFERED)
    val events: Flow<PurchaseEvent> = _events.receiveAsFlow()

    init {
        // Nav arg "section" (cat / hint / mouse) is only set when Shop opens from a booster dialog.
        analytics.log(
            AnalyticsEvent.ShopOpen(if (savedStateHandle.get<String>("section") != null) "booster_dialog" else "other"),
        )
        viewModelScope.launch {
            observeEvents().collect {
                logOutcome(it)
                _events.trySend(it)
            }
        }
    }

    /** Shop visible (first show, return from the Play sheet, "Try again"): refresh prices and re-sync purchases. */
    fun onScreenStart() {
        viewModelScope.launch { safely { loadCatalog() } }
    }

    /** Product of the Play sheet opened last; the failure event itself carries no product. */
    private var lastCheckout: String? = null

    private fun logOutcome(event: PurchaseEvent) {
        when (event) {
            is PurchaseEvent.Failed ->
                analytics.log(AnalyticsEvent.PurchaseFail(lastCheckout ?: "unknown", event.failure.name.lowercase()))
            PurchaseEvent.Restored -> analytics.log(AnalyticsEvent.RestorePurchases(1)) // count not exposed: 1 = "at least one"
            PurchaseEvent.NothingToRestore -> analytics.log(AnalyticsEvent.RestorePurchases(0))
            is PurchaseEvent.PackGranted, is PurchaseEvent.AdsRemovalActive, is PurchaseEvent.Pending -> Unit
        }
    }

    fun onBuy(productId: String) {
        if (!busy.compareAndSet(expect = false, update = true)) return
        lastCheckout = productId
        val price = (observeCatalog().value as? CatalogState.Ready)?.prices?.get(productId)
        analytics.log(AnalyticsEvent.BeginCheckout(productId, price?.value, price?.currencyCode))
        viewModelScope.launch {
            try {
                safely { buyProduct(productId) }
            } finally {
                busy.value = false
            }
        }
    }

    fun onRestore() {
        if (!busy.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                safely { restorePurchases() }
            } finally {
                busy.value = false
            }
        }
    }

    private suspend inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // billing is best effort: failures surface through events
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
