package com.threetech.catpuzzle.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.ui.components.IconImage
import com.threetech.catpuzzle.ui.components.MicPlaceholder
import com.threetech.catpuzzle.ui.components.MusicNotePlaceholder
import com.threetech.catpuzzle.ui.components.LocalAudioController
import com.threetech.catpuzzle.ui.screens.game.SettingToggle
import com.threetech.catpuzzle.ui.screens.game.SettingsUi
import com.threetech.catpuzzle.ui.theme.GameColors

private val CardShape = RoundedCornerShape(20.dp)

/**
 * Full-screen Settings. Stateless: state + lambdas only. Same background and top bar shape as the Shop; no ads here.
 * [onPrivacyOptions] is non-null only when UMP requires a privacy options entry point.
 */
@Composable
fun SettingsScreen(
    settings: SettingsUi,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSettingChanged: (SettingToggle, Boolean) -> Unit,
    onFeedback: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    versionName: String,
    modifier: Modifier = Modifier,
    onPrivacyOptions: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().background(GameColors.ScreenBackground)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            SettingsTopBar(onBack)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SoundAndGameplayGroups(settings, onSettingChanged)

                Group(stringResource(R.string.settings_group_notifications)) {
                    ToggleRow(
                        label = stringResource(R.string.settings_streak_reminders),
                        hint = stringResource(R.string.settings_streak_reminders_hint),
                        checked = settings.streakReminders,
                        onCheckedChange = { onSettingChanged(SettingToggle.STREAK_REMINDERS, it) },
                    ) { IconImage(R.drawable.ic_bell, size = 28.dp, tint = GameColors.Body) }
                    RowDivider()
                    ToggleRow(
                        label = stringResource(R.string.settings_daily_reminders),
                        hint = stringResource(R.string.settings_daily_reminders_hint),
                        checked = settings.dailyReminders,
                        onCheckedChange = { onSettingChanged(SettingToggle.DAILY_REMINDERS, it) },
                    ) { IconImage(R.drawable.ic_bell, size = 28.dp, tint = GameColors.Body) }
                }

                Group(stringResource(R.string.settings_group_support)) {
                    LinkRow(stringResource(R.string.settings_feedback), onFeedback)
                    if (onPrivacyOptions != null) {
                        RowDivider()
                        LinkRow(stringResource(R.string.settings_privacy), onPrivacyOptions)
                    }
                    RowDivider()
                    LinkRow(stringResource(R.string.shop_footer_privacy), onPrivacyPolicy)
                    RowDivider()
                    InfoRow(stringResource(R.string.settings_version), versionName)
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(16.dp),
        )
    }
}

/**
 * "Sound & feel" + "Gameplay" groups. Shared by the full Settings screen and the in-game settings dialog so both look
 * and behave the same.
 */
@Composable
internal fun SoundAndGameplayGroups(
    settings: SettingsUi,
    onSettingChanged: (SettingToggle, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Group(stringResource(R.string.settings_group_sound)) {
                ToggleRow(
                    label = stringResource(R.string.settings_music),
                    checked = settings.music,
                    onCheckedChange = { onSettingChanged(SettingToggle.MUSIC, it) },
                ) { MusicNotePlaceholder(color = GameColors.Body, size = 28.dp) }
                RowDivider()
                ToggleRow(
                    label = stringResource(R.string.settings_sfx),
                    checked = settings.sfx,
                    onCheckedChange = { onSettingChanged(SettingToggle.SFX, it) },
                ) { IconImage(R.drawable.ic_speaker, size = 28.dp, tint = GameColors.Body) }
                RowDivider()
                ToggleRow(
                    label = stringResource(R.string.settings_voice),
                    checked = settings.voice,
                    onCheckedChange = { onSettingChanged(SettingToggle.VOICE, it) },
                ) { MicPlaceholder(color = GameColors.Body, size = 28.dp) }
                RowDivider()
                ToggleRow(
                    label = stringResource(R.string.settings_haptics),
                    checked = settings.haptics,
                    onCheckedChange = { onSettingChanged(SettingToggle.HAPTICS, it) },
                ) { IconImage(R.drawable.ic_vibrate, size = 28.dp, tint = GameColors.Body) }
            }

            Group(stringResource(R.string.settings_group_gameplay)) {
                ToggleRow(
                    label = stringResource(R.string.settings_pattern),
                    hint = stringResource(R.string.settings_pattern_hint),
                    checked = settings.patternMode,
                    onCheckedChange = { onSettingChanged(SettingToggle.PATTERN_MODE, it) },
                ) { IconImage(R.drawable.ic_pattern_mode, size = 28.dp, tint = GameColors.Body) }
            }
    }
}

@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
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
            text = stringResource(R.string.settings_title),
            modifier = Modifier.align(Alignment.Center).semantics { heading() },
            color = GameColors.Title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            modifier = Modifier.semantics { heading() },
            color = GameColors.Body,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp,
        )
        Spacer(Modifier.height(8.dp))
        Surface(shape = CardShape, color = GameColors.Card, shadowElevation = 1.dp) {
            Column { content() }
        }
    }
}

@Composable
private fun RowDivider() = HorizontalDivider(color = GameColors.RuleCard)

/** The whole row is the toggle target (Role.Switch); the Switch is visual only. */
@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    hint: String? = null,
    icon: @Composable () -> Unit,
) {
    val stateText = stringResource(if (checked) R.string.settings_on else R.string.settings_off)
    val audio = LocalAudioController.current
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = {
                // Cue plays before the setting flips: turning Sound OFF clicks once, turning it ON clicks after the flow updates.
                audio?.playUiTap()
                onCheckedChange(it)
            })
            .semantics { stateDescription = stateText }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { icon() }
        Column(Modifier.weight(1f)) {
            Text(label, color = GameColors.Title, fontSize = 18.sp, fontWeight = FontWeight.Normal)
            if (hint != null) {
                Text(hint, color = GameColors.Body, fontSize = 13.sp, lineHeight = 17.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = GameColors.DailyPink,
                checkedThumbColor = Color.White,
                checkedBorderColor = GameColors.DailyPink,
                uncheckedTrackColor = GameColors.ToggleOff,
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = GameColors.ToggleOff,
            ),
        )
    }
}

/** Non-interactive label + right-aligned value. */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = GameColors.Title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Normal,
        )
        Text(
            text = value,
            color = GameColors.Body,
            fontSize = 18.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = GameColors.Title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}
