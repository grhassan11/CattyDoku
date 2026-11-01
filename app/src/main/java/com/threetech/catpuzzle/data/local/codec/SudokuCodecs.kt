package com.threetech.catpuzzle.data.local.codec

import com.threetech.catpuzzle.domain.sudoku.model.SudokuTechnique
import com.threetech.catpuzzle.domain.sudoku.model.SudokuTier
import com.threetech.catpuzzle.domain.sudoku.model.SudokuUndoStep
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Sudoku blob codecs, same rules as Codecs.kt: pure JVM, big-endian, VERSION BYTE FIRST, trailing bytes rejected.
 *  - [SudokuDigitsCodec] v1: count u8, then digits 0..15 as nibbles, 2 per byte, high nibble first
 *  - [SudokuNotesCodec]  v1: count u8, then count x u16 (bit d-1 = pencil digit d)
 *  - [SudokuUndoCodec]   v1: steps u16, each: scoreGain u16, cells u8, each: index u8, prevValue u8, prevNotes u16
 * Enum ids in [SudokuStableIds] are persisted: never renumber, only append.
 */
private const val SUDOKU_CODEC_V1 = 1

private fun sudokuEncoding(block: (DataOutputStream) -> Unit): ByteArray {
    val bytes = ByteArrayOutputStream()
    val out = DataOutputStream(bytes)
    out.writeByte(SUDOKU_CODEC_V1)
    block(out)
    out.flush()
    return bytes.toByteArray()
}

private fun <T> sudokuDecoding(name: String, bytes: ByteArray, block: (DataInputStream) -> T): T {
    val input = DataInputStream(ByteArrayInputStream(bytes))
    try {
        val version = input.readUnsignedByte()
        if (version != SUDOKU_CODEC_V1) throw CodecException("$name: unsupported version $version")
        val result = block(input)
        if (input.available() != 0) throw CodecException("$name: trailing bytes")
        return result
    } catch (e: IOException) {
        throw CodecException("$name: truncated", e)
    }
}

private fun DataOutputStream.putU8(value: Int, what: String) {
    if (value !in 0..255) throw CodecException("$what out of u8 range: $value")
    writeByte(value)
}

private fun DataOutputStream.putU16(value: Int, what: String) {
    if (value !in 0..65535) throw CodecException("$what out of u16 range: $value")
    writeShort(value)
}

object SudokuDigitsCodec {
    fun encode(digits: IntArray): ByteArray = sudokuEncoding { out ->
        out.putU8(digits.size, "count")
        var i = 0
        while (i < digits.size) {
            val hi = digits[i]
            val lo = if (i + 1 < digits.size) digits[i + 1] else 0
            if (hi !in 0..15 || lo !in 0..15) throw CodecException("digit out of nibble range")
            out.writeByte((hi shl 4) or lo)
            i += 2
        }
    }

    fun decode(bytes: ByteArray): IntArray = sudokuDecoding("sudokuDigits", bytes) { input ->
        val count = input.readUnsignedByte()
        val out = IntArray(count)
        var i = 0
        while (i < count) {
            val packed = input.readUnsignedByte()
            out[i] = packed shr 4
            if (i + 1 < count) out[i + 1] = packed and 0x0F
            i += 2
        }
        out
    }
}

object SudokuNotesCodec {
    fun encode(notes: IntArray): ByteArray = sudokuEncoding { out ->
        out.putU8(notes.size, "count")
        for (m in notes) out.putU16(m, "notes mask")
    }

    fun decode(bytes: ByteArray): IntArray = sudokuDecoding("sudokuNotes", bytes) { input ->
        val count = input.readUnsignedByte()
        IntArray(count) { input.readUnsignedShort() }
    }
}

object SudokuUndoCodec {
    fun encode(steps: List<SudokuUndoStep>): ByteArray = sudokuEncoding { out ->
        out.putU16(steps.size, "steps")
        for (s in steps) {
            out.putU16(s.scoreGain, "scoreGain")
            out.putU8(s.cells.size, "cells")
            for (i in s.cells.indices) {
                out.putU8(s.cells[i], "cell index")
                out.putU8(s.prevValues[i], "prev value")
                out.putU16(s.prevNotes[i], "prev notes")
            }
        }
    }

    fun decode(bytes: ByteArray): List<SudokuUndoStep> = sudokuDecoding("sudokuUndo", bytes) { input ->
        val count = input.readUnsignedShort()
        val list = ArrayList<SudokuUndoStep>(count)
        repeat(count) {
            val gain = input.readUnsignedShort()
            val n = input.readUnsignedByte()
            if (n == 0) throw CodecException("sudokuUndo: empty step")
            val cells = IntArray(n)
            val values = IntArray(n)
            val notes = IntArray(n)
            for (i in 0 until n) {
                cells[i] = input.readUnsignedByte()
                values[i] = input.readUnsignedByte()
                notes[i] = input.readUnsignedShort()
            }
            list.add(SudokuUndoStep(cells, values, notes, gain))
        }
        list
    }
}

/** Persisted enum ids. Exhaustive `when` so a new enum entry fails compilation until mapped. */
internal object SudokuStableIds {
    fun tierId(tier: SudokuTier): Int = when (tier) {
        SudokuTier.EASY -> 0
        SudokuTier.MEDIUM -> 1
        SudokuTier.HARD -> 2
        SudokuTier.EXPERT -> 3
        SudokuTier.MASTER -> 4
        SudokuTier.EXTREME -> 5
    }

    fun tierFromId(id: Int): SudokuTier = tierOrNull(id) ?: throw CodecException("unknown sudoku tier id $id")

    fun tierOrNull(id: Int): SudokuTier? = when (id) {
        0 -> SudokuTier.EASY
        1 -> SudokuTier.MEDIUM
        2 -> SudokuTier.HARD
        3 -> SudokuTier.EXPERT
        4 -> SudokuTier.MASTER
        5 -> SudokuTier.EXTREME
        else -> null
    }

    fun techniqueId(t: SudokuTechnique): Int = when (t) {
        SudokuTechnique.NAKED_SINGLE -> 0
        SudokuTechnique.HIDDEN_SINGLE -> 1
        SudokuTechnique.LOCKED_CANDIDATES -> 2
        SudokuTechnique.NAKED_PAIR -> 3
        SudokuTechnique.HIDDEN_PAIR -> 4
        SudokuTechnique.X_WING -> 5
        SudokuTechnique.GUESS -> 6
    }

    fun techniqueFromId(id: Int): SudokuTechnique = when (id) {
        0 -> SudokuTechnique.NAKED_SINGLE
        1 -> SudokuTechnique.HIDDEN_SINGLE
        2 -> SudokuTechnique.LOCKED_CANDIDATES
        3 -> SudokuTechnique.NAKED_PAIR
        4 -> SudokuTechnique.HIDDEN_PAIR
        5 -> SudokuTechnique.X_WING
        6 -> SudokuTechnique.GUESS
        else -> throw CodecException("unknown sudoku technique id $id")
    }
}
