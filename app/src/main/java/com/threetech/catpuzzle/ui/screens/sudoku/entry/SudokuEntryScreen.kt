package com.threetech.catpuzzle.ui.screens.sudoku.entry

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.domain.sudoku.model.SudokuTier
import com.threetech.catpuzzle.ui.components.Button3D
import com.threetech.catpuzzle.ui.components.ButtonStyle
import com.threetech.catpuzzle.ui.components.HomeMode
import com.threetech.catpuzzle.ui.components.HomeModeTabs
import com.threetech.catpuzzle.ui.components.IconImage
import com.threetech.catpuzzle.ui.components.PillButton
import com.threetech.catpuzzle.ui.components.homeBackground
import com.threetech.catpuzzle.ui.theme.FredokaFamily
import com.threetech.catpuzzle.ui.theme.GameColors

private val CardShape = RoundedCornerShape(20.dp)
private val RowShape = RoundedCornerShape(14.dp)
private val ContentMaxWidth = 420.dp
private val SelectedTint = GameColors.CtaTealFace.copy(alpha = 0.12f)

/** Display name of a tier. Shared with the game screen (P3b). */
@StringRes
internal fun SudokuTier.labelRes(): Int = when (this) {
    SudokuTier.EASY -> R.string.sudoku_tier_easy
    SudokuTier.MEDIUM -> R.string.sudoku_tier_medium
    SudokuTier.HARD -> R.string.sudoku_tier_hard
    SudokuTier.EXPERT -> R.string.sudoku_tier_expert
    SudokuTier.MASTER -> R.string.sudoku_tier_master
    SudokuTier.EXTREME -> R.string.sudoku_tier_extreme
}

/** Dot colour of a tier (default gameplay palette, not per biome). */
internal fun SudokuTier.dotColor(): Color = when (this) {
    SudokuTier.EASY -> GameColors.CtaGreenFace
    SudokuTier.MEDIUM -> GameColors.CtaBlueFace
    SudokuTier.HARD -> GameColors.Primary
    SudokuTier.EXPERT -> GameColors.DailyPink
    SudokuTier.MASTER -> GameColors.region(6)
    SudokuTier.EXTREME -> GameColors.Coral
}

/**
 * Sudoku entry (difficulty picker). Stateless: state + lambdas only. Scrolling content, then CONTINUE / NEW GAME, then the
 * shared Levels / Sudoku tab row pinned at the bottom (Sudoku selected; [onLevelsTab] returns to Home). No ads here.
 */
@Composable
fun SudokuEntryScreen(
    state: SudokuEntryUiState,
    snackbarHostState: SnackbarHostState,
    onLevelsTab: () -> Unit,
    onSelectTier: (SudokuTier) -> Unit,
    onContinue: () -> Unit,
    onNewGame: () -> Unit,
    onConfirmNewGame: () -> Unit,
    onDismissConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tierName = stringResource(state.selected.labelRes())
    Box(modifier.fillMaxSize().homeBackground()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Header(Modifier.widthIn(max = ContentMaxWidth))
                DifficultyCard(state, onSelectTier, Modifier.widthIn(max = ContentMaxWidth))
                StatsCard(state, tierName, Modifier.widthIn(max = ContentMaxWidth))
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.canContinue) {
                    Button3D(
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth().widthIn(max = ContentMaxWidth),
                        style = ButtonStyle.Pink,
                        totalHeight = 74.dp,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PillText(stringResource(R.string.sudoku_continue), 22.sp, ButtonStyle.Pink.content, shadow = true)
                            PillText(
                                stringResource(R.string.sudoku_continue_detail, state.continueTimer.orEmpty(), tierName),
                                14.sp,
                                ButtonStyle.Pink.content,
                                shadow = true,
                            )
                        }
                    }
                }
                Button3D(
                    onClick = onNewGame,
                    modifier = Modifier.fillMaxWidth().widthIn(max = ContentMaxWidth),
                    style = ButtonStyle.Secondary,
                ) {
                    PillText(
                        stringResource(R.string.sudoku_new_game, tierName.uppercase()),
                        20.sp,
                        GameColors.CtaTealLip,
                        shadow = false,
                    )
                }
            }
            HomeModeTabs(selected = HomeMode.Sudoku, onLevels = onLevelsTab, onSudoku = {})
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .systemBarsPadding()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
        )
        if (state.confirmNewGame) {
            ConfirmNewGameDialog(tierName, onConfirmNewGame, onDismissConfirm)
        }
    }
}

@Composable
private fun PillText(text: String, size: androidx.compose.ui.unit.TextUnit, color: Color, shadow: Boolean) {
    Text(
        text,
        color = color,
        fontSize = size,
        fontWeight = FontWeight.Bold,
        fontFamily = FredokaFamily,
        maxLines = 1,
        style = if (shadow) {
            androidx.compose.ui.text.TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(Color(0x66000000), androidx.compose.ui.geometry.Offset(0f, 2f), 3f),
            )
        } else {
            androidx.compose.ui.text.TextStyle.Default
        },
    )
}

@Composable
private fun Header(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        IconImage(R.drawable.ic_game_sudoku, size = 56.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.sudoku_entry_title),
            modifier = Modifier.semantics { heading() },
            color = GameColors.Title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FredokaFamily,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.sudoku_entry_subtitle),
            color = GameColors.Body,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DifficultyCard(
    state: SudokuEntryUiState,
    onSelectTier: (SudokuTier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth(), shape = CardShape, color = GameColors.Card, shadowElevation = 1.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.sudoku_choose_difficulty),
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp).semantics { heading() },
                color = GameColors.Title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FredokaFamily,
            )
            state.rows.forEach { row ->
                TierRow(row, selected = row.tier == state.selected, onClick = { onSelectTier(row.tier) })
            }
        }
    }
}

@Composable
private fun TierRow(row: SudokuTierRowUi, selected: Boolean, onClick: () -> Unit) {
    val name = stringResource(row.tier.labelRes())
    val size = stringResource(R.string.sudoku_grid_size, row.gridSize)
    // Locked rows stay clickable (tap explains the requirement); only the visuals are dimmed.
    val description = if (row.locked) {
        stringResource(R.string.sudoku_tier_locked_description, name, size, row.winsHave, row.winsNeeded)
    } else {
        stringResource(R.string.sudoku_tier_description, name, size)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) SelectedTint else Color.Transparent, RowShape)
            .border(2.dp, if (selected) GameColors.CtaTealFace else Color.Transparent, RowShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                this.selected = selected
            }
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .alpha(if (row.locked) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RadioMark(selected)
        Box(Modifier.size(12.dp).background(row.tier.dotColor(), CircleShape))
        Text(
            name,
            color = GameColors.Title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FredokaFamily,
        )
        Text(size, color = GameColors.Body, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (row.locked) {
            IconImage(R.drawable.ic_lock, size = 18.dp, tint = GameColors.Body)
            Text(
                stringResource(R.string.sudoku_unlock_counter, row.winsHave, row.winsNeeded),
                modifier = Modifier
                    .background(GameColors.Disabled, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                color = GameColors.Body,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RadioMark(selected: Boolean, size: Dp = 22.dp) {
    Box(
        Modifier
            .size(size)
            .border(2.dp, if (selected) GameColors.CtaTealFace else GameColors.FishOff, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(size - 12.dp).background(GameColors.CtaTealFace, CircleShape))
    }
}

@Composable
private fun StatsCard(state: SudokuEntryUiState, tierName: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = CardShape, color = GameColors.Card, shadowElevation = 1.dp) {
        val stats = state.stats
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconImage(R.drawable.ic_trophy, size = 40.dp)
            if (stats == null) {
                Column {
                    Text(
                        stringResource(R.string.sudoku_stats_empty_title),
                        color = GameColors.Title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FredokaFamily,
                    )
                    Text(stringResource(R.string.sudoku_stats_empty_body), color = GameColors.Body, fontSize = 14.sp)
                }
            } else {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.sudoku_stats_title, tierName),
                        color = GameColors.Title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FredokaFamily,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatCell(stringResource(R.string.sudoku_stat_best_time), stats.bestTime)
                        StatCell(stringResource(R.string.sudoku_stat_best_score), stats.bestScore.toString())
                        StatCell(stringResource(R.string.sudoku_stat_wins), stats.wins.toString())
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column {
        Text(label, color = GameColors.StatCardLabel, fontSize = 12.sp)
        Text(value, color = GameColors.StatCardValue, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FredokaFamily)
    }
}

@Composable
private fun ConfirmNewGameDialog(tierName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = CardShape, color = GameColors.Card, shadowElevation = 6.dp) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.sudoku_new_game_confirm_title),
                    color = GameColors.Title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FredokaFamily,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.sudoku_new_game_confirm_body, tierName),
                    color = GameColors.Body,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
                PillButton(
                    text = stringResource(R.string.sudoku_new_game_confirm_yes),
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    style = ButtonStyle.Pink,
                )
                PillButton(
                    text = stringResource(R.string.sudoku_new_game_confirm_no),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    filled = false,
                )
            }
        }
    }
}
