package com.threetech.catpuzzle.ui.screens.game

import com.threetech.catpuzzle.domain.engine.BoardValidator
import com.threetech.catpuzzle.domain.engine.GameFixtures
import com.threetech.catpuzzle.domain.model.BoardState
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.model.CellMark
import com.threetech.catpuzzle.domain.model.GameResult
import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.Hint
import com.threetech.catpuzzle.domain.model.HintRule
import com.threetech.catpuzzle.domain.model.HintTechnique
import com.threetech.catpuzzle.domain.model.MistakeKind
import com.threetech.catpuzzle.domain.model.Pos
import com.threetech.catpuzzle.domain.model.ScoreBreakdown
import com.threetech.catpuzzle.domain.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameUiMapperTest {
    private val puzzle = GameFixtures.unique4() // solution cols 1,3,0,2

    private fun content(
        state: GameState = GameState.initial(4),
        hint: Hint? = null,
        settings: Settings = Settings(),
        level: Int = 1,
        outcome: OutcomeUi? = null,
    ): GameContentUi {
        val p = fixturePuzzle(level)
        val analysis = BoardValidator.analyse(p, state.board)
        return GameUiMapper.content(p, state, analysis, hint, score = 123, settings = settings, outcome = outcome)
    }

    @Test
    fun mapsBasicFields() {
        val c = content(GameState.initial(4, mapOf(Booster.CAT to 2, Booster.HINT to 7)))
        assertEquals(1, c.levelNumber)
        assertEquals(123, c.score)
        assertEquals(3, c.hearts)
        assertEquals(3, c.maxHearts)
        assertEquals(2, c.boosters.catCharges)
        assertEquals(7, c.boosters.hintCharges)
        assertEquals(16, c.board.cells.size)
        assertEquals(4, c.regions.size)
        assertFalse(c.canUndo)
        assertFalse(c.patternMode)
    }

    @Test
    fun cellsKeepRowColRegionAndMark() {
        val board = BoardState.empty(4).withMark(1, 2, CellMark.X).withMark(0, 1, CellMark.CAT)
        val c = content(GameState.initial(4).copy(board = board))
        val x = c.board.cell(1, 2)
        assertEquals(1, x.row)
        assertEquals(2, x.col)
        assertEquals(puzzle.regionAt(1, 2), x.region)
        assertEquals(CellMarkUi.X, x.mark)
        assertEquals(CellMarkUi.CAT, c.board.cell(0, 1).mark)
        assertEquals(CellMarkUi.EMPTY, c.board.cell(3, 3).mark)
    }

    @Test
    fun conflictsAndLockedAreFlagged() {
        val board = BoardState.empty(4).withMark(0, 1, CellMark.CAT).withMark(1, 2, CellMark.CAT)
        val c = content(GameState.initial(4).copy(board = board, lockedCells = setOf(1)))
        assertTrue(c.board.cell(0, 1).conflict)
        assertTrue(c.board.cell(1, 2).conflict)
        assertFalse(c.board.cell(3, 3).conflict)
        assertTrue(c.board.cell(0, 1).locked)
        assertFalse(c.board.cell(1, 2).locked)
    }

    @Test
    fun solvedRegionsComeFromAnalysis() {
        // correct cat in region of (0,1) = region 0
        val board = BoardState.empty(4).withMark(0, 1, CellMark.CAT)
        val c = content(GameState.initial(4).copy(board = board))
        val solvedRegion = puzzle.regionAt(0, 1)
        assertEquals(listOf(solvedRegion), c.regions.filter { it.solved }.map { it.region })
    }

    @Test
    fun thirdBoosterUnlocksFromLevel10() {
        assertTrue(content(level = 9).boosters.thirdLocked)
        assertFalse(content(level = 10).boosters.thirdLocked)
        assertEquals(10, content().boosters.thirdUnlockLevel)
    }

    @Test
    fun settingsUiMirrorsSettings() {
        val c = content(settings = Settings(music = true, sfx = false, voice = false, haptics = true, patternMode = true))
        assertEquals(SettingsUi(music = true, sfx = false, voice = false, haptics = true, patternMode = true), c.settings)
        assertEquals(GameUiMapper.SETTINGS_BADGE_ALWAYS_ON, c.settingsBadge)
    }

    @Test
    fun patternModeFollowsSettings() {
        assertTrue(content(settings = Settings(patternMode = true)).patternMode)
    }

    @Test
    fun canUndoOnlyWhilePlayingWithHistory() {
        val engineState = com.threetech.catpuzzle.domain.engine.GameEngine()
            .applyTap(puzzle, GameState.initial(4), Pos(0, 0)).state
        assertTrue(content(engineState).canUndo)
    }

    @Test
    fun hintSpotlightUnionsFocusReasonsAndTargets() {
        val hint = Hint.Eliminate(
            targets = listOf(Pos(0, 0)),
            reasons = listOf(Pos(1, 1)),
            technique = HintTechnique.CONFINEMENT,
            rule = HintRule.REGION,
            focus = listOf(Pos(2, 2)),
        )
        assertEquals(setOf(0, 5, 10), GameUiMapper.hintSpotlight(4, hint))
        val overlay = GameUiMapper.hintOverlay(4, hint)
        assertEquals(HintRule.REGION, overlay?.hint?.rule)
        assertNull(GameUiMapper.hintOverlay(4, Hint.None))
        assertNull(GameUiMapper.hintOverlay(4, null))
    }

    @Test
    fun hintSpotlightPlaceIncludesTargetAndUnit() {
        val hint = Hint.Place(Pos(0, 1), HintTechnique.NAKED_SINGLE, HintRule.ROW, listOf(Pos(0, 0), Pos(0, 1)))
        assertEquals(setOf(0, 1), GameUiMapper.hintSpotlight(4, hint))
        assertEquals(setOf(5), GameUiMapper.hintSpotlight(4, Hint.Mistake(Pos(1, 1), MistakeKind.WRONG_CAT)))
    }

    @Test
    fun hintRolesMapTargetsOverReasons() {
        val hint = Hint.Eliminate(
            targets = listOf(Pos(0, 0)),
            reasons = listOf(Pos(0, 0), Pos(1, 1)),
            technique = HintTechnique.CONFINEMENT,
        )
        val roles = GameUiMapper.hintRoles(4, hint)
        assertEquals(HintRole.TARGET, roles[0])
        assertEquals(HintRole.REASON, roles[5])
        val c = content(hint = hint)
        assertEquals(HintRole.TARGET, c.board.cell(0, 0).hintRole)
        assertEquals(HintRole.REASON, c.board.cell(1, 1).hintRole)
        assertEquals(HintRole.NONE, c.board.cell(3, 3).hintRole)
        assertEquals(HintMessage.ELIMINATE_CONFINEMENT, c.hint?.message)
    }

    @Test
    fun hintMessageMapping() {
        assertEquals(HintMessage.WRONG_CAT, GameUiMapper.hintUi(Hint.Mistake(Pos(0, 0), MistakeKind.WRONG_CAT))?.message)
        assertEquals(HintMessage.X_ON_SOLUTION, GameUiMapper.hintUi(Hint.Mistake(Pos(0, 0), MistakeKind.X_ON_SOLUTION))?.message)
        assertEquals(HintMessage.PLACE_SINGLE, GameUiMapper.hintUi(Hint.Place(Pos(0, 1), HintTechnique.NAKED_SINGLE))?.message)
        assertEquals(HintMessage.PLACE_REVEAL, GameUiMapper.hintUi(Hint.Place(Pos(0, 1), HintTechnique.REVEAL))?.message)
        assertEquals(
            HintMessage.ELIMINATE_CROWDING,
            GameUiMapper.hintUi(Hint.Eliminate(emptyList(), emptyList(), HintTechnique.CROWDING_HALL))?.message,
        )
        assertEquals(
            HintMessage.ELIMINATE_LOOKAHEAD,
            GameUiMapper.hintUi(Hint.Eliminate(emptyList(), emptyList(), HintTechnique.LOOKAHEAD))?.message,
        )
        assertNull(GameUiMapper.hintUi(Hint.None))
        assertNull(GameUiMapper.hintRoles(4, Hint.None).entries.firstOrNull())
    }

    @Test
    fun outcomeMapping() {
        val score = ScoreBreakdown(400, 500, 0, 0, 900)
        val won = GameUiMapper.outcome(GameResult(1, true, score, heartsLeft = 2, hintsUsed = 0, elapsedSeconds = 12))
        assertEquals(OutcomeUi.Won(900, 12, 2, 0), won)
        val lost = GameUiMapper.outcome(GameResult(1, false, ScoreBreakdown(100, 0, 450, 0, 0), 0, 0, 30))
        assertEquals(OutcomeUi.Lost(0), lost)
        assertEquals(won, content(outcome = won).outcome)
    }
}
