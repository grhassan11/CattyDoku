package com.threetech.catpuzzle.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.domain.model.MilestoneStatus
import com.threetech.catpuzzle.ui.components.Button3D
import com.threetech.catpuzzle.ui.components.ButtonStyle
import com.threetech.catpuzzle.ui.components.IconImage
import com.threetech.catpuzzle.ui.components.PillButton
import com.threetech.catpuzzle.ui.components.rememberPressScale
import com.threetech.catpuzzle.ui.theme.FredokaFamily
import com.threetech.catpuzzle.ui.components.homeBackground
import com.threetech.catpuzzle.ui.theme.GameColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val DOTS_PER_ROW = 10
private val DoneGreen = GameColors.CtaGreenFace
private val PaleDisc = GameColors.Disabled

/**
 * Full-screen Daily Streak: current + best streak, Mon-Sun week row and the last 30 days
 * (orange = played, green = Daily Challenge done). Stateless: data in, lambda out.
 */
@Composable
fun StreakScreen(
    calendar: StreakCalendarUi?,
    rewards: StreakRewardUi,
    onClaim: (day: Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.streak_title)
    val data = calendar ?: EMPTY

    Column(
        modifier
            .fillMaxSize()
            .homeBackground()
            .systemBarsPadding()
            .semantics { paneTitle = title },
    ) {
        Header(title = title, onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            IconImage(R.drawable.ic_yarn_ball, size = 120.dp)
            Spacer(Modifier.height(4.dp))
            Text(
                data.current.toString(),
                color = GameColors.Primary,
                fontSize = 72.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                stringResource(R.string.streak_current_label),
                color = GameColors.Primary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.streak_best_chip, data.best),
                color = GameColors.Title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(GameColors.RuleCard, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(24.dp))

            Column(
                Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .background(GameColors.Card, RoundedCornerShape(24.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (data.week.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (day in data.week) WeekCell(day)
                    }
                    Spacer(Modifier.height(20.dp))
                }

                Text(
                    stringResource(R.string.streak_last_30),
                    color = GameColors.Title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                if (data.recent.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (row in data.recent.chunked(DOTS_PER_ROW)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (day in row) RecentDot(day)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Legend(GameColors.Primary, stringResource(R.string.streak_legend_played))
                    Legend(DoneGreen, stringResource(R.string.streak_legend_daily))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.streak_daily_total, data.dailyTotal),
                    color = GameColors.Body,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }

            if (rewards.visible) {
                Spacer(Modifier.height(16.dp))
                RewardTrack(rewards = rewards, onClaim = onClaim)
            }
            Spacer(Modifier.height(16.dp))
        }

        // fixed CTA slot (outside the scroll area)
        PillButton(
            text = stringResource(R.string.streak_continue),
            onClick = onBack,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally),
        )
    }
}

/** Milestone track: progress through the 30-day cycle + one row per milestone (locked / claimable / claimed). */
@Composable
private fun RewardTrack(rewards: StreakRewardUi, onClaim: (Int) -> Unit) {
    val progressText = stringResource(R.string.streak_rewards_progress, rewards.cycleDay, rewards.cycleLength)
    Column(
        Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .background(GameColors.Card, RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.streak_rewards_title),
                color = GameColors.Title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Text(progressText, color = GameColors.Body, fontSize = 14.sp)
        }
        Spacer(Modifier.height(10.dp))
        val fraction = (rewards.cycleDay.toFloat() / rewards.cycleLength).coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(PaleDisc)
                .clearAndSetSemantics { },
        ) {
            if (fraction > 0f) {
                Box(Modifier.fillMaxWidth(fraction).height(10.dp).background(GameColors.Primary, CircleShape))
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (m in rewards.milestones) MilestoneRow(m, onClaim)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MilestoneRow(m: MilestoneUi, onClaim: (Int) -> Unit) {
    val labels = m.lines.map { rewardLabel(it) }
    val summary = labels.joinToString(", ")
    val description = when (m.status) {
        MilestoneStatus.LOCKED -> stringResource(R.string.streak_reward_node_locked, m.day, summary)
        MilestoneStatus.CLAIMABLE -> stringResource(R.string.streak_reward_node_claimable, m.day, summary)
        MilestoneStatus.CLAIMED -> stringResource(R.string.streak_reward_node_claimed, m.day, summary)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(R.string.streak_reward_day, m.day),
                color = GameColors.Title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.width(44.dp).clearAndSetSemantics { },
            )
            FlowRow(
                Modifier
                    .weight(1f)
                    .alpha(if (m.status == MilestoneStatus.CLAIMED) 0.6f else 1f)
                    .clearAndSetSemantics { },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (line in m.lines) {
                    Row(
                        Modifier
                            .background(GameColors.RuleCard, CircleShape)
                            .border(1.dp, GameColors.CtaCreamLip, CircleShape)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RewardBoosterIcon(line.booster, size = 18.dp)
                        Text(
                            "x" + line.amount,
                            color = GameColors.Body,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FredokaFamily,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        when (m.status) {
            MilestoneStatus.CLAIMABLE -> Button3D(
                onClick = { onClaim(m.day) },
                style = ButtonStyle.Green,
                contentDescription = stringResource(R.string.streak_reward_claim_description, m.day, summary),
                horizontalPadding = 14.dp,
                totalHeight = 42.dp,
                lipHeight = 4.dp,
                modifier = Modifier.widthIn(min = 84.dp),
            ) {
                Text(
                    stringResource(R.string.streak_reward_claim),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FredokaFamily,
                    maxLines = 1,
                    softWrap = false,
                    style = androidx.compose.ui.text.TextStyle(
                        lineHeight = 21.sp,
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                        ),
                    ),
                )
            }
            MilestoneStatus.CLAIMED -> IconImage(
                R.drawable.ic_check,
                size = 30.dp,
                tint = DoneGreen,
                modifier = Modifier.clearAndSetSemantics { },
            )
            MilestoneStatus.LOCKED -> Box(
                Modifier
                    .size(30.dp)
                    .shadow(2.dp, CircleShape)
                    .background(GameColors.RuleCard, CircleShape)
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                IconImage(R.drawable.ic_lock, size = 16.dp, tint = GameColors.LockedPill)
            }
        }
    }
}

private val EMPTY = StreakCalendarUi(current = 0, best = 0, week = emptyList(), recent = emptyList(), dailyTotal = 0)

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    val backDescription = stringResource(R.string.streak_close)
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source)
    Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp)) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .size(48.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(GameColors.Card)
                .semantics { contentDescription = backDescription }
                .clickable(interactionSource = source, indication = ripple(), role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            IconImage(R.drawable.ic_back, size = 24.dp, tint = GameColors.Body)
        }
        Text(
            text = title,
            color = GameColors.Title,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.align(Alignment.Center).semantics { heading() },
        )
    }
}

@Composable
private fun dayName(epochDay: Long): String =
    remember(epochDay) {
        LocalDate.ofEpochDay(epochDay).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }

private fun dateText(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))

@Composable
private fun WeekCell(day: DayUi) {
    val name = dayName(day.epochDay)
    val description = when {
        day.isToday -> stringResource(R.string.streak_week_day_today, name)
        day.state == DayDotState.DAILY_DONE || day.state == DayDotState.PLAYED ->
            stringResource(R.string.streak_week_day_played, name)
        else -> stringResource(R.string.streak_week_day_missed, name)
    }
    Column(
        Modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            name.uppercase(Locale.getDefault()),
            color = if (day.isToday) GameColors.Primary else GameColors.Body,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Spacer(Modifier.height(4.dp))
        Disc(day, size = 36.dp)
    }
}

@Composable
private fun Disc(day: DayUi, size: Dp) {
    val fill = when (day.state) {
        DayDotState.DAILY_DONE -> DoneGreen
        DayDotState.PLAYED -> GameColors.Primary
        DayDotState.MISSED, DayDotState.FUTURE -> PaleDisc
    }
    val checked = day.state == DayDotState.DAILY_DONE || day.state == DayDotState.PLAYED
    Box(
        Modifier
            .size(size)
            .background(fill, CircleShape)
            .then(if (day.isToday) Modifier.border(2.dp, GameColors.Title, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) CheckMark(Color.White, size * 0.5f)
    }
}

@Composable
private fun RecentDot(day: DayUi) {
    val description = when (day.state) {
        DayDotState.DAILY_DONE -> stringResource(R.string.streak_dot_daily, dateText(day.epochDay))
        DayDotState.PLAYED -> stringResource(R.string.streak_dot_played, dateText(day.epochDay))
        else -> stringResource(R.string.streak_dot_missed, dateText(day.epochDay))
    }
    val fill = when (day.state) {
        DayDotState.DAILY_DONE -> DoneGreen
        DayDotState.PLAYED -> GameColors.Primary
        DayDotState.MISSED, DayDotState.FUTURE -> PaleDisc
    }
    Box(
        Modifier
            .size(22.dp)
            .background(fill, CircleShape)
            .then(if (day.isToday) Modifier.border(2.dp, GameColors.Title, CircleShape) else Modifier)
            .semantics { contentDescription = description },
    )
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Text(label, color = GameColors.Body, fontSize = 13.sp)
    }
}

/** Own Canvas check mark (no asset). */
@Composable
private fun CheckMark(color: Color, size: Dp) {
    Canvas(Modifier.size(size).clearAndSetSemantics { }) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.16f, cap = StrokeCap.Round)
        val a = Offset(w * 0.08f, h * 0.55f)
        val b = Offset(w * 0.38f, h * 0.85f)
        val c = Offset(w * 0.92f, h * 0.2f)
        drawLine(color, a, b, stroke.width, StrokeCap.Round)
        drawLine(color, b, c, stroke.width, StrokeCap.Round)
    }
}
