package com.tm.meow.sudoku.puzzle.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Max content width for full-screen pages on tablets / foldables / free-form windows (>= 600 dp, where Android 16 ignores the
 * portrait lock). Phones are narrower than this, so they are unaffected. Centre the node yourself (align / contentAlignment).
 */
val ScreenContentMaxWidth: Dp = 560.dp

/** Fill the width up to [ScreenContentMaxWidth]. */
fun Modifier.contentMaxWidth(): Modifier = this.widthIn(max = ScreenContentMaxWidth).fillMaxWidth()
