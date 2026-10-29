package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.engine.CatBoosterEngine
import com.threetech.catpuzzle.domain.engine.GameEngine
import com.threetech.catpuzzle.domain.engine.GameFixtures
import com.threetech.catpuzzle.domain.engine.HintEngine
import com.threetech.catpuzzle.domain.engine.ScoreCalculator
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.model.CellMark
import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.GameStatus
import com.threetech.catpuzzle.domain.model.Hint
import com.threetech.catpuzzle.domain.model.Pos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UseCasesTest {
    private val engine = GameEngine()
    private val p = GameFixtures.unique4()
    private fun fresh(charges: Map<Booster, Int> = emptyMap()) = GameState.initial(4, charges)

    @Test
    fun boardUseCasesDelegate() {
        var s = ToggleX(engine)(p, fresh(), Pos(0, 0)).state
        assertEquals(CellMark.X, s.board[0, 0])
        s = ApplyDragX(engine)(p, s, listOf(Pos(1, 0), Pos(2, 1))).state
        assertEquals(CellMark.X, s.board[2, 1])
        s = PlaceCat(engine)(p, s, Pos(0, 1)).state
        assertEquals(CellMark.CAT, s.board[0, 1])
        s = RemoveCat(engine)(p, s, Pos(0, 1)).state
        assertEquals(CellMark.EMPTY, s.board[0, 1])
        s = Undo(engine)(s).state
        assertEquals(CellMark.CAT, s.board[0, 1])
    }

    @Test
    fun hintNeedsCharge() {
        val r = UseHint(HintEngine())(p, fresh())
        assertEquals(HintResult.NoCharge, r)
    }

    @Test
    fun hintSpendsChargeAndCountsUse() {
        val r = UseHint(HintEngine())(p, fresh(mapOf(Booster.HINT to 2)))
        assertTrue(r is HintResult.Granted)
        r as HintResult.Granted
        assertTrue(r.hint is Hint.Place)
        assertEquals(1, r.state.chargesOf(Booster.HINT))
        assertEquals(1, r.state.hintsUsed)
        assertEquals(3, r.state.hearts)
    }

    @Test
    fun hintOnFinishedGameIsUnavailable() {
        val s = fresh(mapOf(Booster.HINT to 1)).copy(status = GameStatus.SOLVED)
        assertEquals(HintResult.Unavailable, UseHint(HintEngine())(p, s))
    }

    @Test
    fun catBoosterNeedsCharge() {
        val r = UseCatBooster(CatBoosterEngine(), engine)(p, fresh())
        assertEquals(CatBoosterResult.NoCharge, r)
    }

    @Test
    fun catBoosterPlacesSolutionCatWithoutHeartLoss() {
        val use = UseCatBooster(CatBoosterEngine(), engine)
        var s = fresh(mapOf(Booster.CAT to 4))
        // wrong X marks must not matter
        s = engine.applyTap(p, s, Pos(0, 0)).state
        for (i in 1..4) {
            val r = use(p, s)
            assertTrue(r is CatBoosterResult.Placed)
            r as CatBoosterResult.Placed
            assertTrue(GameFixtures.isSolution(p, r.cell))
            s = r.step.state
            assertEquals(3, s.hearts)
            assertEquals(4 - i, s.chargesOf(Booster.CAT))
        }
        assertEquals(GameStatus.SOLVED, s.status)
        assertEquals(0, s.correctPlacements)
    }

    @Test
    fun catBoosterOnFinishedGameIsUnavailable() {
        val s = fresh(mapOf(Booster.CAT to 1)).copy(status = GameStatus.LOST)
        assertEquals(CatBoosterResult.Unavailable, UseCatBooster(CatBoosterEngine(), engine)(p, s))
    }

    @Test
    fun computeScoreReflectsState() {
        val calc = ComputeScore(ScoreCalculator())
        var s = fresh()
        for (r in 0 until 4) s = engine.applyDoubleTap(p, s, Pos(r, p.solution[r])).state
        val clean = calc(p, s, 20)
        assertTrue(clean.won)
        assertEquals(3, clean.heartsLeft)

        val worse = calc(p, s.copy(hearts = 1, hintsUsed = 2), 20)
        assertTrue(worse.score.total < clean.score.total)

        val slower = calc(p, s, 200)
        assertTrue(slower.score.total < clean.score.total)

        val lost = calc(p, s.copy(status = GameStatus.LOST), 20)
        assertFalse(lost.won)
        assertEquals(0, lost.score.timeBonus)
        assertEquals(p.levelIndex, lost.levelIndex)
    }
}
