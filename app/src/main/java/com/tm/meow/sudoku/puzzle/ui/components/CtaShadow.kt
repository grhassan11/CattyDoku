package com.tm.meow.sudoku.puzzle.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle

/** Drop shadow for CTA button labels (same as PillButton's filled-style text). */
val CtaTextShadow: Shadow = Shadow(Color(0x66000000), Offset(0f, 2f), 3f)

/**
 * Inherited text style + [CtaTextShadow]. Use as `style =` so font/size/spacing from LocalTextStyle
 * are kept (a bare TextStyle(shadow = ...) would replace them).
 */
@Composable
fun ctaTextStyle(): TextStyle = LocalTextStyle.current.copy(shadow = CtaTextShadow)
