package com.threetech.catpuzzle.ui.theme

import androidx.compose.ui.graphics.Color

object CatColors {
    val Background = Color(0xFFFFF8F0)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF4A3F3A)
    val InkMuted = Color(0xFF8C7F78)
    val Accent = Color(0xFFE8896B)
    val OnAccent = Color(0xFFFFFFFF)
    val GridLine = Color(0xFF6B5E57)
    val Conflict = Color(0xFFD64545)

    /** 10 pastel region colors; engine caps at 9 regions, 10th spare. */
    val RegionPalette: List<Color> = listOf(
        Color(0xFFF9C6C9), // blush
        Color(0xFFFFE0B2), // apricot
        Color(0xFFFFF3B0), // butter
        Color(0xFFCDEAC0), // mint
        Color(0xFFB8E4E0), // aqua
        Color(0xFFBFD9F2), // sky
        Color(0xFFD5CCF0), // lavender
        Color(0xFFF0C8E6), // orchid
        Color(0xFFE3D5C3), // sand
        Color(0xFFD9DEE3), // mist
    )

    fun region(id: Int): Color = RegionPalette[id.mod(RegionPalette.size)]
}
