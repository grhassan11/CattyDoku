package com.tm.meow.sudoku.puzzle.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.ui.components.Button3D
import com.tm.meow.sudoku.puzzle.ui.components.ButtonStyle
import com.tm.meow.sudoku.puzzle.ui.theme.FredokaFamily
import com.tm.meow.sudoku.puzzle.ui.theme.GameColors

/**
 * Exit confirmation bottom sheet (system Back on Home). Stateless: [onDismiss] = Cancel / scrim / drag-down / Back
 * (the sheet consumes Back itself), [onExit] = leave the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExitSheet(
    onDismiss: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val message = stringResource(R.string.exit_sheet_message)
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
                .semantics { paneTitle = message },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                message,
                color = GameColors.Title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FredokaFamily,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button3D(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    style = ButtonStyle.Secondary,
                    horizontalPadding = 8.dp,
                ) {
                    Text(
                        stringResource(R.string.exit_sheet_cancel),
                        color = ButtonStyle.Secondary.content,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FredokaFamily,
                        maxLines = 1,
                    )
                }
                Button3D(
                    onClick = onExit,
                    modifier = Modifier.weight(1f),
                    style = ButtonStyle.Red,
                    horizontalPadding = 8.dp,
                ) {
                    Text(
                        stringResource(R.string.exit_sheet_exit),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FredokaFamily,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
