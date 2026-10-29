package com.threetech.catpuzzle.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Feedback entry point. The ONE place that knows the destination address.
 * TBD by the user: replace [ADDRESS] with the real support mailbox before release.
 */
object FeedbackEmail {
    /** PLACEHOLDER address (reserved .invalid TLD, can never receive mail). */
    const val ADDRESS: String = "feedback@placeholder.invalid"

    /**
     * Opens the user's email app with a pre-filled draft. Returns false when no email app handles
     * the intent. Plain mailto (no attachments, no device data), so no extra permission is needed.
     */
    fun open(context: Context, subject: String): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(ADDRESS))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
