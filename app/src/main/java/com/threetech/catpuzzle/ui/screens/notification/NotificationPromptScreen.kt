package com.threetech.catpuzzle.ui.screens.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.ui.components.ButtonStyle
import com.threetech.catpuzzle.ui.components.IconImage
import com.threetech.catpuzzle.ui.components.PillButton
import com.threetech.catpuzzle.ui.theme.GameColors

/** Stateless friendly pre-prompt shown before the system notification dialog (Android 13+). */
@Composable
fun NotificationPromptScreen(
    onAllow: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        IconImage(R.drawable.ic_bell, size = 96.dp, tint = GameColors.Primary)
        Text(
            text = stringResource(R.string.notif_title),
            modifier = Modifier.padding(top = 24.dp).semantics { heading() },
            color = GameColors.Title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.notif_body),
            modifier = Modifier.padding(top = 12.dp),
            color = GameColors.Body,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
        )
        PillButton(
            text = stringResource(R.string.notif_allow),
            onClick = onAllow,
            modifier = Modifier.padding(top = 32.dp).widthIn(max = 360.dp).fillMaxWidth(),
        )
        PillButton(
            text = stringResource(R.string.notif_not_now),
            onClick = onNotNow,
            modifier = Modifier.padding(top = 12.dp).widthIn(max = 360.dp).fillMaxWidth(),
            style = ButtonStyle.Secondary,
        )
    }
}
