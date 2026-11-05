package com.tm.meow.sudoku.puzzle.domain.billing

/** Why a billing step failed. Mapped from Play response codes in the data layer; the UI maps each to one message. */
enum class BillingFailure {
    USER_CANCELED,
    ALREADY_OWNED,

    /** Connection dropped mid-call. The caller reconnects and retries once. */
    SERVICE_DISCONNECTED,

    /** Play service busy / timed out. Retryable. */
    SERVICE_UNAVAILABLE,

    /** Billing cannot work on this device / account / country. */
    BILLING_UNAVAILABLE,

    /** No network. */
    NETWORK,

    /** Setup problem (unsigned build, missing product, bad args). Not user fixable. */
    DEVELOPER_ERROR,
    ITEM_UNAVAILABLE,
    ERROR,
    ;

    val isRetryable: Boolean
        get() = this == SERVICE_DISCONNECTED || this == SERVICE_UNAVAILABLE || this == NETWORK || this == ERROR
}

/** Result of one billing call. */
sealed interface StoreResult<out T> {
    data class Success<T>(val value: T) : StoreResult<T>
    data class Failure(val failure: BillingFailure) : StoreResult<Nothing>
}

enum class PurchaseStatus { PURCHASED, PENDING, UNSPECIFIED }

/** A Play purchase as the domain sees it (no Play types). [purchaseToken] is the idempotency key. */
data class StorePurchase(
    val productIds: List<String>,
    val purchaseToken: String,
    /** Null while PENDING. */
    val orderId: String?,
    val status: PurchaseStatus,
    val acknowledged: Boolean,
    val autoRenewing: Boolean = false,
)

/**
 * Localized price straight from Play ProductDetails (never computed, never hard-coded).
 * [priceMicros] / [currencyCode] (ISO 4217) feed analytics revenue only; 0 / "" = unknown.
 */
data class ProductPrice(
    val productId: String,
    val formattedPrice: String,
    val priceMicros: Long = 0L,
    val currencyCode: String = "",
) {
    /** Price as a decimal amount, or null when Play gave no numeric price. */
    val value: Double? get() = if (priceMicros > 0L && currencyCode.isNotEmpty()) priceMicros / 1_000_000.0 else null
}

/** What the Shop can show right now. */
sealed interface CatalogState {
    /** Price query in flight (or not started). */
    data object Loading : CatalogState

    /** [prices] by product id. Products missing from the map are not purchasable (buy disabled). */
    data class Ready(val prices: Map<String, ProductPrice>) : CatalogState

    /** Billing not ready or no products found. Buy buttons disabled. */
    data class Unavailable(val reason: BillingFailure?) : CatalogState
}

/** One-shot, user-visible billing outcome. */
sealed interface PurchaseEvent {
    /** Consumable pack credited to inventory. */
    data class PackGranted(val product: ShopProduct) : PurchaseEvent

    /** Ads removal bought (subscription or forever) and acknowledged. */
    data class AdsRemovalActive(val product: ShopProduct) : PurchaseEvent

    /** Purchase is pending (cash / bank). Nothing granted yet. */
    data class Pending(val product: ShopProduct) : PurchaseEvent

    /** A billing step failed. USER_CANCELED is included so the UI can show a quiet message. */
    data class Failed(val failure: BillingFailure) : PurchaseEvent

    /** Restore found at least one purchase and re-applied it. */
    data object Restored : PurchaseEvent

    /** Restore found nothing to restore. */
    data object NothingToRestore : PurchaseEvent

    /** Restore applied the entitlement but a grant / acknowledge did not complete; the next sync retries it. */
    data object RestoreIncomplete : PurchaseEvent
}

/** Per-purchase result of [PurchaseProcessor]. */
sealed interface PurchaseOutcome {
    data class PackGranted(val product: ShopProduct) : PurchaseOutcome
    data class AdsRemovalActivated(val product: ShopProduct) : PurchaseOutcome
    data class Pending(val product: ShopProduct) : PurchaseOutcome
}
