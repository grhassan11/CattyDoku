package com.threetech.catpuzzle.util

/**
 * Google Play web links the app opens. No secrets, no placeholders: these are public Play URLs built from the app's own id.
 * (Terms / Privacy placeholders live in [LegalLinks], the one constants file for legal URLs.)
 */
object StoreLinks {
    /** Public Play listing of this app (fallback when the in-app review flow cannot start). */
    fun listingUrl(packageName: String): String = "https://play.google.com/store/apps/details?id=$packageName"

    /** Play "Subscriptions" page, deep-linked to our monthly sub. Opens in the Play app when installed. */
    fun manageSubscriptionUrl(packageName: String, subscriptionId: String): String =
        "https://play.google.com/store/account/subscriptions?sku=$subscriptionId&package=$packageName"
}
