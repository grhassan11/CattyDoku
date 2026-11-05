package com.tm.meow.sudoku.puzzle.domain.notification

/** Schedules the single pending "unfinished Sudoku" reminder. Impl lives in data (no Android in domain). */
interface SudokuReminderScheduler {
    /** Replace any pending reminder with one running in [delayMs]. */
    fun schedule(delayMs: Long)

    fun cancel()
}
