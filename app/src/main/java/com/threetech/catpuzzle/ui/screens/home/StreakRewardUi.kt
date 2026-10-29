package com.threetech.catpuzzle.ui.screens.home

import androidx.compose.runtime.Immutable
import com.threetech.catpuzzle.domain.engine.MouseBoosterPolicy
import com.threetech.catpuzzle.domain.engine.StreakRewardPolicy
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.model.MilestoneStatus
import com.threetech.catpuzzle.domain.model.RewardLine
import com.threetech.catpuzzle.domain.model.StreakClaim
import com.threetech.catpuzzle.domain.model.StreakRewardState

@Immutable
data class RewardLineUi(val booster: Booster, val amount: Int)

@Immutable
data class MilestoneUi(val day: Int, val status: MilestoneStatus, val lines: List<RewardLineUi>)

/**
 * Milestone track on the Streak screen. [visible] false = kill switch off (track hidden).
 * [cycleDay] 0 = no live streak. All five milestones are always listed (locked when no streak).
 */
@Immutable
data class StreakRewardUi(
    val visible: Boolean = false,
    val cycleDay: Int = 0,
    val cycleLength: Int = StreakRewardPolicy.CYCLE_LENGTH,
    val milestones: List<MilestoneUi> = emptyList(),
)

/** Claim dialog content. [mouseLocked] = the player is below the Mouse unlock level (credited anyway). */
@Immutable
data class RewardClaimedUi(
    val day: Int,
    val lines: List<RewardLineUi>,
    val mouseLocked: Boolean,
    val mouseUnlockLevel: Int = MouseBoosterPolicy.UNLOCK_LEVEL,
)

/** One-shot Streak screen events. */
sealed interface StreakEvent {
    /** [playSfx] = Sound setting at claim time (the audio controller does not apply it for in-game style cues). */
    data class RewardClaimed(val reward: RewardClaimedUi, val playSfx: Boolean) : StreakEvent
}

/** Domain -> UI model for streak rewards. Pure. */
object StreakRewardUiMapper {
    private fun lines(rewards: List<RewardLine>) = rewards.map { RewardLineUi(it.booster, it.amount) }

    fun map(state: StreakRewardState): StreakRewardUi {
        if (!state.enabled) return StreakRewardUi()
        val milestones = if (state.nodes.isEmpty()) {
            StreakRewardPolicy.MILESTONES.map { MilestoneUi(it.day, MilestoneStatus.LOCKED, lines(it.rewards)) }
        } else {
            state.nodes.map { MilestoneUi(it.milestone.day, it.status, lines(it.milestone.rewards)) }
        }
        return StreakRewardUi(visible = true, cycleDay = state.cycleDay, milestones = milestones)
    }

    fun claimed(claim: StreakClaim): RewardClaimedUi = RewardClaimedUi(
        day = claim.day,
        lines = lines(claim.lines),
        mouseLocked = !MouseBoosterPolicy.isUnlocked(claim.playerLevel),
    )
}
