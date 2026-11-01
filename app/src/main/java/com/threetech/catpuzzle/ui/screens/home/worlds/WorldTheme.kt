package com.threetech.catpuzzle.ui.screens.home.worlds

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import com.threetech.catpuzzle.domain.model.WorldRules
import com.threetech.catpuzzle.ui.theme.GameColors
import kotlin.math.cos
import kotlin.math.sin

/** Level node state on the Home map. */
enum class NodeState { Done, Current, Locked }

/** Corner glyph per world. */
enum class WorldGlyph { Paw, Fish, Leaf, Flake, Heart, Star }

/** Node 3D face palette: [top] / [bottom] gradient, [lip] = the darker depth edge. */
@Immutable
class NodePalette(val top: Color, val bottom: Color, val lip: Color)

/**
 * Per-world skin. done/current/locked = node face palettes; accent = glow ring + glyph badge;
 * path* = trail; banner* = signpost gradient; [background] = parallax scenery; [lightStatusBarIcons] = dark sky.
 */
@Immutable
class WorldTheme internal constructor(
    val name: String,
    val done: NodePalette,
    val current: NodePalette,
    val locked: NodePalette,
    val accent: Color,
    val glyph: WorldGlyph,
    val pathColor: Color,
    val pathStitch: Color,
    val bgTint: Color,
    val bannerA: Color,
    val bannerB: Color,
    /** Dark tray drawn behind a node's 3 stars (yellow stars need >= 3:1 on any backdrop). */
    val starPlate: Color,
    /** Wash over the node band: tones down busy backdrop detail behind nodes and stars. */
    val calm: Color,
    internal val background: WorldBackground,
    /** True = dark backdrop under the status bar: system bar icons must be light. Bright worlds keep dark icons. */
    val lightStatusBarIcons: Boolean = false,
) {
    /**
     * World-name pill on Home, soft world tint: fill = [bannerA] blended 18% into white (alpha .92), text = [bannerB]
     * pulled 70% toward the dark [starPlate] (>= 5:1 on the fill), border = [bannerA] 35% toward white.
     */
    val pillFill: Color get() = lerp(Color.White, bannerA.copy(alpha = 1f), 0.18f).copy(alpha = 0.92f)
    val pillText: Color get() = lerp(bannerB.copy(alpha = 1f), starPlate.copy(alpha = 1f), 0.7f)
    val pillBorder: Color get() = lerp(bannerA.copy(alpha = 1f), Color.White, 0.35f)

    fun palFor(state: NodeState): NodePalette = when (state) {
        NodeState.Done -> done
        NodeState.Current -> current
        NodeState.Locked -> locked
    }
}

private fun pal(top: Long, bottom: Long, lip: Long) = NodePalette(Color(top), Color(bottom), Color(lip))

/**
 * The 10 authored worlds, in play order. Index = [WorldRules.themeIndex]. World 1 ("Cozy Parlor") is the original
 * HOME screen look (cream [GameColors.ScreenBackground] + brown board pattern, CTA green / orange / grey nodes), so
 * first launch matches the previous Home colours; the rest follow.
 */
object WorldThemes {
    private val Themes: List<WorldTheme> = listOf(
        WorldTheme(
            "Cozy Parlor",
            NodePalette(lerp(GameColors.CtaGreenFace, Color.White, 0.28f), GameColors.CtaGreenFace, GameColors.CtaGreenLip),
            NodePalette(lerp(GameColors.CtaOrangeFace, Color.White, 0.28f), GameColors.CtaOrangeFace, GameColors.CtaOrangeLip),
            NodePalette(lerp(GameColors.CtaGreyFace, Color.White, 0.28f), GameColors.CtaGreyFace, GameColors.CtaGreyLip),
            GameColors.Primary, WorldGlyph.Paw,
            GameColors.CtaCreamLip, Color.White.copy(alpha = 0.85f), GameColors.ScreenBackground,
            Color(0xFFF39522), Color(0xFFC96F0A),
            Color(0xFF4A2F1E).copy(alpha = 0.9f), GameColors.ScreenBackground.copy(alpha = 0.55f),
            CozyBackground,
        ),
        WorldTheme(
            "Sunny Garden", pal(0xFF8BD66A, 0xFF4CAF50, 0xFF2E7D32), pal(0xFFFFC857, 0xFFF59E0B, 0xFFB45309),
            pal(0xFFC3CFBE, 0xFF9EB096, 0xFF7F9378), Color(0xFFF59E0B), WorldGlyph.Leaf,
            Color(0xFFE8D9A8), Color.White.copy(alpha = 0.85f), Color(0xFFEAF6DC), Color(0xFF6CC24A), Color(0xFF3E9B3E),
            Color(0xFF1F4A22).copy(alpha = 0.9f), Color(0xFFEAF6DC).copy(alpha = 0.5f),
            SunnyBackground,
        ),
        WorldTheme(
            "Candy Kitchen", pal(0xFFFF8FB8, 0xFFE8508B, 0xFFA82D60), pal(0xFF5ED4C9, 0xFF14B8A6, 0xFF0B7A6E),
            pal(0xFFD2BFCB, 0xFFB59AA9, 0xFF9A7C8E), Color(0xFF14B8A6), WorldGlyph.Heart,
            Color(0xFFF7C6D9), Color.White.copy(alpha = 0.9f), Color(0xFFFFEAF2), Color(0xFFFF7BAA), Color(0xFFE0457F),
            Color(0xFF5A1B38).copy(alpha = 0.9f), Color(0xFFFFEAF2).copy(alpha = 0.5f),
            CandyBackground,
        ),
        WorldTheme(
            "Sandy Shore", pal(0xFF4FD0C5, 0xFF1FA89B, 0xFF0F6F66), pal(0xFFFF8A75, 0xFFF4573E, 0xFFA8321E),
            pal(0xFFD8D0BC, 0xFFB8AF96, 0xFF9A917A), Color(0xFFF4573E), WorldGlyph.Fish,
            Color(0xFFFFF0C2), Color.White.copy(alpha = 0.85f), Color(0xFFFFF4D6), Color(0xFF3BB8D0), Color(0xFF1C8FAA),
            Color(0xFF0B4A55).copy(alpha = 0.9f), Color(0xFFFFF4D6).copy(alpha = 0.5f),
            ShoreBackground,
        ),
        WorldTheme(
            "Autumn Alley", pal(0xFF7DBE6A, 0xFF4E9A45, 0xFF30702F), pal(0xFFFFD166, 0xFFF4A300, 0xFFA86B00),
            pal(0xFFCDB9A5, 0xFFAE9680, 0xFF8F7A66), Color(0xFFC8452B), WorldGlyph.Leaf,
            Color(0xFFF6E3C0), Color.White.copy(alpha = 0.85f), Color(0xFFFFEDD6), Color(0xFFE8873A), Color(0xFFB5531A),
            Color(0xFF4A2412).copy(alpha = 0.9f), Color(0xFFFFEDD6).copy(alpha = 0.5f),
            AutumnBackground,
        ),
        WorldTheme(
            "Snow Cabin", pal(0xFF6EC1E4, 0xFF3A97C9, 0xFF226A92), pal(0xFFFF8A65, 0xFFF4511E, 0xFFA8330F),
            pal(0xFFC4D3E0, 0xFFA2B6C8, 0xFF8499AE), Color(0xFFF4511E), WorldGlyph.Flake,
            Color(0xFFF4FAFF), Color(0xFF9CC4DE), Color(0xFFE3F1FA), Color(0xFF5AB4DD), Color(0xFF2F7FB0),
            Color(0xFF173F5C).copy(alpha = 0.9f), Color(0xFFE3F1FA).copy(alpha = 0.4f),
            SnowBackground,
        ),
        WorldTheme(
            "Misty Forest", pal(0xFF6FA8F0, 0xFF3F7FD6, 0xFF234F96), pal(0xFFFFB454, 0xFFFB8C00, 0xFFA85A00),
            pal(0xFFB9C7C1, 0xFF98A9A2, 0xFF7B8D86), Color(0xFFFB8C00), WorldGlyph.Paw,
            Color(0xFFF4F7F0), Color(0xFF8DB3A0), Color(0xFFEAF3EE), Color(0xFF4F8F79), Color(0xFF2F6552),
            Color(0xFF1F4638).copy(alpha = 0.9f), Color(0xFFEAF3EE).copy(alpha = 0.5f),
            MistBackground,
        ),
        WorldTheme(
            "Berry Dusk", pal(0xFFB07CE8, 0xFF8A4FD0, 0xFF5A2E96), pal(0xFFFFB454, 0xFFFB8C00, 0xFFA85A00),
            pal(0xFFCFC3DD, 0xFFB0A1C5, 0xFF8F7FAA), Color(0xFFFB8C00), WorldGlyph.Fish,
            Color(0xFFE6D8F7), Color.White.copy(alpha = 0.85f), Color(0xFFF1E9FB), Color(0xFFA066E0), Color(0xFF6C3BB0),
            Color(0xFF331A55).copy(alpha = 0.9f), Color(0xFF2A1445).copy(alpha = 0.35f),
            BerryBackground,
            lightStatusBarIcons = true,
        ),
        WorldTheme(
            "Golden Library", pal(0xFF4FA38A, 0xFF2E7F68, 0xFF1A5646), pal(0xFFEF6F7F, 0xFFD6405A, 0xFF8F2338),
            pal(0xFFCDBFA8, 0xFFAE9E86, 0xFF8F7F69), Color(0xFFD6405A), WorldGlyph.Star,
            Color(0xFFFFF3D0), Color(0xFFC9983F), Color(0xFFFFF1CF), Color(0xFFB5733A), Color(0xFF7C4A22),
            Color(0xFF4A2B12).copy(alpha = 0.9f), Color(0xFFFFF1CF).copy(alpha = 0.5f),
            LibraryBackground,
        ),
        WorldTheme(
            "Midnight Roof", pal(0xFF7C8CF8, 0xFF4F5FD6, 0xFF2F3A9A), pal(0xFFFFD166, 0xFFF4A300, 0xFFA86B00),
            pal(0xFF6B7391, 0xFF4F5675, 0xFF3A4060), Color(0xFFFFD166), WorldGlyph.Star,
            Color(0xFF3B4270), Color(0xFF8E98D8), Color(0xFF1F2547), Color(0xFF3B4BB5), Color(0xFF1B2358),
            Color(0xFF10143A).copy(alpha = 0.9f), Color(0xFF0B0F33).copy(alpha = 0.35f),
            MidnightBackground,
            lightStatusBarIcons = true,
        ),
    )

    init {
        check(Themes.size == WorldRules.THEME_COUNT) { "theme table must hold ${WorldRules.THEME_COUNT} worlds" }
    }

    fun forThemeIndex(index: Int): WorldTheme = Themes[Math.floorMod(index, Themes.size)]

    fun forLevel(level: Int): WorldTheme = Themes[WorldRules.themeIndex(level)]
}

/* ---------------------------------------------------------------- glyphs */

internal fun starPath(cx: Float, cy: Float, outer: Float, inner: Float): Path {
    val path = Path()
    for (i in 0 until 10) {
        val angle = (-Math.PI / 2.0 + i * Math.PI / 5.0).toFloat()
        val r = if (i % 2 == 0) outer else inner
        val x = cx + r * cos(angle)
        val y = cy + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

internal fun DrawScope.drawGlyph(g: WorldGlyph, c: Offset, s: Float, color: Color) {
    when (g) {
        WorldGlyph.Paw -> {
            drawOval(color, Offset(c.x - s * 0.5f, c.y - s * 0.05f), Size(s, s * 0.6f))
            drawCircle(color, s * 0.14f, Offset(c.x - 0.5f * s, c.y - 0.25f * s))
            drawCircle(color, s * 0.14f, Offset(c.x - 0.18f * s, c.y - 0.5f * s))
            drawCircle(color, s * 0.14f, Offset(c.x + 0.18f * s, c.y - 0.5f * s))
            drawCircle(color, s * 0.14f, Offset(c.x + 0.5f * s, c.y - 0.25f * s))
        }
        WorldGlyph.Fish -> {
            drawOval(color, Offset(c.x - s * 0.6f, c.y - s * 0.3f), Size(s * 0.95f, s * 0.6f))
            val tail = Path().apply {
                moveTo(c.x + s * 0.2f, c.y); lineTo(c.x + s * 0.6f, c.y - s * 0.3f)
                lineTo(c.x + s * 0.6f, c.y + s * 0.3f); close()
            }
            drawPath(tail, color)
        }
        WorldGlyph.Leaf -> {
            val leaf = Path().apply {
                moveTo(c.x - s * 0.5f, c.y + s * 0.5f)
                quadraticTo(c.x - s * 0.6f, c.y - s * 0.5f, c.x + s * 0.5f, c.y - s * 0.5f)
                quadraticTo(c.x + s * 0.6f, c.y + s * 0.6f, c.x - s * 0.5f, c.y + s * 0.5f)
                close()
            }
            drawPath(leaf, color)
        }
        WorldGlyph.Flake -> for (k in 0 until 3) {
            rotate(k * 60f, c) {
                drawLine(color, Offset(c.x, c.y - s * 0.55f), Offset(c.x, c.y + s * 0.55f), strokeWidth = s * 0.16f, cap = StrokeCap.Round)
            }
        }
        WorldGlyph.Heart -> {
            drawCircle(color, s * 0.28f, Offset(c.x - s * 0.25f, c.y - s * 0.15f))
            drawCircle(color, s * 0.28f, Offset(c.x + s * 0.25f, c.y - s * 0.15f))
            val tri = Path().apply {
                moveTo(c.x - s * 0.5f, c.y - s * 0.02f); lineTo(c.x + s * 0.5f, c.y - s * 0.02f)
                lineTo(c.x, c.y + s * 0.55f); close()
            }
            drawPath(tri, color)
        }
        WorldGlyph.Star -> drawPath(starPath(c.x, c.y, s * 0.6f, s * 0.26f), color)
    }
}
