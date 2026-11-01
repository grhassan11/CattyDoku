package com.threetech.catpuzzle.ui.screens.home.worlds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.threetech.catpuzzle.ui.components.homePatternDrawer
import com.threetech.catpuzzle.ui.theme.GameColors
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/*
 * Parallax world backdrops (promoted from the node gallery prototype). Each world = 4 layers back-to-front
 * (sky pinned, far / mid / near scroll slower than the trail) + a legibility scrim + a trail halo colour.
 * The backdrop is FIXED to the screen (full-bleed behind the trail). Each layer draws one screen-sized tile, mirrored
 * on alternate tiles so it repeats seamlessly, and slides by -scroll * speed. Everything draws inside drawWithCache
 * (build once per size, scroll offset read in the draw phase only: no recomposition per scroll frame).
 */

/** Per-layer parallax speed factors (net screen speed = scroll * speed). */
internal const val ParallaxFar = 0.2f
internal const val ParallaxMid = 0.5f
internal const val ParallaxNear = 0.8f

/** One background layer. [content] fills its parent; slides by -parallaxOffset * [speed] (sky = 0 = pinned). */
internal class BackdropLayer(val name: String, val speed: Float, val content: @Composable (parallaxOffset: () -> Float) -> Unit)

/** Ordered back-to-front layers + legibility [scrim] (over all layers) + trail [halo] colour. */
internal class WorldBackground(val layers: List<BackdropLayer>, val scrim: Color, val halo: Color)

/**
 * One world's full-bleed scenery. A flat [calm] wash (the theme's own colour + alpha) covers the whole area above the
 * layers and scrim, evenly toning down busy detail so nodes and stars stay readable.
 */
@Composable
internal fun WorldBackdrop(
    bg: WorldBackground,
    calm: Color,
    parallaxOffset: () -> Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        for (layer in bg.layers) layer.content(parallaxOffset)
        Box(Modifier.fillMaxSize().background(bg.scrim))
        Box(Modifier.fillMaxSize().background(calm))
    }
}

/**
 * cached Canvas layer. [build] runs on size change only (allocate Paths/Brushes there).
 * Screen-fixed: the layer is one screen-wide tile drawn at -offset*speed (mod width), with the next tile mirrored
 * beside it (alternating mirror = no visible seam). Sky (speed 0) draws once, pinned. [parallaxOffset] = the trail's
 * scroll in px, read in the draw phase only. No allocation per frame (inline translate / scale).
 */
internal fun Modifier.bgLayer(speed: Float, parallaxOffset: () -> Float, build: CacheDrawScope.() -> DrawScope.() -> Unit): Modifier =
    fillMaxSize().drawWithCache {
        val draw = build()
        val w = size.width
        onDrawBehind {
            if (speed == 0f || w <= 0f) {
                draw(this)
            } else {
                val shift = (parallaxOffset() * speed).coerceAtLeast(0f)
                val tile = floor(shift / w).toInt()
                val x = tile * w - shift // left edge of tile `tile`, in (-w, 0]
                if (tile and 1 == 0) {
                    translate(left = x) { draw(this) }
                    translate(left = x + 2f * w) { scale(-1f, 1f, Offset.Zero) { draw(this) } }
                } else {
                    translate(left = x + w) { scale(-1f, 1f, Offset.Zero) { draw(this) } }
                    translate(left = x + w) { draw(this) }
                }
            }
        }
    }

internal fun CacheDrawScope.art(block: DrawScope.() -> Unit): DrawScope.() -> Unit = block

/** filled sine ridge from [baseY] down to bottom. */
internal fun ridgePath(w: Float, h: Float, baseY: Float, amp: Float, bumps: Float, phase: Float): Path {
    val p = Path()
    p.moveTo(0f, h)
    val steps = 48
    for (i in 0..steps) {
        val x = w * i / steps
        p.lineTo(x, baseY + amp * sin(x / w * bumps * 6.2831855f + phase))
    }
    p.lineTo(w, h)
    p.close()
    return p
}

internal fun DrawScope.drawCloud(cx: Float, cy: Float, s: Float, color: Color) {
    drawRoundRect(color, Offset(cx - s, cy), Size(2f * s, s * 0.5f), CornerRadius(s * 0.25f))
    drawCircle(color, s * 0.4f, Offset(cx - s * 0.35f, cy))
    drawCircle(color, s * 0.55f, Offset(cx + s * 0.1f, cy - s * 0.1f))
}

/* ---- Sunny Garden */

@Composable
internal fun SunnySky(o: () -> Float) {
    Box(Modifier.bgLayer(0f, o) {
        val br = Brush.verticalGradient(listOf(Color(0xFF86D4F5), Color(0xFFD3F0EE), Color(0xFFEAF6DC)), 0f, size.height)
        art { drawRect(br) }
    })
}

@Composable
internal fun SunnyFar(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxFar, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val sun = Offset(w * 0.78f, 70f * u)
        val glow = Brush.radialGradient(listOf(Color(0x80FFE680), Color.Transparent), sun, 110f * u)
        val hills = ridgePath(w, h, h * 0.5f, 16f * u, 1.4f, 0.6f)
        art {
            drawCircle(glow, 110f * u, sun)
            for (k in 0 until 12) {
                val a = k * 0.5235988f
                drawLine(
                    Color(0x66FFE680),
                    Offset(sun.x + 40f * u * cos(a), sun.y + 40f * u * sin(a)),
                    Offset(sun.x + 56f * u * cos(a), sun.y + 56f * u * sin(a)),
                    3f * u, StrokeCap.Round,
                )
            }
            drawCircle(Color(0xFFFFD54F), 30f * u, sun)
            drawCircle(Color(0xFFFFF59D), 22f * u, sun)
            val cloud = Color.White.copy(alpha = 0.85f)
            drawCloud(w * 0.2f, 120f * u, 34f * u, cloud)
            drawCloud(w * 0.55f, 40f * u, 26f * u, cloud)
            drawCloud(w * 0.85f, h * 0.3f, 30f * u, cloud)
            drawCloud(w * 0.12f, h * 0.4f, 24f * u, cloud)
            drawPath(hills, Color(0xFFB5E19A))
        }
    })
}

@Composable
internal fun SunnyMid(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxMid, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val hills = ridgePath(w, h, h * 0.62f, 20f * u, 2f, 2f)
        val hedgeTop = h * 0.7f
        val spans = listOf(-0.02f to 0.3f, 0.5f to 0.78f, 0.86f to 1.02f)
        val hedges = Path().apply {
            for ((a, b) in spans) addRoundRect(RoundRect(w * a, hedgeTop, w * b, hedgeTop + 46f * u, 18f * u, 18f * u))
        }
        val rnd = Random(7)
        val n = 24
        val fx = FloatArray(n)
        val fy = FloatArray(n)
        for (i in 0 until n) {
            val (a, b) = spans[i % spans.size]
            fx[i] = w * (a + (b - a) * (0.1f + 0.8f * rnd.nextFloat()))
            fy[i] = hedgeTop + (8f + 28f * rnd.nextFloat()) * u
        }
        val petals = longArrayOf(0xFFFF8FB8, 0xFFFFF176, 0xFFFFFFFF)
        art {
            drawPath(hills, Color(0xFF8ED071))
            drawPath(hedges, Color(0xFF4F9E45))
            for (i in 0 until n) {
                drawCircle(Color(petals[i % 3]), 3.4f * u, Offset(fx[i], fy[i]))
                drawCircle(Color(0xFFFFB300), 1.3f * u, Offset(fx[i], fy[i]))
            }
        }
    })
}

@Composable
internal fun SunnyNear(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxNear, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val ground = ridgePath(w, h, h * 0.9f, 8f * u, 2.5f, 1f)
        val rnd = Random(3)
        val tufts = Path().apply {
            for (i in 0 until 16) {
                val x = w * (i + 0.5f) / 16f + (rnd.nextFloat() - 0.5f) * 10f * u
                val y = h * 0.9f + 8f * u + rnd.nextFloat() * h * 0.08f
                val bh = (12f + rnd.nextFloat() * 10f) * u
                for (b in -1..1) {
                    moveTo(x + b * 5f * u - 2.5f * u, y)
                    lineTo(x + b * 8f * u, y - bh * (if (b == 0) 1f else 0.75f))
                    lineTo(x + b * 5f * u + 2.5f * u, y)
                    close()
                }
            }
        }
        val fx = floatArrayOf(0.1f, 0.3f, 0.66f, 0.9f, 0.45f)
        val fyy = floatArrayOf(0.95f, 0.975f, 0.96f, 0.94f, 0.985f)
        val heads = longArrayOf(0xFFFF8FB8, 0xFFFFF176, 0xFFFFFFFF, 0xFFFF8FB8, 0xFFFFF176)
        art {
            drawPath(ground, Color(0xFF5FB04A))
            drawPath(tufts, Color(0xFF3F8F3A))
            for (i in fx.indices) {
                val x = w * fx[i]
                val y = h * fyy[i]
                drawLine(Color(0xFF2F7A32), Offset(x, y), Offset(x, y - 22f * u), 2f * u, StrokeCap.Round)
                drawCircle(Color(heads[i]), 6f * u, Offset(x, y - 24f * u))
                drawCircle(Color(0xFFFFB300), 2.4f * u, Offset(x, y - 24f * u))
            }
        }
    })
}

/* ---- Midnight Roof */

@Composable
internal fun MidnightSky(o: () -> Float) {
    Box(Modifier.bgLayer(0f, o) {
        val br = Brush.verticalGradient(listOf(Color(0xFF0B0F33), Color(0xFF232A6B), Color(0xFF4B4F96)), 0f, size.height)
        art { drawRect(br) }
    })
}

@Composable
internal fun MidnightFar(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxFar, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val moonC = Offset(w * 0.2f, 70f * u)
        val glow = Brush.radialGradient(listOf(Color(0x55FFF3C4), Color.Transparent), moonC, 80f * u)
        val moon = Path()
        val disc = Path().apply { addOval(Rect(moonC, 26f * u)) }
        val bite = Path().apply { addOval(Rect(Offset(moonC.x + 12f * u, moonC.y - 6f * u), 23f * u)) }
        moon.op(disc, bite, PathOperation.Difference)
        val rnd = Random(11)
        val n = 46
        val sx = FloatArray(n)
        val sy = FloatArray(n)
        val sr = FloatArray(n)
        val sa = FloatArray(n)
        for (i in 0 until n) {
            sx[i] = w * rnd.nextFloat()
            sy[i] = h * 0.55f * rnd.nextFloat()
            sr[i] = (0.7f + 1.1f * rnd.nextFloat()) * u
            sa[i] = 0.45f + 0.55f * rnd.nextFloat()
        }
        val baseY = h * 0.6f
        val skyline = Path()
        val windows = Path()
        var x = -4f * u
        while (x < w) {
            val bw = (26f + rnd.nextFloat() * 26f) * u
            val bh = (40f + rnd.nextFloat() * 80f) * u
            skyline.addRect(Rect(x, baseY - bh, x + bw, h))
            var wy = baseY - bh + 8f * u
            while (wy < baseY - 6f * u) {
                var wx = x + 5f * u
                while (wx < x + bw - 6f * u) {
                    if (rnd.nextFloat() < 0.3f) windows.addRect(Rect(wx, wy, wx + 4f * u, wy + 5f * u))
                    wx += 10f * u
                }
                wy += 14f * u
            }
            x += bw + 2f * u
        }
        art {
            drawCircle(glow, 80f * u, moonC)
            for (i in 0 until n) drawCircle(Color(0xFFFFF8E1).copy(alpha = sa[i]), sr[i], Offset(sx[i], sy[i]))
            drawPath(moon, Color(0xFFFFF3C4))
            drawPath(skyline, Color(0xFF1E2559))
            drawPath(windows, Color(0xFFFFE28A).copy(alpha = 0.55f))
        }
    })
}

@Composable
internal fun MidnightMid(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxMid, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val rnd = Random(13)
        val base = h * 0.76f
        val houses = Path()
        val glass = Path()
        var x = -10f * u
        while (x < w) {
            val bw = (60f + rnd.nextFloat() * 30f) * u
            val top = base + rnd.nextFloat() * 24f * u
            val roofH = (22f + rnd.nextFloat() * 12f) * u
            houses.addRect(Rect(x, top, x + bw, h))
            houses.moveTo(x - 4f * u, top)
            houses.lineTo(x + bw / 2f, top - roofH)
            houses.lineTo(x + bw + 4f * u, top)
            houses.close()
            glass.addRect(Rect(x + bw * 0.2f, top + 14f * u, x + bw * 0.2f + 9f * u, top + 24f * u))
            if (rnd.nextFloat() < 0.7f) glass.addRect(Rect(x + bw * 0.6f, top + 14f * u, x + bw * 0.6f + 9f * u, top + 24f * u))
            x += bw + 6f * u
        }
        art {
            drawPath(houses, Color(0xFF141A47))
            drawPath(glass, Color(0xFFFFD166).copy(alpha = 0.85f))
        }
    })
}

@Composable
internal fun MidnightNear(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxNear, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val roof = Path().apply {
            moveTo(0f, h * 0.93f); lineTo(w * 0.6f, h * 0.9f); lineTo(w, h * 0.94f); lineTo(w, h); lineTo(0f, h); close()
        }
        val chimneyX = w * 0.82f
        val chimneyY = h * 0.922f
        val antX = w * 0.12f
        val antY = h * 0.924f
        art {
            drawRect(Color(0xFF1A2050), Offset(chimneyX - 13f * u, chimneyY - 40f * u), Size(26f * u, 6f * u))
            drawRect(Color(0xFF0C1030), Offset(chimneyX - 10f * u, chimneyY - 34f * u), Size(20f * u, 40f * u))
            drawLine(Color(0xFF0C1030), Offset(antX, antY), Offset(antX, antY - 46f * u), 2.5f * u, StrokeCap.Round)
            for (k in 0 until 3) {
                val y = antY - (14f + 12f * k) * u
                val half = (13f - 3f * k) * u
                drawLine(Color(0xFF0C1030), Offset(antX - half, y), Offset(antX + half, y), 2f * u, StrokeCap.Round)
            }
            drawPath(roof, Color(0xFF0C1030))
        }
    })
}

/* ---- Candy Kitchen */

@Composable
internal fun CandySky(o: () -> Float) {
    Box(Modifier.bgLayer(0f, o) {
        val br = Brush.verticalGradient(listOf(Color(0xFFFFD3E4), Color(0xFFFFE9EF), Color(0xFFFFF5E6)), 0f, size.height)
        art { drawRect(br) }
    })
}

@Composable
internal fun CandyFar(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxFar, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val rnd = Random(17)
        val jarColors = longArrayOf(0xFFFFF3B0, 0xFFB8F0E0, 0xFFD9C2FF, 0xFFFFC9A8, 0xFFBFE3FF)
        val shelves = floatArrayOf(0.22f, 0.5f)
        val jx = FloatArray(10)
        for (i in jx.indices) jx[i] = w * ((i % 5) * 0.2f + 0.04f + 0.08f * rnd.nextFloat())
        art {
            for ((si, sy) in shelves.withIndex()) {
                val y = h * sy
                for (k in 0 until 5) {
                    val jw = (26f + 6f * ((k + si) % 3)) * u
                    val jh = (34f + 8f * ((k + si * 2) % 3)) * u
                    val x = jx[si * 5 + k]
                    val c = Color(jarColors[(k + si * 2) % 5])
                    drawRoundRect(c.copy(alpha = 0.9f), Offset(x, y - jh), Size(jw, jh), CornerRadius(7f * u))
                    drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(x + 4f * u, y - jh + 6f * u), Size(3f * u, jh * 0.5f), CornerRadius(1.5f * u))
                    drawRoundRect(Color(0xFFFF8FB8), Offset(x + 3f * u, y - jh - 6f * u), Size(jw - 6f * u, 7f * u), CornerRadius(3f * u))
                }
                drawRect(Color(0xFFF2B8CD), Offset(0f, y), Size(w, 6f * u))
            }
        }
    })
}

@Composable
internal fun CandyMid(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxMid, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val rnd = Random(19)
        val ns = 70
        val ax = FloatArray(ns)
        val ay = FloatArray(ns)
        val bx = FloatArray(ns)
        val bY = FloatArray(ns)
        val sc = longArrayOf(0xFFFF6FA5, 0xFF4FD1C5, 0xFFFFD54F, 0xFFA78BFA, 0xFFFFFFFF)
        for (i in 0 until ns) {
            ax[i] = w * rnd.nextFloat()
            ay[i] = h * 0.75f * rnd.nextFloat()
            val a = rnd.nextFloat() * 6.2831855f
            bx[i] = ax[i] + 6f * u * cos(a)
            bY[i] = ay[i] + 6f * u * sin(a)
        }
        val base = h * 0.8f
        val cx = floatArrayOf(0.1f, 0.3f, 0.55f, 0.78f, 0.94f)
        val cw = floatArrayOf(52f, 60f, 48f, 56f, 46f)
        val wrappers = List(cx.size) { i ->
            val c = w * cx[i]
            val half = cw[i] * u / 2f
            Path().apply {
                moveTo(c - half, base - 20f * u); lineTo(c + half, base - 20f * u)
                lineTo(c + half * 0.75f, base + 14f * u); lineTo(c - half * 0.75f, base + 14f * u); close()
            }
        }
        val frost = longArrayOf(0xFFFF8FB8, 0xFF9AE6D8, 0xFFFFE08A, 0xFFD9C2FF, 0xFFFF8FB8)
        art {
            for (i in 0 until ns) drawLine(Color(sc[i % 5]).copy(alpha = 0.75f), Offset(ax[i], ay[i]), Offset(bx[i], bY[i]), 2.4f * u, StrokeCap.Round)
            for (i in cx.indices) {
                val c = w * cx[i]
                val r = cw[i] * u / 2f
                drawPath(wrappers[i], Color(0xFFFFC9A8))
                drawLine(Color(0xFFE9A98A), Offset(c - r * 0.3f, base - 20f * u), Offset(c - r * 0.22f, base + 14f * u), 2f * u)
                drawLine(Color(0xFFE9A98A), Offset(c + r * 0.3f, base - 20f * u), Offset(c + r * 0.22f, base + 14f * u), 2f * u)
                val f = Color(frost[i])
                drawCircle(f, r * 0.55f, Offset(c - r * 0.5f, base - 24f * u))
                drawCircle(f, r * 0.55f, Offset(c + r * 0.5f, base - 24f * u))
                drawCircle(f, r * 0.7f, Offset(c, base - 32f * u))
                drawCircle(f, r * 0.45f, Offset(c, base - 46f * u))
                drawCircle(Color.White.copy(alpha = 0.45f), r * 0.14f, Offset(c - r * 0.25f, base - 36f * u))
                drawCircle(Color(0xFFE53950), 4.5f * u, Offset(c, base - 54f * u))
            }
        }
    })
}

@Composable
internal fun CandyNear(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxNear, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val y = h * 0.93f
        art {
            drawRect(Color(0xFFFFF8EE), Offset(0f, y), Size(w, h - y))
            drawRect(Color(0xFFFF8FB8), Offset(0f, y), Size(w, 10f * u))
            var x = 0f
            while (x < w + 16f * u) {
                drawCircle(Color(0xFFFF8FB8), 8f * u, Offset(x, y + 10f * u))
                x += 16f * u
            }
            drawRect(Color(0xFFFFD3E4), Offset(0f, y + 26f * u), Size(w, 3f * u))
        }
    })
}

/* ---- Snow Cabin */

@Composable
internal fun SnowSky(o: () -> Float) {
    Box(Modifier.bgLayer(0f, o) {
        val br = Brush.verticalGradient(listOf(Color(0xFFBFE1F7), Color(0xFFE6F3FC), Color(0xFFF4FAFF)), 0f, size.height)
        art { drawRect(br) }
    })
}

@Composable
internal fun SnowFar(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxFar, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val base = h * 0.52f
        val px = floatArrayOf(0.15f, 0.45f, 0.8f)
        val ph = floatArrayOf(90f, 120f, 100f)
        val mount = Path().apply {
            moveTo(0f, h); lineTo(0f, base)
            for (i in px.indices) {
                lineTo(w * px[i] - 80f * u, base)
                lineTo(w * px[i], base - ph[i] * u)
                lineTo(w * px[i] + 80f * u, base)
            }
            lineTo(w, base); lineTo(w, h); close()
        }
        val caps = Path().apply {
            for (i in px.indices) {
                val tipY = base - ph[i] * u
                moveTo(w * px[i], tipY)
                lineTo(w * px[i] - 26f * u, tipY + 34f * u)
                lineTo(w * px[i] + 26f * u, tipY + 34f * u)
                close()
            }
        }
        val pines = Path().apply {
            var x = 8f * u
            while (x < w) {
                moveTo(x - 9f * u, h * 0.58f); lineTo(x, h * 0.58f - 30f * u); lineTo(x + 9f * u, h * 0.58f); close()
                x += 28f * u
            }
        }
        art {
            drawPath(mount, Color(0xFFCFE3F2))
            drawPath(caps, Color.White.copy(alpha = 0.9f))
            drawRect(Color(0xFFDCEBF6), Offset(0f, h * 0.58f), Size(w, h * 0.42f))
            drawPath(pines, Color(0xFFA9C8DE))
        }
    })
}

@Composable
internal fun SnowMid(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxMid, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val hill = ridgePath(w, h, h * 0.73f, 12f * u, 1.6f, 0.4f)
        val rnd = Random(23)
        val pines = Path()
        val caps = Path()
        val trunks = Path()
        for (i in 0 until 9) {
            val cx = w * (i + 0.3f + rnd.nextFloat() * 0.4f) / 9f
            val ph = (70f + rnd.nextFloat() * 40f) * u
            val by = h * 0.76f + rnd.nextFloat() * 14f * u
            val pw = ph * 0.5f
            trunks.addRect(Rect(cx - 3f * u, by - 6f * u, cx + 3f * u, by + 8f * u))
            for (t in 0 until 3) {
                val ty = by - ph * (0.1f + t * 0.28f)
                val tw = pw * (1f - t * 0.28f)
                val tip = ty - ph * 0.42f
                pines.moveTo(cx - tw / 2f, ty); pines.lineTo(cx, tip); pines.lineTo(cx + tw / 2f, ty); pines.close()
                val cy = tip + (ty - tip) * 0.38f
                caps.moveTo(cx, tip); caps.lineTo(cx - tw * 0.19f, cy); caps.lineTo(cx + tw * 0.19f, cy); caps.close()
            }
        }
        art {
            drawPath(hill, Color(0xFFE8F3FA))
            drawPath(trunks, Color(0xFF6B4A3A))
            drawPath(pines, Color(0xFF4F7F9E))
            drawPath(caps, Color.White)
        }
    })
}

@Composable
internal fun SnowNear(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxNear, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val back = ridgePath(w, h, h * 0.935f, 6f * u, 2f, 2f)
        val drift = ridgePath(w, h, h * 0.9f, 10f * u, 1.5f, 0.3f)
        val cx = w * 0.17f
        val cb = h * 0.915f
        val roof = Path().apply {
            moveTo(cx - 52f * u, cb - 40f * u); lineTo(cx, cb - 74f * u); lineTo(cx + 52f * u, cb - 40f * u); close()
        }
        val snowRoof = Path().apply {
            moveTo(cx - 52f * u, cb - 40f * u); lineTo(cx, cb - 74f * u); lineTo(cx + 52f * u, cb - 40f * u)
            lineTo(cx + 44f * u, cb - 44f * u); lineTo(cx, cb - 66f * u); lineTo(cx - 44f * u, cb - 44f * u); close()
        }
        val rnd = Random(29)
        val n = 70
        val fx = FloatArray(n)
        val fy = FloatArray(n)
        val fr = FloatArray(n)
        for (i in 0 until n) {
            fx[i] = w * rnd.nextFloat()
            fy[i] = h * rnd.nextFloat()
            fr[i] = (1f + 1.8f * rnd.nextFloat()) * u
        }
        art {
            drawPath(back, Color(0xFFE3F0F8))
            drawRect(Color(0xFF5A3822), Offset(cx + 22f * u, cb - 70f * u), Size(10f * u, 24f * u))
            drawRect(Color(0xFF8A5A3B), Offset(cx - 44f * u, cb - 40f * u), Size(88f * u, 44f * u))
            drawRect(Color(0xFF5A3822), Offset(cx - 9f * u, cb - 22f * u), Size(18f * u, 26f * u))
            drawRect(Color(0xFFFFD98A), Offset(cx - 36f * u, cb - 30f * u), Size(14f * u, 14f * u))
            drawRect(Color(0xFFFFD98A), Offset(cx + 22f * u, cb - 30f * u), Size(14f * u, 14f * u))
            drawPath(roof, Color(0xFF5A3822))
            drawPath(snowRoof, Color.White)
            drawPath(drift, Color.White)
            for (i in 0 until n) drawCircle(Color.White.copy(alpha = 0.85f), fr[i], Offset(fx[i], fy[i]))
        }
    })
}

/* ---- Berry Dusk */

@Composable
internal fun BerrySky(o: () -> Float) {
    Box(Modifier.bgLayer(0f, o) {
        val br = Brush.verticalGradient(
            listOf(Color(0xFF3A2766), Color(0xFF8E4A9E), Color(0xFFFF9B6A), Color(0xFF9B4F8F)),
            0f, size.height,
        )
        art { drawRect(br) }
    })
}

@Composable
internal fun BerryFar(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxFar, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val rnd = Random(31)
        val n = 22
        val sx = FloatArray(n)
        val sy = FloatArray(n)
        for (i in 0 until n) {
            sx[i] = w * rnd.nextFloat()
            sy[i] = h * 0.3f * rnd.nextFloat()
        }
        val far1 = ridgePath(w, h, h * 0.58f, 24f * u, 1.3f, 0.2f)
        val far2 = ridgePath(w, h, h * 0.64f, 18f * u, 2.1f, 1.7f)
        art {
            for (i in 0 until n) drawCircle(Color.White.copy(alpha = 0.5f), 1.2f * u, Offset(sx[i], sy[i]))
            drawPath(far1, Color(0xFF6A3585))
            drawPath(far2, Color(0xFF55296F))
        }
    })
}

@Composable
internal fun BerryMid(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxMid, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val vines = Path()
        val vbx = FloatArray(16)
        val vby = FloatArray(16)
        var vn = 0
        for ((fx, len) in listOf(0.08f to 150f, 0.3f to 110f, 0.62f to 130f, 0.9f to 170f)) {
            val x0 = w * fx
            val total = len * u
            vines.moveTo(x0, 0f)
            for (s in 1..12) {
                val t = s / 12f
                val x = x0 + 14f * u * sin(t * 7.5f)
                val y = total * t
                vines.lineTo(x, y)
                if (s % 3 == 0 && vn < vbx.size) {
                    vbx[vn] = x
                    vby[vn] = y + 4f * u
                    vn++
                }
            }
        }
        val vineCount = vn
        val rnd = Random(37)
        val base = h * 0.74f
        val bushN = 12
        val bx = FloatArray(bushN)
        val br = FloatArray(bushN)
        for (i in 0 until bushN) {
            bx[i] = w * (i + 0.2f + 0.6f * rnd.nextFloat()) / bushN
            br[i] = (20f + 14f * rnd.nextFloat()) * u
        }
        val berryN = 30
        val ex = FloatArray(berryN)
        val ey = FloatArray(berryN)
        for (i in 0 until berryN) {
            val b = i % bushN
            ex[i] = bx[b] + (rnd.nextFloat() - 0.5f) * br[b] * 1.2f
            ey[i] = base - br[b] * 0.4f + (rnd.nextFloat() - 0.5f) * br[b]
        }
        art {
            drawRect(Color(0xFF4A2370), Offset(0f, base), Size(w, h - base))
            for (i in 0 until bushN) drawCircle(Color(0xFF4A2370), br[i], Offset(bx[i], base - br[i] * 0.4f))
            for (i in 0 until berryN) {
                drawCircle(Color(0xFFFF5C9A), 4f * u, Offset(ex[i], ey[i]))
                drawCircle(Color.White.copy(alpha = 0.7f), 1.1f * u, Offset(ex[i] - 1.2f * u, ey[i] - 1.4f * u))
            }
            drawPath(vines, Color(0xFF3E6B4A), style = Stroke(width = 3f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            for (i in 0 until vineCount) {
                drawCircle(Color(0xFFE0457F), 4.5f * u, Offset(vbx[i], vby[i]))
                drawCircle(Color.White.copy(alpha = 0.7f), 1.2f * u, Offset(vbx[i] - 1.3f * u, vby[i] - 1.5f * u))
            }
        }
    })
}

@Composable
internal fun BerryNear(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxNear, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val ground = ridgePath(w, h, h * 0.93f, 6f * u, 2f, 0.5f)
        val rnd = Random(41)
        val fn = 18
        val fx = FloatArray(fn)
        val fy = FloatArray(fn)
        for (i in 0 until fn) {
            fx[i] = w * rnd.nextFloat()
            fy[i] = h * (0.2f + 0.75f * rnd.nextFloat())
        }
        val corner = floatArrayOf(0.02f, 0.09f, 0.16f, 0.98f, 0.91f, 0.84f)
        val cr = floatArrayOf(26f, 20f, 14f, 28f, 22f, 15f)
        art {
            drawPath(ground, Color(0xFF23103B))
            for (i in corner.indices) {
                val c = Offset(w * corner[i], h * 0.965f)
                drawCircle(Color(0xFF2E1650), cr[i] * u, c)
                drawCircle(Color(0xFFFF5C9A), 3.6f * u, Offset(c.x - cr[i] * 0.3f * u, c.y - cr[i] * 0.3f * u))
                drawCircle(Color(0xFFFF5C9A), 3.6f * u, Offset(c.x + cr[i] * 0.35f * u, c.y + cr[i] * 0.1f * u))
            }
            val fly = Color(0xFFFFF59D)
            for (i in 0 until fn) {
                val p = Offset(fx[i], fy[i])
                drawCircle(fly.copy(alpha = 0.12f), 10f * u, p)
                drawCircle(fly.copy(alpha = 0.28f), 5.5f * u, p)
                drawCircle(fly, 2f * u, p)
            }
        }
    })
}
