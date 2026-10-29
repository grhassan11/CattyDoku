package com.threetech.catpuzzle.data.local.mapper

import com.threetech.catpuzzle.data.local.codec.CellArrayCodec
import com.threetech.catpuzzle.data.local.codec.ChargesCodec
import com.threetech.catpuzzle.data.local.codec.CodecException
import com.threetech.catpuzzle.data.local.codec.IndexSetCodec
import com.threetech.catpuzzle.data.local.codec.MarksCodec
import com.threetech.catpuzzle.data.local.codec.StableIds
import com.threetech.catpuzzle.data.local.codec.UndoCodec
import com.threetech.catpuzzle.data.local.entity.LevelProgressEntity
import com.threetech.catpuzzle.data.local.entity.PuzzleCacheEntity
import com.threetech.catpuzzle.data.local.entity.SavedGameEntity
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.LevelProgress
import com.threetech.catpuzzle.domain.model.Puzzle
import com.threetech.catpuzzle.domain.model.SavedGame

fun LevelProgressEntity.toDomain(): LevelProgress =
    LevelProgress(levelIndex, bestScore, bestTimeMs, completedAt, mistakes, hintsUsed)

fun LevelProgress.toEntity(): LevelProgressEntity =
    LevelProgressEntity(levelIndex, bestScore, bestTimeMs, completedAt, mistakes, hintsUsed)

fun Puzzle.toEntity(): PuzzleCacheEntity = PuzzleCacheEntity(
    levelIndex = levelIndex,
    size = size,
    regions = CellArrayCodec.encode(regions),
    solution = CellArrayCodec.encode(solution),
    techniqueLevel = techniqueLevel,
    seed = seed,
    generatorVersion = generatorVersion,
)

/** @throws CodecException on a corrupt or inconsistent row. */
fun PuzzleCacheEntity.toDomain(): Puzzle {
    val regionIds = CellArrayCodec.decode(regions)
    val solutionCols = CellArrayCodec.decode(solution)
    try {
        return Puzzle(size, regionIds, solutionCols, seed, levelIndex, techniqueLevel, generatorVersion)
    } catch (e: IllegalArgumentException) {
        throw CodecException("puzzle_cache row inconsistent", e)
    }
}

fun SavedGame.toEntity(): SavedGameEntity = SavedGameEntity(
    levelIndex = levelIndex,
    marks = MarksCodec.encode(state.board),
    hearts = state.hearts,
    status = StableIds.statusCode(state.status),
    elapsedMs = elapsedMs,
    hintsUsed = state.hintsUsed,
    correctPlacements = state.correctPlacements,
    rewardedCells = IndexSetCodec.encode(state.rewardedCells),
    lockedCells = IndexSetCodec.encode(state.lockedCells),
    undo = UndoCodec.encode(state.undoStack),
    charges = ChargesCodec.encode(state.charges),
    updatedAt = savedAtMs,
)

/** @throws CodecException on a corrupt row. */
fun SavedGameEntity.toDomain(): SavedGame = SavedGame(
    levelIndex = levelIndex,
    state = GameState(
        board = MarksCodec.decode(marks),
        hearts = hearts,
        status = StableIds.statusFromCode(status),
        undoStack = UndoCodec.decode(undo),
        rewardedCells = IndexSetCodec.decode(rewardedCells),
        lockedCells = IndexSetCodec.decode(lockedCells),
        correctPlacements = correctPlacements,
        hintsUsed = hintsUsed,
        charges = ChargesCodec.decode(charges),
    ),
    elapsedMs = elapsedMs,
    savedAtMs = updatedAt,
)

fun Booster.storageKey(): String = StableIds.boosterKey(this)

fun boosterFromStorageKey(key: String): Booster? = StableIds.boosterFromKey(key)
