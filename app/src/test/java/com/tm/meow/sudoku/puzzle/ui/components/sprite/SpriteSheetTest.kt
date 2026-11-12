package com.tm.meow.sudoku.puzzle.ui.components.sprite

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class SpriteSheetTest {
    private val sheet = SpriteSheet(res = 0, cols = 4, rows = 2, frameCount = 8, fps = 10)
    private val atlas = IntSize(1416, 768)

    @Test
    fun firstAndRowTwoFrames() {
        assertEquals(FrameRect(IntOffset(0, 0), IntSize(354, 384)), sheet.frameRect(0, atlas))
        assertEquals(FrameRect(IntOffset(354, 0), IntSize(354, 384)), sheet.frameRect(1, atlas))
        assertEquals(FrameRect(IntOffset(0, 384), IntSize(354, 384)), sheet.frameRect(4, atlas))
        assertEquals(FrameRect(IntOffset(1062, 384), IntSize(354, 384)), sheet.frameRect(7, atlas))
    }

    @Test
    fun wrapsAround() {
        assertEquals(sheet.frameRect(0, atlas), sheet.frameRect(8, atlas))
        assertEquals(sheet.frameRect(3, atlas), sheet.frameRect(11, atlas))
        assertEquals(sheet.frameRect(7, atlas), sheet.frameRect(-1, atlas))
    }

    @Test
    fun loopMillis() {
        assertEquals(800, sheet.loopMillis)
    }
}
