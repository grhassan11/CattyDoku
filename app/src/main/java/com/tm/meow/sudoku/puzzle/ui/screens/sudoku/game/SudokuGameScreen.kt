package com.tm.meow.sudoku.puzzle.ui.screens.sudoku.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.domain.ads.BannerAdHandle
import com.tm.meow.sudoku.puzzle.domain.ads.RewardedLoadState
import com.tm.meow.sudoku.puzzle.domain.model.Booster
import com.tm.meow.sudoku.puzzle.domain.model.PieceSkin
import com.tm.meow.sudoku.puzzle.ui.ads.BannerAdSlot
import com.tm.meow.sudoku.puzzle.ui.components.IconImage
import com.tm.meow.sudoku.puzzle.ui.components.homePattern
import com.tm.meow.sudoku.puzzle.ui.screens.game.BoosterAdDialog
import com.tm.meow.sudoku.puzzle.ui.screens.game.rememberAnimationsEnabled
import com.tm.meow.sudoku.puzzle.ui.screens.game.rememberBoardIntro
import com.tm.meow.sudoku.puzzle.ui.screens.sudoku.entry.labelRes
import com.tm.meow.sudoku.puzzle.ui.theme.FredokaFamily
import com.tm.meow.sudoku.puzzle.ui.theme.GameColors

private val ContentMaxWidth = 480.dp

/** Digits start this long after their cell along the intro wave (see [SudokuBoard]). */
internal const val SUDOKU_DIGIT_LAG_MS = 150f

/** Gap kept between the number pad and the banner slot (AdMob accidental-click policy). Same as the Levels screen. */
private val BANNER_CLEARANCE = 48.dp

/**
 * Sudoku game screen. Stateless: state + lambdas only ([SudokuGameRoute] owns the ViewModel).
 * [timeText] is a separate parameter because it changes every second while [state] does not.
 * Dialogs (restart / win / game over / out of hints) are drawn here from [SudokuGameUiState.dialog].
 */
@Composable
fun SudokuGameScreen(
    state: SudokuGameUiState,
    timeText: String,
    snackbarHostState: SnackbarHostState,
    rewardedLoad: RewardedLoadState,
    rewardedBusy: Boolean,
    onBack: () -> Unit,
    onHelp: () -> Unit,
    onRestartClick: () -> Unit,
    onPauseToggle: () -> Unit,
    onCellTap: (Int) -> Unit,
    onDigit: (Int) -> Unit,
    onUndo: () -> Unit,
    onToggleNotes: () -> Unit,
    onErase: () -> Unit,
    onHint: () -> Unit,
    onConfirmRestart: () -> Unit,
    onDismissDialog: () -> Unit,
    onRestart: () -> Unit,
    onQuit: () -> Unit,
    onWinNext: () -> Unit,
    onWinDone: () -> Unit,
    onWatchHintAd: () -> Unit,
    onWatchRevive: (() -> Unit)?,
    onRetryRewardedLoad: () -> Unit,
    onOpenStore: () -> Unit,
    modifier: Modifier = Modifier,
    /** Banner slot is allowed (reserve its height + clearance). False = no space reserved. */
    bannerReserved: Boolean = false,
    /** Loaded banner to host, or null (loading / hidden under a dialog: the slot stays reserved, the view detaches). */
    bannerAd: BannerAdHandle? = null,
) {
    BackHandler(onBack = onBack)
    val theme = remember(state.tier) { sudokuTierTheme(state.tier) }
    // Board intro wave. Held HERE (not in SudokuBoard) so the pause overlay, which removes the board from composition, never replays it.
    val intro = rememberBoardIntro(
        introKey = state.introKey.takeIf { it != 0L && !state.loading },
        animationsOn = rememberAnimationsEnabled(null),
        totalMs = 1300f,
        cellMs = 450f,
        tailMs = SUDOKU_DIGIT_LAG_MS,
    )
    CompositionLocalProvider(LocalSudokuTierTheme provides theme) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(theme.backgroundTop, theme.backgroundBottom)))
            .homePattern(),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Outer column owns the insets: banner slot (full width) sits at the bottom, ABOVE the navigation bar.
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .fillMaxHeight()
                .widthIn(max = ContentMaxWidth)
                // Policy: keep clear space between the number pad (accidental-tap zone) and the banner.
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = if (bannerReserved) BANNER_CLEARANCE else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            // Leftover height (only exists when the board is width-bound: 4x4/6x6/tall phones) is spread between sections.
            // The board's weight(1f, fill=false) claims its space first, so tight screens (9x9, small phones) get zero slack.
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (state.loading) {
                SudokuTopBar(state.tier, state.n, onBack = onBack, onHelp = {}, onRestart = {})
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = theme.accentStrong)
                }
            } else {
                SudokuTopBar(state.tier, state.n, onBack = onBack, onHelp = onHelp, onRestart = onRestartClick)
                Spacer(Modifier.height(4.dp))
                SudokuStatsRow(
                    score = state.score,
                    timeText = timeText,
                    paused = state.paused,
                    canPause = state.paused || state.inputEnabled,
                    hearts = state.hearts,
                    maxHearts = state.maxHearts,
                    onPauseToggle = onPauseToggle,
                )
                Spacer(Modifier.height(8.dp))
                // fill=false: box wraps the board (no centring slack above/below it); leftover height is spread by the Column's SpaceEvenly.
                BoxWithConstraints(Modifier.weight(1f, fill = false).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val side: Dp = minOf(maxWidth, maxHeight)
                    if (state.paused) {
                        PausePanel(onResume = onPauseToggle, modifier = Modifier.size(side))
                    } else {
                        SudokuBoard(
                            cells = state.cells,
                            n = state.n,
                            boxRows = state.boxRows,
                            boxCols = state.boxCols,
                            selected = state.selected,
                            inputEnabled = state.inputEnabled,
                            onCellTap = onCellTap,
                            size = side,
                            intro = intro,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                SudokuActionRow(
                    inputEnabled = state.inputEnabled,
                    canUndo = state.canUndo,
                    canErase = state.canErase,
                    notesMode = state.notesMode,
                    hintCharges = state.hintCharges,
                    onUndo = onUndo,
                    onToggleNotes = onToggleNotes,
                    onErase = onErase,
                    onHint = onHint,
                )
                Spacer(Modifier.height(12.dp))
                SudokuNumberPad(state.padKeys, state.inputEnabled, onDigit)
            }
        }
        }
        if (bannerReserved) BannerAdSlot(bannerAd)
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .systemBarsPadding()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
        )

        when (val dialog = state.dialog) {
            SudokuDialogUi.None -> Unit
            SudokuDialogUi.ConfirmRestart -> SudokuRestartDialog(
                tierName = stringResource(state.tier.labelRes()),
                onConfirm = onConfirmRestart,
                onDismiss = onDismissDialog,
            )
            SudokuDialogUi.HintNeeded -> BoosterAdDialog(
                booster = Booster.HINT,
                load = rewardedLoad,
                busy = rewardedBusy,
                pieceSkin = PieceSkin.cats(),
                onWatch = onWatchHintAd,
                onRetryLoad = onRetryRewardedLoad,
                onStore = onOpenStore,
                onDismiss = onDismissDialog,
            )
            is SudokuDialogUi.Won -> SudokuWinDialog(dialog.win, onNext = onWinNext, onDone = onWinDone)
            is SudokuDialogUi.GameOver -> SudokuGameOverDialog(
                dialog.mistakes,
                onRestart = onRestart,
                onQuit = onQuit,
                onWatchRevive = onWatchRevive,
                rewardedLoad = rewardedLoad,
                rewardedBusy = rewardedBusy,
                onRetryRewardedLoad = onRetryRewardedLoad,
            )
        }
    }
    }
}

/** Pause state: the board is replaced (digits are not readable while the clock is stopped). Tap anywhere to resume. */
@Composable
private fun PausePanel(onResume: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.sudoku_paused_description)
    val theme = LocalSudokuTierTheme.current
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GameColors.Card)
            .clickable(role = Role.Button, onClick = onResume)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(72.dp).background(GameColors.Primary, CircleShape), contentAlignment = Alignment.Center) {
            IconImage(R.drawable.ic_play, size = 32.dp, tint = Color.White)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.sudoku_paused),
            color = theme.ink,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FredokaFamily,
        )
        Text(stringResource(R.string.sudoku_tap_to_resume), color = theme.inkSoft, fontSize = 15.sp)
    }
}
