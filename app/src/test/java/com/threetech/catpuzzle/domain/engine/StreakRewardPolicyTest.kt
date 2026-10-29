package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.model.MilestoneStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakRewardPolicyTest {

    private fun days(from: Long, count: Int): Set<Long> = (from until from + count).toSet()

    private fun status(current: Int, runStart: Long = 100L, claimed: Set<String> = emptySet()) =
        StreakRewardPolicy.nodes(current, runStart, claimed).associate { it.milestone.day to it.status }

    // ---- table ----

    @Test
    fun milestoneDaysAndTotals() {
        assertEquals(listOf(3, 7, 14, 20, 30), StreakRewardPolicy.MILESTONES.map { it.day })
        val all = StreakRewardPolicy.MILESTONES.flatMap { it.rewards }
        assertEquals(9, all.filter { it.booster == Booster.CAT }.sumOf { it.amount })
        assertEquals(7, all.filter { it.booster == Booster.HINT }.sumOf { it.amount })
        assertEquals(4, all.filter { it.booster == Booster.MOUSE }.sumOf { it.amount })
    }

    // ---- cycle ----

    @Test
    fun cycleWrapsAt30() {
        assertNull(StreakRewardPolicy.cycleOf(0))
        assertEquals(StreakRewardPolicy.CyclePosition(1, 0), StreakRewardPolicy.cycleOf(1))
        assertEquals(StreakRewardPolicy.CyclePosition(30, 0), StreakRewardPolicy.cycleOf(30))
        assertEquals(StreakRewardPolicy.CyclePosition(1, 1), StreakRewardPolicy.cycleOf(31))
        assertEquals(StreakRewardPolicy.CyclePosition(30, 1), StreakRewardPolicy.cycleOf(60))
        assertEquals(StreakRewardPolicy.CyclePosition(1, 2), StreakRewardPolicy.cycleOf(61))
    }

    // ---- runStart ----

    @Test
    fun runStartWhenTodayPlayed() {
        assertEquals(19_998L, StreakRewardPolicy.runStart(days(19_998, 3), today = 20_000L))
    }

    @Test
    fun runStartWhenTodayNotPlayedYetAnchorsOnYesterday() {
        // played 19997..19999, today 20000 unplayed: streak alive (3), run began 19997
        assertEquals(19_997L, StreakRewardPolicy.runStart(days(19_997, 3), today = 20_000L))
    }

    @Test
    fun runStartIsNullWithoutLiveStreak() {
        assertNull(StreakRewardPolicy.runStart(emptySet(), 20_000L))
        assertNull(StreakRewardPolicy.runStart(days(19_990, 3), 20_000L)) // missed a full day
    }

    // ---- nodes / pending ----

    @Test
    fun nothingBeforeDay3() {
        assertTrue(StreakRewardPolicy.pendingKeys(2, 100L, emptySet()).isEmpty())
        assertTrue(status(2).values.all { it == MilestoneStatus.LOCKED })
        assertTrue(StreakRewardPolicy.nodes(0, null, emptySet()).isEmpty())
    }

    @Test
    fun neverGrantsAboveCurrent() {
        assertEquals(listOf("100:3"), StreakRewardPolicy.pendingKeys(6, 100L, emptySet()))
        assertEquals(listOf("100:3", "100:7"), StreakRewardPolicy.pendingKeys(7, 100L, emptySet()))
        assertEquals(MilestoneStatus.LOCKED, status(13)[14])
        assertEquals(MilestoneStatus.CLAIMABLE, status(14)[14])
    }

    @Test
    fun claimedKeyIsNoLongerPending() {
        val s = status(7, claimed = setOf("100:3"))
        assertEquals(MilestoneStatus.CLAIMED, s[3])
        assertEquals(MilestoneStatus.CLAIMABLE, s[7])
        assertEquals(listOf("100:7"), StreakRewardPolicy.pendingKeys(7, 100L, setOf("100:3")))
    }

    @Test
    fun breakAndRebuildEarnsAgainUnderNewKey() {
        val claimed = setOf("100:3") // earned in the first run
        // second run starts at a different day -> same milestone, new key
        assertEquals(listOf("200:3"), StreakRewardPolicy.pendingKeys(3, 200L, claimed))
    }

    @Test
    fun day31StartsFreshCycleAndForfeitsOldOne() {
        // day 30 unclaimed, streak rolls to 31: cycle 2 day 1, nothing claimable, old cycle gone
        assertTrue(StreakRewardPolicy.pendingKeys(31, 100L, emptySet()).isEmpty())
        assertTrue(status(31).values.all { it == MilestoneStatus.LOCKED })
    }

    @Test
    fun cycleTwoKeysUseAbsoluteDay() {
        assertEquals(listOf("100:33"), StreakRewardPolicy.pendingKeys(33, 100L, setOf("100:3")))
        // cycle-1 key does not mark cycle-2 node claimed
        assertEquals(MilestoneStatus.CLAIMABLE, status(33, claimed = setOf("100:3"))[3])
        assertEquals(MilestoneStatus.CLAIMED, status(33, claimed = setOf("100:33"))[3])
    }

    @Test
    fun day30IsClaimableInCycleOne() {
        assertEquals(MilestoneStatus.CLAIMABLE, status(30)[30])
        assertTrue("100:30" in StreakRewardPolicy.pendingKeys(30, 100L, emptySet()))
    }
}
