package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.config.RemoteConfigRepository
import com.threetech.catpuzzle.domain.engine.StreakCalculator
import com.threetech.catpuzzle.domain.engine.StreakRewardPolicy
import com.threetech.catpuzzle.domain.model.MilestoneStatus
import com.threetech.catpuzzle.domain.model.StreakClaim
import com.threetech.catpuzzle.domain.model.StreakRewardState
import com.threetech.catpuzzle.domain.repository.AppFlagsRepository
import com.threetech.catpuzzle.domain.repository.StreakRepository
import com.threetech.catpuzzle.domain.repository.StreakRewardRepository
import com.threetech.catpuzzle.domain.time.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Live streak-reward state from played days + claim ledger + player level. Kill switch off (Remote Config
 * `streak_rewards_enabled`) = [StreakRewardState.DISABLED]. "Today" is read per emission (same caveat as ObserveStreak).
 */
class ObserveStreakRewards(
    private val streak: StreakRepository,
    private val rewards: StreakRewardRepository,
    private val flags: AppFlagsRepository,
    private val time: TimeProvider,
    private val config: RemoteConfigRepository,
) {
    operator fun invoke(): Flow<StreakRewardState> {
        if (!config.current().streakRewardsEnabled) return flowOf(StreakRewardState.DISABLED)
        val level = flags.flags.map { it.lastPlayedLevel.coerceAtLeast(1) }.distinctUntilChanged()
        return combine(streak.observePlayedDays(), rewards.observeClaimedKeys(), level) { played, claimed, lvl ->
            val today = time.todayEpochDay()
            val current = StreakCalculator.currentStreak(played, today)
            val pos = StreakRewardPolicy.cycleOf(current)
            StreakRewardState(
                enabled = true,
                current = current,
                cycleDay = pos?.cycleDay ?: 0,
                cycle = (pos?.cycleIndex ?: 0) + 1,
                nodes = StreakRewardPolicy.nodes(current, StreakRewardPolicy.runStart(played, today), claimed),
                playerLevel = lvl,
            )
        }
    }
}

/**
 * Claim one milestone ([day] = cycle day 3/7/14/20/30). Re-derives what is pending from live data, so a stale
 * UI tap after a streak break, a double tap or a second claim all return null and change nothing.
 */
class ClaimStreakReward(
    private val streak: StreakRepository,
    private val rewards: StreakRewardRepository,
    private val flags: AppFlagsRepository,
    private val time: TimeProvider,
    private val config: RemoteConfigRepository,
) {
    suspend operator fun invoke(day: Int): StreakClaim? {
        if (!config.current().streakRewardsEnabled) return null
        val played = streak.observePlayedDays().first()
        val today = time.todayEpochDay()
        val current = StreakCalculator.currentStreak(played, today)
        val pos = StreakRewardPolicy.cycleOf(current) ?: return null
        val claimed = rewards.observeClaimedKeys().first()
        val node = StreakRewardPolicy.nodes(current, StreakRewardPolicy.runStart(played, today), claimed)
            .firstOrNull { it.milestone.day == day && it.status == MilestoneStatus.CLAIMABLE }
            ?: return null
        if (!rewards.claimOnce(node.claimKey, node.milestone.rewards)) return null
        val level = flags.flags.first().lastPlayedLevel.coerceAtLeast(1)
        return StreakClaim(day, pos.cycleIndex + 1, node.milestone.rewards, level)
    }
}
