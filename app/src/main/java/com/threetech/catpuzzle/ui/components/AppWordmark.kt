package com.threetech.catpuzzle.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.ui.theme.FredokaFamily
import com.threetech.catpuzzle.ui.theme.GameColors

/** Index of the "O" in "DOKU": gets the cat ears. */
private const val EAR_LETTER_INDEX = 1

/** Index of the last "U" in "DOKU": gets the tail curl. */
private const val TAIL_LETTER_INDEX = 3

/** Approximate Fredoka cap height as a fraction of the font size (accent placement only). */
private const val CAP_HEIGHT = 0.70f

/**
 * Original two-line wordmark: "CATTY" over "DOKU", Fredoka Bold. Own touches: orange cat ears on the
 * "O" and a brown tail curl under the "U", both drawn in Canvas behind the glyphs.
 *
 * [size] is the font size in dp and ignores the system font scale on purpose (a logo must not reflow).
 * Reusable on splash / other screens. Announced as one heading with the app name.
 */
@Composable
fun AppWordmark(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    color: Color = GameColors.Wordmark,
    accent: Color = GameColors.Primary,
) {
    val appName = stringResource(R.string.app_name)
    val fontSize = with(LocalDensity.current) { size.toSp() }
    val style = TextStyle(
        fontFamily = FredokaFamily,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        lineHeight = fontSize,
        color = color,
        textAlign = TextAlign.Center,
    )
    var line2 by remember { mutableStateOf<TextLayoutResult?>(null) }

    Column(
        modifier.clearAndSetSemantics {
            heading()
            contentDescription = appName
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(size * 0.08f),
    ) {
        Text(
            text = stringResource(R.string.home_wordmark_line1),
            style = style,
            maxLines = 1,
            softWrap = false,
        )
        // room under the baseline for the tail
        Box(Modifier.padding(bottom = size * 0.3f)) {
            Text(
                text = stringResource(R.string.home_wordmark_line2),
                style = style,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { line2 = it },
                modifier = Modifier.drawWithContent {
                    line2?.let { drawAccents(it, fontSize.toPx(), color, accent) }
                    drawContent()
                },
            )
        }
    }
}

private fun DrawScope.drawAccents(layout: TextLayoutResult, fs: Float, brown: Color, orange: Color) {
    val text = layout.layoutInput.text.text
    val baseline = layout.getLineBaseline(0)
    val capTop = baseline - CAP_HEIGHT * fs

    if (text.length > EAR_LETTER_INDEX) {
        val box = layout.getBoundingBox(EAR_LETTER_INDEX)
        val cx = (box.left + box.right) / 2f
        val w = box.width
        ear(Offset(cx - 0.42f * w, capTop + 0.14f * fs), Offset(cx - 0.06f * w, capTop + 0.04f * fs), Offset(cx - 0.30f * w, capTop - 0.20f * fs), orange, fs)
        ear(Offset(cx + 0.42f * w, capTop + 0.14f * fs), Offset(cx + 0.06f * w, capTop + 0.04f * fs), Offset(cx + 0.30f * w, capTop - 0.20f * fs), orange, fs)
    }
    if (text.length > TAIL_LETTER_INDEX) {
        val box = layout.getBoundingBox(TAIL_LETTER_INDEX)
        val cx = (box.left + box.right) / 2f
        val tail = Path().apply {
            moveTo(cx, baseline - 0.05f * fs)
            cubicTo(cx, baseline + 0.30f * fs, cx + 0.42f * fs, baseline + 0.34f * fs, cx + 0.45f * fs, baseline + 0.12f * fs)
            cubicTo(cx + 0.47f * fs, baseline - 0.02f * fs, cx + 0.30f * fs, baseline - 0.02f * fs, cx + 0.31f * fs, baseline + 0.09f * fs)
        }
        drawPath(tail, brown, style = Stroke(width = 0.085f * fs, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Filled triangle with rounded tips (fill + same-colour round-join stroke). */
private fun DrawScope.ear(baseA: Offset, baseB: Offset, apex: Offset, color: Color, fs: Float) {
    val p = Path().apply {
        moveTo(baseA.x, baseA.y)
        lineTo(apex.x, apex.y)
        lineTo(baseB.x, baseB.y)
        close()
    }
    drawPath(p, color)
    drawPath(p, color, style = Stroke(width = 0.05f * fs, join = StrokeJoin.Round))
}
