package com.threetech.catpuzzle.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.threetech.catpuzzle.audio.AudioController
import com.threetech.catpuzzle.ui.screens.game.SfxKind

/** App-wide audio facade, provided once in MainActivity. Null in previews / tests = silent. */
val LocalAudioController = staticCompositionLocalOf<AudioController?> { null }

/** Wraps [onClick] so the shared UI tap cue plays first (Sound setting + ad gating handled by the controller). */
@Composable
fun rememberTapClick(onClick: () -> Unit): () -> Unit {
    val audio = LocalAudioController.current
    return remember(audio, onClick) {
        {
            audio?.playUiTap()
            onClick()
        }
    }
}

/** Plays an in-game cue through the app-wide controller (null-safe). */
fun AudioController?.playCue(kind: SfxKind) {
    this?.playSfx(kind)
}
