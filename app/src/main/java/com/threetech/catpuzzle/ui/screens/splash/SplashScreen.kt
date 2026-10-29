package com.threetech.catpuzzle.ui.screens.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.ui.components.AppWordmark
import com.threetech.catpuzzle.ui.components.CatIcon
import com.threetech.catpuzzle.ui.theme.GameColors

/** Stateless in-app splash: cat mark, wordmark, one tagline, indeterminate loader. No inputs: it only waits. */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.splash_loading)
    Column(
        modifier
            .fillMaxSize()
            .background(GameColors.ScreenBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CatIcon(size = 96.dp)
        AppWordmark(size = 64.dp, modifier = Modifier.padding(top = 16.dp))
        Text(
            text = stringResource(R.string.splash_tagline),
            modifier = Modifier.padding(top = 24.dp),
            color = GameColors.Body,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 3,
        )
        CircularProgressIndicator(
            modifier = Modifier
                .padding(top = 40.dp)
                .size(36.dp)
                .semantics { contentDescription = loading },
            color = GameColors.Primary,
            trackColor = GameColors.Disabled,
            strokeWidth = 4.dp,
        )
    }
}
