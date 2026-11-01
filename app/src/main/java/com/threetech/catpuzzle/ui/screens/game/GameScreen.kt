package com.threetech.catpuzzle.ui.screens.game

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.animation.core.tween
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.threetech.catpuzzle.ui.components.LocalAudioController
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.domain.ads.RewardedLoadState
import com.threetech.catpuzzle.domain.engine.IdleNudgePolicy
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.model.Pos
import com.threetech.catpuzzle.ui.ads.BannerAdSlot
import com.threetech.catpuzzle.ui.components.CatIcon
import com.threetech.catpuzzle.ui.components.FishLife
import com.threetech.catpuzzle.ui.components.HintBulb
import com.threetech.catpuzzle.ui.components.IconArt
import com.threetech.catpuzzle.ui.components.MouseIcon
import com.threetech.catpuzzle.ui.components.IconImage
import com.threetech.catpuzzle.ui.components.drawXMark
import com.threetech.catpuzzle.ui.components.rememberPressScale
import com.threetech.catpuzzle.ui.theme.GameColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

/** Everything the game screen can ask for. Stateless: state in, lambdas out, no ViewModel here. */
@Composable
fun GameScreen(
    state: GameUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onCellTap: (Pos) -> Unit,
    onCellDoubleTap: (Pos) -> Unit,
    onDrag: (List<Pos>) -> Unit,
    onDragCross: () -> Unit = {},
    onUndo: () -> Unit,
    onHint: () -> Unit,
    onCatBooster: () -> Unit,
    onMouseBooster: () -> Unit = {},
    onRetryLoad: () -> Unit,
    onNextLevel: () -> Unit,
    onLoseRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** Level Complete HOME icon; defaults to the same pop-to-Home as [onBack]. */
    onWinHome: () -> Unit = onBack,
    ads: GameAdsUi = GameAdsUi(),
    /** Booster whose "Need one?" rewarded dialog is open (null = closed). */
    boosterDialog: Booster? = null,
    /** Booster tapped while empty AND rewarded is allowed: open its dialog. */
    onEmptyBoosterTap: (Booster) -> Unit = {},
    onDismissBoosterDialog: () -> Unit = {},
    onWatchBoosterAd: (Booster) -> Unit = {},
    onRetryRewardedLoad: () -> Unit = {},
    /** Booster dialog "Get X in Store": the route opens the Shop on that booster section. */
    onOpenStore: (Booster) -> Unit = {},
    onWatchReviveAd: () -> Unit = {},
    /** Hint dim overlay closed (tap anywhere / Got it / back). */
    onHintOverlayDismiss: () -> Unit = {},
    /** Idle nudge on (read only inside the booster icon layers, so flipping it never recomposes the screen). */
    nudgeActive: () -> Boolean = { false },
    /** In-game Settings dialog (Sound & feel + Gameplay + Restart) open. */
    settingsVisible: Boolean = false,
    onDismissSettings: () -> Unit = {},
    onSettingChanged: (SettingToggle, Boolean) -> Unit = { _, _ -> },
    onRestart: () -> Unit = {},
    /** Mouse travel animation finished (run id): the ViewModel unblocks input. */
    onMouseAnimDone: (Int) -> Unit = {},
) {
    Box(modifier.fillMaxSize().background(GameColors.ScreenBackground)) {
        when (state) {
            is GameUiState.Loading -> CenterMessage {
                CircularProgressIndicator(color = GameColors.Primary)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.game_loading), color = GameColors.Body, fontSize = 16.sp)
            }
            is GameUiState.Failed -> CenterMessage {
                Text(
                    stringResource(R.string.game_load_failed),
                    color = GameColors.Title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                com.threetech.catpuzzle.ui.components.PillButton(
                    text = stringResource(R.string.game_retry),
                    onClick = onRetryLoad,
                    textSize = 18.sp,
                )
            }
            is GameUiState.Ready -> GameContent(
                content = state.content,
                onBack = onBack,
                onWinHome = onWinHome,
                onOpenSettings = onOpenSettings,
                onCellTap = onCellTap,
                onCellDoubleTap = onCellDoubleTap,
                onDrag = onDrag,
                onDragCross = onDragCross,
                onUndo = onUndo,
                onHint = onHint,
                onCatBooster = onCatBooster,
                onMouseBooster = onMouseBooster,
                onNextLevel = onNextLevel,
                onLoseRetry = onLoseRetry,
                ads = ads,
                boosterDialog = boosterDialog,
                onEmptyBoosterTap = onEmptyBoosterTap,
                onDismissBoosterDialog = onDismissBoosterDialog,
                onWatchBoosterAd = onWatchBoosterAd,
                onRetryRewardedLoad = onRetryRewardedLoad,
                onOpenStore = onOpenStore,
                onWatchReviveAd = onWatchReviveAd,
                onHintOverlayDismiss = onHintOverlayDismiss,
                nudgeActive = nudgeActive,
                settingsVisible = settingsVisible,
                onDismissSettings = onDismissSettings,
                onSettingChanged = onSettingChanged,
                onRestart = onRestart,
                onMouseAnimDone = onMouseAnimDone,
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    }
}

@Composable
private fun CenterMessage(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Composable
private fun GameContent(
    content: GameContentUi,
    onBack: () -> Unit,
    onWinHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onCellTap: (Pos) -> Unit,
    onCellDoubleTap: (Pos) -> Unit,
    onDrag: (List<Pos>) -> Unit,
    onDragCross: () -> Unit = {},
    onUndo: () -> Unit,
    onHint: () -> Unit,
    onCatBooster: () -> Unit,
    onMouseBooster: () -> Unit,
    onNextLevel: () -> Unit,
    onLoseRetry: () -> Unit,
    ads: GameAdsUi,
    boosterDialog: Booster?,
    onEmptyBoosterTap: (Booster) -> Unit,
    onDismissBoosterDialog: () -> Unit,
    onWatchBoosterAd: (Booster) -> Unit,
    onRetryRewardedLoad: () -> Unit,
    onOpenStore: (Booster) -> Unit,
    onWatchReviveAd: () -> Unit,
    onHintOverlayDismiss: () -> Unit,
    nudgeActive: () -> Boolean,
    settingsVisible: Boolean,
    onDismissSettings: () -> Unit,
    onSettingChanged: (SettingToggle, Boolean) -> Unit,
    onRestart: () -> Unit,
    onMouseAnimDone: (Int) -> Unit,
) {
    // Board bounds in root space for the hint spotlight. Written on layout, read in the overlay's draw phase only.
    val boardGeo = remember { mutableStateOf<BoardGeo?>(null) }
    // Mouse booster: button circle bounds (root space) = travel start / end. The X marks are already in the state;
    // the cells of the active run are drawn EMPTY until the mouse reaches them ([mouseRevealed] counts reached tiles).
    val mouseRun = content.mouseRun
    val mouseBtn = remember { mutableStateOf<Rect?>(null) }
    val mouseRevealed = remember(mouseRun?.id) { mutableIntStateOf(0) }
    val shownBoard = remember(content.board, mouseRun, mouseRevealed.intValue) {
        if (mouseRun == null) {
            content.board
        } else {
            val n = content.board.size
            val hidden = mouseRun.cells.drop(mouseRevealed.intValue).mapTo(HashSet()) { it.row * n + it.col }
            content.board.copy(
                cells = content.board.cells.mapIndexed { i, c ->
                    if (i in hidden && c.mark == CellMarkUi.X) c.copy(mark = CellMarkUi.EMPTY) else c
                },
            )
        }
    }
    Box(Modifier.fillMaxSize()) {
        // Outer column owns the insets: banner slot (full width) sits at the bottom, ABOVE the navigation bar.
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            TopBar(content, onBack, onOpenSettings)
            Spacer(Modifier.height(8.dp))
            LivesRow(content, onUndo)
            Spacer(Modifier.height(12.dp))
            RulesStrip()

            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val tabHeightDp = 44f
                val avail = min(maxWidth.value, maxHeight.value - tabHeightDp)
                val metrics = BoardLayoutCalc.metrics(avail, content.board.size)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RegionTab(
                        content.regions,
                        cardWidthDp = if (content.board.size >= 9) metrics.cardSideDp(content.board.size) else null,
                    )
                    BoardView(
                        board = shownBoard,
                        patternMode = content.patternMode,
                        metrics = metrics,
                        enabled = content.outcome == null && mouseRun == null,
                        onTap = onCellTap,
                        onDoubleTap = onCellDoubleTap,
                        onDrag = onDrag,
                        onDragCross = onDragCross,
                        lastCorrectPlacement = content.lastCorrectPlacement,
                        modifier = Modifier.onGloballyPositioned { boardGeo.value = BoardGeo(it.boundsInRoot(), metrics) },
                    )
                }
            }

            // No hint bar here any more: it resized the board. The explanation lives in the hint overlay's rule card.
            Spacer(Modifier.height(12.dp))
            BoostersRow(
                content.boosters, ads.rewardedSurface, onCatBooster, onHint, onMouseBooster, onEmptyBoosterTap,
                nudgeActive = nudgeActive,
                mouseAway = mouseRun != null,
                onMouseBounds = { mouseBtn.value = it },
            )
            // Policy: keep clear space between the booster buttons (accidental-tap zone) and the banner.
            Spacer(Modifier.height(if (ads.bannerReserved) BANNER_CLEARANCE else 16.dp))
        }
        if (ads.bannerReserved) BannerAdSlot(ads.bannerAd)
        }

        if (mouseRun != null) {
            MouseTravelOverlay(
                run = mouseRun,
                boardSize = content.board.size,
                boardGeo = boardGeo,
                buttonBounds = mouseBtn,
                sfxOn = content.settings.sfx,
                onReveal = { mouseRevealed.intValue = it },
                onDone = onMouseAnimDone,
            )
        }

        // Hint dim: above the whole screen so the board layout never moves. Fades in; leaves instantly.
        val overlay = content.hintOverlay
        AnimatedVisibility(
            visible = overlay != null,
            enter = fadeIn(tween(160)),
            exit = ExitTransition.None,
        ) {
            if (overlay != null) {
                HintOverlay(
                    overlay = overlay,
                    boardGeo = boardGeo,
                    boardSize = content.board.size,
                    onDismiss = onHintOverlayDismiss,
                )
            }
        }

        OutcomeOverlay(
            outcome = content.outcome,
            level = content.levelNumber,
            maxHearts = content.maxHearts,
            onNextLevel = if (content.isDaily) onBack else onNextLevel,
            onRetry = onLoseRetry,
            onHome = onBack, // same pop-to-Home as the top-bar back
            onWinHome = onWinHome,
            daily = content.isDaily,
            onWatchRevive = if (ads.rewardedSurface) onWatchReviveAd else null,
            rewardedLoad = ads.rewardedLoad,
            rewardedBusy = ads.rewardedBusy,
            onRetryRewardedLoad = onRetryRewardedLoad,
        )

        AnimatedVisibility(
            visible = boosterDialog != null,
            enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.94f, animationSpec = tween(200)),
            exit = fadeOut(tween(120)),
        ) {
            // Keep the last booster while the exit fade runs.
            val shown = rememberLastBooster(boosterDialog)
            if (shown != null) {
                BoosterAdDialog(
                    booster = shown,
                    load = ads.rewardedLoad,
                    busy = ads.rewardedBusy,
                    onWatch = { onWatchBoosterAd(shown) },
                    onRetryLoad = onRetryRewardedLoad,
                    onStore = { onOpenStore(shown) },
                    onDismiss = onDismissBoosterDialog,
                )
            }
        }

        AnimatedVisibility(
            visible = settingsVisible,
            enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.94f, animationSpec = tween(200)),
            exit = fadeOut(tween(120)),
        ) {
            GameSettingsDialog(
                settings = content.settings,
                onSettingChanged = onSettingChanged,
                onRestart = onRestart,
                onDismiss = onDismissSettings,
            )
        }
    }
}

// ------------------------------------------------------------------ top area

@Composable
private fun CircleButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source)
    Box(
        modifier
            .size(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(GameColors.Card)
            .semantics { contentDescription = description }
            .clickable(interactionSource = source, indication = ripple(), role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun TopBar(content: GameContentUi, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(56.dp)) {
        CircleButton(
            description = stringResource(R.string.game_back),
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            IconImage(R.drawable.ic_back, size = 24.dp, tint = GameColors.Body)
        }
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            if (content.dailyEpochDay != null) {
                val dateText = remember(content.dailyEpochDay) {
                    LocalDate.ofEpochDay(content.dailyEpochDay)
                        .format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
                }
                LabeledValue(stringResource(R.string.game_daily), dateText)
            } else {
                LabeledValue(stringResource(R.string.game_level), content.levelNumber.toString())
            }
            LabeledValue(stringResource(R.string.game_score), content.score.toString())
        }
        Box(Modifier.align(Alignment.CenterEnd)) {
            CircleButton(
                description = stringResource(R.string.game_settings),
                onClick = onOpenSettings,
            ) {
                IconImage(R.drawable.ic_settings, size = 26.dp, tint = GameColors.Body)
            }
            if (content.settingsBadge) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(12.dp)
                        .background(GameColors.Coral, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = GameColors.Body, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = GameColors.Body, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LivesRow(content: GameContentUi, onUndo: () -> Unit) {
    val livesText = stringResource(R.string.game_lives, content.hearts, content.maxHearts)
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .align(Alignment.Center)
                .background(GameColors.Card, CircleShape)
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .semantics { contentDescription = livesText },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (i in 0 until content.maxHearts) {
                FishLife(active = i < content.hearts, size = 28.dp)
            }
        }
        // Undo UI position is not in the reference screenshots: placeholder spot, user decides.
        val undoSource = remember { MutableInteractionSource() }
        val undoScale = rememberPressScale(undoSource)
        val undoTapClick = com.threetech.catpuzzle.ui.components.rememberTapClick(onUndo)
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .graphicsLayer { scaleX = undoScale; scaleY = undoScale }
                .alpha(if (content.canUndo) 1f else 0.4f)
                .clip(CircleShape)
                .background(GameColors.Card)
                .defaultMinSize(minHeight = 48.dp)
                .clickable(
                    interactionSource = undoSource,
                    indication = ripple(),
                    enabled = content.canUndo,
                    role = Role.Button,
                    onClick = undoTapClick,
                )
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconImage(R.drawable.ic_undo, size = 18.dp)
            Text(
                text = stringResource(R.string.game_undo),
                color = GameColors.Body,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ------------------------------------------------------------------ rules strip

private enum class RuleKind { REGION, LINE, TOUCH }

@Composable
private fun RulesStrip() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(GameColors.Card, RoundedCornerShape(16.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RuleCard(RuleKind.REGION, stringResource(R.string.rule_one_per_color), Modifier.weight(1f))
        RuleCard(RuleKind.LINE, stringResource(R.string.rule_one_per_line), Modifier.weight(1f))
        RuleCard(RuleKind.TOUCH, stringResource(R.string.rule_no_touch), Modifier.weight(1f))
    }
}

@Composable
private fun RuleCard(kind: RuleKind, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(52.dp)
            .background(GameColors.RuleCard, RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        RuleDiagram(kind)
        Text(text, color = GameColors.Body, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.sp, maxLines = 3)
    }
}

/** Own 3x3 diagram: tinted = region, X = excluded, cat head = the cat. */
@Composable
private fun RuleDiagram(kind: RuleKind) {
    val marks = when (kind) {
        RuleKind.REGION -> listOf("XC.", "XX.", "...")
        RuleKind.LINE -> listOf(".X.", "XCX", ".X.")
        RuleKind.TOUCH -> listOf("XXX", "XCX", "XXX")
    }
    val catPainter = painterResource(IconArt.PlacedCat)
    Canvas(Modifier.size(36.dp)) {
        val cell = this.size.minDimension / 3f
        val gap = 1.5f
        for (r in 0 until 3) for (c in 0 until 3) {
            val tl = Offset(c * cell + gap, r * cell + gap)
            val inRegion = kind == RuleKind.REGION && r < 2 && c < 2
            drawRoundRect(
                color = if (inRegion) GameColors.Regions[3].copy(alpha = 0.7f) else Color(0xFFEBDCCD),
                topLeft = tl,
                size = Size(cell - 2 * gap, cell - 2 * gap),
                cornerRadius = CornerRadius(3f),
            )
            when (marks[r][c]) {
                'X' -> drawXMark(tl, cell - 2 * gap, GameColors.XMark)
                'C' -> {
                    val side = cell - 2 * gap
                    val pad = side * 0.06f
                    translate(left = tl.x + pad, top = tl.y + pad) {
                        with(catPainter) { draw(Size(side - 2 * pad, side - 2 * pad)) }
                    }
                }
                else -> Unit
            }
        }
    }
}

// ------------------------------------------------------------------ board tab

@Composable
private fun RegionTab(regions: List<RegionStatusUi>, cardWidthDp: Float? = null) {
    val solved = regions.count { it.solved }
    val text = stringResource(R.string.region_row_description, solved, regions.size)
    // Fixed width (9x9): exactly the board card width; icons shrink to fit, spread evenly.
    val fixed = cardWidthDp != null
    val iconDp = if (cardWidthDp != null) minOf(32f, (cardWidthDp - 16f) / regions.size.coerceAtLeast(1)) else 32f
    Row(
        Modifier
            .then(if (cardWidthDp != null) Modifier.width(cardWidthDp.dp) else Modifier)
            .height(44.dp)
            .background(GameColors.Card, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .then(if (fixed) Modifier else Modifier.padding(horizontal = 14.dp))
            .semantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (fixed) Arrangement.SpaceEvenly else Arrangement.spacedBy(4.dp),
    ) {
        for (region in regions) {
            Box(Modifier.size(iconDp.dp)) {
                if (region.solved) {
                    CatIcon(size = iconDp.dp, variant = region.catVariant)
                } else {
                    CatIcon(size = iconDp.dp, silhouetteTint = GameColors.region(region.region).copy(alpha = 0.45f))
                }
                if (region.solved) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(11.dp)
                            .background(GameColors.region(region.region), CircleShape),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ hint + boosters

@Composable
private fun BoostersRow(
    boosters: BoostersUi,
    rewardedSurface: Boolean,
    onCatBooster: () -> Unit,
    onHint: () -> Unit,
    onMouseBooster: () -> Unit,
    onEmptyBoosterTap: (Booster) -> Unit,
    nudgeActive: () -> Boolean = { false },
    /** Mouse sprite is travelling: the button's own icon is hidden (the overlay sprite is the same icon). */
    mouseAway: Boolean = false,
    onMouseBounds: (Rect) -> Unit = {},
) {
    // Empty booster + rewarded allowed (ad-free levels / consent / kill switch already folded in): badge shows a play
    // icon and the tap opens the "Need one?" dialog instead of the no-charge snackbar.
    val catWatch = rewardedSurface && boosters.catCharges == 0
    val hintWatch = rewardedSurface && boosters.hintCharges == 0
    val mouseWatch = rewardedSurface && boosters.mouseCharges == 0
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoosterButton(
            description = if (catWatch) {
                stringResource(R.string.booster_cat_watch)
            } else {
                stringResource(R.string.booster_cat, boosters.catCharges)
            },
            count = boosters.catCharges,
            onClick = if (catWatch) ({ onEmptyBoosterTap(Booster.CAT) }) else onCatBooster,
            showPlay = catWatch,
            nudge = { nudgeActive() && IdleNudgePolicy.boosterEligible(boosters.catCharges) },
        ) { CatIcon(size = BOOSTER_ICON_DP.dp) }
        BoosterButton(
            description = if (hintWatch) {
                stringResource(R.string.booster_hint_watch)
            } else {
                stringResource(R.string.booster_hint, boosters.hintCharges)
            },
            count = boosters.hintCharges,
            onClick = if (hintWatch) ({ onEmptyBoosterTap(Booster.HINT) }) else onHint,
            showPlay = hintWatch,
            nudge = { nudgeActive() && IdleNudgePolicy.boosterEligible(boosters.hintCharges) },
        ) { HintBulb(size = BOOSTER_ICON_DP.dp) }
        // Third slot: mouse booster. Locked (below Lv.11, and in the daily) keeps the locked look.
        if (boosters.thirdLocked) {
            LockedBooster(boosters)
        } else {
            BoosterButton(
                description = if (mouseWatch) {
                    stringResource(R.string.booster_mouse_watch)
                } else {
                    stringResource(R.string.booster_mouse, boosters.mouseCharges)
                },
                count = boosters.mouseCharges,
                onClick = if (mouseWatch) ({ onEmptyBoosterTap(Booster.MOUSE) }) else onMouseBooster,
                showPlay = mouseWatch,
                onCircleBounds = onMouseBounds,
                nudge = { nudgeActive() && !mouseAway && IdleNudgePolicy.boosterEligible(boosters.mouseCharges) },
            ) { MouseIcon(Modifier.alpha(if (mouseAway) 0f else 1f), size = BOOSTER_ICON_DP.dp) }
        }
    }
}

/** Booster sizes (dp). Was slot 76 / circle 68 / icon 44 / badge 24. Circle 56 stays above the 48dp touch minimum. */
internal const val BOOSTER_SLOT_DP = 64f
internal const val BOOSTER_BUTTON_DP = 56f
internal const val BOOSTER_ICON_DP = 36f
internal const val BOOSTER_SHADOW_ROOM_DP = 8f

/** Shared drop shadow for every booster circle (cat, hint, mouse, locked slot). Apply before clip/background. */
private fun Modifier.boosterCircleShadow(): Modifier = shadow(3.dp, CircleShape)

/** Gap kept between the booster buttons and the banner slot (AdMob accidental-click policy). */
private val BANNER_CLEARANCE = 24.dp

private class LastBooster {
    var value: Booster? = null
}

/** Keeps the last dialog booster so the exit fade still has something to draw. */
@Composable
private fun rememberLastBooster(current: Booster?): Booster? {
    val last = remember { LastBooster() }
    if (current != null) last.value = current
    return current ?: last.value
}

@Composable
internal fun BoosterButton(
    description: String,
    count: Int,
    onClick: () -> Unit,
    showPlay: Boolean = false,
    /** Idle nudge flag, read only inside [NudgeIcon]. Null = this button never nudges (tutorial). */
    nudge: (() -> Boolean)? = null,
    /** Circle bounds in root space, reported on layout (mouse travel animation anchor). */
    onCircleBounds: ((Rect) -> Unit)? = null,
    icon: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source)
    // Extra height below the circle = room for its drop shadow, so ancestors cannot clip it at the slot edge.
    Box(Modifier.size(width = BOOSTER_SLOT_DP.dp, height = (BOOSTER_SLOT_DP + BOOSTER_SHADOW_ROOM_DP).dp)) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(top = (BOOSTER_SLOT_DP - BOOSTER_BUTTON_DP).dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .size(BOOSTER_BUTTON_DP.dp)
                .then(
                    if (onCircleBounds != null) Modifier.onGloballyPositioned { onCircleBounds(it.boundsInRoot()) } else Modifier,
                )
                .boosterCircleShadow()
                .clip(CircleShape)
                .background(GameColors.Card)
                .semantics { contentDescription = description }
                .clickable(interactionSource = source, indication = ripple(), role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (nudge != null) NudgeIcon(nudge, icon) else icon()
        }
        CountBadge(count, Modifier.align(Alignment.TopEnd), showPlay = showPlay)
    }
}

/** Stock badge. [showPlay] (empty + rewarded allowed): green badge with a play icon instead of the number. */
@Composable
internal fun CountBadge(count: Int, modifier: Modifier = Modifier, showPlay: Boolean = false) {
    Box(
        modifier
            .size(20.dp)
            .background(
                when {
                    showPlay -> GameColors.CtaGreenFace
                    count > 0 -> GameColors.Coral
                    else -> GameColors.FishOff
                },
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (showPlay) {
            IconImage(R.drawable.ic_play, size = 12.dp, tint = Color.White)
        } else {
            // Centered digit: no font padding, line box = font size, trimmed both sides so ascent/descent
            // metrics (Fredoka sits high/low in its line box) cannot push the glyph off-centre.
            Text(
                count.toString(),
                color = Color.White,
                fontSize = 11.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                style = BadgeDigitStyle,
            )
        }
    }
}

private val BadgeDigitStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

@Composable
private fun LockedBooster(boosters: BoostersUi) {
    val description = stringResource(R.string.booster_locked, boosters.thirdUnlockLevel)
    Box(Modifier.size(width = BOOSTER_SLOT_DP.dp, height = (BOOSTER_SLOT_DP + BOOSTER_SHADOW_ROOM_DP).dp)) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(top = (BOOSTER_SLOT_DP - BOOSTER_BUTTON_DP).dp)
                .size(BOOSTER_BUTTON_DP.dp)
                .boosterCircleShadow()
                .clip(CircleShape)
                .background(GameColors.Card)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconImage(IconArt.MouseLocked, size = 28.dp)
                if (boosters.thirdLocked) {
                    Text(
                        stringResource(R.string.booster_locked_label, boosters.thirdUnlockLevel),
                        color = GameColors.Body,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        if (boosters.thirdLocked) {
            // Dark-grey badge (same 20dp footprint as CountBadge) with the white lock inside.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .background(GameColors.LockedBadge, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                IconImage(R.drawable.ic_lock, size = 12.dp, tint = Color.White)
            }
        }
    }
}
