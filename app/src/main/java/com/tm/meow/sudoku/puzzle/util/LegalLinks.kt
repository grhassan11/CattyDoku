package com.tm.meow.sudoku.puzzle.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Privacy Policy. The ONE place that knows the URL.
 * Bump TermsPolicy.CURRENT_VERSION when the policy changes materially.
 */
object LegalLinks {
    const val PRIVACY_URL: String = "https://toolsmania3.blogspot.com/2026/11/privacy-policy-of-catty-sudoku-game-on.html"

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
