package com.threetech.catpuzzle.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Game-screen tokens from PRD section 8 (estimated by eye, sample real hex later).
 * Kept apart from [CatColors] (phase 0 pastel placeholders) so nothing earlier changes.
 */
object GameColors {
    val ScreenBackground = Color(0xFFF7F1EC)
    val Card = Color(0xFFFFFFFF)
    val RuleCard = Color(0xFFFBF3EC)
    val Title = Color(0xFF7B4E55)
    val Body = Color(0xFF9A5252)
    val Primary = Color(0xFFF39522)
    val Coral = Color(0xFFE14B4B)
    val XMark = Color.White
    val Conflict = Color(0xFFE14B4B)
    val HintTarget = Color(0xFFF39522)
    val HintReason = Color(0xFFFFFFFF)
    val FishOn = Color(0xFFF39522)
    val FishOff = Color(0xFFD9CEC8)
    val Bulb = Color(0xFFF7C948)
    val Disabled = Color(0xFFE6DCD5)

    // home screen
    /** Wordmark + pattern brown. */
    val Wordmark = Color(0xFF8B5A3C)
    val LockedPill = Color(0xFFABABAB)
    val LockedPillContent = Color(0xFFFFFFFF)
    /** Dark grey badge behind the lock on the locked mouse booster. */
    val LockedBadge = Color(0xFF4A4A4A)

    // 3D CTA buttons: face + darker lip of same hue
    val CtaOrangeFace = Color(0xFFF39A21)
    val CtaOrangeLip = Color(0xFFC8741A)
    val CtaGreenFace = Color(0xFF7CC576)
    val CtaGreenLip = Color(0xFF4F9A4A)
    val CtaBlueFace = Color(0xFF6BBBE8)
    val CtaBlueLip = Color(0xFF3F8FBF)
    val CtaGreyFace = Color(0xFFABABAB)
    val CtaGreyLip = Color(0xFF8A8A8A)
    /** Daily Challenge (available). White label + shadow on DailyPink. */
    val DailyPink = Color(0xFFFF6FA5)
    val DailyPinkDark = Color(0xFFD94A82)
    val CtaCreamFace = Color(0xFFFFFFFF)
    val CtaCreamLip = Color(0xFFE2D2C0)

    // dialogs (phase 5, estimated by eye from reference screenshots)
    val Scrim = Color(0xB3101010)
    val WinScrim = Color(0xC7101010) // ~0.78 alpha; win dialog only
    /** Hint overlay dim over the whole game screen; spotlight cells are cut out of it. */
    val HintDim = Color(0xC7101010)
    val LoseScrim = Color(0xD9101010) // ~0.85 alpha; lose dialog only (was shared Scrim 0.70)
    val StatCardBg = Color(0xFFFFFCF8) // win dialog stat chips; near-white, opaque
    val StatCardLabel = Color(0xFF9A5252) // ~5.7:1 on StatCardBg
    val StatCardValue = Color(0xFF7B4E55) // ~7:1 on StatCardBg
    val Sunburst = Color(0xFFFFD84D)
    val WinMessage = Color(0xFFFFE9A0)
    val RibbonFillTop = Color(0xFFFFB63D)
    val RibbonFill = Color(0xFFF39522)
    val RibbonEdge = Color(0xFFC96F0A)
    val RibbonTail = Color(0xFFD97A12)
    val RibbonFold = Color(0xFF8F4A06)
    val RibbonText = Color(0xFFFFFFFF)
    val RibbonTextShadow = Color(0x663A1D00)
    val ToggleOn = Color(0xFF7CC576)
    val ToggleOff = Color(0xFFEADBC8)
    val OnToggleOn = Color(0xFFFFFFFF)
    val OnToggleOff = Color(0xFF9A8570)

    /** 10 region colors (PRD 8 palette + 2 own picks). Index = region id. Pattern Mode adds a pattern per id. */
    val Regions: List<Color> = listOf(
        Color(0xFFFBB4D1), // 0 pink
        Color(0xFF6BBBE8), // 1 sky
        Color(0xFFE3BB4B), // 2 mustard
        Color(0xFFADDB93), // 3 light green
        Color(0xFF47B5B0), // 4 teal
        Color(0xFFAB6C47), // 5 caramel
        Color(0xFF977FD9), // 6 violet
        Color(0xFF5B75B5), // 7 indigo
        Color(0xFFF2936B), // 8 coral orange (own pick)
        Color(0xFFB9C1CC), // 9 slate (own pick)
    )

    fun region(id: Int): Color = Regions[id.mod(Regions.size)]
}
