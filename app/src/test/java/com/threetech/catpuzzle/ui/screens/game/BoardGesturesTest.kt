package com.threetech.catpuzzle.ui.screens.game

import com.threetech.catpuzzle.domain.model.Pos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardGesturesTest {
    private val a = Pos(1, 1)
    private val b = Pos(1, 2)

    /** Press + release on [pos]; returns the resolved action. */
    private fun BoardGestureResolver.tap(pos: Pos, downMs: Long, upMs: Long = downMs + 60): BoardAction? {
        begin(pos, downMs)
        return end(upMs)
    }

    // ---- tap / double-tap ----

    @Test
    fun singleTapActsImmediatelyAsTap() {
        val r = BoardGestureResolver()
        assertEquals(BoardAction.Tap(a), r.tap(a, 0))
    }

    @Test
    fun secondTapOnSameCellInsideWindowIsDoubleTap() {
        val r = BoardGestureResolver()
        assertEquals(BoardAction.Tap(a), r.tap(a, downMs = 0, upMs = 60))
        assertEquals(BoardAction.DoubleTap(a), r.tap(a, downMs = 200, upMs = 260))
    }

    @Test
    fun secondTapOutsideWindowIsAnotherTap() {
        val r = BoardGestureResolver()
        r.tap(a, 0, 60)
        assertEquals(BoardAction.Tap(a), r.tap(a, downMs = 60 + 301))
    }

    @Test
    fun windowBoundaryIsInclusive() {
        val r = BoardGestureResolver(doubleTapWindowMs = 300)
        r.tap(a, 0, 100)
        assertEquals(BoardAction.DoubleTap(a), r.tap(a, downMs = 400))
    }

    @Test
    fun secondTapOnOtherCellIsTapNotDouble() {
        val r = BoardGestureResolver()
        r.tap(a, 0, 60)
        assertEquals(BoardAction.Tap(b), r.tap(b, downMs = 100))
    }

    @Test
    fun thirdQuickTapStartsFreshChain() {
        val r = BoardGestureResolver()
        r.tap(a, 0, 60)
        assertEquals(BoardAction.DoubleTap(a), r.tap(a, 100, 160))
        assertEquals(BoardAction.Tap(a), r.tap(a, 200, 260))
        assertEquals(BoardAction.DoubleTap(a), r.tap(a, 300, 360))
    }

    @Test
    fun cancelBreaksTapChain() {
        val r = BoardGestureResolver()
        r.tap(a, 0, 60)
        r.cancel()
        assertEquals(BoardAction.Tap(a), r.tap(a, 100))
    }

    @Test
    fun endWithoutBeginIsNull() {
        assertNull(BoardGestureResolver().end(10))
    }

    // ---- drag ----

    @Test
    fun staysOnStartCellMeansNoDrag() {
        val r = BoardGestureResolver()
        r.begin(a, 0)
        assertNull(r.move(a))
        assertFalse(r.isDragging)
        assertEquals(BoardAction.Tap(a), r.end(50))
    }

    @Test
    fun enteringAnotherCellStartsDragWithFirstCellFirst() {
        val r = BoardGestureResolver()
        r.begin(a, 0)
        assertEquals(listOf(a, b), r.move(b))
        assertTrue(r.isDragging)
        assertEquals(BoardAction.Drag(listOf(a, b)), r.end(80))
    }

    @Test
    fun dragFillsSkippedCellsAndKeepsOrder() {
        val r = BoardGestureResolver()
        r.begin(Pos(0, 0), 0)
        r.move(Pos(0, 3))
        val action = r.end(50) as BoardAction.Drag
        assertEquals(listOf(Pos(0, 0), Pos(0, 1), Pos(0, 2), Pos(0, 3)), action.path)
    }

    @Test
    fun dragDoesNotDuplicateWhenBacktrackingOneCell() {
        val r = BoardGestureResolver()
        r.begin(Pos(0, 0), 0)
        r.move(Pos(0, 1))
        r.move(Pos(0, 1))
        r.move(Pos(0, 0))
        val action = r.end(50) as BoardAction.Drag
        assertEquals(listOf(Pos(0, 0), Pos(0, 1), Pos(0, 0)), action.path)
    }

    @Test
    fun dragBreaksTapChain() {
        val r = BoardGestureResolver()
        r.tap(a, 0, 60)
        r.begin(a, 100)
        r.move(b)
        r.end(150)
        assertEquals(BoardAction.Tap(a), r.tap(a, 200))
    }

    @Test
    fun cellsBetweenDiagonalAndSame() {
        assertEquals(emptyList<Pos>(), BoardGestureResolver.cellsBetween(a, a))
        assertEquals(
            listOf(Pos(1, 1), Pos(2, 2), Pos(3, 3)),
            BoardGestureResolver.cellsBetween(Pos(0, 0), Pos(3, 3)),
        )
        assertEquals(
            listOf(Pos(1, 0), Pos(2, 0)),
            BoardGestureResolver.cellsBetween(Pos(0, 0), Pos(2, 0)),
        )
    }

    // ---- drag intent / preview ----

    @Test
    fun dragIntentFollowsFirstCellMark() {
        assertEquals(DragIntent.MARK, BoardGestureResolver.dragIntentFor(CellMarkUi.EMPTY))
        assertEquals(DragIntent.ERASE, BoardGestureResolver.dragIntentFor(CellMarkUi.X))
        assertNull(BoardGestureResolver.dragIntentFor(CellMarkUi.CAT))
    }

    @Test
    fun previewOnlyIncludesCellsTheDragWouldChange() {
        fun cell(r: Int, c: Int, mark: CellMarkUi, locked: Boolean = false) =
            CellUi(r, c, 0, mark, conflict = false, locked = locked, hintRole = HintRole.NONE)
        // 2x2 board: [EMPTY, X] / [CAT, EMPTY]
        val board = BoardUi(
            2,
            listOf(cell(0, 0, CellMarkUi.EMPTY), cell(0, 1, CellMarkUi.X), cell(1, 0, CellMarkUi.CAT), cell(1, 1, CellMarkUi.EMPTY)),
        )
        val path = listOf(Pos(0, 0), Pos(0, 1), Pos(1, 1), Pos(5, 5))
        assertEquals(setOf(0, 3), BoardGestureResolver.previewIndices(board, path, DragIntent.MARK))
        assertEquals(setOf(1), BoardGestureResolver.previewIndices(board, path, DragIntent.ERASE))
        assertEquals(emptySet<Int>(), BoardGestureResolver.previewIndices(board, path, null))
    }

    // ---- layout ----

    @Test
    fun cellAtDividesGridEvenlyAndRejectsOutside() {
        assertEquals(Pos(0, 0), BoardLayoutCalc.cellAt(1f, 1f, 400f, 4))
        assertEquals(Pos(3, 3), BoardLayoutCalc.cellAt(399f, 399f, 400f, 4))
        assertEquals(Pos(1, 2), BoardLayoutCalc.cellAt(250f, 150f, 400f, 4))
        assertNull(BoardLayoutCalc.cellAt(-1f, 10f, 400f, 4))
        assertNull(BoardLayoutCalc.cellAt(400f, 10f, 400f, 4))
        assertNull(BoardLayoutCalc.cellAt(10f, 10f, 0f, 4))
    }

    @Test
    fun smallBoardsKeepFullPaddingAndBigCells() {
        val m = BoardLayoutCalc.metrics(328f, 4)
        assertEquals(16f, m.cardPaddingDp, 0f)
        assertTrue(m.cellDp >= BoardLayoutCalc.MIN_CELL_DP)
    }

    @Test
    fun sevenBySevenShrinksPaddingToKeepCellsNearMinimum() {
        val m = BoardLayoutCalc.metrics(328f, 7)
        assertEquals(4f, m.cardPaddingDp, 0f)
        assertTrue("cell ${m.cellDp}", m.cellDp > 42f)
        // grid always fits inside the available width
        assertTrue(m.gridSideDp(7) + 2 * m.cardPaddingDp <= 328f + 0.01f)
    }

    @Test
    fun cellSizeIsCappedOnWideScreens() {
        val m = BoardLayoutCalc.metrics(900f, 4)
        assertEquals(BoardLayoutCalc.MAX_CELL_DP, m.cellDp, 0f)
    }

    @Test
    fun patternIdsCoverTenAndWrap() {
        assertEquals(10, PATTERN_COUNT)
        assertEquals(3, patternIdForRegion(3))
        assertEquals(3, patternIdForRegion(13))
    }
}
