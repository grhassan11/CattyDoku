package com.threetech.catpuzzle.ui.screens.home.worlds

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.ui.theme.FredokaFamily

private val TailDiameter = 72.dp
private val TailGap = 4.dp

/**
 * End of the trail: not a level, just a "more worlds coming" teaser (dashed seal with a paw + label). Not tappable.
 * Place it so the circle centre sits on the trail: offset by -[TailRadius] from the trail y. The label hangs below.
 */
@Composable
internal fun TailNode(theme: WorldTheme, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.world_tail_title)
    val description = stringResource(R.string.world_tail_description)
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(TailDiameter)) {
            val lipPx = 4.dp.toPx()
            val r = this.size.minDimension / 2f - lipPx / 2f
            val pal = theme.locked
            drawCircle(pal.lip, r, Offset(center.x, center.y + lipPx))
            drawCircle(Brush.verticalGradient(listOf(pal.top, pal.bottom), startY = 0f, endY = this.size.height), r, center)
            drawCircle(
                Color.White.copy(alpha = 0.8f), r * 0.78f, center,
                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
            )
            drawGlyph(WorldGlyph.Paw, Offset(center.x, center.y + r * 0.05f), r * 0.55f, Color.White.copy(alpha = 0.85f))
        }
        Spacer(Modifier.height(TailGap))
        Text(
            label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FredokaFamily,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier
                .width(106.dp)
                .background(theme.starPlate, RoundedCornerShape(12.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp)
                .clearAndSetSemantics { },
        )
    }
}

/** Layout footprint above the tail circle's centre / below it, for vertical placement. */
internal val TailRadius: Dp = TailDiameter / 2
