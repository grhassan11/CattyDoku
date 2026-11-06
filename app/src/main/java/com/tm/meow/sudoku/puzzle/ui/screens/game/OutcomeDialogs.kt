package com.tm.meow.sudoku.puzzle.ui.screens.game

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.domain.ads.RewardedLoadState
import com.tm.meow.sudoku.puzzle.domain.model.AnimalSet
import com.tm.meow.sudoku.puzzle.domain.model.PieceSkin
import com.tm.meow.sudoku.puzzle.ui.components.Button3D
import com.tm.meow.sudoku.puzzle.ui.components.Button3DHeight
import com.tm.meow.sudoku.puzzle.ui.components.ButtonStyle
import com.tm.meow.sudoku.puzzle.ui.components.Confetti
import com.tm.meow.sudoku.puzzle.ui.components.DialogScrim
import com.tm.meow.sudoku.puzzle.ui.components.DownsampledIconImage
import com.tm.meow.sudoku.puzzle.ui.components.IconArt
import com.tm.meow.sudoku.puzzle.ui.components.HomePlaceholder
import com.tm.meow.sudoku.puzzle.ui.components.IconImage
import com.tm.meow.sudoku.puzzle.ui.components.PillButton
import com.tm.meow.sudoku.puzzle.ui.components.StatusBarIconsEffect
import com.tm.meow.sudoku.puzzle.ui.components.Sunburst
import com.tm.meow.sudoku.puzzle.ui.theme.FredokaFamily
import com.tm.meow.sudoku.puzzle.ui.theme.GameColors

/** Keeps the last outcome around so the exit fade still has something to draw. */
private class LastOutcome {
    var value: OutcomeUi? = null
}

/**
 * Win / lose dialog layer. State comes from the ViewModel ([outcome]), so it survives rotation;
 * only the fade / scale transition is local.
 */
@Composable
internal fun OutcomeOverlay(
    outcome: OutcomeUi?,
    level: Int,
    maxHearts: Int,
    onNextLevel: () -> Unit,
    onRetry: () -> Unit,
    /** Lose-screen HOME: leave to Home (the lost level restarts fresh on the next launch). */
    onHome: () -> Unit = {},
    /** Win-screen HOME icon (left of the next-level CTA); hidden for Daily. */
    onWinHome: () -> Unit = {},
    /** Daily Challenge: "Daily complete!" headline and a "Back to Home" button ([onNextLevel] must go home). */
    daily: Boolean = false,
    /** Boss level: win dialog shows one of this skin's animals (and animal wording) instead of the cat. */
    pieceSkin: PieceSkin = PieceSkin.cats(),
    /** Lose-screen revive CTA (rewarded ad). null = rewarded not allowed (ad-free level, no consent, off): single Retry CTA. */
    onWatchRevive: (() -> Unit)? = null,
    rewardedLoad: RewardedLoadState = RewardedLoadState.IDLE,
    rewardedBusy: Boolean = false,
    onRetryRewardedLoad: () -> Unit = {},
) {
    val last = remember { LastOutcome() }
    if (outcome != null) last.value = outcome
    AnimatedVisibility(
        visible = outcome != null,
        enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.92f, animationSpec = tween(260)),
        exit = fadeOut(tween(160)),
    ) {
        when (val shown = outcome ?: last.value) {
            is OutcomeUi.Won -> WinDialog(
                level = level,
                outcome = shown,
                heartsLost = (maxHearts - shown.heartsLeft).coerceAtLeast(0),
                daily = daily,
                pieceSkin = if (daily) PieceSkin.cats() else pieceSkin,
                onNextLevel = onNextLevel,
                onWinHome = onWinHome,
            )
            is OutcomeUi.Lost -> LoseDialog(
                onRetry = onRetry,
                onHome = onHome,
                onWatchRevive = onWatchRevive,
                rewardedLoad = rewardedLoad,
                rewardedBusy = rewardedBusy,
                onRetryRewardedLoad = onRetryRewardedLoad,
            )
            null -> Unit
        }
    }
}

// ------------------------------------------------------------------ win

@StringRes
private fun WinHeadline.res(): Int = when (this) {
    WinHeadline.FIRST_WIN -> R.string.win_headline_first
    WinHeadline.MILESTONE_5 -> R.string.win_headline_5
    WinHeadline.MILESTONE_10 -> R.string.win_headline_10
    WinHeadline.MILESTONE_25 -> R.string.win_headline_25
    WinHeadline.STANDARD -> R.string.win_headline_standard
    WinHeadline.DAILY -> R.string.win_headline_daily
}

@StringRes
private fun WinMessage.res(): Int = when (this) {
    WinMessage.FIRST_WIN -> R.string.win_message_first
    WinMessage.PERFECT -> R.string.win_message_perfect
    WinMessage.GOOD -> R.string.win_message_good
    WinMessage.TOUGH -> R.string.win_message_tough
}

@Composable
private fun WinDialog(
    level: Int,
    outcome: OutcomeUi.Won,
    heartsLost: Int,
    daily: Boolean,
    pieceSkin: PieceSkin,
    onNextLevel: () -> Unit,
    onWinHome: () -> Unit,
) {
    val animalSet = pieceSkin.set
    val headline = stringResource(OutcomeLogic.headline(level, daily).res())
    val winMessage = OutcomeLogic.message(level, heartsLost, outcome.hintsUsed, daily)
    val message = stringResource(
        if (winMessage == WinMessage.GOOD && animalSet != null) {
            if (animalSet == AnimalSet.DOGS) R.string.win_message_good_dogs else R.string.win_message_good_animals
        } else {
            winMessage.res()
        },
    )
    val mascotDescription = stringResource(
        when (animalSet) {
            null -> R.string.win_mascot_description
            AnimalSet.DOGS -> R.string.win_mascot_description_dog
            else -> R.string.win_mascot_description_animal
        },
    )
    val mascotRes = if (animalSet != null) {
        IconArt.animalFor(animalSet, pieceSkin.boosterIndex)
    } else {
        IconArt.mascotFor(level)
    }

    WinScreenLayout(
        headline = headline,
        message = message,
        mascotRes = mascotRes,
        mascotDescription = mascotDescription,
        score = outcome.score,
        stats = {
            StatChip(
                label = stringResource(R.string.win_stat_time),
                value = OutcomeLogic.formatElapsed(outcome.elapsedSeconds),
                iconRes = null,
                description = stringResource(
                    R.string.win_stat_time_description,
                    OutcomeLogic.formatElapsed(outcome.elapsedSeconds),
                ),
            )
            StatChip(
                label = stringResource(R.string.win_stat_mistakes),
                value = heartsLost.toString(),
                iconRes = IconArt.FishBone,
                downsampleIcon = true,
                description = stringResource(R.string.win_stat_mistakes_description, heartsLost),
            )
            StatChip(
                label = stringResource(R.string.win_stat_hints),
                value = outcome.hintsUsed.toString(),
                iconRes = R.drawable.ic_hint,
                description = stringResource(R.string.win_stat_hints_description, outcome.hintsUsed),
            )
        },
        ctaText = if (daily) {
            stringResource(R.string.win_back_home)
        } else {
            stringResource(R.string.win_next_level, level + 1)
        },
        onCta = onNextLevel,
        showHome = !daily,
        onHome = onWinHome,
    )
}

/**
 * Shared win screen (Levels "Level Completed" + Sudoku "Puzzle solved"): confetti, ribbon headline, mascot stage,
 * message, optional [extra] notes, score count-up, stat chip row, then [HOME icon] + CTA. Text and stats come in as params.
 * [inputArmed] false = buttons inert (Sudoku guards against a stray tap as the screen appears).
 */
@Composable
internal fun WinScreenLayout(
    headline: String,
    message: String,
    mascotRes: Int,
    mascotDescription: String,
    score: Int,
    stats: @Composable RowScope.() -> Unit,
    ctaText: String,
    onCta: () -> Unit,
    showHome: Boolean,
    onHome: () -> Unit,
    homeDescription: String = stringResource(R.string.win_home),
    confetti: Boolean = true,
    inputArmed: Boolean = true,
    extra: @Composable () -> Unit = {},
) {
    DialogScrim(Modifier.semantics { paneTitle = headline }, scrimColor = GameColors.OutcomeScrim) {
        StatusBarIconsEffect()
        if (confetti) Confetti()
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            HeadlineRibbon(headline)
            Spacer(Modifier.height(12.dp))

            MascotStage(
                mascotRes = mascotRes,
                description = mascotDescription,
            )

            Text(
                text = message,
                color = GameColors.WinMessage,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
            )
            extra()
            Spacer(Modifier.height(16.dp))

            ScoreCountUp(score = score)
            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth().widthIn(max = 360.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                content = stats,
            )
            Spacer(Modifier.height(24.dp))

            Row(
                Modifier.fillMaxWidth().widthIn(max = 360.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showHome) {
                    Button3D(
                        onClick = { if (inputArmed) onHome() },
                        style = ButtonStyle.Secondary,
                        contentDescription = homeDescription,
                        horizontalPadding = 0.dp,
                        modifier = Modifier.width(Button3DHeight),
                    ) {
                        IconImage(R.drawable.ic_home, size = 28.dp, tint = ButtonStyle.Secondary.content)
                    }
                }
                PillButton(
                    text = ctaText,
                    onClick = { if (inputArmed) onCta() },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Headline inside a folded ribbon banner: main band, swallowtail tails in a darker shade, fold
 * triangles where tails tuck behind the band. Art is drawn once per size (drawWithCache) and is
 * decorative; the text carries the heading semantics. Text shrinks stepwise until it fits one line.
 */
@Composable
private fun HeadlineRibbon(text: String) {
    var fontSize by remember(text) { mutableStateOf(30.sp) }
    val tail = 28.dp
    val drop = 10.dp
    Box(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 340.dp)
            .rotate(-2f)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val t = tail.toPx()
                val d = drop.toPx()
                val bandH = h - d
                val notch = t * 0.45f
                val tailMidY = d + (h - d) / 2f
                fun tailPath(left: Boolean) = Path().apply {
                    if (left) {
                        moveTo(0f, d); lineTo(t + 2f, d); lineTo(t + 2f, h); lineTo(0f, h)
                        lineTo(notch, tailMidY)
                    } else {
                        moveTo(w, d); lineTo(w - t - 2f, d); lineTo(w - t - 2f, h); lineTo(w, h)
                        lineTo(w - notch, tailMidY)
                    }
                    close()
                }
                val leftTail = tailPath(true)
                val rightTail = tailPath(false)
                val leftFold = Path().apply {
                    moveTo(t, bandH); lineTo(t + d, bandH); lineTo(t, h); close()
                }
                val rightFold = Path().apply {
                    moveTo(w - t, bandH); lineTo(w - t - d, bandH); lineTo(w - t, h); close()
                }
                val bandBrush = Brush.verticalGradient(
                    listOf(GameColors.RibbonFillTop, GameColors.RibbonFill),
                    startY = 0f,
                    endY = bandH,
                )
                val corner = CornerRadius(6.dp.toPx())
                onDrawBehind {
                    drawPath(leftTail, GameColors.RibbonTail)
                    drawPath(rightTail, GameColors.RibbonTail)
                    drawPath(leftFold, GameColors.RibbonFold)
                    drawPath(rightFold, GameColors.RibbonFold)
                    drawRoundRect(
                        brush = bandBrush,
                        topLeft = Offset(t, 0f),
                        size = Size(w - 2 * t, bandH),
                        cornerRadius = corner,
                    )
                    drawRoundRect(
                        color = GameColors.RibbonEdge,
                        topLeft = Offset(t, 0f),
                        size = Size(w - 2 * t, bandH),
                        cornerRadius = corner,
                        style = Stroke(2.dp.toPx()),
                    )
                }
            }
            .padding(start = tail, end = tail, bottom = drop)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            color = GameColors.RibbonText,
            fontSize = fontSize,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(fontFamily = FredokaFamily, shadow = Shadow(GameColors.RibbonTextShadow, Offset(0f, 3f), 4f)),
            onTextLayout = { r ->
                if (r.hasVisualOverflow && fontSize.value > 14f) fontSize = (fontSize.value * 0.9f).sp
            },
            modifier = Modifier.semantics {
                heading()
                contentDescription = text
            },
        )
    }
}

/** Sunburst + mascot placeholder (user cat art). Springs in, then bobs gently. */
@Composable
private fun MascotStage(mascotRes: Int, description: String) {
    val pop = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    val bob = rememberInfiniteTransition(label = "mascotBob")
    val bobY by bob.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(1_400), RepeatMode.Reverse),
        label = "mascotBobY",
    )
    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Sunburst(Modifier.requiredSize(340.dp))
        androidx.compose.foundation.Image(
            painter = painterResource(mascotRes),
            contentDescription = description,
            modifier = Modifier
                .size(220.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                    translationY = bobY * density
                },
        )
    }
}

/** "Score" label + number that counts up to [score]. Screen readers get the final value only. */
@Composable
private fun ScoreCountUp(score: Int) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(score) {
        progress.animateTo(
            1f,
            tween(OutcomeLogic.countUpDurationMs(score), easing = FastOutSlowInEasing),
        )
    }
    val description = stringResource(R.string.win_score_description, score)
    Column(
        Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.win_score_label),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            OutcomeLogic.countUpValue(score, progress.value).toString(),
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
internal fun RowScope.StatChip(
    label: String,
    value: String,
    iconRes: Int?,
    description: String,
    iconTint: Color? = null,
    downsampleIcon: Boolean = false,
) {
    Column(
        Modifier
            .weight(1f)
            .heightIn(min = 66.dp)
            .background(GameColors.StatCardBg, RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = GameColors.StatCardLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(
            Modifier.heightIn(min = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (iconRes != null) {
                // Fixed 26dp slot so icon size never changes card height.
                Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                    if (downsampleIcon) {
                        DownsampledIconImage(iconRes, size = 26.dp)
                    } else {
                        IconImage(iconRes, size = 26.dp, tint = iconTint)
                    }
                }
            }
            Text(value, color = GameColors.StatCardValue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ------------------------------------------------------------------ lose

/**
 * Out of lives. Layout: sad-cat hero, title, message, then
 *  a) full-width "Watch Ad to Continue" (rewarded revive: +1 life, same level; only when [onWatchRevive] != null),
 *  b) one row: HOME (secondary, [onHome]) left, RETRY (primary, [onRetry]) right.
 * [onRetry] is the seam for restarting the level (an interstitial may sit in front via AdsViewModel.beforeRetry).
 * The reward is granted by the caller only on the earned-reward result. The revive CTA is inert while the ad loads /
 * a request is in flight, "Try Again" after a load failure.
 */
@Composable
private fun LoseDialog(
    onRetry: () -> Unit,
    onHome: () -> Unit,
    onWatchRevive: (() -> Unit)?,
    rewardedLoad: RewardedLoadState,
    rewardedBusy: Boolean,
    onRetryRewardedLoad: () -> Unit,
) {
    LoseScreenLayout(
        title = stringResource(R.string.lose_title),
        message = stringResource(R.string.lose_message),
        heroDescription = stringResource(R.string.lose_fish_bone_description),
        reviveDescription = stringResource(R.string.lose_watch_ad_description),
        reviveLabel = stringResource(R.string.lose_watch_ad),
        homeLabel = stringResource(R.string.lose_home),
        retryLabel = stringResource(R.string.lose_retry),
        onRetry = onRetry,
        onHome = onHome,
        onWatchRevive = onWatchRevive,
        rewardedLoad = rewardedLoad,
        rewardedBusy = rewardedBusy,
        onRetryRewardedLoad = onRetryRewardedLoad,
    )
}

/**
 * Shared "failed" screen layout (Levels "Out of lives" + Sudoku "Out of hearts"). Text comes in as params; the layout,
 * art, colors and motion are the Levels ones. [inputArmed] false = buttons inert (Sudoku guards against a stray tap
 * landing right as the screen appears); Levels keeps the default true.
 */
@Composable
internal fun LoseScreenLayout(
    title: String,
    message: String,
    heroDescription: String,
    reviveDescription: String,
    reviveLabel: String,
    homeLabel: String,
    retryLabel: String,
    onRetry: () -> Unit,
    onHome: () -> Unit,
    onWatchRevive: (() -> Unit)?,
    rewardedLoad: RewardedLoadState,
    rewardedBusy: Boolean,
    onRetryRewardedLoad: () -> Unit,
    inputArmed: Boolean = true,
) {

    val sway = rememberInfiniteTransition(label = "failedSway")
    val swayDeg by sway.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(2_200), RepeatMode.Reverse),
        label = "failedSwayDeg",
    )

    DialogScrim(Modifier.semantics { paneTitle = title }, scrimColor = GameColors.OutcomeScrim) {
    StatusBarIconsEffect()
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            DownsampledIconImage(
                res = IconArt.CatSad,
                modifier = Modifier
                    .semantics { contentDescription = heroDescription }
                    .rotate(swayDeg),
                size = 220.dp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                color = GameColors.WinMessage,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
            )
            Spacer(Modifier.height(28.dp))
            if (onWatchRevive != null) {
                val ready = rewardedLoad == RewardedLoadState.READY && !rewardedBusy
                val failed = rewardedLoad == RewardedLoadState.FAILED && !rewardedBusy
                val style = when {
                    ready -> ButtonStyle.Red
                    failed -> ButtonStyle.Blue
                    else -> ButtonStyle.Locked
                }
                Button3D(
                    onClick = {
                        if (inputArmed) when {
                            ready -> onWatchRevive()
                            failed -> onRetryRewardedLoad()
                            else -> Unit // loading / busy: inert
                        }
                    },
                    style = style,
                    contentDescription = if (ready) reviveDescription else null,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
                ) {
                    when {
                        failed -> Text(
                            stringResource(R.string.reward_try_again),
                            color = style.content,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        ready -> {
                            IconImage(R.drawable.ic_play, size = 22.dp, tint = style.content)
                            Text(
                                reviveLabel,
                                color = style.content,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                        else -> Text(
                            stringResource(R.string.reward_loading),
                            color = style.content,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(
                Modifier.fillMaxWidth().widthIn(max = 360.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // HOME: secondary (cream) so RETRY stays the primary action.
                Button3D(
                    onClick = { if (inputArmed) onHome() },
                    style = ButtonStyle.Secondary,
                    horizontalPadding = 12.dp,
                    modifier = Modifier.weight(1f),
                ) {
                    HomePlaceholder(color = ButtonStyle.Secondary.content)
                    Text(
                        homeLabel,
                        color = ButtonStyle.Secondary.content,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FredokaFamily,
                        maxLines = 1,
                    )
                }
                PillButton(
                    text = retryLabel,
                    onClick = { if (inputArmed) onRetry() },
                    leadingIconRes = R.drawable.ic_reloading,
                    style = ButtonStyle.Primary,
                    textSize = 20.sp,
                    horizontalPadding = 12.dp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
