package com.threetech.catpuzzle.domain.analytics

import com.threetech.catpuzzle.domain.model.GameResult
import com.threetech.catpuzzle.domain.model.ScoreBreakdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsEventTest {

    private val snake = Regex("^[a-z][a-z0-9_]{0,39}$")

    private fun result(won: Boolean, level: Int = 7, heartsLeft: Int = 2) = GameResult(
        levelIndex = level,
        won = won,
        score = ScoreBreakdown(500, 120, 150, 100, 370),
        heartsLeft = heartsLeft,
        hintsUsed = 1,
        elapsedSeconds = 83,
    )

    private val all: List<AnalyticsEvent> = listOf(
        AnalyticsEvent.TutorialBegin,
        AnalyticsEvent.TutorialComplete(skipped = true),
        AnalyticsEvent.LevelStart(3, 4),
        AnalyticsEvent.LevelWin(3, 4, 60, 1, 0, 400),
        AnalyticsEvent.LevelLose(3, 4, 60, 0, daily = false),
        AnalyticsEvent.HintUsed(3, 4),
        AnalyticsEvent.BoosterUsed("cat", 3),
        AnalyticsEvent.DailyStart(5),
        AnalyticsEvent.DailyComplete(5, 90, 0, 0, 500),
        AnalyticsEvent.StreakDialogOpen,
        AnalyticsEvent.StreakRewardClaim(day = 7, cycle = 1),
        AnalyticsEvent.ShopOpen("other"),
        AnalyticsEvent.PurchaseSuccess("cat_pack_5"),
        AnalyticsEvent.RewardedEarned("revive"),
        AnalyticsEvent.SettingChange("pattern_mode", true),
        AnalyticsEvent.RatePrompt("submitted_low", 2),
    )

    @Test
    fun namesAndKeysAreStableSnakeCase() {
        for (e in all) {
            assertTrue("name ${e.name}", snake.matches(e.name))
            for (k in e.params.keys) assertTrue("key $k of ${e.name}", snake.matches(k))
        }
    }

    @Test
    fun namesAreUnique() {
        assertEquals(all.size, all.map { it.name }.toSet().size)
    }

    @Test
    fun paramCountSmallAndValuesAreLongOrShortString() {
        for (e in all) {
            assertTrue("${e.name} params", e.params.size <= 6)
            for ((k, v) in e.params) {
                assertTrue("$k type", v is Long || v is String)
                if (v is String) assertTrue("$k length", v.length <= 100)
            }
        }
    }

    @Test
    fun levelWinParams() {
        val e = AnalyticsEvent.LevelWin(level = 12, boardSize = 5, timeSec = 61, heartsLost = 2, hintsUsed = 1, score = 380)
        assertEquals("level_win", e.name)
        assertEquals(
            mapOf<String, Any>(
                "level" to 12L,
                "board_size" to 5L,
                "time_sec" to 61L,
                "hearts_lost" to 2L,
                "hints_used" to 1L,
                "score" to 380L,
            ),
            e.params,
        )
    }

    @Test
    fun booleansAreZeroOne() {
        assertEquals(1L, AnalyticsEvent.TutorialComplete(true).params["skipped"])
        assertEquals(0L, AnalyticsEvent.SettingChange("sfx", false).params["enabled"])
    }

    @Test
    fun finishedWinMapsToLevelWinWithHeartsLost() {
        val e = AnalyticsEvent.levelFinished(result(won = true, heartsLeft = 2), boardSize = 5, daily = false)
        assertEquals(AnalyticsEvent.LevelWin(7, 5, 83, 1, 1, 370), e)
    }

    @Test
    fun finishedDailyWinMapsToDailyComplete() {
        val e = AnalyticsEvent.levelFinished(result(won = true, level = -20260101, heartsLeft = 3), boardSize = 6, daily = true)
        assertEquals(AnalyticsEvent.DailyComplete(6, 83, 0, 1, 370), e)
    }

    @Test
    fun finishedLossMapsToLevelLoseAndDailyHasLevelZero() {
        assertEquals(
            AnalyticsEvent.LevelLose(7, 5, 83, 1, daily = false),
            AnalyticsEvent.levelFinished(result(won = false, heartsLeft = 0), 5, daily = false),
        )
        assertEquals(
            AnalyticsEvent.LevelLose(0, 5, 83, 1, daily = true),
            AnalyticsEvent.levelFinished(result(won = false, level = -5, heartsLeft = 0), 5, daily = true),
        )
    }

    @Test
    fun heartsLostIsClamped() {
        val e = AnalyticsEvent.levelFinished(result(won = true, heartsLeft = 9), 4, daily = false) as AnalyticsEvent.LevelWin
        assertEquals(0, e.heartsLost)
    }
}
