package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsUserProps
import com.tm.meow.sudoku.puzzle.domain.billing.AdsRemovedRepository
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentRepository
import com.tm.meow.sudoku.puzzle.domain.consent.TelemetryConsentPolicy
import com.tm.meow.sudoku.puzzle.domain.engine.StreakCalculator
import com.tm.meow.sudoku.puzzle.domain.model.StreakInfo
import com.tm.meow.sudoku.puzzle.domain.notification.NotificationStatus
import com.tm.meow.sudoku.puzzle.domain.repository.AppFlagsRepository
import com.tm.meow.sudoku.puzzle.domain.repository.ProgressRepository
import com.tm.meow.sudoku.puzzle.domain.repository.StreakRepository
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuProgress
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuTier
import com.tm.meow.sudoku.puzzle.domain.sudoku.repository.SudokuRepository
import com.tm.meow.sudoku.puzzle.domain.time.TimeProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Seam the ViewModels call after a finished attempt; the default does nothing (tests, no Firebase). */
interface UserPropsSync {
    suspend fun sync()
}

object NoopUserPropsSync : UserPropsSync {
    override suspend fun sync() = Unit
}

/**
 * Snapshot of player state pushed to GA4 as user properties (18 of the 25 custom dimensions). Skipped entirely unless
 * analytics consent allows it. `payer` comes from the persisted flag that
 * [com.tm.meow.sudoku.puzzle.domain.billing.PurchaseProcessor] writes on a fresh purchase (set only when true; GA4 keeps it). Never throws (except cancellation).
 */
class SyncAnalyticsUserProperties(
    private val analytics: AnalyticsLogger,
    private val consent: ConsentRepository,
    private val flags: AppFlagsRepository,
    private val progress: ProgressRepository,
    private val sudoku: SudokuRepository,
    private val streak: StreakRepository,
    private val adsRemoved: AdsRemovedRepository,
    private val notifications: NotificationStatus,
    private val time: TimeProvider,
    private val language: GetAppLanguage,
) : UserPropsSync {

    override suspend fun sync() {
        if (!consent.state.value.analyticsAllowed) return
        try {
            val f = flags.flags.first()
            val won = progress.observeAll().first().size
            val sudokuProgress = sudoku.observeProgress().first()
            val wins = sudokuProgress.mapValues { it.value.wins }
            fun plays(tier: SudokuTier): String = (sudokuProgress[tier] ?: SudokuProgress(tier)).plays.toString()
            val days: StreakInfo = StreakCalculator.compute(streak.observePlayedDays().first(), time.todayEpochDay())

            set(AnalyticsUserProps.MAX_LEVEL, f.lastPlayedLevel.toString())
            set(AnalyticsUserProps.LEVELS_WON, won.toString())
            set(AnalyticsUserProps.SDK_EASY_PLAYS, plays(SudokuTier.EASY))
            set(AnalyticsUserProps.SDK_MEDIUM_PLAYS, plays(SudokuTier.MEDIUM))
            set(AnalyticsUserProps.SDK_HARD_PLAYS, plays(SudokuTier.HARD))
            set(AnalyticsUserProps.SDK_EXPERT_PLAYS, plays(SudokuTier.EXPERT))
            set(AnalyticsUserProps.SDK_MASTER_PLAYS, plays(SudokuTier.MASTER))
            set(AnalyticsUserProps.SDK_EXTREME_PLAYS, plays(SudokuTier.EXTREME))
            set(AnalyticsUserProps.SUDOKU_UNLOCKED, SudokuTier.entries.count { it.isUnlocked(wins) }.toString())
            set(AnalyticsUserProps.ADS_REMOVED, flag(adsRemoved.adsRemoved.value))
            set(AnalyticsUserProps.STREAK_DAYS, days.current.toString())
            set(AnalyticsUserProps.TUTORIAL_DONE, flag(f.tutorialDone))
            set(AnalyticsUserProps.NOTIF_ENABLED, flag(notifications.areEnabled()))
            set(AnalyticsUserProps.APP_LANGUAGE, language().tag)
            set(AnalyticsUserProps.CONSENT_STATE, TelemetryConsentPolicy.statusLabel(consent.state.value))
            set(AnalyticsUserProps.LEVELS_BUCKET, levelsBucket(won))
            f.installDateEpochDay?.let { set(AnalyticsUserProps.INSTALL_DAYS, installBucket(time.todayEpochDay() - it)) }
            if (f.payer) set(AnalyticsUserProps.PAYER, "1") // sticky: only ever set, never cleared
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // analytics is best effort
        }
    }

    private fun set(name: String, value: String) = analytics.setUserProperty(name, value)

    /** Levels cleared, bucketed so the user property stays low-cardinality. */
    private fun levelsBucket(n: Int): String = when {
        n <= 0 -> "0"
        n <= 5 -> "1-5"
        n <= 10 -> "6-10"
        n <= 25 -> "11-25"
        n <= 50 -> "26-50"
        n <= 100 -> "51-100"
        else -> "100+"
    }

    /** Days since install, bucketed. */
    private fun installBucket(days: Long): String = when {
        days <= 0 -> "d0"
        days <= 1 -> "d1"
        days <= 3 -> "d2-3"
        days <= 7 -> "d4-7"
        days <= 14 -> "d8-14"
        days <= 30 -> "d15-30"
        else -> "d31+"
    }

    private fun flag(value: Boolean) = if (value) "1" else "0"
}
