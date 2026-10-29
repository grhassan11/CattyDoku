package com.threetech.catpuzzle.ui.screens.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.ui.components.AppWordmark
import com.threetech.catpuzzle.ui.components.PillButton
import com.threetech.catpuzzle.ui.theme.GameColors

/** Link colour (reference uses green underlined links); readable on the dialog body. */
private val LinkGreen = Color(0xFF2F7D3C)

/**
 * Stateless Welcome / Terms screen: cream background, wordmark, dialog-style card.
 * No decline path by design: back leaves the app, Accept continues.
 * Says nothing about ads or consent (that is its own step later).
 */
@Composable
fun WelcomeScreen(
    onAccept: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(GameColors.ScreenBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppWordmark(size = 56.dp)
            Column(
                Modifier
                    .padding(top = 32.dp)
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFFFFFBF7))
                    .border(1.dp, GameColors.CtaCreamLip, RoundedCornerShape(28.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.welcome_title),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8E9DF))
                        .padding(vertical = 12.dp)
                        .semantics { heading() },
                    color = GameColors.Title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.welcome_body),
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp),
                    color = GameColors.Body,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                )
                LinkRow(stringResource(R.string.welcome_terms), onOpenTerms)
                LinkRow(stringResource(R.string.welcome_privacy), onOpenPrivacy)
                PillButton(
                    text = stringResource(R.string.welcome_accept),
                    onClick = onAccept,
                    modifier = Modifier
                        .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp)
                        .fillMaxWidth(),
                )
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        color = LinkGreen,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        textDecoration = TextDecoration.Underline,
        textAlign = TextAlign.Center,
    )
}
