package com.tm.meow.sudoku.puzzle.ui.screens.sudoku.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.domain.ads.RewardedLoadState
import com.tm.meow.sudoku.puzzle.ui.components.ButtonStyle
import com.tm.meow.sudoku.puzzle.ui.components.Confetti
import com.tm.meow.sudoku.puzzle.ui.components.DialogScrim
import com.tm.meow.sudoku.puzzle.ui.components.IconImage
import com.tm.meow.sudoku.puzzle.ui.components.PillButton
import com.tm.meow.sudoku.puzzle.ui.components.StatusBarIconsEffect
import com.tm.meow.sudoku.puzzle.ui.screens.game.LoseScreenLayout
import com.tm.meow.sudoku.puzzle.ui.screens.game.rememberAnimationsEnabled
import com.tm.meow.sudoku.puzzle.ui.screens.sudoku.entry.labelRes
import com.tm.meow.sudoku.puzzle.ui.theme.FredokaFamily
import com.tm.meow.sudoku.puzzle.ui.theme.GameColors
import kotlinx.coroutines.delay

/** Scrim + centred white card. Taps inside the card never reach the scrim. [onScrimTap] null = not dismissible by tap. */
@Composable
internal fun DialogCard(
    title: String,
    onScrimTap: (() -> Unit)?,
    modifier: Modifier = Modifier,
    scrimColor: androidx.compose.ui.graphics.Color = GameColors.Scrim,
    lightStatusIcons: Boolean = false,
    behind: @Composable () -> Unit = {},
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    DialogScrim(Modifier.semantics { paneTitle = title }, onClick = onScrimTap, scrimColor = scrimColor) {
        // Dark outcome scrim (win / game over): light status-bar icons, same as the Queens outcome dialogs. Restored on dismiss.
        if (lightStatusIcons) StatusBarIconsEffect()
        behind()
        val absorb = remember { MutableInteractionSource() }
        Column(
            modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .background(GameColors.Card, RoundedCornerShape(28.dp))
                .clickable(interactionSource = absorb, indication = null, onClick = {})
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/**
 * False for the first [ARM_DELAY_MS] after the dialog appears. The last digit tap on the pad can be followed by a second
 * tap just as the win / game-over card pops up; ignoring buttons briefly stops that from triggering "Next puzzle"
 * (and with it an interstitial) by accident.
 */
@Composable
internal fun rememberInputArmed(): Boolean {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(ARM_DELAY_MS)
        armed = true
    }
    return armed
}

private const val ARM_DELAY_MS = 700L

@Composable
internal fun DialogTitle(text: String) {
    Text(
        text,
        color = GameColors.Title,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FredokaFamily,
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun DialogBody(text: String) {
    Text(text, color = GameColors.Body, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
}

/** "Restart <tier>?" - teal Restart, Keep playing. Board stays dimmed behind. */
@Composable
internal fun SudokuRestartDialog(
    tierName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(R.string.sudoku_restart_title, tierName)
    DialogCard(title, onScrimTap = onDismiss) {
        IconImage(R.drawable.ic_reloading, size = 44.dp, tint = GameColors.Primary)
        Spacer(Modifier.height(10.dp))
        DialogTitle(title)
        Spacer(Modifier.height(6.dp))
        DialogBody(stringResource(R.string.sudoku_restart_body))
        Spacer(Modifier.height(20.dp))
        PillButton(
            text = stringResource(R.string.sudoku_restart_confirm),
            onClick = onConfirm,
            style = ButtonStyle.Teal,
            textSize = 18.sp,
            horizontalPadding = 16.dp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        PillButton(
            text = stringResource(R.string.sudoku_keep_playing),
            onClick = onDismiss,
            filled = false,
            textSize = 18.sp,
            horizontalPadding = 16.dp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun SudokuWinDialog(
    win: SudokuWinUi,
    onNext: () -> Unit,
    onDone: () -> Unit,
) {
    val title = stringResource(R.string.sudoku_win_title)
    val armed = rememberInputArmed()
    DialogCard(
        title,
        onScrimTap = null,
        scrimColor = GameColors.OutcomeScrim,
        lightStatusIcons = true,
        behind = { if (rememberAnimationsEnabled(null)) Confetti() },
    ) {
        DialogTitle(title)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            WinStat(stringResource(R.string.sudoku_stat_time), win.timeText)
            WinStat(stringResource(R.string.sudoku_stat_score), win.score.toString())
            WinStat(stringResource(R.string.sudoku_stat_mistakes), win.mistakes.toString())
            WinStat(stringResource(R.string.sudoku_stat_hints), win.hintsUsed.toString())
        }
        if (win.newBestTime) WinNote(stringResource(R.string.sudoku_win_best_time))
        if (win.newBestScore) WinNote(stringResource(R.string.sudoku_win_best_score))
        if (win.unlocked.isNotEmpty()) {
            val names = win.unlocked.map { stringResource(it.labelRes()) }.joinToString(", ")
            WinNote(stringResource(R.string.sudoku_win_unlocked, names))
        }
        Spacer(Modifier.height(20.dp))
        PillButton(
            text = stringResource(R.string.sudoku_win_next),
            onClick = { if (armed) onNext() },
            textSize = 18.sp,
            horizontalPadding = 16.dp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        PillButton(
            text = stringResource(R.string.sudoku_win_done),
            onClick = { if (armed) onDone() },
            filled = false,
            textSize = 18.sp,
            horizontalPadding = 16.dp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.WinStat(label: String, value: String) {
    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = GameColors.StatCardLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(value, color = GameColors.StatCardValue, fontSize = cappedSp(20f), fontWeight = FontWeight.Bold, fontFamily = FredokaFamily, maxLines = 1)
    }
}

@Composable
private fun WinNote(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, color = GameColors.Primary, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
}

/**
 * Out of hearts. Top red "Watch Ad to Continue" (rewarded: +1 heart, same game) only when [onWatchRevive] != null
 * (rewarded allowed); inert while the ad loads / a request is in flight, "Try Again" after a load failure.
 * Then Restart (same puzzle, fresh board) and Quit. The reward is granted by the caller only on the earned-reward result.
 */
@Composable
internal fun SudokuGameOverDialog(
    mistakes: Int,
    onRestart: () -> Unit,
    onQuit: () -> Unit,
    onWatchRevive: (() -> Unit)? = null,
    rewardedLoad: RewardedLoadState = RewardedLoadState.IDLE,
    rewardedBusy: Boolean = false,
    onRetryRewardedLoad: () -> Unit = {},
) {
    val title = stringResource(R.string.sudoku_over_title)
    val armed = rememberInputArmed()
    val resources = LocalContext.current.resources
    // Same screen as Levels "Out of lives" (shared LoseScreenLayout): sad-cat hero, revive CTA, Home + Restart row.
    LoseScreenLayout(
        title = title,
        message = resources.getQuantityString(R.plurals.sudoku_over_body, mistakes, mistakes),
        heroDescription = stringResource(R.string.sudoku_over_hero_description),
        reviveDescription = stringResource(R.string.sudoku_over_watch_ad_description),
        reviveLabel = stringResource(R.string.lose_watch_ad),
        homeLabel = stringResource(R.string.sudoku_over_home),
        retryLabel = stringResource(R.string.sudoku_over_restart),
        onRetry = onRestart,
        onHome = onQuit,
        onWatchRevive = onWatchRevive,
        rewardedLoad = rewardedLoad,
        rewardedBusy = rewardedBusy,
        onRetryRewardedLoad = onRetryRewardedLoad,
        inputArmed = armed,
    )
}
