package com.threetech.catpuzzle.ui.screens.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.domain.ads.RewardedLoadState
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.ui.components.ButtonStyle
import com.threetech.catpuzzle.ui.components.CatIcon
import com.threetech.catpuzzle.ui.components.DialogScrim
import com.threetech.catpuzzle.ui.components.HintBulb
import com.threetech.catpuzzle.ui.components.IconImage
import com.threetech.catpuzzle.ui.components.MouseIcon
import com.threetech.catpuzzle.ui.components.PillButton
import com.threetech.catpuzzle.ui.theme.GameColors

/**
 * "Need one?" dialog opened by tapping an EMPTY booster (play badge). Two CTAs: watch a rewarded video for +1 free, or the
 * Shop ([onStore] navigates to it on the matching pack section). Stateless: load state and busy come in,
 * taps go out. The reward CTA is inert while the ad loads / a request is in flight, and turns into "Try Again" after a
 * load failure. Never shown through Level 9(the caller only opens it when the rewarded surface is allowed).
 */
@Composable
internal fun BoosterAdDialog(
    booster: Booster,
    load: RewardedLoadState,
    busy: Boolean,
    onWatch: () -> Unit,
    onRetryLoad: () -> Unit,
    onStore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val copy = boosterCopy(booster)
    val title = stringResource(copy.title)
    val closeDescription = stringResource(R.string.reward_dialog_close)
    BackHandler(onBack = onDismiss)

    DialogScrim(Modifier.semantics { paneTitle = title }, onClick = onDismiss) {
        val absorb = remember { MutableInteractionSource() }
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .background(GameColors.Card, RoundedCornerShape(28.dp))
                // Taps inside the card must not reach the scrim (which dismisses).
                .clickable(interactionSource = absorb, indication = null, onClick = {})
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Box(
                    Modifier
                        .size(48.dp)
                        .semantics { contentDescription = closeDescription }
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    IconImage(R.drawable.ic_close, size = 20.dp, tint = GameColors.Body)
                }
            }
            when (booster) {
                Booster.CAT -> CatIcon(size = 72.dp)
                Booster.HINT -> HintBulb(size = 72.dp)
                Booster.MOUSE -> MouseIcon(size = 72.dp)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                color = GameColors.Title,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(copy.subtitle),
                color = GameColors.Body,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            val ready = load == RewardedLoadState.READY && !busy
            val failed = load == RewardedLoadState.FAILED && !busy
            PillButton(
                text = when {
                    failed -> stringResource(R.string.reward_try_again)
                    ready -> stringResource(R.string.reward_watch_video)
                    else -> stringResource(R.string.reward_loading)
                },
                onClick = {
                    when {
                        ready -> onWatch()
                        failed -> onRetryLoad()
                        else -> Unit // loading / busy: inert
                    }
                },
                leadingIconRes = if (failed) null else R.drawable.ic_play,
                textSize = 18.sp,
                horizontalPadding = 16.dp,
                style = when {
                    ready -> ButtonStyle.Green
                    failed -> ButtonStyle.Blue
                    else -> ButtonStyle.Locked
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (failed) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.reward_unavailable),
                    color = GameColors.Body,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(10.dp))
            PillButton(
                text = stringResource(copy.store),
                onClick = onStore,
                textSize = 18.sp,
                horizontalPadding = 16.dp,
                style = ButtonStyle.Primary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

private class BoosterCopy(val title: Int, val subtitle: Int, val store: Int)

private fun boosterCopy(booster: Booster): BoosterCopy = when (booster) {
    Booster.CAT -> BoosterCopy(R.string.reward_cat_title, R.string.reward_cat_subtitle, R.string.reward_cat_store)
    Booster.HINT -> BoosterCopy(R.string.reward_hint_title, R.string.reward_hint_subtitle, R.string.reward_hint_store)
    Booster.MOUSE -> BoosterCopy(R.string.reward_mouse_title, R.string.reward_mouse_subtitle, R.string.reward_mouse_store)
}
