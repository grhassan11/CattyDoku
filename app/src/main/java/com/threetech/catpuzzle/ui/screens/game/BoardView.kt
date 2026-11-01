package com.threetech.catpuzzle.ui.screens.game

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.inset
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
import androidx.compose.ui.unit.dp
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.domain.engine.CatVariants
import com.threetech.catpuzzle.domain.model.Pos
import com.threetech.catpuzzle.ui.components.drawXMark
import com.threetech.catpuzzle.ui.theme.GameColors

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
    val catPainters = rememberBoardCatPainters(board, with(density) { (metrics.cellDp.dp.toPx() * 0.84f).toInt() })

    // Correct-placement effect. `done` starts at the token already present on first composition, so rotation /
    // re-entry / restored state never replays; only a token that arrives later plays. Skipped when animations are off.
    val animationsOn = rememberAnimationsEnabled(null)
    val fx = remember { PlacementFx() }
    var doneToken by remember { mutableIntStateOf(lastCorrectPlacement?.token ?: 0) }
    LaunchedEffect(lastCorrectPlacement?.token) {
        val p = lastCorrectPlacement ?: return@LaunchedEffect
        if (p.token == doneToken) return@LaunchedEffect
        if (animationsOn) fx.run(p.token)
        doneToken = p.token
    }
    val fxToken = if (animationsOn && lastCorrectPlacement != null && lastCorrectPlacement.token != doneToken) lastCorrectPlacement.token else 0
    val fxIdx = if (fxToken != 0 && lastCorrectPlacement != null) lastCorrectPlacement.row * n + lastCorrectPlacement.col else -1

    val colorNames = stringArrayResource(R.array.region_color_names)
    val boardLabel = stringResource(R.string.board_description, n)
    val toggleLabel = stringResource(R.string.cell_action_toggle_x)
    val placeLabel = stringResource(R.string.cell_action_place_cat)
    val removeLabel = stringResource(R.string.cell_action_remove_cat)
    val stateEmpty = stringResource(R.string.cell_state_empty)
    val stateX = stringResource(R.string.cell_state_x)
    val stateCat = stringResource(R.string.cell_state_cat)

    Box(
        modifier
            .size(metrics.cardSideDp(n).dp)
            .background(
                GameColors.Card,
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
                    if (fxToken != 0) drawPlacementFx(fx, fxToken, fxIdx, board, metrics.gapDp.dp.toPx())
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
                                    is BoardAction.Tap -> tap(action.pos)
                                    is BoardAction.DoubleTap -> doubleTap(action.pos)
                                    is BoardAction.Drag -> drag(action.path)
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
                        BoardCell(
                            cell = cell,
                            patternMode = patternMode,
                            previewing = (r * n + c) in preview,
                            sideDp = metrics.cellDp,
                            catPainter = if (cell.mark == CellMarkUi.CAT) catPainters[cell.catVariant.mod(CatVariants.UNIQUE)] else null,
                            placementFx = if (r * n + c == fxIdx) fx else null,
                            placementToken = fxToken,
                            modifier = Modifier.semantics {
                                contentDescription = description
                                role = Role.Button
                                onClick(label = toggleLabel) {
                                    if (enabled) tap(pos)
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
    }
}

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
) {
    Canvas(modifier.size(sideDp.dp)) {
        val s = this.size.minDimension
        val radius = 8.dp.toPx()
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
            CellMarkUi.X -> drawXMark(Offset.Zero, s, GameColors.XMark.copy(alpha = if (previewing) 0.25f else 1f))
            CellMarkUi.CAT -> catPainter?.let { painter ->
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
            CellMarkUi.EMPTY -> if (previewing) drawXMark(Offset.Zero, s, GameColors.XMark.copy(alpha = 0.5f))
        }

        if (cell.conflict) {
            val w = 3.dp.toPx()
            drawRoundRect(
                GameColors.Conflict,
                topLeft = Offset(w / 2f, w / 2f),
                size = Size(s - w, s - w),
                cornerRadius = CornerRadius(radius),
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
                    cornerRadius = CornerRadius(radius),
                    style = Stroke(w),
                )
            }
            HintRole.REASON -> {
                val w = 2.dp.toPx()
                drawRoundRect(
                    GameColors.HintReason,
                    topLeft = Offset(w / 2f, w / 2f),
                    size = Size(s - w, s - w),
                    cornerRadius = CornerRadius(radius),
                    style = Stroke(w),
                )
            }
            HintRole.NONE -> Unit
        }
    }
}
