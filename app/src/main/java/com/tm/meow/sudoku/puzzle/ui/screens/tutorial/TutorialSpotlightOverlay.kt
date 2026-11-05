package com.tm.meow.sudoku.puzzle.ui.screens.tutorial

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.domain.tutorial.TutorialTextKey
import com.tm.meow.sudoku.puzzle.ui.components.Confetti
import com.tm.meow.sudoku.puzzle.ui.components.HintBulb
import com.tm.meow.sudoku.puzzle.ui.components.PillButton
import com.tm.meow.sudoku.puzzle.ui.theme.FredokaFamily
import com.tm.meow.sudoku.puzzle.ui.theme.GameColors
import kotlin.math.max
import kotlin.math.roundToInt

private const val RING_INFLATE_DP = 1.5f
private const val SIDE_MARGIN_DP = 20f
private const val BLOCK_GAP_DP = 40f
private const val CTA_WIDTH_DP = 230f
private const val RING_WIDTH_DP = 3.5f
private const val GLOW_WIDTH_DP = 12f

private val RingColor = GameColors.Sunburst
private val HintPillFace = Color(0xFFECECEC)
private val SkipColor = Color(0xFFD8D2CE)

/**
 * Tutorial layer over the board. Dim steps: a screen-wide scrim with one rounded hole per bright cell
 * (cells that hold a cat / X, plus the ringed cells), a glow ring on the ringed cells. Light steps: no scrim.
 * On top of that the instruction card above the board, the bottom block (second card, hint pill, CTA,
 * Skip / Replay) under it, the pointer finger, and confetti on the final step.
 * Touch is not handled here (cell gating lives in [TutorialScreen]); only the buttons and links are clickable.
 *
 * Board geometry comes from [targets] (root-space bounds written on layout) and is read in draw / layout
 * phases only, so layout changes never recompose this.
 */
@Composable
internal fun TutorialSpotlightOverlay(
    state: TutorialUiState,
    targets: TutorialTargets,
    onHintPill: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val d = density.density
    var origin by remember { mutableStateOf(Offset.Zero) }
    val n = state.board.size
    val marked = remember(state.board) {
        state.board.cells.withIndex().filter { it.value.mark != com.tm.meow.sudoku.puzzle.ui.screens.game.CellMarkUi.EMPTY }
            .map { it.index }.toSet()
    }
    val bright = SpotlightLogic.brightCells(state.light, state.spotlight, marked)

    Box(modifier.onGloballyPositioned { origin = it.positionInRoot() }) {
        if (!state.light) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    // Offscreen so BlendMode.Clear punches a real hole instead of painting black.
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            ) {
                drawRect(GameColors.HintDim)
                val geo = targets.board ?: return@Canvas
                val m = geo.metrics
                fun cell(i: Int): Rect = SpotlightLogic.cellRect(
                    boardLeft = geo.bounds.left - origin.x,
                    boardTop = geo.bounds.top - origin.y,
                    pad = m.cardPaddingDp * d,
                    cell = m.cellDp * d,
                    gap = m.gapDp * d,
                    row = i / n,
                    col = i % n,
                )
                val cellCorner = com.tm.meow.sudoku.puzzle.ui.screens.game.BoardLayoutCalc.cellCornerPx(m.cellDp * d, d)
                val corner = CornerRadius(cellCorner)
                // Ring/glow centreline sits RING_INFLATE_DP outside the cell, so radius grows by the same amount (concentric).
                val ringCorner = CornerRadius(cellCorner + RING_INFLATE_DP * d)
                for (i in bright) {
                    val r = cell(i)
                    drawRoundRect(Color.Black, r.topLeft, r.size, corner, blendMode = BlendMode.Clear)
                }
                for (i in state.spotlight) {
                    val r = cell(i).inflate(RING_INFLATE_DP * d)
                    drawRoundRect(
                        RingColor.copy(alpha = 0.28f), r.topLeft, r.size, ringCorner, style = Stroke(GLOW_WIDTH_DP * d),
                    )
                    drawRoundRect(RingColor, r.topLeft, r.size, ringCorner, style = Stroke(RING_WIDTH_DP * d))
                }
            }
        }

        PointerFinger(state, targets, d) { origin }

        ChromeLayout(
            boardBounds = { targets.board?.bounds?.translate(-origin) },
            modifier = Modifier.fillMaxSize(),
            top = { InstructionCard(state) },
            bottom = { BottomBlock(state, onHintPill, onContinue, onSkip, onReplay) },
        )

        if (state.continueIsFinal) Confetti(Modifier.fillMaxSize())
    }
}

// ------------------------------------------------------------------ finger

/** Looping hand over the target cell(s). Lives above the scrim so it stays bright. */
@Composable
private fun PointerFinger(
    state: TutorialUiState,
    targets: TutorialTargets,
    density: Float,
    origin: () -> Offset,
) {
    val pointer = state.pointer ?: return
    val progress = rememberLoopProgress()
    val geo = targets.board ?: return
    val centres = pointer.path.map { cellCenterDp(it, geo.metrics) }
    FingerImage(
        positionDp = {
            val k = PointerMotion.travel(pointer.kind, progress.value)
            val o = origin()
            val p = SpotlightLogic.along(centres, k)
            Offset(
                (geo.bounds.left - o.x) / density + p.x,
                (geo.bounds.top - o.y) / density + p.y,
            )
        },
        scale = { PointerMotion.pressScale(pointer.kind, progress.value) },
    )
}

// ------------------------------------------------------------------ layout

/**
 * Two children: the instruction card ends [BLOCK_GAP_DP] above the board, the bottom block starts
 * [BLOCK_GAP_DP] under it, both kept inside the system bars. Placement is pure: [SpotlightLogic].
 */
@Composable
private fun ChromeLayout(
    boardBounds: () -> Rect?,
    modifier: Modifier = Modifier,
    top: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.getTop(density)
    val bottomInset = WindowInsets.navigationBars.getBottom(density)
    Layout(
        content = {
            Box { top() }
            Box { bottom() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val d = density.density
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val areaTop = topInset + 8f * d
        val areaBottom = h - bottomInset - 8f * d
        val gap = BLOCK_GAP_DP * d
        val side = (SIDE_MARGIN_DP * d).roundToInt()
        val maxW = (w - 2 * side).coerceAtLeast(0)
        val maxH = max(0f, areaBottom - areaTop).roundToInt()
        val c = Constraints(minWidth = 0, maxWidth = maxW, minHeight = 0, maxHeight = maxH)
        val pTop = measurables[0].measure(c)
        val pBottom = measurables[1].measure(c)
        val b = boardBounds()
        val topY = SpotlightLogic.cardTop(areaTop, b?.top, pTop.height.toFloat(), gap)
        val bottomY = SpotlightLogic.bottomTop(areaTop, areaBottom, b?.bottom, pBottom.height.toFloat(), gap)
        layout(w, h) {
            pTop.place((w - pTop.width) / 2, topY.roundToInt())
            pBottom.place((w - pBottom.width) / 2, bottomY.roundToInt())
        }
    }
}

// ------------------------------------------------------------------ cards

@Composable
private fun InstructionCard(state: TutorialUiState) {
    val raw = stringResource(state.text.res())
    val isFinal = state.text == TutorialTextKey.EXCELLENT
    val accent = GameColors.Coral
    val tint = state.tintRegion?.let { GameColors.region(it) } ?: accent
    val bodyColor = if (isFinal) GameColors.Title else GameColors.Body
    val annotated = remember(raw, tint) {
        buildAnnotatedString {
            for (seg in AccentText.split(raw)) {
                when {
                    seg.accent -> withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) { append(seg.text) }
                    seg.tint -> withStyle(SpanStyle(color = tint, fontWeight = FontWeight.Bold)) { append(seg.text) }
                    else -> append(seg.text)
                }
            }
        }
    }
    val subtitle = state.text.subtitleRes()?.let { stringResource(it) }
    val paneTitleText = stringResource(R.string.tutorial_pane_title)
    val shape = RoundedCornerShape(28.dp)

    val shake = remember { Animatable(0f) }
    LaunchedEffect(state.rejectCount) {
        if (state.rejectCount > 0) {
            for (v in floatArrayOf(-10f, 10f, -6f, 6f, 0f)) shake.animateTo(v, tween(45))
        }
    }
    val d = LocalDensity.current.density

    Column(
        Modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .graphicsLayer { translationX = shake.value * d }
            .shadow(6.dp, shape)
            .background(GameColors.Card, shape)
            .padding(horizontal = 20.dp, vertical = 22.dp)
            .semantics(mergeDescendants = true) {
                paneTitle = paneTitleText
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = annotated,
            color = bodyColor,
            fontFamily = FredokaFamily,
            fontSize = 21.sp,
            lineHeight = 29.sp,
            fontWeight = if (isFinal) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = subtitle,
                color = GameColors.Body.copy(alpha = 0.8f),
                fontFamily = FredokaFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BottomBlock(
    state: TutorialUiState,
    onHintPill: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onReplay: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.bottom?.let { BottomCard(stringResource(it.res())) }
        if (state.hintPill) HintPill(onHintPill)
        if (state.showContinue) {
            PillButton(
                text = stringResource(if (state.continueIsFinal) R.string.tutorial_start_game else R.string.tutorial_got_it),
                onClick = onContinue,
                modifier = Modifier.width(CTA_WIDTH_DP.dp),
            )
            if (state.continueIsFinal) {
                TextLink(stringResource(R.string.tutorial_replay), GameColors.Body.copy(alpha = 0.85f), onReplay)
            }
        }
        if (state.showSkip) TextLink(stringResource(R.string.tutorial_skip), SkipColor, onSkip)
    }
}

@Composable
private fun BottomCard(text: String) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, shape)
            .background(GameColors.Card, shape)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = GameColors.Body,
            fontFamily = FredokaFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

/** Grey raised pill: tapping it places the last cat. */
@Composable
private fun HintPill(onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp, shape)
            .background(HintPillFace, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.tutorial_hint_pill),
            color = GameColors.Body,
            fontFamily = FredokaFamily,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(12.dp))
        HintBulb(size = 32.dp)
    }
}

/** Underlined text link, 48dp touch target. */
@Composable
private fun TextLink(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color,
            fontFamily = FredokaFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textDecoration = TextDecoration.Underline,
        )
    }
}
