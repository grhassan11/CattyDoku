package com.tm.meow.sudoku.puzzle.data.local.codec

import com.tm.meow.sudoku.puzzle.data.local.mapper.toDomain
import com.tm.meow.sudoku.puzzle.data.local.mapper.toDomainOrNull
import com.tm.meow.sudoku.puzzle.data.local.mapper.toEntity
import com.tm.meow.sudoku.puzzle.domain.sudoku.engine.SUDOKU_GENERATOR_VERSION
import com.tm.meow.sudoku.puzzle.domain.sudoku.engine.SudokuGameEngine
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuProgress
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuPuzzle
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuStatus
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuTechnique
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuTier
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuUndoStep
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SudokuCodecsTest {
    private val solution = intArrayOf(1, 2, 3, 4, 3, 4, 1, 2, 2, 1, 4, 3, 4, 3, 2, 1)

    private fun game() = run {
        val givens = solution.copyOf().also { for (c in intArrayOf(0, 1, 5, 10, 15)) it[c] = 0 }
        val p = SudokuPuzzle(SudokuTier.EASY, givens, solution, 99L, SUDOKU_GENERATOR_VERSION, SudokuTechnique.HIDDEN_SINGLE, false)
        var s = SudokuGameEngine.newGame(p)
        s = SudokuGameEngine.toggleNotesMode(SudokuGameEngine.select(s, 1))
        s = SudokuGameEngine.enterDigit(SudokuGameEngine.enterDigit(s, 1), 2)
        s = SudokuGameEngine.toggleNotesMode(s)
        s = SudokuGameEngine.enterDigit(SudokuGameEngine.select(s, 0), 1) // correct, clears note 1 on cell 1
        s = SudokuGameEngine.enterDigit(SudokuGameEngine.select(s, 5), 1) // mistake
        s.copy(elapsedMs = 12_345L, hintsUsed = 2)
    }

    private fun expectCodec(block: () -> Unit) {
        try {
            block()
            fail("expected CodecException")
        } catch (e: CodecException) {
            // ok
        }
    }

    @Test
    fun digitsRoundTripOddAndEven() {
        for (d in listOf(intArrayOf(), intArrayOf(9), intArrayOf(1, 2, 3), solution, IntArray(81) { it % 10 })) {
            val bytes = SudokuDigitsCodec.encode(d)
            assertEquals(1, bytes[0].toInt()) // version byte first
            assertArrayEquals(d, SudokuDigitsCodec.decode(bytes))
        }
    }

    @Test
    fun notesAndUndoRoundTrip() {
        val notes = intArrayOf(0, 1, 0x1FF, 0b101)
        assertArrayEquals(notes, SudokuNotesCodec.decode(SudokuNotesCodec.encode(notes)))
        val steps = listOf(
            SudokuUndoStep(intArrayOf(3), intArrayOf(0), intArrayOf(0b11), 0),
            SudokuUndoStep(intArrayOf(0, 1, 4), intArrayOf(0, 0, 0), intArrayOf(0, 0b1, 0b10), 60),
        )
        val back = SudokuUndoCodec.decode(SudokuUndoCodec.encode(steps))
        assertEquals(2, back.size)
        for (i in steps.indices) {
            assertArrayEquals(steps[i].cells, back[i].cells)
            assertArrayEquals(steps[i].prevValues, back[i].prevValues)
            assertArrayEquals(steps[i].prevNotes, back[i].prevNotes)
            assertEquals(steps[i].scoreGain, back[i].scoreGain)
        }
        assertTrue(SudokuUndoCodec.decode(SudokuUndoCodec.encode(emptyList())).isEmpty())
    }

    @Test
    fun corruptBlobsThrowCodecException() {
        val good = SudokuDigitsCodec.encode(solution)
        expectCodec { SudokuDigitsCodec.decode(good.copyOf(good.size - 1)) } // truncated
        expectCodec { SudokuDigitsCodec.decode(good + byteArrayOf(0)) } // trailing
        expectCodec { SudokuDigitsCodec.decode(byteArrayOf(2) + good.copyOfRange(1, good.size)) } // bad version
        expectCodec { SudokuDigitsCodec.encode(intArrayOf(16)) } // not a nibble
        expectCodec { SudokuNotesCodec.encode(intArrayOf(70_000)) }
        expectCodec { SudokuUndoCodec.decode(byteArrayOf(1, 0, 1, 0, 0, 0)) } // empty step
        expectCodec { SudokuDigitsCodec.decode(ByteArray(0)) }
    }

    @Test
    fun savedGameRoundTrip() {
        val s = game()
        val back = s.toEntity(updatedAt = 5L).toDomain()
        assertEquals(s.tier, back.tier)
        assertEquals(99L, back.puzzle.seed)
        assertEquals(SUDOKU_GENERATOR_VERSION, back.puzzle.generatorVersion)
        assertEquals(SudokuTechnique.HIDDEN_SINGLE, back.puzzle.rating)
        assertFalse(back.puzzle.matchedTier)
        assertArrayEquals(s.puzzle.givens, back.puzzle.givens)
        assertArrayEquals(s.puzzle.solution, back.puzzle.solution)
        assertArrayEquals(s.values, back.values)
        assertArrayEquals(s.notes, back.notes)
        assertEquals(s.undo.size, back.undo.size)
        assertEquals(s.hearts, back.hearts)
        assertEquals(s.mistakes, back.mistakes)
        assertEquals(s.hintsUsed, back.hintsUsed)
        assertEquals(s.score, back.score)
        assertEquals(12_345L, back.elapsedMs)
        assertEquals(SudokuStatus.PLAYING, back.status)
        assertFalse(back.paused)
        assertEquals(-1, back.selected)
        // restored undo still works
        val undone = SudokuGameEngine.undo(back)
        assertEquals(0, undone.values[5])
    }

    @Test
    fun inconsistentSaveIsRejected() {
        val e = game().toEntity(1L)
        // 4x4 save whose digits blob claims 9x9 size
        val bad = com.tm.meow.sudoku.puzzle.data.local.entity.SudokuSavedGameEntity(
            e.tierId, e.seed, e.generatorVersion, e.rating, e.matchedTier,
            SudokuDigitsCodec.encode(IntArray(81)), e.solution, e.cellValues, e.cellNotes, e.undo,
            e.elapsedMs, e.hearts, e.mistakes, e.hintsUsed, e.score, e.updatedAt,
        )
        expectCodec { bad.toDomain() }
        val badTier = com.tm.meow.sudoku.puzzle.data.local.entity.SudokuSavedGameEntity(
            99, e.seed, e.generatorVersion, e.rating, e.matchedTier, e.givens, e.solution, e.cellValues, e.cellNotes,
            e.undo, e.elapsedMs, e.hearts, e.mistakes, e.hintsUsed, e.score, e.updatedAt,
        )
        expectCodec { badTier.toDomain() }
    }

    @Test
    fun legacyV1NineByNineHardSaveIsRejectedButMasterStillDecodes() {
        val sol9 = IntArray(81) { ((it / 9) * 3 + (it / 9) / 3 + it % 9) % 9 + 1 }
        val giv9 = sol9.copyOf().also { for (i in 0 until 81 step 2) it[i] = 0 }
        fun save(tier: SudokuTier) = SudokuGameEngine.newGame(
            SudokuPuzzle(SudokuTier.MASTER, giv9, sol9, 5L, 1, SudokuTechnique.HIDDEN_PAIR, true),
        ).toEntity(1L).copy(tierId = SudokuStableIds.tierId(tier))
        expectCodec { save(SudokuTier.HARD).toDomain() }
        expectCodec { save(SudokuTier.EXPERT).toDomain() }
        val master = save(SudokuTier.MASTER).toDomain()
        assertEquals(1, master.puzzle.generatorVersion)
        assertEquals(81, master.values.size)
    }

    @Test
    fun eightByEightSaveRoundTrips() {
        val sol8 = IntArray(64) { ((it / 8 % 2) * 4 + it / 8 / 2 + it % 8) % 8 + 1 }
        val giv8 = sol8.copyOf().also { for (i in 0 until 64 step 2) it[i] = 0 }
        val state = SudokuGameEngine.newGame(
            SudokuPuzzle(SudokuTier.HARD, giv8, sol8, 8L, SUDOKU_GENERATOR_VERSION, SudokuTechnique.HIDDEN_SINGLE, true),
        )
        val back = state.toEntity(1L).toDomain()
        assertEquals(SudokuTier.HARD, back.tier)
        assertArrayEquals(giv8, back.puzzle.givens)
        assertArrayEquals(state.values, back.values)
    }

    @Test
    fun progressMapping() {
        val p = SudokuProgress(SudokuTier.MASTER, wins = 3, plays = 5, bestTimeMs = 9_000L, bestScore = 321)
        assertEquals(p, p.toEntity().toDomainOrNull())
        assertEquals(4, SudokuStableIds.tierId(SudokuTier.MASTER))
        assertNull(p.toEntity().copy(tierId = 77).toDomainOrNull())
        for (t in SudokuTier.entries) assertEquals(t, SudokuStableIds.tierFromId(SudokuStableIds.tierId(t)))
        for (t in SudokuTechnique.entries) assertEquals(t, SudokuStableIds.techniqueFromId(SudokuStableIds.techniqueId(t)))
    }
}
