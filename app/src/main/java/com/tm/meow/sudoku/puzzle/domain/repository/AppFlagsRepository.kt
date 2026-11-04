package com.tm.meow.sudoku.puzzle.domain.repository

import com.tm.meow.sudoku.puzzle.domain.model.AppFlags
import kotlinx.coroutines.flow.Flow

interface AppFlagsRepository {
    val flags: Flow<AppFlags>

    suspend fun update(transform: (AppFlags) -> AppFlags)

    /** Set the install date only if none is stored yet (atomic read-modify-write). */
    suspend fun setInstallDateIfAbsent(epochDay: Long)
}
