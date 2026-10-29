package com.threetech.catpuzzle.domain.model

/** [amount] (> 0) of one [booster]. */
data class RewardLine(val booster: Booster, val amount: Int)

/** Streak [day] (1..30, position inside a 30-day cycle) and what claiming it pays out. */
data class StreakMilestone(val day: Int, val rewards: List<RewardLine>)

enum class MilestoneStatus { LOCKED, CLAIMABLE, CLAIMED }

/**
 * One milestone as shown for the CURRENT 30-day cycle. [claimKey] = "runStart:absDay" (see StreakRewardPolicy);
 * it is what the claim ledger stores.
 */
data class StreakRewardNode(
    val milestone: StreakMilestone,
    val status: MilestoneStatus,
    val claimKey: String,
)

/**
 * Live reward state. [enabled] false = remote kill switch off (no nodes, nothing claimable).
 * [cycleDay] 1..30 (0 when no streak), [cycle] 1-based cycle number (day 31 starts cycle 2).
 * [playerLevel] is the level the player is on (>= 1), used for the "mouse unlocks at Lv N" note.
 */
data class StreakRewardState(
    val enabled: Boolean,
    val current: Int,
    val cycleDay: Int,
    val cycle: Int,
    val nodes: List<StreakRewardNode>,
    val playerLevel: Int,
) {
    val pendingCount: Int get() = if (enabled) nodes.count { it.status == MilestoneStatus.CLAIMABLE } else 0

    companion object {
        val DISABLED = StreakRewardState(false, 0, 0, 1, emptyList(), 1)
    }
}

/** Result of a successful claim. [cycle] is 1-based. */
data class StreakClaim(val day: Int, val cycle: Int, val lines: List<RewardLine>, val playerLevel: Int)
