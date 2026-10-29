package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.config.AppRemoteConfig
import com.threetech.catpuzzle.domain.model.AppFlags
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.model.MilestoneStatus
import com.threetech.catpuzzle.domain.model.RewardLine
import com.threetech.catpuzzle.ui.screens.game.FakeAppFlagsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakRewardUseCasesTest {

    private class Rig(level: Int = 1) {
        val streak = FakeStreakRepository()
        val inventory = FakeInventoryRepository()
        val rewards = FakeStreakRewardRepository(inventory)
        val flags = FakeAppFlagsRepository(AppFlags(lastPlayedLevel = level))
        val time = FakeTimeProvider(now = 1_000L, today = 20_000L)
        val config = FakeRemoteConfigRepository()
        val claim = ClaimStreakReward(streak, rewards, flags, time, config)
        val observe = ObserveStreakRewards(streak, rewards, flags, time, config)

        suspend fun play(from: Long, count: Int) {
            for (d in from until from + count) streak.recordPlayed(d)
        }
    }

    @Test
    fun claimBelowMilestoneReturnsNullAndCreditsNothing() = runTest {
        val rig = Rig()
        rig.play(19_999, 2) // 19999..20000 -> streak 2
        assertNull(rig.claim(3))
        assertEquals(0, rig.rewards.claimCalls)
        assertEquals(0, rig.inventory.get(Booster.HINT))
    }

    @Test
    fun claimCreditsInventoryOnce() = runTest {
        val rig = Rig()
        rig.play(19_998, 3) // 19998..20000 -> streak 3
        val first = rig.claim(3)
        assertNotNull(first)
        assertEquals(3, first!!.day)
        assertEquals(1, first.cycle)
        assertEquals(listOf(RewardLine(Booster.HINT, 1)), first.lines)
        assertEquals(1, rig.inventory.get(Booster.HINT))
    }

    @Test
    fun doubleClaimIsRejectedAndDoesNotDoubleCredit() = runTest {
        val rig = Rig()
        rig.play(19_998, 3)
        assertNotNull(rig.claim(3))
        assertNull(rig.claim(3))
        assertEquals(1, rig.inventory.get(Booster.HINT))
    }

    @Test
    fun ledgerRejectsRepeatKeyDirectly() = runTest {
        val rewards = FakeStreakRewardRepository()
        val lines = listOf(RewardLine(Booster.CAT, 2))
        assertTrue(rewards.claimOnce("100:7", lines))
        assertFalse(rewards.claimOnce("100:7", lines))
    }

    @Test
    fun breakForfeitsPendingAndRebuildEarnsAgain() = runTest {
        val rig = Rig()
        rig.play(19_998, 3) // streak 3, hint pending, unclaimed
        rig.time.today = 20_002L // missed 20001 fully -> streak 0
        assertNull(rig.claim(3))
        assertEquals(0, rig.observe().first().pendingCount)
        // rebuild a new run 20002..20004
        rig.time.today = 20_004L
        rig.play(20_002, 3)
        assertNotNull(rig.claim(3))
        assertEquals(1, rig.inventory.get(Booster.HINT))
    }

    @Test
    fun reachingMilestoneInOneRunClaimsEachSeparately() = runTest {
        val rig = Rig()
        rig.play(19_994, 7) // streak 7 through today
        assertEquals(2, rig.observe().first().pendingCount)
        assertNotNull(rig.claim(3))
        assertNotNull(rig.claim(7))
        assertEquals(2, rig.inventory.get(Booster.CAT))
        assertEquals(1, rig.inventory.get(Booster.MOUSE)) // credited even while Mouse is locked
        assertEquals(0, rig.observe().first().pendingCount)
    }

    @Test
    fun claimCarriesPlayerLevelForMouseNote() = runTest {
        val rig = Rig(level = 4)
        rig.play(19_994, 7)
        assertEquals(4, rig.claim(7)!!.playerLevel)
    }

    @Test
    fun killSwitchHidesStateAndRefusesClaim() = runTest {
        val rig = Rig()
        rig.play(19_998, 3)
        rig.config.config = AppRemoteConfig(streakRewardsEnabled = false)
        assertFalse(rig.observe().first().enabled)
        assertEquals(0, rig.observe().first().pendingCount)
        assertNull(rig.claim(3))
        assertEquals(0, rig.rewards.claimCalls)
    }

    @Test
    fun observeReportsStatusesAndCycle() = runTest {
        val rig = Rig()
        rig.play(19_998, 3)
        val s = rig.observe().first()
        assertTrue(s.enabled)
        assertEquals(3, s.current)
        assertEquals(3, s.cycleDay)
        assertEquals(1, s.cycle)
        assertEquals(MilestoneStatus.CLAIMABLE, s.nodes.first { it.milestone.day == 3 }.status)
        assertEquals(MilestoneStatus.LOCKED, s.nodes.first { it.milestone.day == 7 }.status)
    }
}
