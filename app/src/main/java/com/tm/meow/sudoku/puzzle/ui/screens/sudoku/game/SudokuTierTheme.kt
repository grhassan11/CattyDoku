package com.tm.meow.sudoku.puzzle.ui.screens.sudoku.game

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuTier
import com.tm.meow.sudoku.puzzle.ui.screens.sudoku.entry.dotColor
import com.tm.meow.sudoku.puzzle.ui.theme.GameColors

/**
 * Per-tier accent colours of the Sudoku game screen: background tint, board highlights, number pad and header accents.
 * Board face, digits, givens, notes, error red, cards and dialogs are not part of it (they stay neutral). Light only.
 * [Default] is the pre-tier look (flat cream, orange accents) and is what the tutorial uses.
 */
@Immutable
data class SudokuTierTheme(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val accent: Color,
    val accentStrong: Color,
    val title: Color,
    val ink: Color,
    val inkSoft: Color,
    val line: Color,
    val peerFill: Color,
    val sameDigitFill: Color,
    val selectedFill: Color,
    val selectRing: Color,
    val unitFlash: Color,
    val padFill: Color,
    val padBorder: Color,
    val padBorderWidthDp: Float,
) {
    companion object {
        val Default = SudokuTierTheme(
            backgroundTop = GameColors.ScreenBackground,
            backgroundBottom = GameColors.ScreenBackground,
            accent = GameColors.Primary,
            accentStrong = GameColors.Primary,
            title = GameColors.Title,
            ink = GameColors.Title,
            inkSoft = GameColors.Body,
            line = GameColors.CtaCreamLip,
            peerFill = GameColors.CtaCreamLip.copy(alpha = 0.45f),
            sameDigitFill = GameColors.Primary.copy(alpha = 0.22f),
            selectedFill = GameColors.Primary.copy(alpha = 0.5f),
            selectRing = GameColors.Primary,
            unitFlash = GameColors.Primary,
            padFill = GameColors.Card,
            padBorder = Color.Transparent,
            padBorderWidthDp = 0f,
        )
    }
}

internal val LocalSudokuTierTheme = staticCompositionLocalOf { SudokuTierTheme.Default }

private val INK = Color(0xFF2B1B1F)

/** Extreme's base is the same red as the error colour, so its highlight tints use a mauve instead (red digits stay distinct). */
private val ExtremeFillBase = Color(0xFFB5649A)

/** Single tier to theme mapping. Base colour = the entry screen tier dot. */
fun sudokuTierTheme(tier: SudokuTier): SudokuTierTheme {
    val base = tier.dotColor()
    val fillBase = if (tier == SudokuTier.EXTREME) ExtremeFillBase else base
    return deriveSudokuTierTheme(base, fillBase)
}

internal fun deriveSudokuTierTheme(base: Color, fillBase: Color = base): SudokuTierTheme {
    val strong = lerp(base, INK, 0.55f)
    val hue = hueOf(base)
    val ink = Color.hsl(hue, 0.45f, 0.24f)
    return SudokuTierTheme(
        backgroundTop = lerp(GameColors.ScreenBackground, base, 0.18f),
        backgroundBottom = lerp(GameColors.ScreenBackground, base, 0.06f),
        accent = base,
        accentStrong = strong,
        title = ink,
        ink = ink,
        inkSoft = Color.hsl(hue, 0.40f, 0.31f),
        line = Color.hsl(hue, 0.45f, 0.24f).copy(alpha = 0.2f).compositeOver(GameColors.Card),
        peerFill = fillBase.copy(alpha = 0.10f),
        sameDigitFill = fillBase.copy(alpha = 0.18f),
        selectedFill = fillBase.copy(alpha = 0.30f),
        selectRing = strong,
        unitFlash = fillBase,
        padFill = lerp(GameColors.Card, fillBase, 0.10f),
        padBorder = base.copy(alpha = 0.75f),
        padBorderWidthDp = 1.5f,
    )
}

/** Hue in degrees [0, 360) of an sRGB colour; 0 for greys. Used to derive the dark ink of a tier from its base colour. */
internal fun hueOf(c: Color): Float {
    val max = maxOf(c.red, c.green, c.blue)
    val d = max - minOf(c.red, c.green, c.blue)
    if (d == 0f) return 0f
    val h = when (max) {
        c.red -> ((c.green - c.blue) / d) % 6f
        c.green -> (c.blue - c.red) / d + 2f
        else -> (c.red - c.green) / d + 4f
    } * 60f
    return if (h < 0f) h + 360f else h
}
