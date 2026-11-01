package com.threetech.catpuzzle.ui.screens.home

import androidx.compose.runtime.Immutable
import com.threetech.catpuzzle.domain.engine.DailyRules
import com.threetech.catpuzzle.domain.model.LevelDetail
import com.threetech.catpuzzle.domain.model.LevelDifficulty
import com.threetech.catpuzzle.ui.screens.game.SettingsUi
import com.threetech.catpuzzle.ui.screens.home.worlds.WorldMapUi

/** What the Daily Challenge button offers. Unlock level lives in [DailyRules.UNLOCK_LEVEL]. */
sealed interface DailyUi {
    /** Padlock; tap explains "Reach Level N". */
    data class Locked(val requiredLevel: Int) : DailyUi

    /** Primary button, tap starts today's daily. */
    data object Available : DailyUi

    /** Today's daily completed; grey, tap only explains. */
    data object Done : DailyUi
}

/** Dot state in the streak dialog. */
enum class DayDotState { DAILY_DONE, PLAYED, MISSED, FUTURE }

@Immutable
data class DayUi(val epochDay: Long, val state: DayDotState, val isToday: Boolean)

/** Streak dialog content: Mon-Sun [week], last-30-days [recent] (oldest first). */
@Immutable
data class StreakCalendarUi(
    val current: Int,
    val best: Int,
    val week: List<DayUi>,
    val recent: List<DayUi>,
    val dailyTotal: Int,
)

/**
 * What the home screen shows.
 * [levelNumber] null = not resolved yet (button reads "Play"). [streak] = current daily streak days.
 * [calendar] null until the first emission (streak chip stays tappable, dialog shows zeros).
 */
@Immutable
data class HomeUiState(
    val levelNumber: Int?,
    val streak: Int,
    val daily: DailyUi,
    val settings: SettingsUi,
    val calendar: StreakCalendarUi? = null,
    /** At least one streak milestone reward is ready to claim: small dot on the streak chip. */
    val streakRewardPending: Boolean = false,
    /** Home world map: current level + stars per won level. */
    val worldMap: WorldMapUi = WorldMapUi(),
    /** Open level detail sheet (null = closed). */
    val levelSheet: LevelSheetUi? = null,
)

/**
 * Level detail sheet content. [completed] false = the current, never-won level: no stars and no best values.
 * [bestScore] / [bestTimeMs] null = show "--".
 */
@Immutable
data class LevelSheetUi(
    val level: Int,
    val worldNumber: Int,
    val boardSize: Int,
    val difficulty: LevelDifficulty,
    val completed: Boolean,
    val stars: Int,
    val bestScore: Int?,
    val bestTimeMs: Long?,
) {
    companion object {
        fun from(d: LevelDetail): LevelSheetUi = LevelSheetUi(
            level = d.level,
            worldNumber = d.worldNumber,
            boardSize = d.boardSize,
            difficulty = d.difficulty,
            completed = d.completed,
            stars = d.stars,
            bestScore = d.bestScore,
            bestTimeMs = d.bestTimeMs,
        )
    }
}
