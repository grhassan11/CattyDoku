package com.tm.meow.sudoku.puzzle.ui.screens.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tm.meow.sudoku.puzzle.BuildConfig
import com.tm.meow.sudoku.puzzle.R

/** About has no ViewModel: nothing to load or persist. Version comes from BuildConfig. */
@Composable
fun AboutRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AboutScreen(
        appName = stringResource(R.string.app_name),
        versionName = BuildConfig.VERSION_NAME,
        onBack = onBack,
        modifier = modifier,
    )
}
