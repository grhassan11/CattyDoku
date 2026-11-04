package com.tm.meow.sudoku.puzzle.ui.screens.sudoku.game

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuTier
import com.tm.meow.sudoku.puzzle.ui.components.AdBadgeRed
import com.tm.meow.sudoku.puzzle.ui.components.IconImage
import com.tm.meow.sudoku.puzzle.ui.screens.sudoku.entry.labelRes
import com.tm.meow.sudoku.puzzle.ui.theme.FredokaFamily
import com.tm.meow.sudoku.puzzle.ui.theme.GameColors

/** 48 dp circular icon button (back, help, restart). Always a full touch target. */
@Composable
internal fun CircleButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(48.dp)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(GameColors.Card)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Back, tier title ("Medium - 6x6"), help, restart. */
@Composable
internal fun SudokuTopBar(
    tier: SudokuTier,
    n: Int,
    onBack: () -> Unit,
    onHelp: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = stringResource(tier.labelRes())
    val theme = LocalSudokuTierTheme.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(stringResource(R.string.sudoku_back), onBack) {
            IconImage(R.drawable.ic_back, size = 22.dp, tint = theme.ink)
        }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.sudoku_game_title, name, n),
                modifier = Modifier.semantics { heading() },
                color = theme.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FredokaFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CircleButton(stringResource(R.string.sudoku_help), onHelp) {
            Text("?", color = theme.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FredokaFamily)
        }
        Box(Modifier.width(8.dp))
        CircleButton(stringResource(R.string.sudoku_restart), onRestart) {
            IconImage(R.drawable.ic_reloading, size = 22.dp, tint = theme.ink)
        }
    }
}

/** Score / Time (+ pause circle) / Mistakes (hearts). */
@Composable
internal fun SudokuStatsRow(
    score: Int,
    timeText: String,
    paused: Boolean,
    canPause: Boolean,
    hearts: Int,
    maxHearts: Int,
    onPauseToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalSudokuTierTheme.current
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        StatColumn(stringResource(R.string.sudoku_stat_score), Modifier.weight(0.75f), hideLabel = true) {
            StatValue(score.toString(), stringResource(R.string.sudoku_score_description, score))
        }
        // Wider than the others: the clock text + 48 dp pause target must fit at large font scales.
        StatColumn(stringResource(R.string.sudoku_stat_time), Modifier.weight(1.6f), hideLabel = true) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                StatValue(timeText, stringResource(R.string.sudoku_time_description, timeText))
                val pauseDescription = stringResource(if (paused) R.string.sudoku_resume else R.string.sudoku_pause)
                Box(
                    Modifier
                        .size(width = 48.dp, height = 40.dp)
                        .clip(CircleShape)
                        .clickable(enabled = canPause, role = Role.Button, onClick = onPauseToggle)
                        .semantics { contentDescription = pauseDescription }
                        .alpha(if (canPause) 1f else 0.4f),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(24.dp).border(2.dp, theme.ink, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (paused) {
                            IconImage(R.drawable.ic_play, size = 12.dp, tint = theme.ink)
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Box(Modifier.size(width = 3.dp, height = 10.dp).background(theme.ink, RoundedCornerShape(1.dp)))
                                Box(Modifier.size(width = 3.dp, height = 10.dp).background(theme.ink, RoundedCornerShape(1.dp)))
                            }
                        }
                    }
                }
            }
        }
        val heartsDescription = stringResource(R.string.sudoku_hearts_description, hearts, maxHearts)
        StatColumn(stringResource(R.string.sudoku_stat_mistakes), Modifier.weight(1.0f)) {
            Row(
                Modifier.height(40.dp).clearAndSetSemantics { contentDescription = heartsDescription },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                repeat(maxHearts) { i ->
                    IconImage(
                        R.drawable.ic_heart_filled,
                        size = 22.dp,
                        tint = if (i < hearts) GameColors.Conflict else GameColors.FishOff,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, modifier: Modifier, hideLabel: Boolean = false, content: @Composable () -> Unit) {
    val theme = LocalSudokuTierTheme.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxHeight()
            .shadow(2.dp, shape)
            .clip(shape)
            .background(GameColors.Card)
            .border(1.dp, theme.accent.copy(alpha = 0.35f), shape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // hideLabel: the value node already speaks "Time 01:22" / "Score 75", so the visual label is not read twice.
        Text(
            label,
            modifier = if (hideLabel) Modifier.clearAndSetSemantics {} else Modifier,
            color = theme.inkSoft,
            fontSize = 14.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        content()
    }
}

/** sp value that scales with the system font size, but never past [maxScale] (fixed-width slots: clock, pad keys). */
@Composable
internal fun cappedSp(base: Float, maxScale: Float = FONT_SCALE_CAP): TextUnit {
    val scale = LocalDensity.current.fontScale
    return (base * minOf(scale, maxScale) / scale).sp
}

internal const val FONT_SCALE_CAP = 1.15f

@Composable
private fun StatValue(text: String, description: String) {
    val theme = LocalSudokuTierTheme.current
    Text(
        text,
        modifier = Modifier.height(40.dp).wrapMiddle().clearAndSetSemantics { contentDescription = description },
        color = theme.ink,
        fontSize = cappedSp(22f),
        fontWeight = FontWeight.Bold,
        fontFamily = FredokaFamily,
        maxLines = 1,
    )
}

private fun Modifier.wrapMiddle(): Modifier = this.then(Modifier.padding(top = 6.dp))

/** Undo / Notes (ON badge) / Erase / Hint (stock badge, play glyph at 0). */
@Composable
internal fun SudokuActionRow(
    inputEnabled: Boolean,
    canUndo: Boolean,
    canErase: Boolean,
    notesMode: Boolean,
    hintCharges: Int,
    onUndo: () -> Unit,
    onToggleNotes: () -> Unit,
    onErase: () -> Unit,
    onHint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
        ToolButton(
            label = stringResource(R.string.sudoku_undo),
            description = stringResource(R.string.sudoku_undo_description),
            iconRes = R.drawable.ic_sudoku_undo,
            enabled = canUndo,
            onClick = onUndo,
        )
        ToolButton(
            label = stringResource(R.string.sudoku_notes),
            description = stringResource(if (notesMode) R.string.sudoku_notes_on_description else R.string.sudoku_notes_off_description),
            iconRes = R.drawable.ic_sudoku_notes,
            enabled = inputEnabled,
            active = notesMode,
            badge = if (notesMode) {
                { BadgeText(stringResource(R.string.sudoku_notes_on)) }
            } else {
                null
            },
            onClick = onToggleNotes,
        )
        ToolButton(
            label = stringResource(R.string.sudoku_erase),
            description = stringResource(R.string.sudoku_erase_description),
            iconRes = R.drawable.ic_sudoku_erase,
            enabled = canErase,
            onClick = onErase,
        )
        ToolButton(
            label = stringResource(R.string.sudoku_hint),
            description = if (hintCharges > 0) {
                stringResource(R.string.sudoku_hint_description, hintCharges)
            } else {
                stringResource(R.string.sudoku_hint_none_description)
            },
            iconRes = R.drawable.ic_sudoku_hint,
            enabled = inputEnabled,
            badgeColor = AdBadgeRed,
            badge = {
                if (hintCharges > 0) {
                    BadgeText(hintCharges.toString())
                } else {
                    IconImage(R.drawable.ic_play, size = 10.dp, tint = Color.White)
                }
            },
            onClick = onHint,
        )
    }
}

@Composable
private fun BadgeText(text: String) {
    // No font padding + trimmed line box so the glyph sits on the exact centre of the badge.
    Text(
        text,
        color = Color.White,
        fontSize = 11.sp,
        lineHeight = 11.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        style = BadgeTextStyle,
    )
}

private val BadgeTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

@Composable
private fun ToolButton(
    label: String,
    description: String,
    @DrawableRes iconRes: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    badgeColor: Color? = null,
    badge: (@Composable () -> Unit)? = null,
) {
    val theme = LocalSudokuTierTheme.current
    Column(
        modifier
            .width(76.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = 64.dp, height = 58.dp)) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .size(52.dp)
                    .shadow(if (active) 0.dp else 2.dp, CircleShape)
                    .background(if (active) theme.accentStrong else GameColors.Card, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                IconImage(iconRes, size = 26.dp, tint = if (active) Color.White else theme.ink)
            }
            if (badge != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-2).dp)
                        .defaultMinSize20()
                        .background(badgeColor ?: theme.accentStrong, RoundedCornerShape(50))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) { badge() }
            }
        }
        Text(
            label,
            color = if (active) theme.accentStrong else theme.ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

private fun Modifier.defaultMinSize20(): Modifier = this.then(Modifier.widthIn(min = 20.dp).height(20.dp))

/** N keys, digit on top and what is left of it below. A key is dimmed and inert when all N are placed. */
@Composable
internal fun SudokuNumberPad(
    keys: List<SudokuPadKeyUi>,
    inputEnabled: Boolean,
    onDigit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gap: Dp = if (keys.size > 6) 4.dp else 8.dp
    val theme = LocalSudokuTierTheme.current
    Row(
        modifier.fillMaxWidth().widthIn(max = (keys.size * 72).dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        keys.forEach { key ->
            val enabled = inputEnabled && key.enabled
            val description = stringResource(R.string.sudoku_pad_description, key.digit, key.remaining)
            Column(
                Modifier
                    .weight(1f)
                    .height(68.dp)
                    .alpha(if (key.enabled) 1f else 0.35f)
                    .shadow(1.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.padFill)
                    .then(
                        if (theme.padBorderWidthDp > 0f) {
                            Modifier.border(theme.padBorderWidthDp.dp, theme.padBorder, RoundedCornerShape(14.dp))
                        } else {
                            Modifier
                        },
                    )
                    .clickable(enabled = enabled, role = Role.Button) { onDigit(key.digit) }
                    .semantics(mergeDescendants = true) { contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    key.digit.toString(),
                    color = SudokuColors.PlayerDigit,
                    fontSize = cappedSp(28f),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FredokaFamily,
                    maxLines = 1,
                )
                Text(
                    key.remaining.toString(),
                    color = theme.inkSoft,
                    fontSize = cappedSp(12f),
                    maxLines = 1,
                )
            }
        }
    }
}
