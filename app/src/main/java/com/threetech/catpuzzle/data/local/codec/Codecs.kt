package com.threetech.catpuzzle.data.local.codec

import com.threetech.catpuzzle.domain.engine.MAX_BOARD_SIZE
import com.threetech.catpuzzle.domain.model.BoardState
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.model.CellChange
import com.threetech.catpuzzle.domain.model.CellMark
import com.threetech.catpuzzle.domain.model.GameStatus
import com.threetech.catpuzzle.domain.model.UndoEntry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Compact versioned blob codecs (pure JVM, no reflection, no JSON).
 *
 * Every blob starts with one version byte. Layouts (all big-endian, u8/u16 unsigned):
 *  - [CellArrayCodec]  v1: count u16, then count x u8                      (regions / solution)
 *  - [MarksCodec]      v1: size u8, then size*size marks, 2 bits each, 4 per byte, low bits first
 *  - [IndexSetCodec]   v1: count u16, then count x u8 (ascending cell indices)
 *  - [UndoCodec]       v1: entries u16, each: changes u16, each: cellIndex u8, markCode u8
 *  - [ChargesCodec]    v1: count u8, each: boosterId u8, value u16
 *
 * Changing a layout: bump the version constant and keep a decoder for the old version, then add a Room
 * migration only if the column set changes. Enum ids below are persisted: never renumber, only append.
 */
class CodecException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

private const val VERSION_1 = 1
private const val U8_MAX = 255
private const val U16_MAX = 65535

private fun encoding(block: (DataOutputStream) -> Unit): ByteArray {
    val bytes = ByteArrayOutputStream()
    val out = DataOutputStream(bytes)
    out.writeByte(VERSION_1)
    block(out)
    out.flush()
    return bytes.toByteArray()
}

private fun <T> decoding(name: String, bytes: ByteArray, block: (DataInputStream) -> T): T {
    val input = DataInputStream(ByteArrayInputStream(bytes))
    try {
        val version = input.readUnsignedByte()
        if (version != VERSION_1) throw CodecException("$name: unsupported version $version")
        val result = block(input)
        if (input.available() != 0) throw CodecException("$name: trailing bytes")
        return result
    } catch (e: IOException) {
        throw CodecException("$name: truncated", e)
    }
}

private fun DataOutputStream.writeU8(value: Int, what: String) {
    if (value !in 0..U8_MAX) throw CodecException("$what out of u8 range: $value")
    writeByte(value)
}

private fun DataOutputStream.writeU16(value: Int, what: String) {
    if (value !in 0..U16_MAX) throw CodecException("$what out of u16 range: $value")
    writeShort(value)
}

/** Persisted enum ids. Exhaustive `when` so a new enum entry fails compilation until mapped. */
internal object StableIds {
    fun markCode(mark: CellMark): Int = when (mark) {
        CellMark.EMPTY -> 0
        CellMark.X -> 1
        CellMark.CAT -> 2
    }

    fun markFromCode(code: Int): CellMark = when (code) {
        0 -> CellMark.EMPTY
        1 -> CellMark.X
        2 -> CellMark.CAT
        else -> throw CodecException("unknown mark code $code")
    }

    fun boosterId(booster: Booster): Int = when (booster) {
        Booster.CAT -> 0
        Booster.HINT -> 1
        Booster.MOUSE -> 2
    }

    fun boosterFromId(id: Int): Booster = when (id) {
        0 -> Booster.CAT
        1 -> Booster.HINT
        2 -> Booster.MOUSE
        else -> throw CodecException("unknown booster id $id")
    }

    fun statusCode(status: GameStatus): Int = when (status) {
        GameStatus.PLAYING -> 0
        GameStatus.SOLVED -> 1
        GameStatus.LOST -> 2
    }

    fun statusFromCode(code: Int): GameStatus = when (code) {
        0 -> GameStatus.PLAYING
        1 -> GameStatus.SOLVED
        2 -> GameStatus.LOST
        else -> throw CodecException("unknown status code $code")
    }

    /** Stable text key for the inventory table. */
    fun boosterKey(booster: Booster): String = when (booster) {
        Booster.CAT -> "cat"
        Booster.HINT -> "hint"
        Booster.MOUSE -> "mouse"
    }

    fun boosterFromKey(key: String): Booster? = when (key) {
        "cat" -> Booster.CAT
        "hint" -> Booster.HINT
        "mouse" -> Booster.MOUSE
        else -> null
    }
}

/** Small non-negative ints (each 0..255): region ids, solution columns. */
object CellArrayCodec {
    fun encode(values: IntArray): ByteArray = encoding { out ->
        out.writeU16(values.size, "count")
        for (v in values) out.writeU8(v, "value")
    }

    fun decode(bytes: ByteArray): IntArray = decoding("cells", bytes) { input ->
        val count = input.readUnsignedShort()
        IntArray(count) { input.readUnsignedByte() }
    }
}

object MarksCodec {
    fun encode(board: BoardState): ByteArray = encoding { out ->
        out.writeU8(board.size, "size")
        val marks = board.marks
        var i = 0
        while (i < marks.size) {
            var packed = 0
            for (k in 0 until 4) {
                val idx = i + k
                if (idx < marks.size) packed = packed or (StableIds.markCode(marks[idx]) shl (k * 2))
            }
            out.writeByte(packed)
            i += 4
        }
    }

    fun decode(bytes: ByteArray): BoardState = decoding("marks", bytes) { input ->
        val size = input.readUnsignedByte()
        if (size !in 1..MAX_BOARD_SIZE) throw CodecException("marks: bad size $size")
        val cells = size * size
        val marks = ArrayList<CellMark>(cells)
        var remaining = cells
        while (remaining > 0) {
            val packed = input.readUnsignedByte()
            var k = 0
            while (k < 4 && remaining > 0) {
                marks.add(StableIds.markFromCode((packed shr (k * 2)) and 0b11))
                remaining--
                k++
            }
        }
        BoardState(size, marks)
    }
}

/** Set of flat cell indices (each 0..255). Stored ascending; decode returns an ordered set. */
object IndexSetCodec {
    fun encode(indices: Set<Int>): ByteArray = encoding { out ->
        val sorted = indices.sorted()
        out.writeU16(sorted.size, "count")
        for (i in sorted) out.writeU8(i, "cell index")
    }

    fun decode(bytes: ByteArray): Set<Int> = decoding("indexSet", bytes) { input ->
        val count = input.readUnsignedShort()
        val set = LinkedHashSet<Int>(count)
        repeat(count) { set.add(input.readUnsignedByte()) }
        set
    }
}

object UndoCodec {
    fun encode(stack: List<UndoEntry>): ByteArray = encoding { out ->
        out.writeU16(stack.size, "entries")
        for (entry in stack) {
            out.writeU16(entry.changes.size, "changes")
            for (c in entry.changes) {
                out.writeU8(c.index, "cell index")
                out.writeU8(StableIds.markCode(c.before), "mark")
            }
        }
    }

    fun decode(bytes: ByteArray): List<UndoEntry> = decoding("undo", bytes) { input ->
        val entries = input.readUnsignedShort()
        val list = ArrayList<UndoEntry>(entries)
        repeat(entries) {
            val changes = input.readUnsignedShort()
            val cs = ArrayList<CellChange>(changes)
            repeat(changes) {
                val index = input.readUnsignedByte()
                cs.add(CellChange(index, StableIds.markFromCode(input.readUnsignedByte())))
            }
            list.add(UndoEntry(cs))
        }
        list
    }
}

object ChargesCodec {
    fun encode(charges: Map<Booster, Int>): ByteArray = encoding { out ->
        // Deterministic order: by stable id.
        val sorted = charges.entries.sortedBy { StableIds.boosterId(it.key) }
        out.writeU8(sorted.size, "count")
        for ((booster, value) in sorted) {
            out.writeU8(StableIds.boosterId(booster), "booster id")
            out.writeU16(value, "charges")
        }
    }

    fun decode(bytes: ByteArray): Map<Booster, Int> = decoding("charges", bytes) { input ->
        val count = input.readUnsignedByte()
        val map = LinkedHashMap<Booster, Int>(count)
        repeat(count) {
            val booster = StableIds.boosterFromId(input.readUnsignedByte())
            map[booster] = input.readUnsignedShort()
        }
        map
    }
}
