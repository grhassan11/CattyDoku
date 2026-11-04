package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsUserProps
import com.tm.meow.sudoku.puzzle.domain.billing.AdsRemovedRepository
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentRepository
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
 * Snapshot of player state pushed to GA4 as user properties (14 of the 25 custom dimensions). Skipped entirely unless
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
            if (f.payer) set(AnalyticsUserProps.PAYER, "1") // sticky: only ever set, never cleared
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // analytics is best effort
        }
    }

    private fun set(name: String, value: String) = analytics.setUserProperty(name, value)

    private fun flag(value: Boolean) = if (value) "1" else "0"
}
