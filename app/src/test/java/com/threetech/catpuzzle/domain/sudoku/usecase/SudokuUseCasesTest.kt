package com.threetech.catpuzzle.domain.sudoku.usecase

import com.threetech.catpuzzle.domain.ads.AdSessionTracker
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.sudoku.engine.SUDOKU_GENERATOR_VERSION
import com.threetech.catpuzzle.domain.sudoku.engine.SudokuGameEngine
import com.threetech.catpuzzle.domain.sudoku.model.SudokuGameState
import com.threetech.catpuzzle.domain.sudoku.model.SudokuProgress
import com.threetech.catpuzzle.domain.sudoku.model.SudokuPuzzle
import com.threetech.catpuzzle.domain.sudoku.model.SudokuStatus
import com.threetech.catpuzzle.domain.sudoku.model.SudokuTechnique
import com.threetech.catpuzzle.domain.sudoku.model.SudokuTier
import com.threetech.catpuzzle.domain.sudoku.repository.SudokuRepository
import com.threetech.catpuzzle.domain.usecase.ConsumeBooster
import com.threetech.catpuzzle.domain.usecase.FakeInventoryRepository
import com.threetech.catpuzzle.domain.usecase.FakeStreakRepository
import com.threetech.catpuzzle.domain.usecase.FakeTimeProvider
import com.threetech.catpuzzle.domain.usecase.RecordLevelWon
import com.threetech.catpuzzle.domain.usecase.RecordPlayedToday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSudokuRepository : SudokuRepository {
    val progress = MutableStateFlow<Map<SudokuTier, SudokuProgress>>(emptyMap())
    val saves = MutableStateFlow<Map<SudokuTier, SudokuGameState>>(emptyMap())

    private fun cur(t: SudokuTier) = progress.value[t] ?: SudokuProgress(t)

    fun setWins(tier: SudokuTier, wins: Int) {
        progress.value = progress.value + (tier to cur(tier).copy(wins = wins))
    }

    override fun observeProgress(): Flow<Map<SudokuTier, SudokuProgress>> =
        progress.map { m -> SudokuTier.entries.associateWith { m[it] ?: SudokuProgress(it) } }

    override fun observeActiveTiers(): Flow<Set<SudokuTier>> = saves.map { it.keys }

    override suspend fun getProgress(tier: SudokuTier) = cur(tier)

    override suspend fun winsByTier(): Map<SudokuTier, Int> = SudokuTier.entries.associateWith { cur(it).wins }

    override suspend fun nextPlayIndex(tier: SudokuTier): Int {
        val n = cur(tier).copy(plays = cur(tier).plays + 1)
        progress.value = progress.value + (tier to n)
        return n.plays
    }

    override suspend fun recordWin(tier: SudokuTier, timeMs: Long, score: Int): SudokuProgress {
        val c = cur(tier)
        val n = c.copy(wins = c.wins + 1, bestTimeMs = c.bestTimeMs?.let { minOf(it, timeMs) } ?: timeMs, bestScore = maxOf(c.bestScore, score))
        progress.value = progress.value + (tier to n)
        return n
    }

    override suspend fun getActiveGame(tier: SudokuTier): SudokuGameState? = saves.value[tier]

    override suspend fun saveActiveGame(state: SudokuGameState) {
        saves.value = saves.value + (state.tier to state)
    }

    override suspend fun clearActiveGame(tier: SudokuTier) {
        saves.value = saves.value - tier
    }
}

class SudokuUseCasesTest {
    // 9x9 puzzle used only as a stub for unlocked 9x9 tiers (never solved here).
    private val sol9 = "534678912672195348198342567859761423426853791713924856961537284287419635345286179"
        .map { it - '0' }.toIntArray()
    private val giv9 = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"
        .map { it - '0' }.toIntArray()
    private val sol4 = intArrayOf(1, 2, 3, 4, 3, 4, 1, 2, 2, 1, 4, 3, 4, 3, 2, 1)

    private fun puzzleFor(tier: SudokuTier, seed: Long): SudokuPuzzle = when (tier.size.n) {
        4 -> SudokuPuzzle(
            tier, sol4.copyOf().also { it[0] = 0; it[15] = 0 }, sol4, seed, SUDOKU_GENERATOR_VERSION, SudokuTechnique.NAKED_SINGLE, true,
        )
        else -> SudokuPuzzle(tier, giv9, sol9, seed, SUDOKU_GENERATOR_VERSION, SudokuTechnique.HIDDEN_SINGLE, true)
    }

    private fun starter(repo: FakeSudokuRepository, seeds: MutableList<Pair<SudokuTier, Long>> = mutableListOf()) =
        StartOrResumeSudoku(repo, Dispatchers.Unconfined, baseSeed = 1234L) { t, s ->
            seeds += t to s
            puzzleFor(t, s)
        }

    // ---- unlock ----

    @Test
    fun easyAndMediumAlwaysOpen() = runTest {
        val repo = FakeSudokuRepository()
        assertTrue(starter(repo)(SudokuTier.EASY) is SudokuStartResult.Started)
        assertTrue(starter(repo)(SudokuTier.MEDIUM) is SudokuStartResult.Started)
    }

    @Test
    fun unlockChainThresholds() = runTest {
        val repo = FakeSudokuRepository()
        val start = starter(repo)
        val locked = start(SudokuTier.HARD) as SudokuStartResult.Locked
        assertEquals(SudokuTier.MEDIUM, locked.requiredTier)
        assertEquals(2, locked.winsNeeded)
        assertEquals(0, locked.winsHave)
        repo.setWins(SudokuTier.MEDIUM, 1)
        assertEquals(1, (start(SudokuTier.HARD) as SudokuStartResult.Locked).winsHave)
        repo.setWins(SudokuTier.MEDIUM, 2)
        assertTrue(start(SudokuTier.HARD) is SudokuStartResult.Started)

        val expert = start(SudokuTier.EXPERT) as SudokuStartResult.Locked
        assertEquals(SudokuTier.HARD, expert.requiredTier)
        assertEquals(4, expert.winsNeeded)
        repo.setWins(SudokuTier.HARD, 4)
        assertTrue(start(SudokuTier.EXPERT) is SudokuStartResult.Started)

        assertEquals(10, (start(SudokuTier.MASTER) as SudokuStartResult.Locked).winsNeeded)
        assertEquals(14, (start(SudokuTier.EXTREME) as SudokuStartResult.Locked).winsNeeded)
        repo.setWins(SudokuTier.EXPERT, 10)
        repo.setWins(SudokuTier.MASTER, 14)
        assertTrue(start(SudokuTier.MASTER) is SudokuStartResult.Started)
        assertTrue(start(SudokuTier.EXTREME) is SudokuStartResult.Started)
    }

    @Test
    fun lockedTierDoesNotGenerateOrCountAPlay() = runTest {
        val repo = FakeSudokuRepository()
        val seeds = mutableListOf<Pair<SudokuTier, Long>>()
        starter(repo, seeds)(SudokuTier.MASTER)
        assertTrue(seeds.isEmpty())
        assertEquals(0, repo.getProgress(SudokuTier.MASTER).plays)
    }

    // ---- start / resume ----

    @Test
    fun startSavesAndSecondCallResumesWithoutRegenerating() = runTest {
        val repo = FakeSudokuRepository()
        val seeds = mutableListOf<Pair<SudokuTier, Long>>()
        val start = starter(repo, seeds)
        val first = start(SudokuTier.EASY) as SudokuStartResult.Started
        assertNotNull(repo.getActiveGame(SudokuTier.EASY))
        assertEquals(1, repo.getProgress(SudokuTier.EASY).plays)

        // progress made, saved, then resume returns that state and does not generate again
        val moved = SudokuGameEngine.enterDigit(SudokuGameEngine.select(first.state, 0), 1, 4_000L)
        SaveSudokuGame(repo)(moved)
        val second = start(SudokuTier.EASY) as SudokuStartResult.Resumed
        assertEquals(1, seeds.size)
        assertEquals(1, second.state.values[0])
        assertEquals(1, repo.getProgress(SudokuTier.EASY).plays)
    }

    @Test
    fun resumeKeepsSavedPuzzleEvenIfGeneratorWouldDiffer() = runTest {
        val repo = FakeSudokuRepository()
        val old = SudokuPuzzle(
            SudokuTier.EASY, sol4.copyOf().also { it[3] = 0 }, sol4, 5L, SUDOKU_GENERATOR_VERSION - 1, SudokuTechnique.NAKED_SINGLE, true,
        )
        repo.saveActiveGame(SudokuGameEngine.newGame(old))
        val seeds = mutableListOf<Pair<SudokuTier, Long>>()
        val r = starter(repo, seeds)(SudokuTier.EASY) as SudokuStartResult.Resumed
        assertTrue(seeds.isEmpty())
        assertEquals(SUDOKU_GENERATOR_VERSION - 1, r.state.puzzle.generatorVersion)
    }

    @Test
    fun resumeHealsAlreadySolvedSave() = runTest {
        val repo = FakeSudokuRepository()
        val p = puzzleFor(SudokuTier.EASY, 1L)
        repo.saveActiveGame(SudokuGameEngine.newGame(p).copy(values = sol4.copyOf()))
        val r = starter(repo)(SudokuTier.EASY) as SudokuStartResult.Resumed
        assertEquals(SudokuStatus.SOLVED, r.state.status)
    }

    @Test
    fun freshPuzzlesGetDistinctSeedsAfterEachFinishedOrAbandonedGame() = runTest {
        val repo = FakeSudokuRepository()
        val seeds = mutableListOf<Pair<SudokuTier, Long>>()
        val start = starter(repo, seeds)
        start(SudokuTier.EASY)
        ResetActiveSudokuGame(repo)(SudokuTier.EASY)
        start(SudokuTier.EASY)
        ResetActiveSudokuGame(repo)(SudokuTier.EASY)
        start(SudokuTier.MEDIUM)
        assertEquals(3, seeds.size)
        assertEquals(3, seeds.map { it.second }.toSet().size)
        assertEquals(2, repo.getProgress(SudokuTier.EASY).plays)
        // deterministic: same inputs, same seed
        assertEquals(
            StartOrResumeSudoku.seedFor(1234L, SudokuTier.EASY, 1),
            seeds[0].second,
        )
        assertNotEquals(
            StartOrResumeSudoku.seedFor(1234L, SudokuTier.EASY, 1),
            StartOrResumeSudoku.seedFor(1234L, SudokuTier.MEDIUM, 1),
        )
    }

    @Test
    fun generatorFailureIsReportedNotThrown() = runTest {
        val repo = FakeSudokuRepository()
        val uc = StartOrResumeSudoku(repo, Dispatchers.Unconfined) { _, _ -> throw IllegalStateException("boom") }
        val r = uc(SudokuTier.EASY)
        assertTrue(r is SudokuStartResult.Failed)
        assertNull(repo.getActiveGame(SudokuTier.EASY))
    }

    @Test
    fun saveOnlyStoresPlayingGames() = runTest {
        val repo = FakeSudokuRepository()
        val s = SudokuGameEngine.newGame(puzzleFor(SudokuTier.EASY, 1L))
        SaveSudokuGame(repo)(s.copy(status = SudokuStatus.GAME_OVER))
        assertNull(repo.getActiveGame(SudokuTier.EASY))
        SaveSudokuGame(repo)(s)
        assertNotNull(repo.getActiveGame(SudokuTier.EASY))
    }

    // ---- hint ----

    @Test
    fun hintUsesSharedBoosterAndSpendsOne() = runTest {
        val inv = FakeInventoryRepository()
        val uc = UseSudokuHint(ConsumeBooster(inv))
        val s = SudokuGameEngine.select(SudokuGameEngine.newGame(puzzleFor(SudokuTier.EASY, 1L)), 0)

        assertEquals(SudokuHintResult.NoCharge, uc(s))
        assertEquals(0, s.hintsUsed)

        inv.add(Booster.HINT, 2)
        val g = uc(s) as SudokuHintResult.Granted
        assertEquals(0, g.hint.cell)
        assertEquals(1, g.state.values[0])
        assertEquals(1, g.state.hintsUsed)
        assertEquals(1, inv.get(Booster.HINT))
    }

    @Test
    fun hintUnavailableSpendsNothing() = runTest {
        val inv = FakeInventoryRepository().also { it.add(Booster.HINT, 1) }
        val uc = UseSudokuHint(ConsumeBooster(inv))
        val s = SudokuGameEngine.newGame(puzzleFor(SudokuTier.EASY, 1L))
        assertEquals(SudokuHintResult.Unavailable, uc(SudokuGameEngine.pause(s)))
        assertEquals(SudokuHintResult.Unavailable, uc(s.copy(values = sol4.copyOf())))
        assertEquals(1, inv.get(Booster.HINT))
    }

    // ---- complete ----

    private class Rig {
        val repo = FakeSudokuRepository()
        val time = FakeTimeProvider(today = 100L)
        val streak = FakeStreakRepository()
        val tracker = AdSessionTracker(time)
        val complete = CompleteSudoku(repo, RecordPlayedToday(streak, time), RecordLevelWon(tracker))
    }

    private fun solved(tier: SudokuTier, elapsed: Long = 50_000L, score: Int = 200): SudokuGameState =
        SudokuGameEngine.newGame(puzzleFor(tier, 1L)).copy(status = SudokuStatus.SOLVED, elapsedMs = elapsed, score = score)

    @Test
    fun winUpdatesProgressStreakPacingAndClearsSave() = runTest {
        val r = Rig()
        val state = solved(SudokuTier.EASY)
        r.repo.saveActiveGame(state.copy(status = SudokuStatus.PLAYING))

        val out = r.complete(state)
        assertTrue(out.won)
        assertEquals(1, out.progress!!.wins)
        assertEquals(50_000L, out.progress!!.bestTimeMs)
        assertEquals(200, out.progress!!.bestScore)
        assertTrue(out.isNewBestTime)
        assertTrue(out.isNewBestScore)
        assertTrue(out.firstWinInTier)
        assertTrue(out.streakDayAdded)
        assertEquals(setOf(100L), r.streak.observePlayedDays().first())
        assertEquals(1, r.tracker.winsSinceLastAd)
        assertNull(r.repo.getActiveGame(SudokuTier.EASY))
    }

    @Test
    fun streakCountsOncePerDayAcrossGames() = runTest {
        val r = Rig()
        assertTrue(r.complete(solved(SudokuTier.EASY)).streakDayAdded)
        assertFalse(r.complete(solved(SudokuTier.EASY)).streakDayAdded)
        assertEquals(setOf(100L), r.streak.observePlayedDays().first())
        assertEquals(2, r.tracker.winsSinceLastAd)
    }

    @Test
    fun bestTimeAndScoreOnlyImprove() = runTest {
        val r = Rig()
        r.complete(solved(SudokuTier.EASY, elapsed = 50_000L, score = 200))
        val worse = r.complete(solved(SudokuTier.EASY, elapsed = 70_000L, score = 150))
        assertFalse(worse.isNewBestTime)
        assertFalse(worse.isNewBestScore)
        assertFalse(worse.firstWinInTier)
        assertEquals(50_000L, worse.progress!!.bestTimeMs)
        assertEquals(200, worse.progress!!.bestScore)
        val better = r.complete(solved(SudokuTier.EASY, elapsed = 40_000L, score = 250))
        assertTrue(better.isNewBestTime)
        assertTrue(better.isNewBestScore)
        assertEquals(3, better.progress!!.wins)
    }

    @Test
    fun secondMediumWinUnlocksHard() = runTest {
        val r = Rig()
        val first = r.complete(solved(SudokuTier.MEDIUM))
        assertTrue(first.justUnlocked.isEmpty())
        val second = r.complete(solved(SudokuTier.MEDIUM))
        assertEquals(listOf(SudokuTier.HARD), second.justUnlocked)
        val third = r.complete(solved(SudokuTier.MEDIUM))
        assertTrue(third.justUnlocked.isEmpty())
    }

    @Test
    fun gameOverClearsSaveWithoutWinStreakOrPacing() = runTest {
        val r = Rig()
        val s = SudokuGameEngine.newGame(puzzleFor(SudokuTier.EASY, 1L))
        r.repo.saveActiveGame(s)
        val out = r.complete(s.copy(status = SudokuStatus.GAME_OVER, hearts = 0))
        assertFalse(out.won)
        assertNull(r.repo.getActiveGame(SudokuTier.EASY))
        assertEquals(0, r.repo.getProgress(SudokuTier.EASY).wins)
        assertTrue(r.streak.observePlayedDays().first().isEmpty())
        assertEquals(0, r.tracker.winsSinceLastAd)
    }

    @Test
    fun completingARunningGameIsANoOp() = runTest {
        val r = Rig()
        val s = SudokuGameEngine.newGame(puzzleFor(SudokuTier.EASY, 1L))
        r.repo.saveActiveGame(s)
        assertFalse(r.complete(s).won)
        assertSame(s, r.repo.getActiveGame(SudokuTier.EASY))
        assertEquals(0, r.repo.getProgress(SudokuTier.EASY).wins)
    }

    // ---- observe ----

    @Test
    fun observeProgressReportsUnlockAndActiveFlags() = runTest {
        val repo = FakeSudokuRepository()
        repo.setWins(SudokuTier.MEDIUM, 2)
        repo.saveActiveGame(SudokuGameEngine.newGame(puzzleFor(SudokuTier.EASY, 1L)))
        val rows = ObserveSudokuProgress(repo)().first().associateBy { it.tier }
        assertEquals(SudokuTier.entries.size, rows.size)
        assertTrue(rows.getValue(SudokuTier.EASY).unlocked)
        assertTrue(rows.getValue(SudokuTier.EASY).hasActiveGame)
        assertTrue(rows.getValue(SudokuTier.HARD).unlocked)
        assertFalse(rows.getValue(SudokuTier.EXPERT).unlocked)
        assertEquals(2, rows.getValue(SudokuTier.HARD).requirementWins)
        assertEquals(0, rows.getValue(SudokuTier.EASY).requirementWins)
    }
}
