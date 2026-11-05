package com.tm.meow.sudoku.puzzle.domain.sudoku.usecase

import com.tm.meow.sudoku.puzzle.domain.ads.AdTrigger
import com.tm.meow.sudoku.puzzle.domain.ads.SudokuAdRules
import com.tm.meow.sudoku.puzzle.domain.engine.SeededRng
import com.tm.meow.sudoku.puzzle.domain.model.Booster
import com.tm.meow.sudoku.puzzle.domain.sudoku.engine.SudokuGameEngine
import com.tm.meow.sudoku.puzzle.domain.sudoku.engine.SudokuGenerator
import com.tm.meow.sudoku.puzzle.domain.sudoku.engine.SudokuHintFinder
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuGameState
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuHint
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuProgress
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuPuzzle
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuStatus
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuTier
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuTierStatus
import com.tm.meow.sudoku.puzzle.domain.sudoku.repository.SudokuRepository
import com.tm.meow.sudoku.puzzle.domain.usecase.ConsumeBooster
import com.tm.meow.sudoku.puzzle.domain.usecase.RecordPlayedToday
import com.tm.meow.sudoku.puzzle.domain.usecase.RecordSudokuWon
import com.tm.meow.sudoku.puzzle.domain.usecase.ShowInterstitialIfAllowed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

sealed interface SudokuStartResult {
    /** Saved game restored. [state] may already be SOLVED / GAME_OVER (healed); the caller then completes it. */
    data class Resumed(val state: SudokuGameState) : SudokuStartResult

    /** Fresh puzzle generated, wrapped in a new game and saved. */
    data class Started(val state: SudokuGameState) : SudokuStartResult

    /** Tier still locked: [winsHave] of [winsNeeded] wins in [requiredTier]. */
    data class Locked(
        val tier: SudokuTier,
        val requiredTier: SudokuTier,
        val winsNeeded: Int,
        val winsHave: Int,
    ) : SudokuStartResult

    data class Failed(val reason: String) : SudokuStartResult
}

/**
 * Open a tier: locked -> [SudokuStartResult.Locked]; saved game -> resumed untouched (never regenerated, even if the
 * generator version moved); else count a play, generate on [dispatcher] (seed = f(baseSeed, tier, play index)),
 * save at once so the puzzle survives process death. Unlimited fresh puzzles: the play index only grows.
 */
class StartOrResumeSudoku(
    private val repo: SudokuRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val baseSeed: Long = DEFAULT_BASE_SEED,
    private val timeBudgetMs: Long = GENERATION_BUDGET_MS,
    private val generate: (SudokuTier, Long, () -> Boolean) -> SudokuPuzzle =
        { t, s, stop -> SudokuGenerator.generate(t, s, stop) },
) {
    suspend operator fun invoke(tier: SudokuTier): SudokuStartResult {
        val wins = repo.winsByTier()
        if (!tier.isUnlocked(wins)) {
            val req = tier.unlockTier ?: return SudokuStartResult.Failed("tier locked without requirement")
            return SudokuStartResult.Locked(tier, req, tier.unlockWins, wins[req] ?: 0)
        }
        repo.getActiveGame(tier)?.let { return SudokuStartResult.Resumed(SudokuGameEngine.normalize(it)) }

        val index = repo.nextPlayIndex(tier)
        val seed = seedFor(baseSeed, tier, index)
        val puzzle = try {
            withContext(dispatcher) {
                // Bounded: stops retrying once the budget is spent or this coroutine is cancelled (best valid puzzle so far is used).
                val deadline = System.nanoTime() + timeBudgetMs * 1_000_000L
                generate(tier, seed) { !isActive || System.nanoTime() > deadline }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            return SudokuStartResult.Failed(t.message ?: "generation failed")
        }
        val state = SudokuGameEngine.newGame(puzzle)
        repo.saveActiveGame(state)
        return SudokuStartResult.Started(state)
    }

    companion object {
        /** "SDKU". Changing it changes every future puzzle: treat like a generator version bump. */
        const val DEFAULT_BASE_SEED: Long = 0x5344_4B55L

        /** Soft cap for retrying generation attempts (low-end devices). The first attempt always completes. */
        const val GENERATION_BUDGET_MS: Long = 3_000L

        fun seedFor(baseSeed: Long, tier: SudokuTier, playIndex: Int): Long =
            SeededRng(baseSeed xor ((tier.ordinal + 1).toLong() * 0x2545F4914F6CDD1DL) xor (playIndex.toLong() * -0x61c8864680b583ebL))
                .nextLong()
    }
}

/** Autosave. Only a PLAYING game is stored; finished games are cleared by [CompleteSudoku] instead. */
class SaveSudokuGame(private val repo: SudokuRepository) {
    suspend operator fun invoke(state: SudokuGameState) {
        if (state.status == SudokuStatus.PLAYING) repo.saveActiveGame(state)
    }
}

/**
 * Idle-exit rule: a game left with NO moves in under [MIN_PLAY_MS] of play time is not a game. Pure.
 * "Move" = any digit / note / erase (each pushes an undo step), a hint, or a mistake. Time = game clock (monotonic, pauses
 * excluded), never wall clock. Only a PLAYING game can be discarded.
 */
object SudokuIdleExit {
    const val MIN_PLAY_MS: Long = 5_000L

    fun hasMoves(state: SudokuGameState): Boolean =
        state.undo.isNotEmpty() || state.hintsUsed > 0 || state.mistakes > 0

    fun shouldDiscard(state: SudokuGameState): Boolean =
        state.status == SudokuStatus.PLAYING && !hasMoves(state) && state.elapsedMs < MIN_PLAY_MS
}

/**
 * Save on leave / app stop / user pause: an idle game ([SudokuIdleExit.shouldDiscard]) clears the save instead, so the entry
 * shows no CONTINUE. Stats, loss and streak are untouched on abandon anyway (only [CompleteSudoku] writes them); the
 * start-time `plays` count is also the puzzle seed index, so it is kept (next puzzle differs).
 */
class SaveOrDiscardSudokuGame(private val repo: SudokuRepository) {
    suspend operator fun invoke(state: SudokuGameState) {
        when {
            state.status != SudokuStatus.PLAYING -> Unit
            SudokuIdleExit.shouldDiscard(state) -> repo.clearActiveGame(state.tier)
            else -> repo.saveActiveGame(state)
        }
    }
}

/** Drop the saved game of [tier] (abandon). The play stays counted. */
class ResetActiveSudokuGame(private val repo: SudokuRepository) {
    suspend operator fun invoke(tier: SudokuTier) = repo.clearActiveGame(tier)
}

sealed interface SudokuHintResult {
    /** [state] has the digit placed, hintsUsed + 1 and ONE shared HINT charge spent. */
    data class Granted(val state: SudokuGameState, val hint: SudokuHint) : SudokuHintResult

    data object NoCharge : SudokuHintResult

    /** Not playing / paused / board already solved: nothing spent. */
    data object Unavailable : SudokuHintResult
}

/**
 * Reveal a digit with the SHARED Queens HINT booster (same inventory, via [ConsumeBooster]). Nothing is spent unless a hint
 * exists and the charge was taken. Target = selected cell when empty or wrong, else the best logical cell.
 */
class UseSudokuHint(private val consume: ConsumeBooster) {
    suspend operator fun invoke(state: SudokuGameState, elapsedMs: Long = state.elapsedMs): SudokuHintResult {
        if (!state.isActive) return SudokuHintResult.Unavailable
        val hint = SudokuHintFinder.find(
            state.size,
            state.puzzle.solution,
            state.values,
            state.selected.takeIf { it >= 0 },
        ) ?: return SudokuHintResult.Unavailable
        if (!consume(Booster.HINT)) return SudokuHintResult.NoCharge
        val next = SudokuGameEngine.applyHint(state, hint, elapsedMs)
        return SudokuHintResult.Granted(next, hint)
    }
}

/**
 * [won] false = lost (or the game was still running and nothing was touched). [progress] = stored stats after a win.
 * [justUnlocked] = tiers whose requirement this win completed.
 */
data class SudokuCompletion(
    val won: Boolean,
    val progress: SudokuProgress? = null,
    val isNewBestTime: Boolean = false,
    val isNewBestScore: Boolean = false,
    val firstWinInTier: Boolean = false,
    val streakDayAdded: Boolean = false,
    val justUnlocked: List<SudokuTier> = emptyList(),
)

/**
 * Finish a game. SOLVED: merge progress, count today in the SHARED streak (idempotent per day), count the win toward the
 * interstitial pacing, clear the save. GAME_OVER: clear the save only (play was already counted at start). PLAYING: no-op.
 * No reward on win. Call ONCE per finished game.
 */
class CompleteSudoku(
    private val repo: SudokuRepository,
    private val recordPlayedToday: RecordPlayedToday,
    private val recordWon: RecordSudokuWon,
) {
    suspend operator fun invoke(state: SudokuGameState): SudokuCompletion {
        when (state.status) {
            SudokuStatus.PLAYING -> return SudokuCompletion(won = false)
            SudokuStatus.GAME_OVER -> {
                repo.clearActiveGame(state.tier)
                return SudokuCompletion(won = false)
            }
            SudokuStatus.SOLVED -> Unit
        }
        val tier = state.tier
        val before = repo.getProgress(tier)
        val after = repo.recordWin(tier, state.elapsedMs, state.score)
        val streakAdded = recordPlayedToday()
        recordWon()
        repo.clearActiveGame(tier)
        val unlocked = SudokuTier.entries.filter {
            it.unlockTier == tier && before.wins < it.unlockWins && after.wins >= it.unlockWins
        }
        return SudokuCompletion(
            won = true,
            progress = after,
            isNewBestTime = before.bestTimeMs == null || state.elapsedMs < before.bestTimeMs,
            isNewBestScore = state.score > before.bestScore,
            firstWinInTier = before.wins == 0,
            streakDayAdded = streakAdded,
            justUnlocked = unlocked,
        )
    }
}

/** Picker feed: one row per tier with stats, unlock state and saved-game flag. */
class ObserveSudokuProgress(private val repo: SudokuRepository) {
    operator fun invoke(): Flow<List<SudokuTierStatus>> =
        combine(repo.observeProgress(), repo.observeActiveTiers()) { progress, active ->
            val wins = progress.mapValues { it.value.wins }
            SudokuTier.entries.map { tier ->
                SudokuTierStatus(
                    tier = tier,
                    progress = progress[tier] ?: SudokuProgress(tier),
                    unlocked = tier.isUnlocked(wins),
                    hasActiveGame = tier in active,
                    requirementWins = tier.unlockTier?.let { wins[it] } ?: 0,
                )
            }
        }
}

/** Saved timer of [tier]'s active game for the entry CONTINUE pill; null when there is no save. */
class GetActiveSudokuElapsed(private val repo: SudokuRepository) {
    suspend operator fun invoke(tier: SudokuTier): Long? = repo.getActiveGame(tier)?.elapsedMs
}

/**
 * Sudoku interstitials (rules in [SudokuAdRules]): after a win (called when the player taps Next puzzle / Back) and before a
 * restart. Reuses [ShowInterstitialIfAllowed] with the own triggers [AdTrigger.SUDOKU_WON_CONTINUE] / [AdTrigger.SUDOKU_RESTART]:
 * kill switch, Remove Ads, consent and ad readiness still apply; the Queens ad-free level, cadence and caps do not.
 * Queens lastInterstitialLevel is left alone. Both return true when an ad was shown; false = proceed at once (fail open).
 */
class ShowSudokuInterstitialIfAllowed(
    private val show: ShowInterstitialIfAllowed,
) {
    /** [tierWinsIncludingThis] = lifetime wins in [tier] counting the win just recorded by [CompleteSudoku]. */
    suspend fun onWin(tier: SudokuTier, tierWinsIncludingThis: Int?): Boolean {
        if (!SudokuAdRules.showOnWin(tier, tierWinsIncludingThis)) return false
        return show(AdTrigger.SUDOKU_WON_CONTINUE, level = 0, isDaily = false, persistLevel = false)
    }

    suspend fun onRestart(tier: SudokuTier): Boolean {
        if (!SudokuAdRules.showOnRestart(tier)) return false
        return show(AdTrigger.SUDOKU_RESTART, level = 0, isDaily = false, persistLevel = false)
    }
}
