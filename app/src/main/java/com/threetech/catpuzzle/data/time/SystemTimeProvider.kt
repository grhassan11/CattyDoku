package com.threetech.catpuzzle.data.time

import android.os.SystemClock
import com.threetech.catpuzzle.domain.time.TimeProvider
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Wall clock + device-local date (re-read each call, so timezone / DST changes are honoured). */
@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun monotonicMillis(): Long = SystemClock.elapsedRealtime()

    override fun todayEpochDay(): Long = LocalDate.now().toEpochDay()
}
