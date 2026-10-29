package com.threetech.catpuzzle.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.threetech.catpuzzle.ui.components.LocalAudioController
import com.threetech.catpuzzle.ui.components.playCue
import com.threetech.catpuzzle.ui.screens.game.SfxKind

/**
 * Only place that touches [StreakViewModel]; hands plain state + lambdas to the stateless [StreakScreen].
 * Owns the claim celebration: a [StreakEvent.RewardClaimed] opens [StreakRewardDialog] (plays the win cue if Sound is on).
 */
@Composable
fun StreakRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StreakViewModel = hiltViewModel(),
) {
    val calendar by viewModel.calendar.collectAsStateWithLifecycle()
    val rewards by viewModel.rewards.collectAsStateWithLifecycle()
    val audio = LocalAudioController.current
    var claimed by remember { mutableStateOf<RewardClaimedUi?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is StreakEvent.RewardClaimed -> {
                    if (event.playSfx) audio.playCue(SfxKind.WIN)
                    claimed = event.reward
                }
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        StreakScreen(
            calendar = calendar,
            rewards = rewards,
            onClaim = viewModel::claim,
            onBack = onBack,
            modifier = Modifier.fillMaxSize(),
        )
        claimed?.let { StreakRewardDialog(reward = it, onDismiss = { claimed = null }) }
    }
}
