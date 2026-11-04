package com.tm.meow.sudoku.puzzle.ui.screens.sudoku.entry

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
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
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuTier
import com.tm.meow.sudoku.puzzle.ui.components.Button3D
import com.tm.meow.sudoku.puzzle.ui.components.ButtonStyle
import com.tm.meow.sudoku.puzzle.ui.components.IconImage
import com.tm.meow.sudoku.puzzle.ui.components.PillButton
import com.tm.meow.sudoku.puzzle.domain.ads.NativeAdSlotState
import com.tm.meow.sudoku.puzzle.ui.ads.NativeAdSlot
import com.tm.meow.sudoku.puzzle.ui.screens.home.SettingsButton
import com.tm.meow.sudoku.puzzle.ui.screens.home.ShopButton
import com.tm.meow.sudoku.puzzle.ui.components.homeBackground
import com.tm.meow.sudoku.puzzle.ui.screens.sudoku.game.cappedSp
import com.tm.meow.sudoku.puzzle.ui.theme.FredokaFamily
import com.tm.meow.sudoku.puzzle.ui.theme.GameColors

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
 * bottom native ad slot (same component as Home). Top bar: Back with Shop to its right (left) and Settings (right), fixed above
 * the scrolling content. [onBack] returns to the Levels Home.
 */
@Composable
fun SudokuEntryScreen(
    state: SudokuEntryUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenShop: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectTier: (SudokuTier) -> Unit,
    onContinue: () -> Unit,
    onNewGame: () -> Unit,
    onConfirmNewGame: () -> Unit,
    onDismissConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    nativeAdSlot: NativeAdSlotState = NativeAdSlotState.Hidden,
) {
    val tierName = stringResource(state.selected.labelRes())
    Box(modifier.fillMaxSize().homeBackground()) {
        // Ad slot (Loading / Loaded) owns the navigation-bar inset (its background runs behind the bar); otherwise content keeps it.
        val contentInsets = if (nativeAdSlot.reservesSlot) {
            WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        } else {
            WindowInsets.systemBars
        }
        Column(Modifier.fillMaxSize()) {
          Column(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(contentInsets)) {
            Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
                Row(Modifier.align(Alignment.TopStart), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BackButton(onBack)
                    ShopButton(onOpenShop)
                }
                SettingsButton(onOpenSettings, Modifier.align(Alignment.TopEnd))
                // Title sits between the Back+Shop pair and Settings (112dp = 2x48dp buttons + 2x8dp gaps; symmetric to stay centered).
                Header(Modifier.align(Alignment.TopCenter).padding(horizontal = 112.dp).height(48.dp))
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DifficultyCard(state, onSelectTier, Modifier.widthIn(max = ContentMaxWidth))
                StatsCard(state, tierName, Modifier.widthIn(max = ContentMaxWidth))
            }
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().widthIn(max = ContentMaxWidth),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.canContinue) {
                        Button3D(
                            onClick = onContinue,
                            modifier = Modifier.weight(1f),
                            style = ButtonStyle.Pink,
                            horizontalPadding = 10.dp,
                            totalHeight = 74.dp,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PillText(stringResource(R.string.sudoku_continue), cappedSp(20f), ButtonStyle.Pink.content, shadow = true)
                                PillText(
                                    stringResource(R.string.sudoku_continue_detail, state.continueTimer.orEmpty(), tierName),
                                    cappedSp(13f),
                                    ButtonStyle.Pink.content,
                                    shadow = true,
                                )
                            }
                        }
                    }
                    Button3D(
                        onClick = onNewGame,
                        modifier = Modifier.weight(1f),
                        style = ButtonStyle.Primary,
                        horizontalPadding = 10.dp,
                        totalHeight = 74.dp,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PillText(
                                stringResource(R.string.sudoku_new_game),
                                cappedSp(if (state.canContinue) 17f else 22f),
                                ButtonStyle.Primary.content,
                                shadow = true,
                            )
                            PillText(tierName, cappedSp(13f), ButtonStyle.Primary.content, shadow = true)
                        }
                    }
                }
            }
          }
            NativeAdSlot(nativeAdSlot)
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
private fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.settings_back)
    Surface(
        modifier = modifier.size(48.dp).semantics { contentDescription = description },
        shape = CircleShape,
        color = GameColors.Card,
        shadowElevation = 2.dp,
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            IconImage(R.drawable.ic_back, size = 24.dp, tint = GameColors.Body)
        }
    }
}

@Composable
private fun PillText(
    text: String,
    size: androidx.compose.ui.unit.TextUnit,
    color: Color,
    shadow: Boolean,
    maxLines: Int = 1,
) {
    Text(
        text,
        color = color,
        fontSize = size,
        fontWeight = FontWeight.Bold,
        fontFamily = FredokaFamily,
        maxLines = maxLines,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
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
    Row(
        modifier.semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconImage(R.drawable.ic_game_sudoku, size = 40.dp)
        Spacer(Modifier.width(8.dp))
        // Two lines (first space becomes a line break); fixed sp (no scaling) so 2 lines always fit the 48dp row.
        Text(
            stringResource(R.string.sudoku_entry_title).replaceFirst(' ', '\n'),
            modifier = Modifier.weight(1f, fill = false),
            color = GameColors.Title,
            fontSize = 18.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FredokaFamily,
            textAlign = TextAlign.Start,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            .heightIn(min = 48.dp)
            .background(if (selected) SelectedTint else Color.Transparent, RowShape)
            .border(2.dp, if (selected) GameColors.CtaTealFace else Color.Transparent, RowShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                this.selected = selected
            }
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .alpha(if (row.locked) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RadioMark(selected)
        Box(Modifier.size(12.dp).background(row.tier.dotColor(), CircleShape))
        Text(
            name,
            color = GameColors.Title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FredokaFamily,
        )
        Text(size, color = GameColors.Body, fontSize = 12.sp, modifier = Modifier.weight(1f))
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
