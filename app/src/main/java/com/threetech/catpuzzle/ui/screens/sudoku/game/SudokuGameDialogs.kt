package com.threetech.catpuzzle.ui.screens.sudoku.game

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.ui.components.ButtonStyle
import com.threetech.catpuzzle.ui.components.Confetti
import com.threetech.catpuzzle.ui.components.DialogScrim
import com.threetech.catpuzzle.ui.components.IconImage
import com.threetech.catpuzzle.ui.components.PillButton
import com.threetech.catpuzzle.ui.screens.sudoku.entry.labelRes
import com.threetech.catpuzzle.ui.theme.FredokaFamily
import com.threetech.catpuzzle.ui.theme.GameColors

/** Scrim + centred white card. Taps inside the card never reach the scrim. [onScrimTap] null = not dismissible by tap. */
@Composable
private fun DialogCard(
    title: String,
    onScrimTap: (() -> Unit)?,
    modifier: Modifier = Modifier,
    scrimColor: androidx.compose.ui.graphics.Color = GameColors.Scrim,
    behind: @Composable () -> Unit = {},
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    DialogScrim(Modifier.semantics { paneTitle = title }, onClick = onScrimTap, scrimColor = scrimColor) {
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

@Composable
private fun DialogTitle(text: String) {
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
private fun DialogBody(text: String) {
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

/** Placeholder for the P3c tutorial: short rules. */
@Composable
internal fun SudokuHelpDialog(onDismiss: () -> Unit) {
    val title = stringResource(R.string.sudoku_help_title)
    DialogCard(title, onScrimTap = onDismiss) {
        DialogTitle(title)
        Spacer(Modifier.height(10.dp))
        DialogBody(stringResource(R.string.sudoku_help_body))
        Spacer(Modifier.height(20.dp))
        PillButton(
            text = stringResource(R.string.sudoku_help_ok),
            onClick = onDismiss,
            style = ButtonStyle.Teal,
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
    DialogCard(title, onScrimTap = null, scrimColor = GameColors.OutcomeScrim, behind = { Confetti() }) {
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
            onClick = onNext,
            textSize = 18.sp,
            horizontalPadding = 16.dp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        PillButton(
            text = stringResource(R.string.sudoku_win_done),
            onClick = onDone,
            filled = false,
            textSize = 18.sp,
            horizontalPadding = 16.dp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.WinStat(label: String, value: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = GameColors.StatCardLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(value, color = GameColors.StatCardValue, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FredokaFamily, maxLines = 1)
    }
}

@Composable
private fun WinNote(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, color = GameColors.Primary, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
}

@Composable
internal fun SudokuGameOverDialog(
    mistakes: Int,
    onRetry: () -> Unit,
    onQuit: () -> Unit,
) {
    val title = stringResource(R.string.sudoku_over_title)
    DialogCard(title, onScrimTap = null, scrimColor = GameColors.OutcomeScrim) {
        DialogTitle(title)
        Spacer(Modifier.height(6.dp))
        DialogBody(stringResource(R.string.sudoku_over_body, mistakes))
        Spacer(Modifier.height(20.dp))
        PillButton(
            text = stringResource(R.string.sudoku_over_retry),
            onClick = onRetry,
            leadingIconRes = R.drawable.ic_reloading,
            textSize = 18.sp,
            horizontalPadding = 16.dp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        PillButton(
            text = stringResource(R.string.sudoku_over_quit),
            onClick = onQuit,
            filled = false,
            textSize = 18.sp,
            horizontalPadding = 16.dp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
