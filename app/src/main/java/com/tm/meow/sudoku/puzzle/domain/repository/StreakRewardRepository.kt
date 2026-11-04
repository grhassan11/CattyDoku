package com.tm.meow.sudoku.puzzle.domain.repository

import com.tm.meow.sudoku.puzzle.domain.model.RewardLine
import kotlinx.coroutines.flow.Flow

/** Ledger of claimed streak milestones ("runStart:absDay" keys) + the booster credit. */
interface StreakRewardRepository {
    fun observeClaimedKeys(): Flow<Set<String>>

    /**
     * In ONE atomic transaction: record [key] and credit every line to inventory, unless [key] was already
     * recorded. True = credited now; false = already claimed (nothing changed).
     */
    suspend fun claimOnce(key: String, rewards: List<RewardLine>): Boolean
}
