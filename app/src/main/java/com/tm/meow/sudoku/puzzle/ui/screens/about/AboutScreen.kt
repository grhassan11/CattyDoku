package com.tm.meow.sudoku.puzzle.ui.screens.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.ui.components.DownsampledIconImage
import com.tm.meow.sudoku.puzzle.ui.components.IconImage
import com.tm.meow.sudoku.puzzle.ui.components.contentMaxWidth
import com.tm.meow.sudoku.puzzle.ui.components.homeBackground
import com.tm.meow.sudoku.puzzle.ui.theme.GameColors

/** Full-screen About. Stateless: state + lambdas only. Same background and top bar shape as Settings. */
@Composable
fun AboutScreen(
    appName: String,
    versionName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().homeBackground()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            AboutTopBar(onBack)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val minHeight = maxHeight
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .contentMaxWidth()
                    .heightIn(min = minHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val iconDescription = stringResource(R.string.about_app_icon, appName)
                Box(
                    Modifier
                        .size(112.dp)
                        .clip(RoundedCornerShape(48.dp))
                        .background(GameColors.RuleCard)
                        .semantics { contentDescription = iconDescription },
                    contentAlignment = Alignment.Center,
                ) {
                    // Adaptive foreground carries safe-zone padding: scale up so the art fills the rounded tile.
                    // Downsampled decode (~112 dp) instead of the full 1024 px bitmap.
                    DownsampledIconImage(R.drawable.ic_launcher_foreground, modifier = Modifier.scale(1.4f), size = 112.dp)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = appName,
                    color = GameColors.Title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.about_version, versionName),
                    color = GameColors.Body,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.about_copyright),
                    color = GameColors.Body,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
            }
        }
    }
}

@Composable
private fun AboutTopBar(onBack: () -> Unit) {
    val backDescription = stringResource(R.string.settings_back)
    Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp)) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(48.dp)
                .semantics { contentDescription = backDescription },
            shape = CircleShape,
            color = GameColors.Card,
            shadowElevation = 2.dp,
            onClick = onBack,
        ) {
            Box(contentAlignment = Alignment.Center) {
                IconImage(R.drawable.ic_back, size = 24.dp, tint = GameColors.Body)
            }
        }
        Text(
            text = stringResource(R.string.about_title),
            modifier = Modifier.align(Alignment.Center).semantics { heading() },
            color = GameColors.Title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
