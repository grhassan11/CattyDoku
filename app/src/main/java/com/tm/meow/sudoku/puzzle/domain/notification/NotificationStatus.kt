package com.tm.meow.sudoku.puzzle.domain.notification

/** Whether the OS currently lets the app post notifications. Impl lives in data (no Android in domain). */
fun interface NotificationStatus {
    fun areEnabled(): Boolean
}
