package com.tm.meow.sudoku.puzzle.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.aspectRatio
import com.tm.meow.sudoku.puzzle.R

/**
 * App title image (plaque logo, 2:1 PNG, transparent). Used on Splash + Welcome.
 * Caller passes width via [modifier] (e.g. fillMaxWidth(0.8f)); [maxWidth] caps it on tablets.
 */
@Composable
fun AppTitleLogo(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 320.dp,
) {
    Image(
        painter = painterResource(R.drawable.app_title_logo),
        contentDescription = stringResource(R.string.app_name),
        modifier = modifier.widthIn(max = maxWidth).aspectRatio(2f),
        contentScale = ContentScale.Fit,
    )
}
