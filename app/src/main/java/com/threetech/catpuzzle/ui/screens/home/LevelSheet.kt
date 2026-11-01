package com.threetech.catpuzzle.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.domain.model.LevelDifficulty
import com.threetech.catpuzzle.domain.model.LevelStars
import com.threetech.catpuzzle.ui.components.Button3D
import com.threetech.catpuzzle.ui.components.ButtonStyle
import com.threetech.catpuzzle.ui.components.IconArt
import com.threetech.catpuzzle.ui.components.ShadowedIcon
import com.threetech.catpuzzle.ui.screens.home.worlds.WorldThemes
import com.threetech.catpuzzle.ui.theme.FredokaFamily
import com.threetech.catpuzzle.ui.theme.GameColors

/**
 * Level details bottom sheet (opened by tapping a done / current node). Stateless: [sheet] in, lambdas out.
 * Drag-down / scrim tap = [onDismiss]; the PLAY button calls [onPlay] (the caller clears the selection and navigates).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelSheet(
    sheet: LevelSheetUi,
    onDismiss: () -> Unit,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val description = stringResource(R.string.level_sheet_description, sheet.level)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = GameColors.ScreenBackground,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .semantics { paneTitle = description },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LevelSheetContent(sheet, onPlay)
        }
    }
}

@Composable
private fun LevelSheetContent(sheet: LevelSheetUi, onPlay: (Int) -> Unit) {
    val worldName = WorldThemes.forLevel(sheet.level).name
    Text(
        stringResource(R.string.level_sheet_title, sheet.level),
        color = GameColors.Title,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FredokaFamily,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
    Text(
        worldName,
        color = GameColors.Body,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FredokaFamily,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
    Spacer(Modifier.height(16.dp))
    DetailRow(stringResource(R.string.level_sheet_board_size)) {
        ValueText(stringResource(R.string.level_sheet_board_value, sheet.boardSize))
    }
    RowDivider()
    DetailRow(stringResource(R.string.level_sheet_difficulty)) {
        ValueText(stringResource(difficultyLabel(sheet.difficulty)))
    }
    if (sheet.completed) {
        RowDivider()
        DetailRow(stringResource(R.string.level_sheet_stars)) {
            val starsDescription = stringResource(R.string.level_sheet_stars_description, sheet.stars)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clearAndSetSemantics { contentDescription = starsDescription },
            ) {
                for (i in 0 until LevelStars.MAX) {
                    ShadowedIcon(res = IconArt.StarIsland, size = 30.dp, greyed = i >= sheet.stars)
                }
            }
        }
        RowDivider()
        DetailRow(stringResource(R.string.level_sheet_best_time)) {
            ValueText(sheet.bestTimeMs?.let(::formatBestTime) ?: stringResource(R.string.level_sheet_none))
        }
        RowDivider()
        DetailRow(stringResource(R.string.level_sheet_best_score)) {
            ValueText(sheet.bestScore?.toString() ?: stringResource(R.string.level_sheet_none))
        }
    }
    Spacer(Modifier.height(24.dp))
    Button3D(
        onClick = { onPlay(sheet.level) },
        modifier = Modifier.fillMaxWidth(),
        style = ButtonStyle.Green,
    ) {
        Text(
            stringResource(if (sheet.completed) R.string.level_sheet_replay else R.string.level_sheet_play),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FredokaFamily,
            maxLines = 1,
        )
    }
}

@Composable
private fun DetailRow(label: String, value: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = GameColors.Body,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FredokaFamily,
            maxLines = 1,
        )
        value()
    }
}

@Composable
private fun ValueText(text: String) {
    Text(
        text,
        color = GameColors.Wordmark,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FredokaFamily,
        maxLines = 1,
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = GameColors.Wordmark.copy(alpha = 0.15f))
}

private fun difficultyLabel(d: LevelDifficulty): Int = when (d) {
    LevelDifficulty.Easy -> R.string.level_sheet_difficulty_easy
    LevelDifficulty.Medium -> R.string.level_sheet_difficulty_medium
    LevelDifficulty.Hard -> R.string.level_sheet_difficulty_hard
    LevelDifficulty.Expert -> R.string.level_sheet_difficulty_expert
}

/** m:ss style (mm:ss), or h:mm:ss from an hour up. Negative clamps to 00:00. */
internal fun formatBestTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000L
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
