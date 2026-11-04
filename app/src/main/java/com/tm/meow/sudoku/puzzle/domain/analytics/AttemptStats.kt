package com.tm.meow.sudoku.puzzle.domain.analytics

/** Values of the `mode` param shared by every level event. */
object AttemptMode {
    const val LEVELS = "levels"
    const val DAILY = "daily"
    const val SUDOKU = "sudoku"
}

/** Values of the `quit_reason` param on `level_quit`. */
object QuitReason {
    const val SYSTEM_BACK = "system_back"
    const val TOP_BAR_BACK = "top_bar_back"
    const val SWITCH_LEVEL = "switch_level"

    /** The ViewModel was cleared with an attempt still open (system back / swipe-back / process teardown). */
    const val CLEARED = "cleared"
}

/**
 * Identity of one level attempt; added to every level event so events join without a lookup.
 * Null fields are omitted (daily has no level number / world; sudoku has no world; difficulty is optional).
 * [attemptNo] is SESSION-LOCAL: the Nth attempt of the same level key since this screen/ViewModel was created
 * (see [AttemptTracker]). Lifetime attempt number in BigQuery:
 * `ROW_NUMBER() OVER (PARTITION BY user_pseudo_id, mode, level_number ORDER BY event_timestamp)` over the
 * `level_start` events (resumes are `level_resume`, restarts open a new `level_start`).
 */
data class LevelContext(
    val mode: String,
    val levelNumber: Int?,
    val world: Int?,
    val boardSize: Int,
    val difficulty: String?,
    val attemptNo: Int,
    val isReplay: Boolean = false,
) {
    fun toParams(): Map<String, Any> = buildMap {
        put("mode", mode)
        levelNumber?.let { put("level_number", it.toLong()) }
        world?.let { put("world", it.toLong()) }
        put("board_size", boardSize.toLong())
        difficulty?.let { put("difficulty", it) }
        put("attempt_no", attemptNo.toLong())
        put("is_replay", if (isReplay) 1L else 0L)
        put("is_daily", if (mode == AttemptMode.DAILY) 1L else 0L)
    }
}

/**
 * Progress of the current attempt at the moment an event fires. [durationSec] excludes pauses (game clock).
 * [livesLost] = hearts lost (levels) / mistakes (sudoku). [score] only once it is meaningful (end of attempt).
 */
data class AttemptStats(
    val durationSec: Int,
    val livesLost: Int,
    val hintsUsed: Int,
    val boostersUsed: Int,
    val score: Int? = null,
    val resumed: Boolean = false,
) {
    fun toParams(): Map<String, Any> = buildMap {
        put("duration_sec", durationSec.toLong())
        put("lives_lost", livesLost.toLong())
        put("hints_used", hintsUsed.toLong())
        put("boosters_used", boostersUsed.toLong())
        score?.let { put("score", it.toLong()) }
        put("resumed", if (resumed) 1L else 0L)
    }
}

/**
 * Per-ViewModel attempt bookkeeping, pure Kotlin.
 * - [begin] opens an attempt (fresh start, restart, or resume) and bumps the session-local count for [begin]'s key.
 * - [end] closes it on win / lose; [revive] re-opens it (same attempt continues).
 * - [consumeQuit] is true AT MOST ONCE per attempt, and only while the attempt is open: callers log `level_quit` then.
 */
class AttemptTracker {
    private val counts = HashMap<String, Int>()
    private var active = false
    private var quitLogged = false

    var attemptNo: Int = 0
        private set
    var boostersUsed: Int = 0
        private set
    var resumed: Boolean = false
        private set

    val isActive: Boolean get() = active

    fun begin(key: String, resumed: Boolean = false): Int {
        attemptNo = (counts[key] ?: 0) + 1
        counts[key] = attemptNo
        this.resumed = resumed
        boostersUsed = 0
        active = true
        quitLogged = false
        return attemptNo
    }

    fun boosterUsed() {
        boostersUsed++
    }

    /** Win or lose: the attempt is over and can no longer be a quit. */
    fun end() {
        active = false
    }

    /** Lose-screen revive: the same attempt goes on. */
    fun revive() {
        active = true
        quitLogged = false
    }

    fun consumeQuit(): Boolean {
        if (!active || quitLogged) return false
        quitLogged = true
        active = false
        return true
    }
}
