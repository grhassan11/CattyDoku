package com.threetech.catpuzzle.data.platform

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks the foreground app Activity so data-layer SDK wrappers (UMP form, interstitial show) can get one
 * without any Activity reaching ViewModels or the domain. Registered once in the Application.
 * Only this app's own activities count: the Ads SDK's full-screen AdActivity must never replace it.
 */
@Singleton
class CurrentActivityProvider @Inject constructor() : Application.ActivityLifecycleCallbacks {
    private var ref: WeakReference<Activity>? = null

    /** The resumed, non-finishing app activity, or null. */
    fun current(): Activity? = ref?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    fun register(app: Application) {
        app.registerActivityLifecycleCallbacks(this)
    }

    private fun Activity.isOurs() = javaClass.name.startsWith(application.packageName)

    override fun onActivityResumed(activity: Activity) {
        if (activity.isOurs()) ref = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        // keep the reference through pause: the UMP form / ad can be shown while paused-transition; destroyed check covers staleness
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (ref?.get() === activity) ref = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
