package com.threetech.catpuzzle.ui.screens.game

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.threetech.catpuzzle.domain.engine.Sample9x9
import com.threetech.catpuzzle.ui.screens.home.worlds.WorldTheme
import com.threetech.catpuzzle.ui.screens.home.worlds.WorldThemes
import com.threetech.catpuzzle.ui.theme.GameColors

/**
 * Gameplay surface colours for one level's world. Colours only. Derived from the Home WorldTheme palette.
 * Covers: screen background, board card, the rules strip + its 3 cards, top bar, lives pill and undo pill.
 * Booster buttons, ad strip, region (cell) colours and X marks are NOT part of this.
 * dark = dark world: surfaces are deep, text and icons light; the board card stays a light tint so region colours read.
 */
@Immutable
class GameSurfaceColors(
    val background: Color,
    val boardCard: Color,
    val ruleStrip: Color,
    val ruleCard: Color,
    val onSurface: Color,
    val buttonContainer: Color,
    val buttonContent: Color,
    val dark: Boolean,
    /** False = default look (daily, sample, tutorial): the screen keeps the system status bar icons. */
    val themed: Boolean,
) {
    companion object {
        val Default = GameSurfaceColors(
            background = GameColors.ScreenBackground,
            boardCard = GameColors.Card,
            ruleStrip = GameColors.Card,
            ruleCard = GameColors.RuleCard,
            onSurface = GameColors.Body,
            buttonContainer = GameColors.Card,
            buttonContent = GameColors.Body,
            dark = false,
            themed = false,
        )

        /** Level <= 0 (daily) and the 9x9 sample keep Default. Boss levels use their world like any other. */
        fun forLevel(level: Int?): GameSurfaceColors {
            if (level == null || level <= 0 || level == Sample9x9.LEVEL_INDEX) return Default
            return from(WorldThemes.forLevel(level))
        }

        fun from(w: WorldTheme): GameSurfaceColors {
            val plate = w.starPlate.copy(alpha = 1f)
            val tint = w.bgTint.copy(alpha = 1f)
            val accent = w.bannerA.copy(alpha = 1f)
            return if (w.lightStatusBarIcons) {
                val text = lerp(Color.White, accent, 0.10f)
                GameSurfaceColors(
                    background = plate,
                    boardCard = lerp(Color.White, accent, 0.14f),
                    ruleStrip = lerp(plate, Color.White, 0.12f),
                    ruleCard = lerp(plate, Color.White, 0.22f),
                    onSurface = text,
                    buttonContainer = lerp(plate, Color.White, 0.14f),
                    buttonContent = text,
                    dark = true,
                    themed = true,
                )
            } else {
                val text = w.pillText.copy(alpha = 1f)
                GameSurfaceColors(
                    background = tint,
                    boardCard = lerp(tint, Color.White, 0.75f),
                    ruleStrip = lerp(tint, Color.White, 0.70f),
                    ruleCard = lerp(tint, Color.White, 0.35f),
                    onSurface = text,
                    buttonContainer = lerp(tint, Color.White, 0.70f),
                    buttonContent = text,
                    dark = false,
                    themed = true,
                )
            }
        }
    }
}

/** Provided by GameScreen; BoardView falls back to Default (tutorial). */
val LocalGameSurface = compositionLocalOf { GameSurfaceColors.Default }
