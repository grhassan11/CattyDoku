package com.threetech.catpuzzle.ui.screens.game

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.threetech.catpuzzle.domain.model.BoardState
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.model.CellMark
import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.LevelProgress
import com.threetech.catpuzzle.domain.model.Pos
import com.threetech.catpuzzle.domain.model.SavedGame
import com.threetech.catpuzzle.domain.model.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    /** Solution of the fixture: row -> col = 1,3,0,2. */
    private val solution = listOf(Pos(0, 1), Pos(1, 3), Pos(2, 0), Pos(3, 2))

    private fun vmTest(
        rig: GameRig = GameRig(),
        block: suspend TestScope.(GameRig, GameViewModel) -> Unit,
    ) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            block(rig, rig.viewModel())
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun GameViewModel.ready(): GameContentUi = (state.value as GameUiState.Ready).content

    private fun GameContentUi.markAt(row: Int, col: Int): CellMarkUi = board.cell(row, col).mark

    private suspend inline fun <reified T : GameUiState> ReceiveTurbine<GameUiState>.awaitUntil(): T {
        while (true) {
            val item = awaitItem()
            if (item is T) return item
        }
    }

    private suspend inline fun <reified T : GameUiEvent> ReceiveTurbine<GameUiEvent>.awaitEvent(): T {
        while (true) {
            val item = awaitItem()
            if (item is T) return item
        }
    }

    // ---- load ----

    @Test
    fun loadsLevelOneWithStarterBoostersAndFullHearts() = vmTest { rig, vm ->
        val c = vm.ready()
        assertEquals(1, c.levelNumber)
        assertEquals(4, c.board.size)
        assertEquals(3, c.hearts)
        assertEquals(0, c.score)
        assertEquals(5, c.boosters.catCharges)
        assertEquals(5, c.boosters.hintCharges)
        assertTrue(c.boosters.thirdLocked)
        assertEquals(1, rig.flags.flags.first().lastPlayedLevel)
    }

    @Test
    fun doesNotRecordStreakOnLevelStart() = vmTest { rig, _ ->
        // win-only streak: opening a level must not count the day (RECORD_STREAK_ON_LEVEL_START = false)
        assertFalse(GameViewModel.RECORD_STREAK_ON_LEVEL_START)
        assertTrue(rig.streak.observePlayedDays().first().isEmpty())
    }

    @Test
    fun resumesAfterLastWonLevelAtNextLevel() = vmTest(
        GameRig(generator = RecordingGenerator { fixturePuzzle(it) }).also { rig ->
            rig.progress.seedWonLevel(1)
        },
    ) { rig, vm ->
        // level 1 already won, nothing in progress -> start must be level 2
        assertEquals(2, vm.ready().levelNumber)
        assertNotNull(rig.puzzles.rows[2])
    }

    @Test
    fun generationFailureShowsFailedThenRetrySucceeds() = vmTest(GameRig(seedLevel1 = false)) { rig, vm ->
        vm.state.test {
            val failed = awaitUntil<GameUiState.Failed>()
            assertEquals(1, failed.levelNumber)
            rig.puzzles.rows[1] = fixturePuzzle(1)
            vm.onRetryLoad()
            val ready = awaitUntil<GameUiState.Ready>()
            assertEquals(1, ready.content.levelNumber)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun prefetchesNextLevelAfterStart() = vmTest(GameRig(generator = RecordingGenerator { fixturePuzzle(it) })) { rig, _ ->
        assertEquals(listOf(2), rig.generator.levels)
        assertNotNull(rig.puzzles.rows[2])
    }

    @Test
    fun prefetchFailureDoesNotBreakPlay() = vmTest { rig, vm ->
        assertTrue(rig.generator.levels.isNotEmpty() && rig.generator.levels.all { it == 2 }) // asked, failed, swallowed
        assertTrue(vm.state.value is GameUiState.Ready)
    }

    // ---- input ----

    @Test
    fun tapTogglesXWithoutCost() = vmTest { _, vm ->
        vm.onCellTap(Pos(0, 0))
        assertEquals(CellMarkUi.X, vm.ready().markAt(0, 0))
        assertEquals(3, vm.ready().hearts)
        assertTrue(vm.ready().canUndo)
        vm.onCellTap(Pos(0, 0))
        assertEquals(CellMarkUi.EMPTY, vm.ready().markAt(0, 0))
    }

    @Test
    fun tapThenDoubleTapReplacesXWithCat() = vmTest { _, vm ->
        vm.onCellTap(Pos(0, 1))
        vm.onCellDoubleTap(Pos(0, 1))
        assertEquals(CellMarkUi.CAT, vm.ready().markAt(0, 1))
        vm.onCellDoubleTap(Pos(0, 1))
        assertEquals(CellMarkUi.EMPTY, vm.ready().markAt(0, 1))
    }

    @Test
    fun dragMarksXAcrossCells() = vmTest { _, vm ->
        vm.onDrag(listOf(Pos(0, 0), Pos(0, 1), Pos(0, 2)))
        val c = vm.ready()
        assertEquals(CellMarkUi.X, c.markAt(0, 0))
        assertEquals(CellMarkUi.X, c.markAt(0, 1))
        assertEquals(CellMarkUi.X, c.markAt(0, 2))
    }

    @Test
    fun undoRevertsLastMove() = vmTest { _, vm ->
        vm.onCellTap(Pos(2, 2))
        vm.onUndo()
        assertEquals(CellMarkUi.EMPTY, vm.ready().markAt(2, 2))
        assertFalse(vm.ready().canUndo)
    }

    @Test
    fun wrongCatLosesHeart() = vmTest { _, vm ->
        vm.events.test {
            vm.onCellDoubleTap(Pos(0, 0)) // solution is (0,1)
            assertEquals(GameUiEvent.Haptic(HapticKind.WRONG), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, vm.ready().hearts)
        assertEquals(CellMarkUi.CAT, vm.ready().markAt(0, 0))
    }

    @Test
    fun conflictingCatsAreFlagged() = vmTest { _, vm ->
        vm.onCellDoubleTap(Pos(0, 1))
        vm.onCellDoubleTap(Pos(1, 2)) // touches (0,1) diagonally
        val c = vm.ready()
        assertTrue(c.board.cell(0, 1).conflict)
        assertTrue(c.board.cell(1, 2).conflict)
    }

    @Test
    fun hapticsSettingOffSuppressesHapticEvents() = vmTest(GameRig(settings = Settings(haptics = false, sfx = false))) { _, vm ->
        vm.events.test {
            vm.onCellTap(Pos(0, 0))
            vm.onCellDoubleTap(Pos(3, 3))
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- sfx ----

    // Correct, conflict-free placement plays the soft chime (not the meow) with a light tick.
    private val meow = GameUiEvent.Sfx(SfxKind.CHIME)

    @Test
    fun catPlacementEmitsMeow() = vmTest { _, vm ->
        vm.events.test {
            vm.onCellDoubleTap(Pos(0, 1)) // correct
            assertEquals(GameUiEvent.Haptic(HapticKind.TICK), awaitItem())
            assertEquals(meow, awaitItem())
            vm.onCellDoubleTap(Pos(3, 3)) // wrong cat: heart-lost haptic + wrong-placement sound, no meow
            assertEquals(GameUiEvent.Haptic(HapticKind.WRONG), awaitItem())
            assertEquals(GameUiEvent.Sfx(SfxKind.WRONG), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun bossLevelPlacementUsesBareChimeNotCat() = vmTest(
        GameRig().also { rig ->
            rig.puzzles.rows[10] = fixturePuzzle(10)
            kotlinx.coroutines.runBlocking { rig.flags.update { it.copy(lastPlayedLevel = 10) } }
        },
    ) { _, vm ->
        assertTrue(vm.ready().pieceSkin.isAnimal)
        vm.events.test {
            vm.onCellDoubleTap(Pos(0, 1)) // correct (solution cols 1,3,0,2)
            assertEquals(GameUiEvent.Haptic(HapticKind.TICK), awaitItem())
            assertEquals(GameUiEvent.Sfx(SfxKind.CHIME_SOLO), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun removeCatXAndUndoDoNotMeow() = vmTest { _, vm ->
        vm.events.test {
            vm.onCellDoubleTap(Pos(0, 1))
            awaitItem(); awaitItem() // haptic + meow
            vm.onCellDoubleTap(Pos(0, 1)) // remove
            vm.onCellTap(Pos(2, 2)) // X
            vm.onUndo()
            val rest = cancelAndConsumeRemainingEvents()
            assertTrue(rest.none { it is app.cash.turbine.Event.Item && it.value == meow })
        }
    }

    @Test
    fun xPlacementPlaysCrossButRemovalAndUndoDoNot() = vmTest { _, vm ->
        vm.events.test {
            vm.onCellTap(Pos(2, 2)) // X placed
            assertEquals(GameUiEvent.Haptic(HapticKind.TICK), awaitItem())
            assertEquals(GameUiEvent.Sfx(SfxKind.CROSS), awaitItem())
            vm.onCellTap(Pos(2, 2)) // X removed
            vm.onUndo() // restores the X: still silent
            val rest = cancelAndConsumeRemainingEvents()
                .mapNotNull { (it as? app.cash.turbine.Event.Item<*>)?.value }
            assertTrue(rest.none { it == GameUiEvent.Sfx(SfxKind.CROSS) })
        }
    }

    @Test
    fun catBoosterSuccessEmitsMeowFailureDoesNot() = vmTest { _, vm ->
        vm.events.test {
            vm.onCatBooster()
            assertEquals(GameUiEvent.Haptic(HapticKind.TICK), awaitItem())
            assertEquals(meow, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun mouseRig(): GameRig = GameRig(seedLevel1 = false).also { rig ->
        rig.puzzles.rows[11] = fixturePuzzle(11)
        kotlinx.coroutines.runBlocking { rig.flags.update { it.copy(lastPlayedLevel = 11) } }
    }

    @Test
    fun mouseBoosterMarksThreeSafeXsAndSpendsOneCharge() = vmTest(mouseRig()) { rig, vm ->
        val before = vm.ready()
        assertEquals(11, before.levelNumber)
        assertFalse(before.boosters.thirdLocked)
        assertEquals(5, before.boosters.mouseCharges)
        vm.onMouseBooster()
        advanceUntilIdle()
        val c = vm.ready()
        assertEquals(4, c.boosters.mouseCharges)
        assertEquals(4, rig.inventory.get(Booster.MOUSE))
        val xs = c.board.cells.filter { it.mark == CellMarkUi.X }
        assertEquals(3, xs.size)
        xs.forEach { cell -> assertFalse(solution.contains(Pos(cell.row, cell.col))) }
        assertEquals(before.hearts, c.hearts)
        assertEquals(before.score, c.score)
    }

    @Test
    fun mouseBoosterWithoutChargesEmitsNoCharges() = vmTest(mouseRig()) { rig, vm ->
        vm.ready()
        repeat(5) { rig.inventory.consume(Booster.MOUSE) }
        vm.onBoosterRewardGranted()
        advanceUntilIdle()
        vm.events.test {
            vm.onMouseBooster()
            assertEquals(GameUiEvent.NoCharges(Booster.MOUSE), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun mouseBoosterIgnoredBelowUnlockLevel() = vmTest { rig, vm ->
        assertTrue(vm.ready().boosters.thirdLocked)
        vm.onMouseBooster()
        advanceUntilIdle()
        assertEquals(5, rig.inventory.get(Booster.MOUSE))
        assertTrue(vm.ready().board.cells.none { it.mark == CellMarkUi.X })
    }

    @Test
    fun catBoosterWithoutChargesDoesNotMeow() = vmTest(
        GameRig().also { kotlinx.coroutines.runBlocking { it.flags.setInstallDateIfAbsent(1L) } },
    ) { _, vm ->
        vm.events.test {
            vm.onCatBooster()
            assertEquals(GameUiEvent.NoCharges(Booster.CAT), awaitItem())
            assertEquals(GameUiEvent.Sfx(SfxKind.BLOCKED), awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sfxSettingOffSuppressesMeow() = vmTest(GameRig(settings = Settings(sfx = false, haptics = false))) { _, vm ->
        vm.events.test {
            vm.onCellDoubleTap(Pos(0, 1))
            vm.onCatBooster()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun hintEmitsHintSound() = vmTest { _, vm ->
        vm.events.test {
            vm.onHint()
            assertEquals(GameUiEvent.Sfx(SfxKind.HINT), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun doubleTapOnLockedBoosterCatEmitsBlocked() = vmTest { _, vm ->
        vm.onCatBooster()
        val cat = vm.ready().board.cells.single { it.mark == CellMarkUi.CAT }
        vm.events.test {
            vm.onCellDoubleTap(Pos(cat.row, cat.col))
            assertEquals(GameUiEvent.Sfx(SfxKind.BLOCKED), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun winningPlacementPlaysAwardOnMilestoneLevelWithoutMeow() = vmTest { _, vm ->
        vm.events.test {
            for (p in solution) vm.onCellDoubleTap(p)
            val rest = cancelAndConsumeRemainingEvents()
                .mapNotNull { (it as? app.cash.turbine.Event.Item<*>)?.value }
            assertEquals(3, rest.count { it == meow }) // 3 non-winning placements; the 4th has none
            assertEquals(1, rest.count { it == GameUiEvent.Sfx(SfxKind.AWARD) }) // level 1 = milestone
            assertTrue(rest.none { it == GameUiEvent.Sfx(SfxKind.WIN) })
        }
    }

    @Test
    fun losingPlacementPlaysWrongOnly() = vmTest { _, vm ->
        vm.events.test {
            vm.onCellDoubleTap(Pos(0, 0))
            vm.onCellDoubleTap(Pos(1, 0))
            vm.onCellDoubleTap(Pos(2, 1))
            val items = cancelAndConsumeRemainingEvents()
                .mapNotNull { (it as? app.cash.turbine.Event.Item<*>)?.value }
            assertEquals(3, items.count { it == GameUiEvent.Sfx(SfxKind.WRONG) })
            assertTrue(items.none { it == meow || it == GameUiEvent.Sfx(SfxKind.WIN) || it == GameUiEvent.Sfx(SfxKind.AWARD) })
        }
    }

    // ---- boosters ----

    @Test
    fun hintSpendsChargeInStateAndInventory() = vmTest { rig, vm ->
        vm.onHint()
        advanceUntilIdle()
        val c = vm.ready()
        assertEquals(4, c.boosters.hintCharges)
        assertNotNull(c.hint)
        assertTrue(c.board.cells.any { it.hintRole != HintRole.NONE })
        assertEquals(4, rig.inventory.get(Booster.HINT))
        // hint clears on the next board change
        vm.onCellTap(Pos(0, 0))
        assertNull(vm.ready().hint)
    }

    @Test
    fun catBoosterPlacesLockedSolutionCatAndSpendsCharge() = vmTest { rig, vm ->
        vm.onCatBooster()
        advanceUntilIdle()
        val c = vm.ready()
        val cats = c.board.cells.filter { it.mark == CellMarkUi.CAT }
        assertEquals(1, cats.size)
        assertTrue(cats.single().locked)
        assertTrue(Pos(cats.single().row, cats.single().col) in solution)
        assertEquals(3, c.hearts)
        assertEquals(4, c.boosters.catCharges)
        assertEquals(4, rig.inventory.get(Booster.CAT))
    }

    @Test
    fun noChargesEmitsEventInsteadOfActing() = vmTest(
        GameRig().also { kotlinx.coroutines.runBlocking { it.flags.setInstallDateIfAbsent(1L) } },
    ) { _, vm ->
        vm.events.test {
            vm.onHint()
            assertEquals(GameUiEvent.NoCharges(Booster.HINT), awaitItem())
            assertEquals(GameUiEvent.Sfx(SfxKind.BLOCKED), awaitItem())
            vm.onCatBooster()
            assertEquals(GameUiEvent.NoCharges(Booster.CAT), awaitItem())
            assertEquals(GameUiEvent.Sfx(SfxKind.BLOCKED), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, vm.ready().boosters.hintCharges)
    }

    // ---- autosave / restore / timer ----

    @Test
    fun autosavesEveryMove() = vmTest { rig, vm ->
        vm.onCellTap(Pos(0, 0))
        advanceUntilIdle()
        val s1 = rig.saved.rows[1]!!
        assertEquals(CellMark.X, s1.state.board[0, 0])

        vm.onCellTap(Pos(1, 1))
        advanceUntilIdle()
        val s2 = rig.saved.rows[1]!!
        assertEquals(CellMark.X, s2.state.board[1, 1])
    }

    @Test
    fun restoresAutosaveOnStart() = vmTest(
        GameRig().also { rig ->
            val board = BoardState.empty(4).withMark(0, 0, CellMark.X)
            val state = GameState.initial(4).copy(board = board, hearts = 2)
            rig.saved.rows[1] = SavedGame(1, state, elapsedMs = 5_000L, savedAtMs = 1L)
        },
    ) { rig, vm ->
        val c = vm.ready()
        assertEquals(2, c.hearts)
        assertEquals(CellMarkUi.X, c.markAt(0, 0))
        assertEquals(5, c.boosters.catCharges) // charges come from inventory, not the save

        vm.onCellTap(Pos(1, 1))
        advanceUntilIdle()
        assertEquals(5_000L, rig.saved.rows[1]!!.elapsedMs) // fake clock did not move
    }

    @Test
    fun timerPausesWhileAppStopped() = vmTest { rig, vm ->
        rig.time.now = 4_000L // 3s played since load at t=1000
        vm.onAppStopped()
        advanceUntilIdle()
        assertEquals(3_000L, rig.saved.rows[1]!!.elapsedMs)

        rig.time.now = 10_000L // 6s in background: must not count
        vm.onAppStarted()
        rig.time.now = 11_000L // 1s played
        vm.onCellTap(Pos(0, 0))
        advanceUntilIdle()
        assertEquals(4_000L, rig.saved.rows[1]!!.elapsedMs)
    }

    // ---- win / lose ----

    @Test
    fun winCompletesLevelAndClearsAutosave() = vmTest { rig, vm ->
        vm.events.test {
            for (p in solution) vm.onCellDoubleTap(p)
            val won = awaitEvent<GameUiEvent.LevelWon>()
            assertEquals(1, won.level)
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()
        val outcome = vm.ready().outcome as OutcomeUi.Won
        assertEquals(900, outcome.score) // 4 * 100 + 500 time bonus at 0s
        assertEquals(3, outcome.heartsLeft)
        assertNotNull(rig.progress.get(1))
        assertEquals(900, rig.progress.get(1)!!.bestScore)
        assertNull(rig.saved.rows[1])
        assertTrue(20_000L in rig.streak.observePlayedDays().first()) // win counts the day (win-only streak)
    }

    @Test
    fun replayWinDoesNotRecordStreakDay() = vmTest { rig, vm ->
        rig.progress.save(LevelProgress(1, 500, 60_000L, 1_000L, mistakes = 0, hintsUsed = 0)) // already completed
        vm.events.test {
            for (p in solution) vm.onCellDoubleTap(p)
            awaitEvent<GameUiEvent.LevelWon>()
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()
        assertEquals(900, rig.progress.get(1)!!.bestScore) // progress still merged
        assertTrue(rig.streak.observePlayedDays().first().isEmpty()) // replay: no streak day
    }

    @Test
    fun winIgnoresFurtherInputAndNextLevelLoadsLevelTwo() = vmTest(
        GameRig(generator = RecordingGenerator { fixturePuzzle(it) }),
    ) { rig, vm ->
        for (p in solution) vm.onCellDoubleTap(p)
        vm.onCellTap(Pos(0, 0)) // ignored
        assertEquals(CellMarkUi.CAT, vm.ready().markAt(0, 1))
        assertNull(rig.saved.rows[1])

        vm.onNextLevel()
        advanceUntilIdle()
        assertEquals(2, vm.ready().levelNumber)
        assertNull(vm.ready().outcome)
        assertEquals(2, rig.flags.flags.first().lastPlayedLevel)
    }

    @Test
    fun threeWrongCatsLoseTheLevelAndRetryRestarts() = vmTest { rig, vm ->
        vm.events.test {
            vm.onCellDoubleTap(Pos(0, 0))
            vm.onCellDoubleTap(Pos(1, 0))
            vm.onCellDoubleTap(Pos(2, 1))
            val lost = awaitEvent<GameUiEvent.LevelLost>()
            assertEquals(1, lost.level)
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()
        val c = vm.ready()
        assertEquals(0, c.hearts)
        assertTrue(c.outcome is OutcomeUi.Lost)
        assertNull(rig.progress.get(1))
        assertNull(rig.saved.rows[1])

        vm.onRestart()
        advanceUntilIdle()
        val again = vm.ready()
        assertEquals(3, again.hearts)
        assertNull(again.outcome)
        assertEquals(0, again.board.cells.count { it.mark != CellMarkUi.EMPTY })
        assertEquals(3, rig.saved.rows[1]!!.state.hearts) // fresh autosave replaced stale state
    }

    @Test
    fun removingStrayWrongCatLeavingSolvedBoardWins() = vmTest { rig, vm ->
        vm.onCellDoubleTap(Pos(0, 1))
        vm.onCellDoubleTap(Pos(1, 3))
        vm.onCellDoubleTap(Pos(3, 3)) // wrong: heart lost
        vm.onCellDoubleTap(Pos(2, 0))
        vm.onCellDoubleTap(Pos(3, 2)) // 5 cats incl. the stray: engine does not report Solved
        assertNull(vm.ready().outcome)
        vm.onCellDoubleTap(Pos(3, 3)) // remove stray -> board is a valid full solution
        advanceUntilIdle()
        val outcome = vm.ready().outcome
        assertTrue(outcome is OutcomeUi.Won)
        assertEquals(2, (outcome as OutcomeUi.Won).heartsLeft)
        assertNotNull(rig.progress.get(1))
        assertNull(rig.saved.rows[1])
    }

    @Test
    fun savedPlayingStateAlreadySolvedLoadsAsWon() = vmTest(
        GameRig().also { rig ->
            var board = BoardState.empty(4)
            for (p in solution) board = board.withMark(p.row, p.col, CellMark.CAT)
            rig.saved.rows[1] = SavedGame(1, GameState.initial(4).copy(board = board), elapsedMs = 1_000L, savedAtMs = 1L)
        },
    ) { rig, vm ->
        advanceUntilIdle()
        assertTrue(vm.ready().outcome is OutcomeUi.Won)
        assertNotNull(rig.progress.get(1))
        assertNull(rig.saved.rows[1])
    }

    // ---- starter cat ----

    private fun GameContentUi.cats() = board.cells.filter { it.mark == CellMarkUi.CAT }

    @Test
    fun freshStartHasOneLockedFreeSolutionCat() = vmTest(GameRig(starterCat = true)) { rig, vm ->
        val c = vm.ready()
        val cats = c.cats()
        assertEquals(1, cats.size)
        assertTrue(cats.single().locked)
        assertTrue(Pos(cats.single().row, cats.single().col) in solution)
        assertEquals(3, c.hearts)
        assertEquals(0, c.score) // no placement score for the free cat
        assertFalse(c.canUndo)
        advanceUntilIdle()
        assertEquals(5, c.boosters.hintCharges) // no hint cost
    }

    @Test
    fun undoAndTapsNeverRemoveStarterCat() = vmTest(GameRig(starterCat = true)) { _, vm ->
        val starter = vm.ready().cats().single()
        val pos = Pos(starter.row, starter.col)
        vm.onUndo()
        vm.onCellTap(pos)
        vm.onCellDoubleTap(pos)
        assertEquals(1, vm.ready().cats().size)
        assertTrue(vm.ready().cats().single().locked)
    }

    @Test
    fun restoreDoesNotApplyStarterCatTwice() = vmTest(
        GameRig(starterCat = true).also { rig ->
            val p = fixturePuzzle(1)
            val pos = com.threetech.catpuzzle.domain.engine.StarterCatPicker().pick(p)!!
            val state = com.threetech.catpuzzle.domain.engine.GameEngine()
                .placeStarter(p, GameState.initial(4), pos).state
            rig.saved.rows[1] = SavedGame(1, state, elapsedMs = 1_000L, savedAtMs = 1L)
        },
    ) { _, vm ->
        assertEquals(1, vm.ready().cats().size)
    }

    @Test
    fun restoreOfProgressedSaveWithoutStarterIsLeftAlone() = vmTest(
        GameRig(starterCat = true).also { rig ->
            val state = GameState.initial(4).copy(
                board = BoardState.empty(4).withMark(0, 0, CellMark.X),
                undoStack = listOf(
                    com.threetech.catpuzzle.domain.model.UndoEntry(
                        listOf(com.threetech.catpuzzle.domain.model.CellChange(0, CellMark.EMPTY)),
                    ),
                ),
            )
            rig.saved.rows[1] = SavedGame(1, state, elapsedMs = 1_000L, savedAtMs = 1L)
        },
    ) { _, vm ->
        assertEquals(0, vm.ready().cats().size) // real progress: no retroactive starter cat
    }

    @Test
    fun restoreOfUntouchedSavePlacesStarterCat() = vmTest(
        GameRig(starterCat = true).also { rig ->
            rig.saved.rows[1] = SavedGame(1, GameState.initial(4), elapsedMs = 1_000L, savedAtMs = 1L)
        },
    ) { rig, vm ->
        val cats = vm.ready().cats()
        assertEquals(1, cats.size)
        assertTrue(cats.single().locked)
        assertTrue(Pos(cats.single().row, cats.single().col) in solution)
        advanceUntilIdle()
        assertEquals(1, rig.saved.rows[1]!!.state.lockedCells.size) // persisted
        assertEquals(1_000L, rig.saved.rows[1]!!.elapsedMs)
    }

    @Test
    fun retryAfterLossReappliesStarterCat() = vmTest(GameRig(starterCat = true)) { rig, vm ->
        vm.onCellDoubleTap(Pos(0, 0))
        vm.onCellDoubleTap(Pos(1, 0))
        vm.onCellDoubleTap(Pos(2, 1))
        advanceUntilIdle()
        assertTrue(vm.ready().outcome is OutcomeUi.Lost)

        vm.onRestart()
        advanceUntilIdle()
        val again = vm.ready()
        assertEquals(3, again.hearts)
        assertEquals(1, again.cats().size)
        assertTrue(again.cats().single().locked)
        assertEquals(1, rig.saved.rows[1]!!.state.lockedCells.size)
    }

    /** Rig whose start level is [level] (fresh, no save), with the fixture served under that number. */
    private fun starterRigAt(level: Int): GameRig = GameRig(starterCat = true).also { rig ->
        rig.puzzles.rows[level] = fixturePuzzle(level)
        kotlinx.coroutines.runBlocking { rig.flags.update { it.copy(lastPlayedLevel = level) } }
    }

    @Test
    fun levelTenGetsStarterCat() = vmTest(starterRigAt(10)) { _, vm ->
        assertEquals(10, vm.ready().levelNumber)
        assertEquals(1, vm.ready().cats().size)
        assertTrue(vm.ready().cats().single().locked)
    }

    @Test
    fun levelElevenGetsNoStarterCat() = vmTest(starterRigAt(11)) { rig, vm ->
        assertEquals(11, vm.ready().levelNumber)
        assertEquals(0, vm.ready().cats().size)
        advanceUntilIdle()
        assertTrue(rig.saved.rows[11]?.state?.lockedCells?.isEmpty() ?: true)
    }

    @Test
    fun restartAtLevelElevenGetsNoStarterCat() = vmTest(starterRigAt(11)) { _, vm ->
        vm.onRestart()
        advanceUntilIdle()
        assertEquals(0, vm.ready().cats().size)
    }

    @Test
    fun restoreOfUntouchedSaveAtLevelElevenGetsNoStarterCat() = vmTest(
        GameRig(starterCat = true).also { rig ->
            rig.puzzles.rows[11] = fixturePuzzle(11)
            rig.saved.rows[11] = SavedGame(11, GameState.initial(4), elapsedMs = 1_000L, savedAtMs = 1L)
        },
    ) { rig, vm ->
        assertEquals(11, vm.ready().levelNumber)
        assertEquals(0, vm.ready().cats().size)
        advanceUntilIdle()
        assertTrue(rig.saved.rows[11]!!.state.lockedCells.isEmpty())
    }

    @Test
    fun restoreAtLevelElevenKeepsStarterFromOlderBuild() = vmTest(
        GameRig(starterCat = true).also { rig ->
            val p = fixturePuzzle(11)
            rig.puzzles.rows[11] = p
            val pos = com.threetech.catpuzzle.domain.engine.StarterCatPicker().pick(p)!!
            val state = com.threetech.catpuzzle.domain.engine.GameEngine()
                .placeStarter(p, GameState.initial(4), pos).state
            rig.saved.rows[11] = SavedGame(11, state, elapsedMs = 1_000L, savedAtMs = 1L)
        },
    ) { _, vm ->
        assertEquals(1, vm.ready().cats().size) // not stripped
    }

    @Test
    fun winAtLevelElevenScoresAllPlacements() = vmTest(starterRigAt(11)) { _, vm ->
        for (p in solution) vm.onCellDoubleTap(p)
        advanceUntilIdle()
        val outcome = vm.ready().outcome as OutcomeUi.Won
        assertEquals(900, outcome.score) // 4 * 100 + 500 time bonus at 0s
    }

    @Test
    fun winWithStarterCatScoresOnlyPlayerPlacements()= vmTest(GameRig(starterCat = true)) { _, vm ->
        for (p in solution) vm.onCellDoubleTap(p) // starter cell is a no-op
        advanceUntilIdle()
        val outcome = vm.ready().outcome as OutcomeUi.Won
        assertEquals(800, outcome.score) // 3 * 100 + 500 time bonus at 0s
    }

    // ---- settings dialog ----

    @Test
    fun settingToggleIsPersistedAndPublished() = vmTest { rig, vm ->
        assertTrue(vm.ready().settings.music)
        vm.onSettingChanged(SettingToggle.MUSIC, false)
        vm.onSettingChanged(SettingToggle.HAPTICS, false)
        vm.onSettingChanged(SettingToggle.PATTERN_MODE, true)
        advanceUntilIdle()
        val stored = rig.settingsRepo.settings.first()
        assertFalse(stored.music)
        assertFalse(stored.haptics)
        assertTrue(stored.patternMode)
        val ui = vm.ready()
        assertFalse(ui.settings.music)
        assertFalse(ui.settings.haptics)
        assertTrue(ui.patternMode)
        assertTrue(ui.settings.sfx) // untouched default stays
    }

    @Test
    fun winOutcomeCarriesHintsUsed() = vmTest { _, vm ->
        vm.onHint()
        for (p in solution) vm.onCellDoubleTap(p)
        advanceUntilIdle()
        val outcome = vm.ready().outcome as OutcomeUi.Won
        assertEquals(1, outcome.hintsUsed)
    }

    // ---- rewarded: revive + booster grant ----

    private fun GameViewModel.loseLevelOne() {
        onCellDoubleTap(Pos(0, 0))
        onCellDoubleTap(Pos(1, 0))
        onCellDoubleTap(Pos(2, 1))
    }

    @Test
    fun reviveAfterLossContinuesSameLevelWithOneHeart() = vmTest { rig, vm ->
        vm.loseLevelOne()
        advanceUntilIdle()
        assertTrue(vm.ready().outcome is OutcomeUi.Lost)
        assertEquals(0, vm.ready().hearts)
        val catsBefore = vm.ready().cats().size

        vm.onRevive()
        advanceUntilIdle()
        val c = vm.ready()
        assertNull(c.outcome)
        assertEquals(1, c.hearts)
        assertEquals(1, c.levelNumber)
        assertEquals("board kept", catsBefore, c.cats().size)
        assertNotNull("autosave written back", rig.saved.rows[1])
        assertEquals(1, rig.saved.rows[1]!!.state.hearts)

        // Play continues: a correct cat lands, a wrong one costs the last heart and loses again.
        vm.onCellDoubleTap(Pos(3, 2))
        assertEquals(CellMarkUi.CAT, vm.ready().markAt(3, 2))
        vm.onCellDoubleTap(Pos(0, 3))
        advanceUntilIdle()
        assertTrue(vm.ready().outcome is OutcomeUi.Lost)
    }

    @Test
    fun reviveIsIgnoredUnlessTheLoseDialogIsShowing() = vmTest { _, vm ->
        vm.onRevive()
        assertEquals(3, vm.ready().hearts)
        assertNull(vm.ready().outcome)
        for (p in solution) vm.onCellDoubleTap(p)
        advanceUntilIdle()
        assertTrue(vm.ready().outcome is OutcomeUi.Won)
        vm.onRevive()
        assertTrue(vm.ready().outcome is OutcomeUi.Won)
    }

    @Test
    fun reviveTwiceInARowOnlyRevivesOnce() = vmTest { _, vm ->
        vm.loseLevelOne()
        advanceUntilIdle()
        vm.onRevive()
        vm.onRevive()
        assertEquals(1, vm.ready().hearts)
    }

    @Test
    fun boosterRewardGrantedMirrorsInventoryIntoRunningGame() = vmTest { rig, vm ->
        assertEquals(5, vm.ready().boosters.catCharges)
        rig.inventory.add(Booster.CAT, 1)
        rig.inventory.add(Booster.HINT, 1)
        rig.inventory.add(Booster.MOUSE, 2)
        vm.onBoosterRewardGranted()
        advanceUntilIdle()
        val b = vm.ready().boosters
        assertEquals(6, b.catCharges)
        assertEquals(6, b.hintCharges)
        assertEquals(7, b.mouseCharges) // 5 starter + 2 granted
    }

    @Test
    fun grantedCatChargeIsUsableAfterTheStockWasEmpty() = vmTest { rig, vm ->
        repeat(5) { rig.inventory.consume(Booster.CAT) }
        // game state still mirrors the old 5; refresh to the real 0, then grant 1
        vm.onBoosterRewardGranted()
        advanceUntilIdle()
        assertEquals(0, vm.ready().boosters.catCharges)
        rig.inventory.add(Booster.CAT, 1)
        vm.onBoosterRewardGranted()
        advanceUntilIdle()
        assertEquals(1, vm.ready().boosters.catCharges)
        vm.onCatBooster()
        advanceUntilIdle()
        assertEquals(1, vm.ready().cats().size)
        assertEquals(0, vm.ready().boosters.catCharges)
        assertEquals(0, rig.inventory.get(Booster.CAT))
    }

    // ---- helpers ----

    /** Test helper: a finished level 1 and last-played 1, so start resolves to level 2. */
    private fun com.threetech.catpuzzle.domain.usecase.FakeProgressRepository.seedWonLevel(level: Int) {
        kotlinx.coroutines.runBlocking {
            save(LevelProgress(level, bestScore = 100, bestTimeMs = 1_000L, completedAt = 1L, mistakes = 0, hintsUsed = 0))
        }
    }
}
