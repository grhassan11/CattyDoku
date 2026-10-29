package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.model.MilestoneStatus
import com.threetech.catpuzzle.domain.model.RewardLine
import com.threetech.catpuzzle.domain.model.StreakMilestone
import com.threetech.catpuzzle.domain.model.StreakRewardNode

/**
 * Streak milestone rewards. Pure: no clock, no Android, no storage.
 *
 * Rules:
 *  - Manual claim only. A milestone is claimable once the live streak reaches it.
 *  - The streak loops in 30-day cycles: cycleDay = ((current-1) % 30) + 1, cycleIndex = (current-1) / 30.
 *    Day 31 is cycle 2, day 1 again.
 *  - ONLY the current cycle is claimable. Unclaimed milestones of an earlier cycle (e.g. day 30 not claimed
 *    before the streak rolls to day 31) are forfeited, same as when the streak breaks.
 *  - Unclaimed rewards are forfeited when the streak breaks: pending is derived from the live streak, so a
 *    break (current = 0) leaves nothing pending.
 *  - Claim key = "runStart:absDay". runStart = epoch day the current run began; absDay = milestone day +
 *    30 * cycleIndex. A new run after a break has a different runStart, so it can earn again; within a run
 *    each key exists once.
 *  - Never grants a milestone above the current cycle day.
 */
object StreakRewardPolicy {
    const val CYCLE_LENGTH = 30

    /** Hardcoded defaults (only the on/off kill switch is remote). */
    val MILESTONES: List<StreakMilestone> = listOf(
        StreakMilestone(3, listOf(RewardLine(Booster.HINT, 1))),
        StreakMilestone(7, listOf(RewardLine(Booster.CAT, 2), RewardLine(Booster.MOUSE, 1))),
        StreakMilestone(
            14,
            listOf(RewardLine(Booster.CAT, 1), RewardLine(Booster.HINT, 2), RewardLine(Booster.MOUSE, 1)),
        ),
        StreakMilestone(20, listOf(RewardLine(Booster.CAT, 2), RewardLine(Booster.MOUSE, 1))),
        StreakMilestone(
            30,
            listOf(RewardLine(Booster.CAT, 4), RewardLine(Booster.HINT, 4), RewardLine(Booster.MOUSE, 1)),
        ),
    )

    /** Position inside the loop. [cycleIndex] is 0-based. */
    data class CyclePosition(val cycleDay: Int, val cycleIndex: Int)

    /** null when [current] <= 0 (no live streak). */
    fun cycleOf(current: Int): CyclePosition? {
        if (current <= 0) return null
        return CyclePosition(cycleDay = ((current - 1) % CYCLE_LENGTH) + 1, cycleIndex = (current - 1) / CYCLE_LENGTH)
    }

    /**
     * First epoch day of the live run: anchor - current + 1, where anchor = today if played today, else yesterday
     * (a streak survives until a full day is missed). null when there is no live streak.
     */
    fun runStart(playedDays: Set<Long>, today: Long): Long? {
        val current = StreakCalculator.currentStreak(playedDays, today)
        if (current <= 0) return null
        val anchor = if (today in playedDays) today else today - 1
        return anchor - current + 1
    }

    fun claimKey(runStart: Long, absDay: Int): String = "$runStart:$absDay"

    /**
     * The five milestones of the CURRENT cycle with their status. Empty when there is no live streak.
     * [claimed] is the full ledger (all runs); only keys of this run + cycle matter.
     */
    fun nodes(current: Int, runStart: Long?, claimed: Set<String>): List<StreakRewardNode> {
        val pos = cycleOf(current) ?: return emptyList()
        val start = runStart ?: return emptyList()
        return MILESTONES.map { m ->
            val key = claimKey(start, m.day + CYCLE_LENGTH * pos.cycleIndex)
            val status = when {
                key in claimed -> MilestoneStatus.CLAIMED
                m.day <= pos.cycleDay -> MilestoneStatus.CLAIMABLE
                else -> MilestoneStatus.LOCKED
            }
            StreakRewardNode(m, status, key)
        }
    }

    /** Claim keys currently claimable. */
    fun pendingKeys(current: Int, runStart: Long?, claimed: Set<String>): List<String> =
        nodes(current, runStart, claimed).filter { it.status == MilestoneStatus.CLAIMABLE }.map { it.claimKey }
}
