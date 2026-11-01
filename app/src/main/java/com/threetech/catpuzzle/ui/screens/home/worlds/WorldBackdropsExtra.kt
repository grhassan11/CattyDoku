package com.threetech.catpuzzle.ui.screens.home.worlds

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.threetech.catpuzzle.ui.components.homePatternDrawer
import com.threetech.catpuzzle.ui.theme.GameColors
import kotlin.random.Random

/*
 * Backdrops for the newer worlds: Cozy Parlor (world 1 = the HOME screen look), Sandy Shore, Autumn Alley,
 * Misty Forest, Golden Library. Same 4-layer structure and helpers as WorldBackdrops.kt.
 * Plus the [WorldBackground] holders for the five gallery worlds (declared here to keep the theme table in one place).
 */

private fun Path.addConifer(cx: Float, baseY: Float, height: Float, halfW: Float, tiers: Int) {
    val th = height * 0.45f
    val step = (height - th) / maxOf(1, tiers - 1)
    for (t in 0 until tiers) {
        val topY = baseY - height + t * step
        val hw = halfW * (0.5f + 0.5f * (t + 1f) / tiers)
        moveTo(cx, topY)
        lineTo(cx + hw, topY + th)
        lineTo(cx - hw, topY + th)
        close()
    }
}

/* ---- Cozy Parlor (world 1): the HOME background as a world. Cream + faint board pattern. */

@Composable
private fun CozySky(o: () -> Float) {
    Box(Modifier.bgLayer(0f, o) { art { drawRect(GameColors.ScreenBackground) } })
}

@Composable
private fun CozyPattern(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxFar, o) {
        val draw = homePatternDrawer(GameColors.Wordmark, size.width, size.height)
        art { draw(this) }
    })
}

@Composable
private fun CozyRug(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxNear, o) {
        val w = size.width
        val h = size.height
        val floor = ridgePath(w, h, h * 0.95f, 5f * 1.dp.toPx(), 2f, 0.5f)
        art { drawPath(floor, GameColors.CtaCreamLip.copy(alpha = 0.45f)) }
    })
}

/* ---- Sandy Shore */

@Composable
private fun ShoreSky(o: () -> Float) {
    Box(Modifier.bgLayer(0f, o) {
        val br = Brush.verticalGradient(listOf(Color(0xFF8FD8F0), Color(0xFFD9F3F5), Color(0xFFFFF1CC)), 0f, size.height)
        art { drawRect(br) }
    })
}

@Composable
private fun ShoreFar(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxFar, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val sun = Offset(w * 0.22f, 64f * u)
        val glow = Brush.radialGradient(listOf(Color(0x80FFE9A0), Color.Transparent), sun, 100f * u)
        val sea = ridgePath(w, h, h * 0.52f, 5f * u, 7f, 0f)
        art {
            drawCircle(glow, 100f * u, sun)
            drawCircle(Color(0xFFFFD54F), 28f * u, sun)
            val cloud = Color.White.copy(alpha = 0.85f)
            drawCloud(w * 0.5f, 50f * u, 28f * u, cloud)
            drawCloud(w * 0.82f, 100f * u, 32f * u, cloud)
            drawCloud(w * 0.08f, h * 0.36f, 22f * u, cloud)
            drawPath(sea, Color(0xFF63CFE0))
        }
    })
}

@Composable
private fun ShoreMid(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxMid, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val dunes = ridgePath(w, h, h * 0.68f, 16f * u, 2f, 1f)
        val palmX = floatArrayOf(0.14f, 0.52f, 0.88f)
        art {
            drawPath(dunes, Color(0xFFF3DFA2))
            for (px in palmX) {
                val bx = w * px
                val by = h * 0.72f
                val top = Offset(bx + 8f * u, by - 70f * u)
                drawLine(Color(0xFF8D6E4C), Offset(bx, by), top, 5f * u, StrokeCap.Round)
                for (k in 0 until 5) {
                    rotate(-80f + k * 40f, top) {
                        drawOval(Color(0xFF3FA96B), Offset(top.x - 3f * u, top.y - 30f * u), Size(6f * u, 30f * u))
                    }
                }
            }
        }
    })
}

@Composable
private fun ShoreNear(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxNear, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val sand = ridgePath(w, h, h * 0.9f, 8f * u, 2.5f, 2f)
        val sx = floatArrayOf(0.08f, 0.27f, 0.46f, 0.64f, 0.8f, 0.95f)
        val sy = floatArrayOf(0.96f, 0.975f, 0.95f, 0.98f, 0.96f, 0.97f)
        art {
            drawPath(sand, Color(0xFFEBCF80))
            for (i in sx.indices) {
                val c = Offset(w * sx[i], h * sy[i])
                if (i % 2 == 0) {
                    drawCircle(Color(0xFFFF9FB5), 6f * u, c)
                    drawCircle(Color(0xFFFFD1DB), 2.5f * u, c)
                } else {
                    drawOval(Color(0xFFFFF3E0), Offset(c.x - 7f * u, c.y - 4f * u), Size(14f * u, 8f * u))
                }
            }
        }
    })
}

/* ---- Autumn Alley */

@Composable
private fun AutumnSky(o: () -> Float) {
    Box(Modifier.bgLayer(0f, o) {
        val br = Brush.verticalGradient(listOf(Color(0xFFFFD9A8), Color(0xFFFFEBCF), Color(0xFFFFF6E5)), 0f, size.height)
        art { drawRect(br) }
    })
}

@Composable
private fun AutumnFar(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxFar, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val sun = Offset(w * 0.7f, 76f * u)
        val glow = Brush.radialGradient(listOf(Color(0x80FFB74D), Color.Transparent), sun, 110f * u)
        val hills = ridgePath(w, h, h * 0.52f, 16f * u, 1.6f, 0.4f)
        art {
            drawCircle(glow, 110f * u, sun)
            drawCircle(Color(0xFFFFB74D), 28f * u, sun)
            val cloud = Color.White.copy(alpha = 0.6f)
            drawCloud(w * 0.18f, 70f * u, 30f * u, cloud)
            drawCloud(w * 0.45f, 120f * u, 24f * u, cloud)
            drawPath(hills, Color(0xFFE8B37B))
        }
    })
}

@Composable
private fun AutumnMid(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxMid, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val hills = ridgePath(w, h, h * 0.7f, 14f * u, 2f, 2f)
        val crowns = longArrayOf(0xFFE8672C, 0xFFD9A12B, 0xFFC8452B)
        val n = 9
        art {
            drawPath(hills, Color(0xFFD99A5E))
            for (i in 0 until n) {
                val x = w * (i + 0.5f) / n
                val by = h * 0.78f
                drawRect(Color(0xFF6B4226), Offset(x - 3f * u, by - 40f * u), Size(6f * u, 44f * u))
                val c = Color(crowns[i % 3])
                drawCircle(c, 22f * u, Offset(x, by - 58f * u))
                drawCircle(c, 15f * u, Offset(x - 16f * u, by - 46f * u))
                drawCircle(c, 15f * u, Offset(x + 16f * u, by - 46f * u))
            }
        }
    })
}

@Composable
private fun AutumnNear(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxNear, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val ground = ridgePath(w, h, h * 0.9f, 8f * u, 2.5f, 1f)
        val rnd = Random(21)
        val n = 26
        val lx = FloatArray(n) { w * rnd.nextFloat() }
        val ly = FloatArray(n) { h * (0.92f + 0.07f * rnd.nextFloat()) }
        val lc = longArrayOf(0xFFE8672C, 0xFFD9A12B, 0xFFC8452B, 0xFFF0B84A)
        art {
            drawPath(ground, Color(0xFFB86B3A))
            for (i in 0 until n) drawOval(Color(lc[i % 4]), Offset(lx[i], ly[i]), Size(9f * u, 5f * u))
        }
    })
}

/* ---- Misty Forest */

@Composable
private fun MistSky(o: () -> Float) {
    Box(Modifier.bgLayer(0f, o) {
        val br = Brush.verticalGradient(listOf(Color(0xFFC9DDD5), Color(0xFFE3EEE8), Color(0xFFF2F6F1)), 0f, size.height)
        art { drawRect(br) }
    })
}

@Composable
private fun MistFar(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxFar, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val ridge = ridgePath(w, h, h * 0.58f, 12f * u, 1.8f, 0.8f)
        val trees = Path().apply {
            val n = 14
            for (i in 0 until n) addConifer(w * (i + 0.5f) / n, h * 0.62f, (70f + 18f * (i % 3)) * u, 18f * u, 3)
        }
        val mist = Brush.verticalGradient(listOf(Color.Transparent, Color(0x66FFFFFF), Color.Transparent), h * 0.45f, h * 0.75f)
        art {
            drawPath(ridge, Color(0xFFA9C7B8))
            drawPath(trees, Color(0xFF93B8A6))
            drawRect(mist, Offset(0f, h * 0.45f), Size(w, h * 0.3f))
        }
    })
}

@Composable
private fun MistMid(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxMid, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val ridge = ridgePath(w, h, h * 0.76f, 10f * u, 2f, 2f)
        val trees = Path().apply {
            val n = 9
            for (i in 0 until n) addConifer(w * (i + 0.3f + 0.4f * (i % 2)) / n, h * 0.8f, (110f + 24f * (i % 3)) * u, 28f * u, 4)
        }
        art {
            drawPath(ridge, Color(0xFF6FA085))
            drawPath(trees, Color(0xFF5E8F78))
        }
    })
}

@Composable
private fun MistNear(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxNear, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val ground = ridgePath(w, h, h * 0.9f, 8f * u, 2.5f, 0.5f)
        val mx = floatArrayOf(0.12f, 0.38f, 0.66f, 0.9f)
        art {
            drawPath(ground, Color(0xFF3F6B55))
            for (fx in mx) {
                val x = w * fx
                val y = h * 0.955f
                drawRect(Color(0xFFF3E6D4), Offset(x - 3f * u, y - 10f * u), Size(6f * u, 12f * u))
                drawArc(Color(0xFFE05A4F), 180f, 180f, true, Offset(x - 11f * u, y - 22f * u), Size(22f * u, 20f * u))
                drawCircle(Color.White, 2f * u, Offset(x - 4f * u, y - 14f * u))
                drawCircle(Color.White, 1.6f * u, Offset(x + 4f * u, y - 16f * u))
            }
        }
    })
}

/* ---- Golden Library */

@Composable
private fun LibrarySky(o: () -> Float) {
    Box(Modifier.bgLayer(0f, o) {
        val br = Brush.verticalGradient(listOf(Color(0xFFFFE9B8), Color(0xFFFFF1D1), Color(0xFFFFF8E6)), 0f, size.height)
        art { drawRect(br) }
    })
}

@Composable
private fun LibraryFar(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxFar, o) {
        val w = size.width
        val u = 1.dp.toPx()
        val xs = floatArrayOf(0.14f, 0.34f, 0.56f, 0.78f, 0.94f)
        art {
            for (fx in xs) {
                val cx = w * fx
                val top = 24f * u
                val half = 30f * u
                val bodyH = 96f * u
                drawRect(Color(0xFFC9983F), Offset(cx - half - 4f * u, top + half - 4f * u), Size(2f * (half + 4f * u), bodyH + 4f * u))
                drawCircle(Color(0xFFC9983F), half + 4f * u, Offset(cx, top + half))
                drawRect(Color(0xFFFFF3C2), Offset(cx - half, top + half), Size(2f * half, bodyH))
                drawCircle(Color(0xFFFFF3C2), half, Offset(cx, top + half))
                drawLine(Color(0xFFC9983F), Offset(cx, top), Offset(cx, top + half + bodyH), 2f * u)
                drawLine(Color(0xFFC9983F), Offset(cx - half, top + half + 36f * u), Offset(cx + half, top + half + 36f * u), 2f * u)
            }
        }
    })
}

@Composable
private fun LibraryMid(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxMid, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val rnd = Random(31)
        val shelfY = floatArrayOf(h * 0.58f, h * 0.76f)
        val bx = ArrayList<Float>()
        val by = ArrayList<Float>()
        val bw = ArrayList<Float>()
        val bh = ArrayList<Float>()
        val bc = ArrayList<Int>()
        for (sy in shelfY) {
            var x = 0f
            while (x < w) {
                val bookW = (7f + rnd.nextFloat() * 7f) * u
                val bookH = (22f + rnd.nextFloat() * 14f) * u
                if (rnd.nextFloat() < 0.88f) {
                    bx.add(x); by.add(sy - bookH); bw.add(bookW); bh.add(bookH); bc.add(rnd.nextInt(5))
                }
                x += bookW + 1f * u
            }
        }
        val palette = longArrayOf(0xFFB5523B, 0xFF3F7F6B, 0xFFD9A441, 0xFF4A5FA0, 0xFF8E5A9B)
        art {
            drawRect(Color(0xFFB37B45), Offset(0f, h * 0.5f), Size(w, h * 0.5f))
            for (y in shelfY) drawRect(Color(0xFF8A5A2B), Offset(0f, y), Size(w, 5f * u))
            for (i in bx.indices) drawRect(Color(palette[bc[i]]), Offset(bx[i], by[i]), Size(bw[i], bh[i]))
        }
    })
}

@Composable
private fun LibraryNear(o: () -> Float) {
    Box(Modifier.bgLayer(ParallaxNear, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val floor = ridgePath(w, h, h * 0.92f, 4f * u, 3f, 0f)
        val sx = floatArrayOf(0.2f, 0.58f, 0.86f)
        art {
            drawPath(floor, Color(0xFF7A4B2A))
            for (fx in sx) {
                val x = w * fx
                val y = h * 0.965f
                drawRect(Color(0xFFD9A441), Offset(x - 18f * u, y - 7f * u), Size(36f * u, 7f * u))
                drawRect(Color(0xFFB5523B), Offset(x - 15f * u, y - 14f * u), Size(30f * u, 7f * u))
                drawRect(Color(0xFF3F7F6B), Offset(x - 12f * u, y - 21f * u), Size(24f * u, 7f * u))
            }
        }
    })
}

/* ---- background holders (new worlds) */

internal val CozyBackground = WorldBackground(
    listOf(
        BackdropLayer("sky", 0f) { CozySky(it) },
        BackdropLayer("pattern", ParallaxFar) { CozyPattern(it) },
        BackdropLayer("rug", ParallaxNear) { CozyRug(it) },
    ),
    scrim = Color.Transparent, halo = Color.White.copy(alpha = 0.6f),
)
internal val ShoreBackground = WorldBackground(
    listOf(
        BackdropLayer("sky", 0f) { ShoreSky(it) },
        BackdropLayer("far", ParallaxFar) { ShoreFar(it) },
        BackdropLayer("mid", ParallaxMid) { ShoreMid(it) },
        BackdropLayer("near", ParallaxNear) { ShoreNear(it) },
    ),
    scrim = Color.White.copy(alpha = 0.10f), halo = Color.White.copy(alpha = 0.45f),
)
internal val AutumnBackground = WorldBackground(
    listOf(
        BackdropLayer("sky", 0f) { AutumnSky(it) },
        BackdropLayer("far", ParallaxFar) { AutumnFar(it) },
        BackdropLayer("mid", ParallaxMid) { AutumnMid(it) },
        BackdropLayer("near", ParallaxNear) { AutumnNear(it) },
    ),
    scrim = Color.White.copy(alpha = 0.08f), halo = Color.White.copy(alpha = 0.40f),
)
internal val MistBackground = WorldBackground(
    listOf(
        BackdropLayer("sky", 0f) { MistSky(it) },
        BackdropLayer("far", ParallaxFar) { MistFar(it) },
        BackdropLayer("mid", ParallaxMid) { MistMid(it) },
        BackdropLayer("near", ParallaxNear) { MistNear(it) },
    ),
    scrim = Color.White.copy(alpha = 0.08f), halo = Color.White.copy(alpha = 0.40f),
)
internal val LibraryBackground = WorldBackground(
    listOf(
        BackdropLayer("sky", 0f) { LibrarySky(it) },
        BackdropLayer("far", ParallaxFar) { LibraryFar(it) },
        BackdropLayer("mid", ParallaxMid) { LibraryMid(it) },
        BackdropLayer("near", ParallaxNear) { LibraryNear(it) },
    ),
    scrim = Color.White.copy(alpha = 0.12f), halo = Color.White.copy(alpha = 0.50f),
)

/* ---- background holders (gallery worlds, promoted) */

internal val SunnyBackground = WorldBackground(
    listOf(
        BackdropLayer("sky", 0f) { SunnySky(it) },
        BackdropLayer("far", ParallaxFar) { SunnyFar(it) },
        BackdropLayer("mid", ParallaxMid) { SunnyMid(it) },
        BackdropLayer("near", ParallaxNear) { SunnyNear(it) },
    ),
    scrim = Color.White.copy(alpha = 0.10f), halo = Color.White.copy(alpha = 0.35f),
)
internal val MidnightBackground = WorldBackground(
    listOf(
        BackdropLayer("sky", 0f) { MidnightSky(it) },
        BackdropLayer("far", ParallaxFar) { MidnightFar(it) },
        BackdropLayer("mid", ParallaxMid) { MidnightMid(it) },
        BackdropLayer("near", ParallaxNear) { MidnightNear(it) },
    ),
    scrim = Color.Transparent, halo = Color(0xFF8E98D8).copy(alpha = 0.28f),
)
internal val CandyBackground = WorldBackground(
    listOf(
        BackdropLayer("sky", 0f) { CandySky(it) },
        BackdropLayer("far", ParallaxFar) { CandyFar(it) },
        BackdropLayer("mid", ParallaxMid) { CandyMid(it) },
        BackdropLayer("near", ParallaxNear) { CandyNear(it) },
    ),
    scrim = Color.White.copy(alpha = 0.12f), halo = Color.White.copy(alpha = 0.40f),
)
internal val SnowBackground = WorldBackground(
    listOf(
        BackdropLayer("sky", 0f) { SnowSky(it) },
        BackdropLayer("far", ParallaxFar) { SnowFar(it) },
        BackdropLayer("mid", ParallaxMid) { SnowMid(it) },
        BackdropLayer("near", ParallaxNear) { SnowNear(it) },
    ),
    scrim = Color.Transparent, halo = Color(0xFF7FB1D1).copy(alpha = 0.35f),
)
internal val BerryBackground = WorldBackground(
    listOf(
        BackdropLayer("sky", 0f) { BerrySky(it) },
        BackdropLayer("far", ParallaxFar) { BerryFar(it) },
        BackdropLayer("mid", ParallaxMid) { BerryMid(it) },
        BackdropLayer("near", ParallaxNear) { BerryNear(it) },
    ),
    scrim = Color.Black.copy(alpha = 0.08f), halo = Color(0xFF2A1445).copy(alpha = 0.35f),
)
