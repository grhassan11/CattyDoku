package com.tm.meow.sudoku.puzzle.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsEventTest {

    private val snake = Regex("^[a-z][a-z0-9_]{0,39}$")

    private val ctx = LevelContext(
        mode = AttemptMode.LEVELS, levelNumber = 3, world = 1, boardSize = 4, difficulty = "t2", attemptNo = 2, isReplay = true,
    )
    private val stats = AttemptStats(durationSec = 60, livesLost = 1, hintsUsed = 0, boostersUsed = 1, score = 400)

    private val all: List<AnalyticsEvent> = listOf(
        AnalyticsEvent.TutorialBegin,
        AnalyticsEvent.TutorialStep(2),
        AnalyticsEvent.TutorialComplete(skipped = true),
        AnalyticsEvent.TermsAccept,
        AnalyticsEvent.NotificationPermission(granted = true),
        AnalyticsEvent.LevelStart(ctx),
        AnalyticsEvent.LevelResume(ctx, stats),
        AnalyticsEvent.LevelEnd(ctx, stats, won = true, newBestTime = true, newBestScore = false),
        AnalyticsEvent.LevelQuit(ctx, stats, QuitReason.TOP_BAR_BACK),
        AnalyticsEvent.LevelRestart(ctx, stats),
        AnalyticsEvent.LevelRevive(ctx, stats),
        AnalyticsEvent.UndoUsed(ctx),
        AnalyticsEvent.HintUsed(ctx),
        AnalyticsEvent.BoosterUsed("cat", ctx),
        AnalyticsEvent.GamePause(ctx, stats),
        AnalyticsEvent.SudokuTierSelect("hard", locked = true),
        AnalyticsEvent.SudokuNewGame("easy", hadSave = false),
        AnalyticsEvent.SudokuContinue("easy"),
        AnalyticsEvent.SudokuTierUnlock("expert"),
        AnalyticsEvent.SudokuTutorialBegin(size = "6x6"),
        AnalyticsEvent.SudokuTutorialStep(lesson = 1, step = 3, size = "6x6"),
        AnalyticsEvent.SudokuTutorialEnd(completed = true, size = "6x6"),
        AnalyticsEvent.HomeModeTab("sudoku"),
        AnalyticsEvent.LevelSelect(12, 2),
        AnalyticsEvent.StreakDialogOpen,
        AnalyticsEvent.StreakRewardClaim(day = 7, cycle = 1),
        AnalyticsEvent.ShopOpen("other"),
        AnalyticsEvent.BeginCheckout("cat_pack_5"),
        AnalyticsEvent.Purchase("cat_pack_5", 1.99, "USD", "GPA.1"),
        AnalyticsEvent.ScreenView("home"),
        AnalyticsEvent.PurchaseSuccess("cat_pack_5"),
        AnalyticsEvent.PurchaseFail("cat_pack_5", "network"),
        AnalyticsEvent.RestorePurchases(1),
        AnalyticsEvent.AdInterstitial("shown", "level_won_continue"),
        AnalyticsEvent.RewardedEarned("revive"),
        AnalyticsEvent.RewardedFailed("revive"),
        AnalyticsEvent.SettingChange("pattern_mode", true),
        AnalyticsEvent.SettingsOpen("screen"),
        AnalyticsEvent.RatePrompt("submitted_low"),
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
    fun paramCountWithinFirebaseLimitAndValuesAreLongStringDouble() {
        for (e in all) {
            assertTrue("${e.name} params", e.params.size <= 25)
            for ((k, v) in e.params) {
                assertTrue("$k type", v is Long || v is String || v is Double || (k == "items" && v is List<*>))
                if (v is String) assertTrue("$k length", v.length <= 100)
            }
        }
    }

    @Test
    fun legacySplitEventsAreGone() {
        val removed = setOf("level_win", "level_lose", "daily_start", "daily_complete", "sudoku_start", "sudoku_win", "sudoku_fail", "sudoku_hint_used")
        assertTrue(all.none { it.name in removed })
    }

    @Test
    fun userPropertyNamesFitFirebaseLimits() {
        assertTrue(AnalyticsUserProps.ALL.size <= 25)
        assertEquals(AnalyticsUserProps.ALL.size, AnalyticsUserProps.ALL.toSet().size)
        for (n in AnalyticsUserProps.ALL) assertTrue(n, n.length <= 24 && snake.matches(n))
    }

    @Test
    fun beginCheckoutCarriesValueOnlyWhenPriced() {
        assertEquals(mapOf<String, Any>("item_id" to "p"), AnalyticsEvent.BeginCheckout("p").params)
        assertEquals(
            mapOf<String, Any>("item_id" to "p", "value" to 1.5, "currency" to "USD"),
            AnalyticsEvent.BeginCheckout("p", 1.5, "USD").params,
        )
    }

    @Test
    fun purchaseUsesGa4ReservedParams() {
        val e = AnalyticsEvent.Purchase("p", 2.5, "EUR", "GPA.9")
        assertEquals("purchase", e.name)
        assertEquals(2.5, e.params["value"])
        assertEquals("EUR", e.params["currency"])
        assertEquals("GPA.9", e.params["transaction_id"])
        assertEquals(listOf(mapOf<String, Any>("item_id" to "p")), e.params["items"])
    }

    @Test
    fun screenViewAndEntryPointParams() {
        assertEquals("screen_view", AnalyticsEvent.ScreenView("home").name)
        assertEquals("home", AnalyticsEvent.ScreenView("home").params["screen_name"])
        assertEquals("other", AnalyticsEvent.ShopOpen("other").params["entry_point"])
        assertEquals("screen", AnalyticsEvent.SettingsOpen("screen").params["entry_point"])
    }

    @Test
    fun levelEndWinParams() {
        val e = AnalyticsEvent.LevelEnd(ctx, stats, won = true, newBestTime = true, newBestScore = false)
        assertEquals("level_end", e.name)
        assertEquals(
            mapOf<String, Any>(
                "mode" to "levels",
                "level_number" to 3L,
                "world" to 1L,
                "board_size" to 4L,
                "difficulty" to "t2",
                "attempt_no" to 2L,
                "is_replay" to 1L,
                "is_daily" to 0L,
                "duration_sec" to 60L,
                "lives_lost" to 1L,
                "hints_used" to 0L,
                "boosters_used" to 1L,
                "score" to 400L,
                "resumed" to 0L,
                "result" to "win",
                "new_best_time" to 1L,
                "new_best_score" to 0L,
            ),
            e.params,
        )
    }

    @Test
    fun loseHasNoBestFlags() {
        val e = AnalyticsEvent.LevelEnd(ctx, stats, won = false, newBestTime = true, newBestScore = true)
        assertEquals("lose", e.params["result"])
        assertFalse("new_best_time" in e.params)
        assertFalse("new_best_score" in e.params)
    }

    @Test
    fun dailyAndSudokuContextsOmitInapplicableParams() {
        val daily = LevelContext(AttemptMode.DAILY, null, null, 6, null, 1).toParams()
        assertEquals(1L, daily["is_daily"])
        assertNull(daily["level_number"])
        assertNull(daily["world"])
        assertNull(daily["difficulty"])
        val sudoku = LevelContext(AttemptMode.SUDOKU, null, null, 9, "master", 1).toParams()
        assertEquals("master", sudoku["difficulty"])
        assertEquals(0L, sudoku["is_daily"])
        assertNull(sudoku["world"])
    }

    @Test
    fun scoreOnlyWhenKnown() {
        assertNull(AttemptStats(1, 0, 0, 0).toParams()["score"])
        assertEquals(5L, AttemptStats(1, 0, 0, 0, score = 5).toParams()["score"])
    }

    @Test
    fun booleansAreZeroOne() {
        assertEquals(1L, AnalyticsEvent.TutorialComplete(true).params["skipped"])
        assertEquals(0L, AnalyticsEvent.SettingChange("sfx", false).params["enabled"])
    }

    // ---- AttemptTracker ----

    @Test
    fun quitIsConsumedAtMostOncePerAttempt() {
        val t = AttemptTracker()
        assertFalse(t.consumeQuit()) // nothing open yet
        t.begin("L1")
        assertTrue(t.consumeQuit())
        assertFalse(t.consumeQuit())
    }

    @Test
    fun endedAttemptCannotQuitAndReviveReopensIt() {
        val t = AttemptTracker()
        t.begin("L1")
        t.end()
        assertFalse(t.consumeQuit())
        t.revive()
        assertTrue(t.consumeQuit())
    }

    @Test
    fun attemptNumberCountsPerKeyAndResetsBoosters() {
        val t = AttemptTracker()
        assertEquals(1, t.begin("L1"))
        t.boosterUsed()
        assertEquals(1, t.boostersUsed)
        assertEquals(2, t.begin("L1"))
        assertEquals(0, t.boostersUsed)
        assertEquals(1, t.begin("L2"))
        assertEquals(3, t.begin("L1"))
    }

    @Test
    fun resumedFlagFollowsBegin() {
        val t = AttemptTracker()
        t.begin("L1", resumed = true)
        assertTrue(t.resumed)
        t.begin("L1")
        assertFalse(t.resumed)
    }
}
