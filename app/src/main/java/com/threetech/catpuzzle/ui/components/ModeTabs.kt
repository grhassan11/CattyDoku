package com.threetech.catpuzzle.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.ui.theme.FredokaFamily

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
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        ModeTab(stringResource(R.string.home_tab_classic), R.drawable.ic_game_classic, selected = selected == HomeMode.Levels, tealTab = true, onClick = onLevels, modifier = Modifier.weight(1f))
        ModeTab(stringResource(R.string.home_tab_sudoku), R.drawable.ic_game_sudoku, selected = selected == HomeMode.Sudoku, tealTab = false, onClick = onSudoku, modifier = Modifier.weight(1f))
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
