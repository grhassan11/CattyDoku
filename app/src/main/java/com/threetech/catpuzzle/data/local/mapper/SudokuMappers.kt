package com.threetech.catpuzzle.data.local.mapper

import com.threetech.catpuzzle.data.local.codec.CodecException
import com.threetech.catpuzzle.data.local.codec.SudokuDigitsCodec
import com.threetech.catpuzzle.data.local.codec.SudokuNotesCodec
import com.threetech.catpuzzle.data.local.codec.SudokuStableIds
import com.threetech.catpuzzle.data.local.codec.SudokuUndoCodec
import com.threetech.catpuzzle.data.local.entity.SudokuProgressEntity
import com.threetech.catpuzzle.data.local.entity.SudokuSavedGameEntity
import com.threetech.catpuzzle.domain.sudoku.model.SudokuGameState
import com.threetech.catpuzzle.domain.sudoku.model.SudokuProgress
import com.threetech.catpuzzle.domain.sudoku.model.SudokuPuzzle
import com.threetech.catpuzzle.domain.sudoku.model.SudokuStatus

/** Null for a row with an unknown tier id (written by a newer build). */
fun SudokuProgressEntity.toDomainOrNull(): SudokuProgress? {
    val tier = SudokuStableIds.tierOrNull(tierId) ?: return null
    return SudokuProgress(tier, wins, plays, bestTimeMs, bestScore)
}

fun SudokuProgress.toEntity(): SudokuProgressEntity =
    SudokuProgressEntity(SudokuStableIds.tierId(tier), wins, plays, bestTimeMs, bestScore)

/** Only PLAYING games are persisted; paused/selected/notesMode are UI state and are not stored. */
fun SudokuGameState.toEntity(updatedAt: Long): SudokuSavedGameEntity = SudokuSavedGameEntity(
    tierId = SudokuStableIds.tierId(tier),
    seed = puzzle.seed,
    generatorVersion = puzzle.generatorVersion,
    rating = SudokuStableIds.techniqueId(puzzle.rating),
    matchedTier = puzzle.matchedTier,
    givens = SudokuDigitsCodec.encode(puzzle.givens),
    solution = SudokuDigitsCodec.encode(puzzle.solution),
    cellValues = SudokuDigitsCodec.encode(values),
    cellNotes = SudokuNotesCodec.encode(notes),
    undo = SudokuUndoCodec.encode(undo),
    elapsedMs = elapsedMs,
    hearts = hearts,
    mistakes = mistakes,
    hintsUsed = hintsUsed,
    score = score,
    updatedAt = updatedAt,
)

/** Throws [CodecException] on any corrupt or inconsistent row (caller drops it). */
fun SudokuSavedGameEntity.toDomain(): SudokuGameState {
    val tier = SudokuStableIds.tierFromId(tierId)
    val cells = tier.size.cells
    val n = tier.size.n
    val g = SudokuDigitsCodec.decode(givens)
    val s = SudokuDigitsCodec.decode(solution)
    val v = SudokuDigitsCodec.decode(cellValues)
    val nt = SudokuNotesCodec.decode(cellNotes)
    if (g.size != cells || s.size != cells || v.size != cells || nt.size != cells) {
        throw CodecException("sudoku save: wrong cell count")
    }
    if (g.any { it > n } || s.any { it > n } || v.any { it > n }) throw CodecException("sudoku save: digit above $n")
    val noteMask = (1 shl n) - 1
    if (nt.any { it and noteMask.inv() != 0 }) throw CodecException("sudoku save: note bit above $n")
    val steps = SudokuUndoCodec.decode(undo)
    for (st in steps) {
        if (st.cells.any { it >= cells } || st.prevValues.any { it > n } || st.prevNotes.any { it and noteMask.inv() != 0 }) {
            throw CodecException("sudoku save: bad undo step")
        }
    }
    val puzzle = SudokuPuzzle(
        tier = tier,
        givens = g,
        solution = s,
        seed = seed,
        generatorVersion = generatorVersion,
        rating = SudokuStableIds.techniqueFromId(rating),
        matchedTier = matchedTier,
    )
    return SudokuGameState(
        puzzle = puzzle,
        values = v,
        notes = nt,
        undo = steps,
        hearts = hearts,
        mistakes = mistakes,
        hintsUsed = hintsUsed,
        score = score,
        elapsedMs = elapsedMs,
        status = SudokuStatus.PLAYING,
    )
}
