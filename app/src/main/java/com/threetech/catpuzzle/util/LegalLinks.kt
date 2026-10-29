package com.threetech.catpuzzle.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Terms of Service + Privacy Policy. The ONE place that knows the URLs.
 * TBD by the user: replace both placeholders (reserved .invalid TLD, never resolves) with the hosted
 * HTTPS pages before release. Bump TermsPolicy.CURRENT_VERSION when the text changes materially.
 */
object LegalLinks {
    const val TERMS_URL: String = "https://example.invalid/terms"
    const val PRIVACY_URL: String = "https://example.invalid/privacy"

    /** Opens [url] in the browser. False when nothing can handle it. */
    fun open(context: Context, url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
}
