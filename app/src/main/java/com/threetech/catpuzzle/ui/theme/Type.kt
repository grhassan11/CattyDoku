package com.threetech.catpuzzle.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.threetech.catpuzzle.R

// Fredoka (SIL OFL 1.1) variable font, bundled at res/font/fredoka.ttf (wght 300-700, wdth 75-125).
// One Font entry per weight, each pinning the wght axis. Max weight is 700, so Black/ExtraBold
// requests resolve to the Bold entry (closest declared weight, no synthetic bold).
@OptIn(ExperimentalTextApi::class)
private fun fredoka(weight: FontWeight) = Font(
    resId = R.font.fredoka,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.width(100f),
    ),
)

val FredokaFamily = FontFamily(
    fredoka(FontWeight.Normal),
    fredoka(FontWeight.Medium),
    fredoka(FontWeight.SemiBold),
    fredoka(FontWeight.Bold),
)

private val base = Typography()

val CatTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = FredokaFamily),
    displayMedium = base.displayMedium.copy(fontFamily = FredokaFamily),
    displaySmall = base.displaySmall.copy(fontFamily = FredokaFamily),
    headlineLarge = base.headlineLarge.copy(fontFamily = FredokaFamily),
    headlineMedium = base.headlineMedium.copy(fontFamily = FredokaFamily),
    headlineSmall = base.headlineSmall.copy(fontFamily = FredokaFamily),
    titleLarge = base.titleLarge.copy(fontFamily = FredokaFamily),
    titleMedium = base.titleMedium.copy(fontFamily = FredokaFamily),
    titleSmall = base.titleSmall.copy(fontFamily = FredokaFamily),
    bodyLarge = base.bodyLarge.copy(fontFamily = FredokaFamily),
    bodyMedium = base.bodyMedium.copy(fontFamily = FredokaFamily),
    bodySmall = base.bodySmall.copy(fontFamily = FredokaFamily),
    labelLarge = base.labelLarge.copy(fontFamily = FredokaFamily),
    labelMedium = base.labelMedium.copy(fontFamily = FredokaFamily),
    labelSmall = base.labelSmall.copy(fontFamily = FredokaFamily),
)
