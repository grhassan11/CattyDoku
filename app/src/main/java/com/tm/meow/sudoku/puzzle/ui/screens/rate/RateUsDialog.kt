package com.tm.meow.sudoku.puzzle.ui.screens.rate

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.ui.components.DialogScrim
import com.tm.meow.sudoku.puzzle.ui.components.IconImage
import com.tm.meow.sudoku.puzzle.ui.theme.GameColors

/**
 * Own "Rate us" dialog: [RateUsContent] + a close X (top-right). Fully stateless: tapping a star IS the action
 * ([onStarSelected]); 4-5 -> store listing, 1-3 -> feedback email, routing in the ViewModel, intents in the host.
 */
@Composable
internal fun RateUsDialog(
    onStarSelected: (stars: Int) -> Unit,
    onClose: () -> Unit,
) {
    val title = stringResource(R.string.rate_title)
    val closeDescription = stringResource(R.string.reward_dialog_close)
    BackHandler(onBack = onClose)

    DialogScrim(Modifier.semantics { paneTitle = title }, onClick = onClose) {
        val absorb = remember { MutableInteractionSource() }
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .background(GameColors.Card, RoundedCornerShape(28.dp))
                // Taps inside the card must not reach the scrim (which dismisses).
                .clickable(interactionSource = absorb, indication = null, onClick = {})
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Box(
                    Modifier
                        .size(48.dp)
                        .semantics { contentDescription = closeDescription }
                        .clickable(role = Role.Button, onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    IconImage(R.drawable.ic_close, size = 20.dp, tint = GameColors.Body)
                }
            }
            RateUsContent(onStarSelected = onStarSelected)
        }
    }
}
