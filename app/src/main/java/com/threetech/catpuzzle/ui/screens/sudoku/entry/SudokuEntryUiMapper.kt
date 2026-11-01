package com.threetech.catpuzzle.ui.screens.sudoku.entry

import com.threetech.catpuzzle.domain.sudoku.model.SudokuProgress
import com.threetech.catpuzzle.domain.sudoku.model.SudokuTier
import com.threetech.catpuzzle.domain.sudoku.model.SudokuTierStatus

/** Pure domain -> UI mapping for the Sudoku entry screen. */
object SudokuEntryUiMapper {

    /** Zeroed statuses (nothing played): Easy and Medium open. Initial state before the first emission. */
    fun emptyStatuses(): List<SudokuTierStatus> = SudokuTier.entries.map {
        SudokuTierStatus(it, SudokuProgress(it), unlocked = it.unlockTier == null, hasActiveGame = false, requirementWins = 0)
    }

    /** A locked or unknown selection falls back to Easy. */
    fun resolveSelected(statuses: List<SudokuTierStatus>, requested: SudokuTier?): SudokuTier =
        statuses.firstOrNull { it.tier == requested && it.unlocked }?.tier ?: SudokuTier.EASY

    fun map(statuses: List<SudokuTierStatus>, selected: SudokuTier, activeElapsedMs: Long?): SudokuEntryUiState {
        val rows = statuses.map { s ->
            SudokuTierRowUi(
                tier = s.tier,
                gridSize = s.tier.size.n,
                locked = !s.unlocked,
                winsHave = if (s.unlocked) s.tier.unlockWins else s.requirementWins.coerceAtMost(s.tier.unlockWins),
                winsNeeded = s.tier.unlockWins,
                requiredTier = s.tier.unlockTier,
                hasActiveGame = s.hasActiveGame,
            )
        }
        val sel = statuses.firstOrNull { it.tier == selected }
        val canContinue = sel != null && sel.unlocked && sel.hasActiveGame
        return SudokuEntryUiState(
            rows = rows,
            selected = selected,
            stats = sel?.progress?.takeIf { it.wins > 0 }?.let {
                SudokuStatsUi(it.wins, it.bestTimeMs?.let(::formatTime) ?: "--", it.bestScore)
            },
            canContinue = canContinue,
            continueTimer = if (canContinue) formatTime(activeElapsedMs ?: 0L) else null,
        )
    }

    /** "mm:ss"; minutes are not capped (a 100 minute game reads 100:00). */
    fun formatTime(ms: Long): String {
        val total = ms.coerceAtLeast(0L) / 1000L
        return String.format(java.util.Locale.ROOT, "%02d:%02d", total / 60L, total % 60L)
    }
}
