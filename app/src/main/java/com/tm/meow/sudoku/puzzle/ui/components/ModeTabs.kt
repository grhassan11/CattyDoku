package com.tm.meow.sudoku.puzzle.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.ui.theme.FredokaFamily

/** Game mode a screen belongs to; drives the selected tab of [HomeModeTabs]. */
enum class HomeMode { Levels, Sudoku }

/**
 * Levels / Sudoku pill tabs shared by Home (Levels selected) and the Sudoku entry screen (Sudoku selected).
 * Colours are fixed (Levels = Teal, Sudoku = Green); [selected] is semantics-only, so Home looks as before.
 */
@Composable
fun HomeModeTabs(
    selected: HomeMode,
    onLevels: () -> Unit,
    onSudoku: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Narrow screens (large display size / small phones): each tab is only ~(width - 40) / 2 dp wide, so shrink icon, label and padding.
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val compact = maxWidth < CompactTabsBelow
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            ModeTab(stringResource(R.string.home_tab_classic), R.drawable.ic_game_classic, selected = selected == HomeMode.Levels, tealTab = true, compact = compact, onClick = onLevels, modifier = Modifier.weight(1f))
            ModeTab(stringResource(R.string.home_tab_sudoku), R.drawable.ic_game_sudoku, selected = selected == HomeMode.Sudoku, tealTab = false, compact = compact, onClick = onSudoku, modifier = Modifier.weight(1f))
        }
    }
}

/** Below this width the tabs use the compact look (icon 22dp, 17sp label, 8dp padding); at or above it they look as designed. */
private val CompactTabsBelow = 340.dp

@Composable
private fun ModeTab(label: String, @DrawableRes icon: Int, selected: Boolean, tealTab: Boolean, compact: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Full colour always: Levels = Teal, Sudoku = Green. Selection is semantics-only. Button3D owns tap cue.
    val style = if (tealTab) ButtonStyle.Teal else ButtonStyle.Green
    Button3D(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = if (compact) 0.dp else 120.dp),
        style = style,
        horizontalPadding = if (compact) 8.dp else 12.dp, // compact: icon + "Sudoku" must fit a ~124dp tab at 288dp wide
        totalHeight = 62.dp, // 56dp face + 6dp lip
        role = Role.Tab,
        selected = selected,
    ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(if (compact) 22.dp else 28.dp))
        Text(
            label,
            color = style.content,
            fontSize = if (compact) 17.sp else 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FredokaFamily,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
            style = androidx.compose.ui.text.TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(Color(0x66000000), Offset(0f, 2f), 3f),
            ),
        )
    }
}
