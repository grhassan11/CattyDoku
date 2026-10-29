package com.threetech.catpuzzle.ui.screens.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.ui.components.DialogScrim
import com.threetech.catpuzzle.ui.components.IconImage
import com.threetech.catpuzzle.ui.components.PillButton
import com.threetech.catpuzzle.ui.screens.settings.SoundAndGameplayGroups
import com.threetech.catpuzzle.ui.theme.GameColors

/**
 * In-game Settings dialog: only "Sound & feel" and "Gameplay" (shared with the full screen), plus the Restart pill.
 * Stateless. Tap outside, the close button and back all call [onDismiss].
 */
@Composable
internal fun GameSettingsDialog(
    settings: SettingsUi,
    onSettingChanged: (SettingToggle, Boolean) -> Unit,
    onRestart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(R.string.settings_title)
    val closeDescription = stringResource(R.string.reward_dialog_close)
    BackHandler(onBack = onDismiss)

    DialogScrim(Modifier.semantics { paneTitle = title }, onClick = onDismiss) {
        val absorb = remember { MutableInteractionSource() }
        Column(
            Modifier
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .background(GameColors.ScreenBackground, RoundedCornerShape(28.dp))
                // Taps inside the card must not reach the scrim (which dismisses).
                .clickable(interactionSource = absorb, indication = null, onClick = {})
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = title,
                    modifier = Modifier.semantics { heading() },
                    color = GameColors.Title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                )
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .size(48.dp)
                        .semantics { contentDescription = closeDescription }
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    IconImage(R.drawable.ic_close, size = 20.dp, tint = GameColors.Body)
                }
            }
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(4.dp))
                SoundAndGameplayGroups(settings, onSettingChanged)
                Spacer(Modifier.height(20.dp))
                PillButton(
                    text = stringResource(R.string.settings_restart),
                    onClick = onRestart,
                    leadingIconRes = R.drawable.ic_reloading,
                    textSize = 18.sp,
                    horizontalPadding = 12.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
