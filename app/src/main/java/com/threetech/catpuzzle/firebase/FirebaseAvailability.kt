package com.threetech.catpuzzle.firebase

import android.content.Context
import com.google.firebase.FirebaseApp

/**
 * True only when the default FirebaseApp exists, i.e. google-services.json was present at build time (the
 * google-services plugin generates the options the init provider reads). Without it every Firebase seam falls back
 * to its no-op implementation and nothing touches Firebase classes.
 */
object FirebaseAvailability {
    fun isAvailable(context: Context): Boolean = try {
        FirebaseApp.getApps(context).isNotEmpty()
    } catch (t: Throwable) {
        false
    }
}
