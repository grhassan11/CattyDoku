package com.threetech.catpuzzle.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.ui.components.CatIcon
import com.threetech.catpuzzle.ui.components.Confetti
import com.threetech.catpuzzle.ui.components.DialogScrim
import com.threetech.catpuzzle.ui.components.HintBulb
import com.threetech.catpuzzle.ui.components.MouseIcon
import com.threetech.catpuzzle.ui.components.PillButton
import com.threetech.catpuzzle.ui.components.Sunburst
import com.threetech.catpuzzle.ui.theme.GameColors

/**
 * "Reward claimed" celebration: confetti, sunburst, the credited boosters, a note when the Mouse is still locked.
 * Stateless; [onDismiss] closes it (button or system back).
 */
@Composable
fun StreakRewardDialog(
    reward: RewardClaimedUi,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)
    val title = stringResource(R.string.streak_reward_dialog_title)
    val labels = reward.lines.map { rewardLabel(it) }
    val description = stringResource(R.string.streak_reward_dialog_description, labels.joinToString(", "))

    DialogScrim(modifier.semantics { paneTitle = title }, scrimColor = GameColors.WinScrim) {
        Confetti()
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(16.dp))
            Box(contentAlignment = Alignment.Center) {
                Sunburst(Modifier.size(320.dp))
                Column(
                    Modifier
                        .widthIn(max = 320.dp)
                        .fillMaxWidth()
                        .background(GameColors.Card, RoundedCornerShape(28.dp))
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                        .semantics(mergeDescendants = true) { contentDescription = description },
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    reward.lines.forEachIndexed { i, line ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clearAndSetSemantics { },
                        ) {
                            RewardBoosterIcon(line.booster, size = 48.dp)
                            Column {
                                Text(
                                    text = "+" + labels[i],
                                    color = GameColors.Title,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (line.booster == Booster.MOUSE && reward.mouseLocked) {
                                    Text(
                                        text = stringResource(R.string.streak_reward_mouse_locked, reward.mouseUnlockLevel),
                                        color = GameColors.Body,
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            PillButton(
                text = stringResource(R.string.streak_reward_dialog_ok),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
            )
        }
    }
}

/** Booster art for reward rows / chips. Decorative: the label carries the meaning. */
@Composable
internal fun RewardBoosterIcon(booster: Booster, size: Dp, modifier: Modifier = Modifier) {
    when (booster) {
        Booster.CAT -> CatIcon(modifier, size)
        Booster.HINT -> HintBulb(modifier, size)
        Booster.MOUSE -> MouseIcon(modifier, size)
    }
}

/** "2 Cats", "1 Hint", "1 Mouse". */
@Composable
internal fun rewardLabel(line: RewardLineUi): String {
    val plural = when (line.booster) {
        Booster.CAT -> R.plurals.streak_reward_cat
        Booster.HINT -> R.plurals.streak_reward_hint
        Booster.MOUSE -> R.plurals.streak_reward_mouse
    }
    return LocalContext.current.resources.getQuantityString(plural, line.amount, line.amount)
}
