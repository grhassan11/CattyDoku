package com.tm.meow.sudoku.puzzle.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.domain.language.AppLanguage
import com.tm.meow.sudoku.puzzle.ui.components.DialogScrim
import com.tm.meow.sudoku.puzzle.ui.components.IconImage
import com.tm.meow.sudoku.puzzle.ui.components.LocalAudioController
import com.tm.meow.sudoku.puzzle.ui.components.PillButton
import com.tm.meow.sudoku.puzzle.ui.theme.FredokaFamily
import com.tm.meow.sudoku.puzzle.ui.theme.GameColors

/** Selected row fill. CtaGreenLip (not the lighter face) so white text keeps >= 3:1 contrast. */
private val SelectedFill = GameColors.CtaGreenLip
private val UnselectedFill = Color(0xFFF6E7DA)
private val BodyFill = GameColors.StatCardBg

/**
 * App Language picker. Stateless apart from the pending selection (saved across recreation, starts at [currentTag]).
 * Row tap = select (pending), [onConfirm] applies, tap outside / X / back = [onDismiss] without change.
 * Rows are one line per name with ellipsis, so long names never break narrow (288 dp) screens.
 */
@Composable
internal fun LanguageDialog(
    languages: List<AppLanguage>,
    currentTag: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(R.string.language_title)
    val closeDescription = stringResource(R.string.language_close)
    val selectedText = stringResource(R.string.language_selected)
    val audio = LocalAudioController.current
    var pendingTag by rememberSaveable { mutableStateOf(currentTag) }
    BackHandler(onBack = onDismiss)

    DialogScrim(Modifier.semantics { paneTitle = title }, onClick = onDismiss) {
        val absorb = remember { MutableInteractionSource() }
        Column(
            Modifier
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(BodyFill)
                // Taps inside the card must not reach the scrim (which dismisses).
                .clickable(interactionSource = absorb, indication = null, onClick = {}),
        ) {
            Box(
                Modifier.fillMaxWidth().background(GameColors.ScreenBackground).height(64.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.padding(horizontal = 56.dp).semantics { heading() },
                    color = GameColors.Title,
                    fontFamily = FredokaFamily,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .size(56.dp)
                        .semantics { contentDescription = closeDescription }
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    IconImage(R.drawable.ic_close, size = 22.dp, tint = GameColors.Body)
                }
            }

            // Open with the selected row in view (2 rows of context above). Initial index = no first-frame jump.
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = (languages.indexOfFirst { it.tag == pendingTag } - 2).coerceAtLeast(0),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .scrollHint(listState),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 22.dp, top = 12.dp, bottom = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(languages, key = { it.tag }) { language ->
                    val selected = language.tag == pendingTag
                    LanguageRow(
                        language = language,
                        selected = selected,
                        selectedText = selectedText,
                        onClick = {
                            if (!selected) audio?.playUiTap()
                            pendingTag = language.tag
                        },
                    )
                }
            }

            PillButton(
                text = stringResource(R.string.language_confirm),
                onClick = { onConfirm(pendingTag) },
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 18.dp),
            )
        }
    }
}

@Composable
private fun LanguageRow(
    language: AppLanguage,
    selected: Boolean,
    selectedText: String,
    onClick: () -> Unit,
) {
    val nativeColor = if (selected) Color.White else GameColors.Body
    val englishColor = if (selected) Color.White.copy(alpha = 0.85f) else GameColors.Body.copy(alpha = 0.8f)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) SelectedFill else UnselectedFill)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { if (selected) stateDescription = selectedText }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = language.nativeName,
                color = nativeColor,
                fontFamily = FredokaFamily, // no CJK glyphs in Fredoka: those scripts fall back to the system font per glyph
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = language.englishName,
                color = englishColor,
                fontFamily = FredokaFamily,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Box(
                Modifier.size(32.dp).background(Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                IconImage(R.drawable.ic_check, size = 20.dp, tint = SelectedFill)
            }
        }
    }
}

/** Thin rounded scrollbar thumb on the right edge; only drawn when the list actually scrolls. */
private fun Modifier.scrollHint(state: androidx.compose.foundation.lazy.LazyListState): Modifier =
    drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val total = info.totalItemsCount
        val visible = info.visibleItemsInfo
        if (total == 0 || visible.isEmpty() || visible.size >= total && !state.canScrollForward && !state.canScrollBackward) {
            return@drawWithContent
        }
        val itemPx = visible.sumOf { it.size }.toFloat() / visible.size
        val contentPx = itemPx * total
        val viewport = size.height
        if (contentPx <= viewport) return@drawWithContent
        val first = visible.first()
        val scrolled = first.index * itemPx - first.offset
        val thumbH = (viewport * viewport / contentPx).coerceAtLeast(24.dp.toPx())
        val maxScroll = (contentPx - viewport).coerceAtLeast(1f)
        val top = ((viewport - thumbH) * (scrolled / maxScroll)).coerceIn(0f, viewport - thumbH)
        val w = 4.dp.toPx()
        drawRoundRect(
            color = GameColors.Body.copy(alpha = 0.35f),
            topLeft = Offset(size.width - w - 8.dp.toPx(), top),
            size = Size(w, thumbH),
            cornerRadius = CornerRadius(w / 2f),
        )
    }
