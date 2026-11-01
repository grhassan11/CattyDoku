package com.threetech.catpuzzle.ui.screens.home

import com.threetech.catpuzzle.ui.theme.FredokaFamily
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.domain.ads.NativeAdSlotState
import com.threetech.catpuzzle.ui.ads.NativeAdSlot
import com.threetech.catpuzzle.ui.components.homeBackground
import com.threetech.catpuzzle.ui.screens.home.worlds.WorldMap
import com.threetech.catpuzzle.ui.screens.home.worlds.WorldThemes
import com.threetech.catpuzzle.ui.components.IconArt
import com.threetech.catpuzzle.ui.components.IconImage
import com.threetech.catpuzzle.ui.components.Button3D
import com.threetech.catpuzzle.ui.components.ButtonStyle
import com.threetech.catpuzzle.ui.components.PillButton
import com.threetech.catpuzzle.ui.components.rememberPressScale
import com.threetech.catpuzzle.ui.components.rememberTapClick
import com.threetech.catpuzzle.ui.theme.GameColors

private val ButtonMaxWidth = 360.dp

/**
 * Stateless home screen: state in, lambdas out. No ViewModel here. [onNodeClick] = a done / current node was tapped (opens the level sheet), [onSheetDismiss] = sheet closed,
 * [onPlayLevel] = the sheet's Play / Replay button.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onNodeClick: (Int) -> Unit,
    onSheetDismiss: () -> Unit,
    onPlayLevel: (Int) -> Unit,
    onDailyChallenge: () -> Unit,
    onOpenStreak: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenShop: () -> Unit,
    onOpenAvatar: () -> Unit, // TEMP node gallery
    onSudokuTab: () -> Unit,
    modifier: Modifier = Modifier,
    nativeAdSlot: NativeAdSlotState = NativeAdSlotState.Hidden,
) {
    // With the ad slot pinned at the bottom (skeleton while Loading, ad when Loaded), it owns the navigation-bar inset (its
    // background runs behind the bar), so the content stops at the slot and only keeps the status bar + side insets.
    // Hidden / Failed: no slot, content keeps all system bars.
    val contentInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    // The tabs row is always present above the ad slot: with a slot it keeps side insets only (slot owns the nav bar);
    // without a slot it also clears the navigation bar, so the map region now always drops its bottom inset.
    val tabsInsets = if (nativeAdSlot.reservesSlot) {
        WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
    } else {
        WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    }
    // Trail band = the free space between the Shop row and the Daily pill, measured from the overlay itself so it
    // follows font scale, a hidden pill (Daily done) and small screens. Map-relative px; 0 height = not measured yet.
    var mapTopPx by remember { mutableFloatStateOf(0f) }
    var bandTopPx by remember { mutableFloatStateOf(0f) }
    var bandBottomPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val bandTop = with(density) { (bandTopPx - mapTopPx).coerceAtLeast(0f).toDp() }
    val bandHeight = with(density) { (bandBottomPx - bandTopPx).coerceAtLeast(0f).toDp() }
    // Theme index of the world under the viewport centre (-1 = map not reported yet). Read only by WorldNameLabel, so a
    // world change recomposes just the label, not this screen.
    var worldIndex by remember { mutableIntStateOf(-1) }
    // Anchors for the "Jump to current level" overlay (root px): bottom of the map region, top of the Daily pill, and top
    // of the bottom spacer (= where the Daily pill's bottom edge would be when the pill is hidden).
    var mapBottomPx by remember { mutableFloatStateOf(0f) }
    var dailyTopPx by remember { mutableFloatStateOf(0f) }
    var spacerTopPx by remember { mutableFloatStateOf(0f) }
    val jumpGapPx = with(density) { 12.dp.toPx() }

    Box(
        modifier
            .fillMaxSize()
            .homeBackground(),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Map region = everything above the native ad slot. The world map is full-bleed (behind the status bar and
            // the side/bottom insets); the controls are overlays on top, padded by contentInsets exactly as before.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        mapTopPx = it.positionInRoot().y
                        mapBottomPx = it.boundsInRoot().bottom
                    },
            ) {
                WorldMap(
                    map = state.worldMap,
                    bandTop = bandTop,
                    bandHeight = bandHeight,
                    onNodeClick = onNodeClick,
                    onWorldChange = { worldIndex = it },
                    jumpPill = { direction, onJump ->
                        val dailyShown = state.daily != DailyUi.Done && dailyTopPx > 0f
                        val anchorPx = if (dailyShown) dailyTopPx - jumpGapPx else spacerTopPx
                        JumpToCurrentPill(
                            direction = direction,
                            worldIndex = { worldIndex },
                            onClick = onJump,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = with(density) { (mapBottomPx - anchorPx).coerceAtLeast(0f).toDp() }),
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                // Controls + sticky tabs stacked over the full-height world backdrop, so the tabs row sits on the environment.
                Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(contentInsets)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(8.dp))
                    TopBar(
                        streak = state.streak,
                        rewardPending = state.streakRewardPending,
                        onOpenStreak = onOpenStreak,
                        onOpenSettings = onOpenSettings,
                        onOpenAvatar = onOpenAvatar, // TEMP node gallery
                    )
                    // Shop button sits directly under the avatar (left edge). Its own row: clear of the streak chip,
                    // the daily pill and the bottom native-ad slot.
                    Spacer(Modifier.height(8.dp))
                    // The world-name pill shares this row but sits at the true horizontal centre of the screen. Its width is
                    // capped symmetrically (shop button 2 + 48 dp + 12 dp gap on each side) so it can never touch the shop button.
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val pillMaxWidth = (maxWidth - 124.dp).coerceAtLeast(0.dp)
                        ShopButton(onOpenShop, Modifier.align(Alignment.CenterStart).padding(start = 2.dp))
                        WorldNameLabel(
                            worldIndex = { worldIndex },
                            modifier = Modifier.align(Alignment.Center).widthIn(max = pillMaxWidth),
                        )
                    }
                    // Trail band: transparent, just measured. The map draws its trail + nodes inside it.
                    Spacer(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onGloballyPositioned {
                                val r = it.boundsInRoot()
                                bandTopPx = r.top
                                bandBottomPx = r.bottom
                            },
                    )
                    Spacer(Modifier.height(12.dp))
                    // Daily done today: hide the pill (and its top gap) entirely; it returns on the next date.
                    AnimatedVisibility(
                        visible = state.daily != DailyUi.Done,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(16.dp))
                            DailyChallengePill(
                                daily = state.daily,
                                onClick = onDailyChallenge,
                                modifier = Modifier
                                    .widthIn(max = ButtonMaxWidth)
                                    .onGloballyPositioned { dailyTopPx = it.boundsInRoot().top },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp).onGloballyPositioned { spacerTopPx = it.boundsInRoot().top })
                }
                }
                // Sticky mode tabs: never scroll; drawn over the world backdrop, directly above the native ad slot (own surface).
                HomeModeTabs(
                    onSudoku = onSudokuTab,
                    modifier = Modifier.windowInsetsPadding(tabsInsets),
                )
                }
            }
            NativeAdSlot(nativeAdSlot)
        }

        state.levelSheet?.let { sheet ->
            LevelSheet(sheet = sheet, onDismiss = onSheetDismiss, onPlay = onPlayLevel)
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(top = 72.dp, start = 16.dp, end = 16.dp),
        )
    }
}

/**
 * Pinned Levels / Sudoku pill tabs. Levels is always the selected one (this is the Classic home); Sudoku only reports
 * a tap via [onSudoku] (screen not built yet), so selection never moves.
 */
@Composable
private fun HomeModeTabs(onSudoku: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        ModeTab(stringResource(R.string.home_tab_classic), R.drawable.ic_game_classic, selected = true, tealTab = true, onClick = {}, modifier = Modifier.weight(1f))
        ModeTab(stringResource(R.string.home_tab_sudoku), R.drawable.ic_game_sudoku, selected = false, tealTab = false, onClick = onSudoku, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ModeTab(label: String, @DrawableRes icon: Int, selected: Boolean, tealTab: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Full colour always: Levels = Teal, Sudoku = Green. Selection is semantics-only. Button3D owns tap cue.
    val style = if (tealTab) ButtonStyle.Teal else ButtonStyle.Green
    Button3D(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 120.dp),
        style = style,
        horizontalPadding = 12.dp, // tight so icon + label fit a 140dp tab on 320dp phones
        totalHeight = 62.dp, // 56dp face + 6dp lip
        role = Role.Tab,
        selected = selected,
    ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(28.dp))
        Text(
            label,
            color = style.content,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FredokaFamily,
            maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(Color(0x66000000), Offset(0f, 2f), 3f),
            ),
        )
    }
}

/**
 * "Jump to current level" pill, shown while the current node is scrolled off screen ([direction] != 0: -1 = it lies to the
 * left, +1 = right; the arrow points that way). Coloured from the displayed world's theme like the world-name pill.
 * [direction] is read here only, so a flip recomposes just this pill.
 */
@Composable
private fun JumpToCurrentPill(
    direction: () -> Int,
    worldIndex: () -> Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dir = direction()
    val last = remember { intArrayOf(1) }
    if (dir != 0) last[0] = dir // keep the arrow stable during the exit animation
    val pointsLeft = last[0] < 0
    AnimatedVisibility(
        visible = dir != 0,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        val theme = WorldThemes.forThemeIndex(worldIndex().coerceAtLeast(0))
        val label = stringResource(R.string.home_jump_to_current)
        val arrow: @Composable (Boolean) -> Unit = { flip ->
            IconImage(
                R.drawable.ic_back,
                size = 18.dp,
                tint = theme.pillText,
                modifier = Modifier.graphicsLayer { scaleX = if (flip) -1f else 1f },
            )
        }
        Row(
            Modifier
                .shadow(2.dp, CircleShape)
                .background(theme.pillFill, CircleShape)
                .border(1.5.dp, theme.pillBorder, CircleShape)
                .clip(CircleShape)
                .defaultMinSize(minHeight = 48.dp)
                .semantics(mergeDescendants = true) { contentDescription = label }
                .clickable(role = Role.Button, onClick = rememberTapClick(onClick))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pointsLeft) arrow(false)
            Text(
                label,
                color = theme.pillText,
                style = MaterialTheme.typography.titleSmall,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FredokaFamily,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (!pointsLeft) arrow(true)
        }
    }
}

@Composable
private fun TopBar(
    streak: Int,
    rewardPending: Boolean,
    onOpenStreak: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAvatar: () -> Unit, // TEMP node gallery
) {
    Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)) {
        // PLACEHOLDER mascot badge: mascot concept is an open product decision. Decorative.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFFFE2A8))
                .border(2.dp, GameColors.Primary, RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = rememberTapClick(onOpenAvatar)) // TEMP node gallery
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            IconImage(IconArt.mascotFor(1), size = 40.dp)
        }
        StreakChip(streak, rewardPending, onOpenStreak, Modifier.align(Alignment.Center))
        SettingsButton(onOpenSettings, Modifier.align(Alignment.CenterEnd))
    }
}

/**
 * Name of the world under the viewport centre, on a soft pill tinted from that world's [WorldTheme] (pastel fill, deep
 * same-hue text, soft ring, light shadow) so it stays readable on every backdrop. Crossfades when the world changes.
 */
@Composable
private fun WorldNameLabel(worldIndex: () -> Int, modifier: Modifier = Modifier) {
    Crossfade(targetState = worldIndex(), modifier = modifier, label = "worldName") { index ->
        if (index >= 0) {
            val theme = WorldThemes.forThemeIndex(index)
            val description = stringResource(R.string.home_world_name_description, theme.name)
            Box(
                Modifier
                    .shadow(2.dp, CircleShape)
                    .background(theme.pillFill, CircleShape)
                    .border(1.5.dp, theme.pillBorder, CircleShape)
                    .defaultMinSize(minHeight = 48.dp) // = shop button height
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    theme.name,
                    color = theme.pillText,
                    style = MaterialTheme.typography.titleSmall,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FredokaFamily,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StreakChip(streak: Int, rewardPending: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = if (rewardPending) {
        stringResource(R.string.home_streak_open_reward_description, streak)
    } else {
        stringResource(R.string.home_streak_open_description, streak)
    }
    Box(modifier) {
        Row(
            Modifier
                .defaultMinSize(minHeight = 48.dp)
                .shadow(2.dp, CircleShape)
                .background(GameColors.Card, CircleShape)
                .clip(CircleShape)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .clickable(role = Role.Button, onClick = rememberTapClick(onClick))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconImage(R.drawable.ic_yarn_ball, size = 22.dp)
            Text(
                streak.toString(),
                color = GameColors.Wordmark,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        // Reward-ready dot. Decorative: the chip description already says a reward is waiting.
        if (rewardPending) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 2.dp)
                    .size(14.dp)
                    .background(GameColors.Coral, CircleShape)
                    .border(2.dp, GameColors.Card, CircleShape)
                    .clearAndSetSemantics { },
            )
        }
    }
}

@Composable
private fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.home_settings)
    val tapClick = rememberTapClick(onClick)
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(48.dp)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(GameColors.Card)
            .semantics { contentDescription = description }
            .clickable(interactionSource = source, indication = ripple(), role = Role.Button, onClick = tapClick),
        contentAlignment = Alignment.Center,
    ) {
        IconImage(R.drawable.ic_settings, size = 26.dp, tint = GameColors.Wordmark)
    }
}

@Composable
private fun ShopButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.home_shop)
    val tapClick = rememberTapClick(onClick)
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(48.dp)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(GameColors.Card)
            .semantics { contentDescription = description }
            .clickable(interactionSource = source, indication = ripple(), role = Role.Button, onClick = tapClick),
        contentAlignment = Alignment.Center,
    ) {
        IconImage(R.drawable.ic_cart_store, size = 26.dp)
    }
}

/**
 * Daily Challenge pill. Locked: grey + padlock. Available: orange (primary). Done: grey, no padlock,
 * "Done today". Tap stays live in every state (the route shows a snackbar for locked / done); TalkBack
 * reads locked and done as disabled.
 */
@Composable
private fun DailyChallengePill(daily: DailyUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = when (daily) {
        DailyUi.Done -> stringResource(R.string.home_daily_done)
        else -> stringResource(R.string.home_daily_challenge)
    }
    val description = when (daily) {
        is DailyUi.Locked -> stringResource(R.string.home_daily_locked_description, daily.requiredLevel)
        DailyUi.Done -> stringResource(R.string.home_daily_done_description)
        DailyUi.Available -> null
    }
    Button3D(
        onClick = onClick,
        modifier = modifier,
        style = if (daily == DailyUi.Available) ButtonStyle.Pink else ButtonStyle.Locked,
        contentDescription = description,
        markDisabled = daily != DailyUi.Available,
        horizontalPadding = 20.dp,
    ) {
        // Calendar replaces the padlock; multi-colour so untinted. Dimmed while locked (grey pill + hint text carry the lock cue).
        IconImage(
            R.drawable.ic_calendar,
            modifier = Modifier.alpha(if (daily is DailyUi.Locked) 0.55f else 1f),
            size = 28.dp,
        )
        val labelColor = if (daily == DailyUi.Available) Color.White else GameColors.LockedPillContent
        val labelShadow = androidx.compose.ui.text.TextStyle(
            shadow = androidx.compose.ui.graphics.Shadow(Color(0x66000000), Offset(0f, 2f), 3f),
        )
        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                label,
                color = labelColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FredokaFamily,
                textAlign = TextAlign.Center,
                maxLines = 2,
                style = labelShadow,
            )
            if (daily is DailyUi.Locked) {
                Text(
                    stringResource(R.string.home_daily_locked_hint, daily.requiredLevel),
                    color = labelColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FredokaFamily,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

