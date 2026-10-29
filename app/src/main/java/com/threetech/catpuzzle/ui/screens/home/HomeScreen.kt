package com.threetech.catpuzzle.ui.screens.home

import com.threetech.catpuzzle.ui.theme.FredokaFamily
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.domain.ads.NativeAdSlotState
import com.threetech.catpuzzle.ui.ads.NativeAdSlot
import com.threetech.catpuzzle.ui.components.AppWordmark
import com.threetech.catpuzzle.ui.components.IconArt
import com.threetech.catpuzzle.ui.components.IconImage
import com.threetech.catpuzzle.ui.components.Button3D
import com.threetech.catpuzzle.ui.components.ButtonStyle
import com.threetech.catpuzzle.ui.components.PillButton
import com.threetech.catpuzzle.ui.components.rememberPressScale
import com.threetech.catpuzzle.ui.components.rememberTapClick
import com.threetech.catpuzzle.ui.theme.GameColors

private val ButtonMaxWidth = 360.dp
private val PatternCell = 64.dp

/** Stateless home screen: state in, lambdas out. No ViewModel here. */
@Composable
fun HomeScreen(
    state: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onPlay: () -> Unit,
    onDailyChallenge: () -> Unit,
    onOpenStreak: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenShop: () -> Unit,
    onOpenAvatar: () -> Unit, // TEMP node gallery
    modifier: Modifier = Modifier,
    nativeAdSlot: NativeAdSlotState = NativeAdSlotState.Hidden,
) {
    // With the ad slot pinned at the bottom (skeleton while Loading, ad when Loaded), it owns the navigation-bar inset (its
    // background runs behind the bar), so the content stops at the slot and only keeps the status bar + side insets.
    // Hidden / Failed: no slot, content keeps all system bars.
    val contentInsets = if (nativeAdSlot.reservesSlot) {
        WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    } else {
        WindowInsets.systemBars
    }
    Box(
        modifier
            .fillMaxSize()
            .background(GameColors.ScreenBackground)
            .homePattern(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
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
                Box(Modifier.fillMaxWidth()) {
                    ShopButton(onOpenShop, Modifier.align(Alignment.CenterStart).padding(start = 2.dp))
                }
                Spacer(Modifier.weight(0.22f))
                AppWordmark()
                Spacer(Modifier.weight(0.78f))

                PillButton(
                    text = state.levelNumber?.let { stringResource(R.string.home_level_button, it) }
                        ?: stringResource(R.string.home_play),
                    onClick = onPlay,
                    textSize = 24.sp,
                    modifier = Modifier.widthIn(max = ButtonMaxWidth).fillMaxWidth(),
                )
                // Daily done today: hide the pill (and its top gap) entirely; it returns on the next date.
                AnimatedVisibility(
                    visible = state.daily != DailyUi.Done,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(16.dp))
                        DailyChallengePill(
                            daily = state.daily,
                            onClick = onDailyChallenge,
                            modifier = Modifier.widthIn(max = ButtonMaxWidth).fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
            NativeAdSlot(nativeAdSlot)
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
        is DailyUi.Locked -> stringResource(R.string.home_daily_locked_description)
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
        if (daily is DailyUi.Locked) IconImage(R.drawable.ic_lock, size = 24.dp, tint = GameColors.LockedPillContent)
        Text(
            label,
            color = if (daily == DailyUi.Available) Color.White else GameColors.LockedPillContent,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FredokaFamily,
            textAlign = TextAlign.Center,
            maxLines = 2,
            style = androidx.compose.ui.text.TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(Color(0x66000000), Offset(0f, 2f), 3f),
            ),
        )
    }
}

/** Faint board pattern: rounded squares with scattered X marks, drawn once per size (drawWithCache). No images. */
private fun Modifier.homePattern(color: Color = GameColors.Wordmark): Modifier = drawWithCache {
    val cell = PatternCell.toPx()
    val gap = 4.dp.toPx()
    val side = cell - gap
    val corner = CornerRadius(12.dp.toPx())
    val line = Stroke(width = 1.5.dp.toPx())
    val xWidth = 3.dp.toPx()
    val faint = color.copy(alpha = HOME_PATTERN_ALPHA)
    val cols = (size.width / cell).toInt() + 2
    val rows = (size.height / cell).toInt() + 2
    onDrawBehind {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x = c * cell - cell / 2f
                val y = r * cell - cell / 2f
                drawRoundRect(faint, Offset(x, y), Size(side, side), corner, line)
                if (patternHasX(r, c)) {
                    val inset = side * 0.28f
                    drawLine(faint, Offset(x + inset, y + inset), Offset(x + side - inset, y + side - inset), xWidth, StrokeCap.Round)
                    drawLine(faint, Offset(x + side - inset, y + inset), Offset(x + inset, y + side - inset), xWidth, StrokeCap.Round)
                }
            }
        }
    }
}
