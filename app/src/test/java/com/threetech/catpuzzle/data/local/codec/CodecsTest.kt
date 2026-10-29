package com.threetech.catpuzzle.data.local.codec

import com.threetech.catpuzzle.domain.model.BoardState
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.model.CellChange
import com.threetech.catpuzzle.domain.model.CellMark
import com.threetech.catpuzzle.domain.model.UndoEntry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CodecsTest {

    private fun assertCodecFails(block: () -> Unit) {
        try {
            block()
            fail("expected CodecException")
        } catch (e: CodecException) {
            // expected
        }
    }

    @Test
    fun cellArrayRoundTrip() {
        val values = intArrayOf(0, 1, 8, 255, 3, 3)
        assertArrayEquals(values, CellArrayCodec.decode(CellArrayCodec.encode(values)))
    }

    @Test
    fun cellArrayEmptyRoundTrip() {
        assertEquals(0, CellArrayCodec.decode(CellArrayCodec.encode(IntArray(0))).size)
    }

    @Test
    fun cellArrayRejectsOutOfRange() {
        assertCodecFails { CellArrayCodec.encode(intArrayOf(256)) }
        assertCodecFails { CellArrayCodec.encode(intArrayOf(-1)) }
    }

    @Test
    fun cellArrayIsCompact() {
        // version + u16 count + 1 byte per value
        assertEquals(1 + 2 + 81, CellArrayCodec.encode(IntArray(81)).size)
    }

    @Test
    fun marksRoundTripAllSizes() {
        for (size in 1..9) {
            val marks = List(size * size) { CellMark.entries[it % 3] }
            val board = BoardState(size, marks)
            assertEquals("size $size", board, MarksCodec.decode(MarksCodec.encode(board)))
        }
    }

    @Test
    fun marksArePackedTwoBitsPerCell() {
        // 9x9 = 81 cells -> 21 bytes + version + size
        assertEquals(2 + 21, MarksCodec.encode(BoardState.empty(9)).size)
    }

    @Test
    fun marksRejectBadVersionTruncationAndTrailing() {
        val good = MarksCodec.encode(BoardState.empty(4))
        val badVersion = good.copyOf().also { it[0] = 99 }
        assertCodecFails { MarksCodec.decode(badVersion) }
        assertCodecFails { MarksCodec.decode(good.copyOf(good.size - 1)) }
        assertCodecFails { MarksCodec.decode(good + byteArrayOf(0)) }
        assertCodecFails { MarksCodec.decode(ByteArray(0)) }
    }

    @Test
    fun marksRejectInvalidMarkCode() {
        val bytes = MarksCodec.encode(BoardState.empty(2)).copyOf()
        bytes[2] = 0b11 // code 3 is not a mark
        assertCodecFails { MarksCodec.decode(bytes) }
    }

    @Test
    fun indexSetRoundTripSortedAndDeduplicated() {
        val set = setOf(40, 3, 0, 80)
        val decoded = IndexSetCodec.decode(IndexSetCodec.encode(set))
        assertEquals(set, decoded)
        assertEquals(listOf(0, 3, 40, 80), decoded.toList())
        assertEquals(emptySet<Int>(), IndexSetCodec.decode(IndexSetCodec.encode(emptySet())))
    }

    @Test
    fun indexSetRejectsOutOfRange() {
        assertCodecFails { IndexSetCodec.encode(setOf(256)) }
    }

    @Test
    fun undoRoundTrip() {
        val stack = listOf(
            UndoEntry(listOf(CellChange(0, CellMark.EMPTY))),
            UndoEntry(listOf(CellChange(5, CellMark.X), CellChange(6, CellMark.CAT), CellChange(7, CellMark.EMPTY))),
            UndoEntry(emptyList()),
        )
        assertEquals(stack, UndoCodec.decode(UndoCodec.encode(stack)))
        assertEquals(emptyList<UndoEntry>(), UndoCodec.decode(UndoCodec.encode(emptyList())))
    }

    @Test
    fun chargesRoundTripIncludingZeroAndEmpty() {
        val charges = mapOf(Booster.CAT to 2, Booster.HINT to 0)
        assertEquals(charges, ChargesCodec.decode(ChargesCodec.encode(charges)))
        assertEquals(emptyMap<Booster, Int>(), ChargesCodec.decode(ChargesCodec.encode(emptyMap())))
    }

    @Test
    fun chargesEncodingIsDeterministicRegardlessOfMapOrder() {
        val a = linkedMapOf(Booster.CAT to 1, Booster.HINT to 4)
        val b = linkedMapOf(Booster.HINT to 4, Booster.CAT to 1)
        assertArrayEquals(ChargesCodec.encode(a), ChargesCodec.encode(b))
    }

    @Test
    fun stableIdsAreFixed() {
        // Persisted values: a failure here means data written by an older build would be misread.
        assertEquals(0, StableIds.markCode(CellMark.EMPTY))
        assertEquals(1, StableIds.markCode(CellMark.X))
        assertEquals(2, StableIds.markCode(CellMark.CAT))
        assertEquals(0, StableIds.boosterId(Booster.CAT))
        assertEquals(1, StableIds.boosterId(Booster.HINT))
        assertEquals("cat", StableIds.boosterKey(Booster.CAT))
        assertEquals("hint", StableIds.boosterKey(Booster.HINT))
        assertEquals(2, StableIds.boosterId(Booster.MOUSE))
        assertEquals(Booster.MOUSE, StableIds.boosterFromId(2))
        assertEquals("mouse", StableIds.boosterKey(Booster.MOUSE))
        for (b in Booster.entries) assertEquals(b, StableIds.boosterFromKey(StableIds.boosterKey(b)))
        assertTrue(StableIds.boosterFromKey("nope") == null)
    }
}
