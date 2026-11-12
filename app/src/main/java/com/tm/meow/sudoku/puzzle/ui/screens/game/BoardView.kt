package com.tm.meow.sudoku.puzzle.ui.screens.game

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.tm.meow.sudoku.puzzle.ui.components.RegionBurst
import com.tm.meow.sudoku.puzzle.ui.components.rememberLowRamDevice
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.abs
import kotlin.math.max
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.domain.engine.CatVariants
import com.tm.meow.sudoku.puzzle.domain.model.PieceSkin
import com.tm.meow.sudoku.puzzle.domain.model.Pos
import com.tm.meow.sudoku.puzzle.ui.components.drawXMark
import com.tm.meow.sudoku.puzzle.ui.theme.GameColors

/**
 * Board card + cell grid. Stateless apart from the transient swipe preview (pure UI state).
 * Gestures go through [BoardGestureResolver]; this composable only translates pointer events.
 */
@Composable
fun BoardView(
    board: BoardUi,
    patternMode: Boolean,
    metrics: BoardMetrics,
    enabled: Boolean,
    onTap: (Pos) -> Unit,
    onDoubleTap: (Pos) -> Unit,
    onDrag: (List<Pos>) -> Unit,
    modifier: Modifier = Modifier,
    onDragCross: () -> Unit = {},
    /** Latest correct placement; each new token plays the placement effect once. Null (tutorial) = no effect. */
    lastCorrectPlacement: CorrectPlacementUi? = null,
    /** Cats (default) or, on a boss level, one animal set: each placed piece shows a fixed pseudo-random animal of it. */
    pieceSkin: PieceSkin = PieceSkin.cats(),
    /** Identity of the level being shown (level number / daily day). A new non-null key plays the intro wave once. Null = no wave. */
    introKey: Long? = null,
    /** Transient wrong-move cue (red flash + broken heart; red cross for a wrong cat). Each new token plays once. Null (tutorial) = none. */
    wrongMove: WrongMoveUi? = null,
    /** Latest region completed by a clean placement (tint wave + confetti burst). Each new token plays once. Null (tutorial) = none. */
    regionCleared: RegionClearedUi? = null,
) {
    val n = board.size
    val gridSideDp = metrics.gridSideDp(n)
    val density = LocalDensity.current
    val sidePx = with(density) { gridSideDp.dp.toPx() }

    val currentBoard by rememberUpdatedState(board)
    val tap by rememberUpdatedState(onTap)
    val doubleTap by rememberUpdatedState(onDoubleTap)
    val drag by rememberUpdatedState(onDrag)
    val dragCross by rememberUpdatedState(onDragCross)
    val resolver = remember(n) { BoardGestureResolver() }
    var preview by remember { mutableStateOf(emptySet<Int>()) }

    // Hoisted once: painters for only the cat variants this board uses, decoded at ~cell size (not 10 full 512 px PNGs).
    val noCats = remember(n) { BoardUi(n, emptyList()) }
    val catPainters = rememberBoardCatPainters(
        if (pieceSkin.isAnimal) noCats else board, // animal skin: no cat PNG decoding
        with(density) { (metrics.cellDp.dp.toPx() * 0.84f).toInt() },
    )
    // Boss levels: animal painters + one animal per region (same source as the RegionTab icon, so they always match).
    val regionAnimal = remember(pieceSkin, n) { IntArray(n) { pieceSkin.regionIndex(it) } }
    val animalPainters = pieceSkin.set?.let {
        rememberAnimalPainters(it, regionAnimal, with(density) { (metrics.cellDp.dp.toPx() * 0.84f).toInt() })
    }

    // Correct-placement effect. `done` starts at the token already present on first composition, so rotation /
    // re-entry / restored state never replays; only a token that arrives later plays. Skipped when animations are off.
    val animationsOn = rememberAnimationsEnabled(null)
    val fx = remember { PlacementFx() }
    val lowRam = rememberLowRamDevice()
    var doneToken by remember { mutableIntStateOf(lastCorrectPlacement?.token ?: 0) }
    LaunchedEffect(lastCorrectPlacement?.token) {
        val p = lastCorrectPlacement ?: return@LaunchedEffect
        if (p.token == doneToken) return@LaunchedEffect
        if (animationsOn) fx.run(p.token)
        doneToken = p.token
    }
    // Region-clear effects (tint wave + burst): same token pattern. `clearDone` starts at the token already present.
    val clearFx = remember { RegionClearFx() }
    var clearDone by remember { mutableIntStateOf(regionCleared?.token ?: 0) }
    LaunchedEffect(regionCleared?.token) {
        val rc = regionCleared ?: return@LaunchedEffect
        if (rc.token == clearDone) return@LaunchedEffect
        if (animationsOn) clearFx.run(rc.token)
        clearDone = rc.token
    }
    val clearToken = if (animationsOn && regionCleared != null && regionCleared.token != clearDone) regionCleared.token else 0
    // "+N" popup text, measured once per value (never per frame).
    val textMeasurer = rememberTextMeasurer()
    // Popup grows (1.0 -> 1.4) and shifts colour (gold -> amber -> orange -> pink) with the combo level.
    val plusStreak = lastCorrectPlacement?.streak ?: 0
    val plusStyle = remember(density, metrics.cellDp, plusStreak) {
        TextStyle(
            color = Color(ComboStyle.colorArgb(plusStreak)),
            fontSize = with(density) { (metrics.cellDp.dp * 0.42f * ComboStyle.scale(plusStreak)).toSp() },
            fontWeight = FontWeight.Black,
            shadow = Shadow(Color(0xCC000000), Offset(2f, 2f), 4f),
        )
    }
    val plusPoints = lastCorrectPlacement?.points ?: 0
    val plusLayout = remember(plusPoints, plusStyle, textMeasurer) {
        if (plusPoints > 0) textMeasurer.measure("+$plusPoints", plusStyle) else null
    }

    // New-level intro wave: corner-diagonal sweep, per-cell scale + alpha read in the layer phase (no per-frame recomposition).
    // Saveable per key so rotation / resume / undo / revive never replay it; only a changed key (new level) does.
    val intro = rememberBoardIntro(introKey, animationsOn)

    // Cross jiggle: only crosses that newly appear within a short window after a player board gesture (tap / double-tap / drag,
    // incl. crosses auto-added by that move). Undo / restore / rotation / resume / intro never arm it. `prevMarks` is seeded from
    // the first composed board, so a loaded or restored game never jiggles. Clock read in draw of jiggling cells only.
    val jiggle = remember { CrossJiggle() }
    val armedUntil = remember { longArrayOf(0L) }
    val prevMarks = remember(n) { Array(board.cells.size) { board.cells[it].mark } }
    var jiggles by remember(n) { mutableStateOf<Map<Int, Long>>(emptyMap()) }
    LaunchedEffect(board) {
        val now = CrossJiggle.nowMs()
        val armed = animationsOn && !intro.running && now <= armedUntil[0] && prevMarks.size == board.cells.size
        var next: MutableMap<Int, Long>? = null
        if (armed) {
            for (i in board.cells.indices) {
                if (board.cells[i].mark == CellMarkUi.X && prevMarks[i] != CellMarkUi.X) {
                    val m = next ?: jiggles.filterValues { now - it < CrossJiggle.DURATION_MS }.toMutableMap().also { next = it }
                    if (m.size < CrossJiggle.MAX_CONCURRENT) m[i] = now
                }
            }
        }
        for (i in board.cells.indices) prevMarks[i] = board.cells[i].mark
        next?.let { jiggles = it }
    }
    LaunchedEffect(jiggles) {
        if (jiggles.isEmpty()) return@LaunchedEffect
        val end = (jiggles.values.maxOrNull() ?: return@LaunchedEffect) + CrossJiggle.DURATION_MS
        while (CrossJiggle.nowMs() < end) withFrameNanos { jiggle.tick() }
        jiggles = emptyMap()
    }

    // Wrong-move cue: `wrongDone` starts at the token already present, so rotation / re-entry never replays. Skipped when animations are off.
    val wrongFx = remember { WrongMoveFx() }
    var wrongDone by remember { mutableIntStateOf(wrongMove?.token ?: 0) }
    LaunchedEffect(wrongMove?.token) {
        val w = wrongMove ?: return@LaunchedEffect
        if (w.token == wrongDone) return@LaunchedEffect
        if (animationsOn) wrongFx.run(w.token)
        wrongDone = w.token
    }
    val wrongActive = animationsOn && wrongMove != null && wrongMove.token != wrongDone
    val heartPainter = painterResource(R.drawable.ic_heart_broken)

    val fxToken = if (animationsOn && lastCorrectPlacement != null && lastCorrectPlacement.token != doneToken) lastCorrectPlacement.token else 0
    val fxIdx = if (fxToken != 0 && lastCorrectPlacement != null) lastCorrectPlacement.row * n + lastCorrectPlacement.col else -1
    val fxRow = if (fxIdx >= 0) fxIdx / n else -1
    val fxCol = if (fxIdx >= 0) fxIdx % n else -1

    val colorNames = stringArrayResource(R.array.region_color_names)
    val boardLabel = stringResource(R.string.board_description, n)
    val toggleLabel = stringResource(R.string.cell_action_toggle_x)
    val placeLabel = stringResource(PieceWording.cellPlace(pieceSkin.word()))
    val removeLabel = stringResource(PieceWording.cellRemove(pieceSkin.word()))
    val stateEmpty = stringResource(R.string.cell_state_empty)
    val stateX = stringResource(R.string.cell_state_x)
    val stateCat = stringResource(PieceWording.cellState(pieceSkin.word()))

    Box(
        modifier
            .size(metrics.cardSideDp(n).dp)
            .background(
                LocalGameSurface.current.boardCard,
                RoundedCornerShape(
                    topStart = BoardLayoutCalc.cardTopCornerDp(n).dp,
                    topEnd = BoardLayoutCalc.cardTopCornerDp(n).dp,
                    bottomEnd = BoardLayoutCalc.cardCornerDp(n).dp,
                    bottomStart = BoardLayoutCalc.cardCornerDp(n).dp,
                ),
            )
            .padding(metrics.cardPaddingDp.dp),
    ) {
        Column(
            Modifier
                .size(gridSideDp.dp)
                .semantics { contentDescription = boardLabel }
                .drawWithContent {
                    drawContent()
                    // Above all cells, not clipped by neighbours. Draw-phase reads only.
                    if (fxToken != 0) drawPlacementFx(fx, fxToken, fxIdx, board, metrics.gapDp.dp.toPx(), plusLayout, halfSparkles = lowRam)
                    if (clearToken != 0 && regionCleared != null) {
                        drawRegionWave(clearFx.time(clearToken), regionCleared, board, metrics.gapDp.dp.toPx())
                    }
                }
                .pointerInput(n, sidePx, enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = BoardLayoutCalc.cellAt(down.position.x, down.position.y, sidePx, n)
                            ?: return@awaitEachGesture
                        resolver.begin(start, down.uptimeMillis)
                        val intent = BoardGestureResolver.dragIntentFor(
                            currentBoard.cell(start.row, start.col).mark,
                        )
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                resolver.cancel()
                                break
                            }
                            if (!change.pressed) {
                                when (val action = resolver.end(change.uptimeMillis)) {
                                    is BoardAction.Tap -> { armedUntil[0] = CrossJiggle.nowMs() + ARM_WINDOW_MS; tap(action.pos) }
                                    is BoardAction.DoubleTap -> { armedUntil[0] = CrossJiggle.nowMs() + ARM_WINDOW_MS; doubleTap(action.pos) }
                                    is BoardAction.Drag -> { armedUntil[0] = CrossJiggle.nowMs() + ARM_WINDOW_MS; drag(action.path) }
                                    null -> Unit
                                }
                                break
                            }
                            val cell = BoardLayoutCalc.cellAt(change.position.x, change.position.y, sidePx, n)
                            if (cell != null) {
                                val path = resolver.move(cell)
                                if (path != null) {
                                    val next = BoardGestureResolver.previewIndices(currentBoard, path, intent)
                                    // One sfx + haptic per cell newly marked X as the finger enters it (MARK only).
                                    if (intent == DragIntent.MARK) {
                                        for (i in next) if (i !in preview) dragCross()
                                    }
                                    preview = next
                                    change.consume()
                                }
                            }
                        }
                        preview = emptySet()
                    }
                },
            verticalArrangement = Arrangement.spacedBy(metrics.gapDp.dp),
        ) {
            for (r in 0 until n) {
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.gapDp.dp)) {
                    for (c in 0 until n) {
                        val cell = board.cell(r, c)
                        val stateText = when (cell.mark) {
                            CellMarkUi.EMPTY -> stateEmpty
                            CellMarkUi.X -> stateX
                            CellMarkUi.CAT -> stateCat
                        }
                        val description = stringResource(
                            R.string.cell_description,
                            r + 1,
                            c + 1,
                            colorNames[cell.region.mod(colorNames.size)],
                            if (cell.conflict) stringResource(R.string.cell_state_conflict, stateText) else stateText,
                        )
                        val pos = Pos(r, c)
                        // X marks on the placed cat's row / column or touching it pop in a wave radiating outward (draw-phase delay).
                        val waveDist = if (fxIdx >= 0 && cell.mark == CellMarkUi.X) {
                            val dr = abs(r - fxRow)
                            val dc = abs(c - fxCol)
                            if ((dr == 0) != (dc == 0) || (dr <= 1 && dc <= 1 && dr + dc > 0)) max(dr, dc) else 0
                        } else {
                            0
                        }
                        val wrongOwn = wrongActive && wrongMove != null && (r * n + c) in wrongMove.cells
                        val wrongPartner = wrongActive && wrongMove != null && (r * n + c) in wrongMove.partners
                        val wrongHere = wrongOwn || wrongPartner
                        BoardCell(
                            cell = cell,
                            patternMode = patternMode,
                            previewing = (r * n + c) in preview,
                            sideDp = metrics.cellDp,
                            catPainter = when {
                                cell.mark != CellMarkUi.CAT -> null
                                animalPainters != null -> animalPainters[regionAnimal[cell.region.mod(regionAnimal.size)].mod(animalPainters.size)]
                                    ?: catPainters[cell.catVariant.mod(CatVariants.UNIQUE)]
                                else -> catPainters[cell.catVariant.mod(CatVariants.UNIQUE)]
                            },
                            placementFx = if (r * n + c == fxIdx) fx else null,
                            placementToken = fxToken,
                            waveFx = if (waveDist > 0) fx else null,
                            waveToken = fxToken,
                            waveDist = waveDist,
                            jiggle = jiggle,
                            jiggleStartMs = jiggles[r * n + c] ?: -1L,
                            wrongFx = if (wrongHere) wrongFx else null,
                            wrongToken = if (wrongHere && wrongMove != null) wrongMove.token else 0,
                            heartPainter = if (wrongHere) heartPainter else null,
                            wrongPartner = wrongPartner,
                            modifier = intro.layer(r, c, n).semantics {
                                contentDescription = description
                                role = Role.Button
                                onClick(label = toggleLabel) {
                                    if (enabled) {
                                        armedUntil[0] = CrossJiggle.nowMs() + ARM_WINDOW_MS
                                        tap(pos)
                                    }
                                    enabled
                                }
                                customActions = buildList {
                                    if (cell.mark == CellMarkUi.CAT) {
                                        if (!cell.locked) {
                                            add(CustomAccessibilityAction(removeLabel) { doubleTap(pos); true })
                                        }
                                    } else {
                                        add(CustomAccessibilityAction(placeLabel) { doubleTap(pos); true })
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        // Finite confetti over the cleared region: <= 36 pieces, one Canvas, clock read in draw only.
        if (clearToken != 0 && regionCleared != null) {
            val cellPx = with(density) { metrics.cellDp.dp.toPx() }
            val gapPx = with(density) { metrics.gapDp.dp.toPx() }
            val origins = remember(clearToken) { regionCenters(board, regionCleared.region, cellPx, gapPx) }
            val burstColors = remember(regionCleared.region) {
                val c = GameColors.region(regionCleared.region)
                listOf(c, lerp(c, Color.White, 0.5f), Color(0xFFFFD54F), Color.White)
            }
            RegionBurst(
                token = clearToken,
                progress = { clearFx.time(clearToken) },
                origins = origins,
                cellPx = cellPx,
                colors = burstColors,
                modifier = Modifier.size(gridSideDp.dp),
            )
        }
    }
}

/** Pixel centres (x, y pairs) of every cell of [region], in the grid's own coordinate space. */
private fun regionCenters(board: BoardUi, region: Int, cellPx: Float, gapPx: Float): FloatArray {
    val pitch = cellPx + gapPx
    val out = ArrayList<Float>()
    for (c in board.cells) {
        if (c.region == region) {
            out.add(c.col * pitch + cellPx / 2f)
            out.add(c.row * pitch + cellPx / 2f)
        }
    }
    return out.toFloatArray()
}

private const val ARM_WINDOW_MS = 500L

@Composable
private fun BoardCell(
    cell: CellUi,
    patternMode: Boolean,
    previewing: Boolean,
    sideDp: Float,
    catPainter: Painter?,
    modifier: Modifier = Modifier,
    /** Non-null only for the just-placed cell while its effect runs: scales the cat (pop + squish). */
    placementFx: PlacementFx? = null,
    placementToken: Int = 0,
    /** Cross jiggle clock + this cell's start (-1 = none). Clock is read in draw only, and only while jiggling. */
    jiggle: CrossJiggle? = null,
    jiggleStartMs: Long = -1L,
    /** Wrong-move cue: non-null only for an affected cell while its effect runs. Time read in draw only. */
    wrongFx: WrongMoveFx? = null,
    wrongToken: Int = 0,
    heartPainter: Painter? = null,
    /** This cell holds a cat touching the wrong move: it shakes and pulses red instead of drawing the wrong cross. */
    wrongPartner: Boolean = false,
    /** X-mark wave pop (row / column / neighbours of the just-placed cat): non-null only for an affected X while the effect runs. */
    waveFx: PlacementFx? = null,
    waveToken: Int = 0,
    waveDist: Int = 0,
) {
    Canvas(modifier.size(sideDp.dp)) {
        val s = this.size.minDimension
        val radius = BoardLayoutCalc.cellCornerPx(s, 1.dp.toPx())
        val base = GameColors.region(cell.region)
        drawRoundRect(base, size = Size(s, s), cornerRadius = CornerRadius(radius))

        if (patternMode) {
            val clip = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, s, s, CornerRadius(radius)))
            }
            clipPath(clip) {
                drawRegionPattern(cell.region, Offset.Zero, s, Color.Black.copy(alpha = 0.22f))
            }
        }

        if (cell.hintRole == HintRole.REASON) {
            drawRoundRect(Color.White.copy(alpha = 0.35f), size = Size(s, s), cornerRadius = CornerRadius(radius))
        }

        when (cell.mark) {
            // Wrong-CAT fx draws this cell's X itself (red -> normal), so skip the plain X while it runs.
            CellMarkUi.X -> if (!(wrongFx != null && wrongToken != 0 && wrongFx.time(wrongToken) < 1f)) {
                val xColor = (if (cell.wrongX) WrongX else GameColors.XMark).copy(alpha = if (previewing) 0.25f else 1f)
                val elapsed = if (jiggle != null && jiggleStartMs >= 0L) jiggle.clock - jiggleStartMs else Long.MAX_VALUE
                if (jiggle == null || elapsed >= CrossJiggle.DURATION_MS) {
                    val wave = if (waveFx != null && waveToken != 0 && waveDist > 0) xWaveScale(waveFx.time(waveToken), waveDist) else 1f
                    if (wave != 1f) {
                        scale(wave, pivot = Offset(s / 2f, s / 2f)) { drawXMark(Offset.Zero, s, xColor) }
                    } else {
                        drawXMark(Offset.Zero, s, xColor)
                    }
                } else {
                    val pivot = Offset(s / 2f, s / 2f)
                    rotate(jiggle.angle(elapsed), pivot) {
                        scale(jiggle.scale(elapsed), pivot = pivot) { drawXMark(Offset.Zero, s, xColor) }
                    }
                }
            }
            CellMarkUi.CAT -> catPainter?.let { painter ->
              val shakeDx = if (wrongPartner && wrongFx != null && wrongToken != 0) {
                  val wt = wrongFx.time(wrongToken)
                  if (wt < 1f) wrongPartnerShake(wt, s) else 0f
              } else {
                  0f
              }
              translate(left = shakeDx) {
                if (placementFx == null) {
                    inset(s * 0.08f) {
                        val iconSize = this.size
                        with(painter) { draw(iconSize) }
                    }
                } else {
                    // Pop + squish about the cell centre: 0.6 -> ~1.12 -> 1.0, x/y squash coupled to the overshoot.
                    val sc = placementFx.popScale(placementToken)
                    val k = PlacementFx.SQUASH * (sc - 1f)
                    scale(sc * (1f - k), sc * (1f + k), pivot = Offset(s / 2f, s / 2f)) {
                        inset(s * 0.08f) {
                            val iconSize = this.size
                            with(painter) { draw(iconSize) }
                        }
                    }
                }
              }
            }
            CellMarkUi.EMPTY -> if (previewing) drawXMark(Offset.Zero, s, GameColors.XMark.copy(alpha = 0.5f))
        }

        // Wrong-move cue layered over marks, UNDER the existing conflict (red outline) + hint strokes below, which stay untouched.
        if (wrongFx != null && wrongToken != 0) {
            val wt = wrongFx.time(wrongToken)
            if (wt < 1f) {
                clipRect(0f, 0f, s, s) { drawWrongMove(wt, heartPainter, s, radius, wrongPartner) }
            }
        }

        if (cell.conflict) {
            val w = 3.dp.toPx()
            drawRoundRect(
                GameColors.Conflict,
                topLeft = Offset(w / 2f, w / 2f),
                size = Size(s - w, s - w),
                cornerRadius = CornerRadius((radius - w / 2f).coerceAtLeast(0f)),
                style = Stroke(w),
            )
        }
        when (cell.hintRole) {
            HintRole.TARGET -> {
                val w = 4.dp.toPx()
                drawRoundRect(
                    GameColors.HintTarget,
                    topLeft = Offset(w / 2f, w / 2f),
                    size = Size(s - w, s - w),
                    cornerRadius = CornerRadius((radius - w / 2f).coerceAtLeast(0f)),
                    style = Stroke(w),
                )
            }
            HintRole.REASON -> {
                val w = 2.dp.toPx()
                drawRoundRect(
                    GameColors.HintReason,
                    topLeft = Offset(w / 2f, w / 2f),
                    size = Size(s - w, s - w),
                    cornerRadius = CornerRadius((radius - w / 2f).coerceAtLeast(0f)),
                    style = Stroke(w),
                )
            }
            HintRole.NONE -> Unit
        }
    }
}
