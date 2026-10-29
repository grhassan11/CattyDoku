package com.threetech.catpuzzle.audio

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.threetech.catpuzzle.domain.ads.AdSessionTracker
import com.threetech.catpuzzle.domain.repository.SettingsRepository
import com.threetech.catpuzzle.ui.screens.game.SfxKind
import com.threetech.catpuzzle.util.SfxPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * App-wide audio owner (process lifetime, started from the Application like the other lifecycle observers).
 *
 * - Music: follows the Music setting; pauses while the process is backgrounded or a full-screen ad is showing
 *   ([AdSessionTracker.fullScreenAdShowingState]); resumes in place afterwards.
 * - SFX: one shared [SfxPlayer]. Requests are ignored while a full-screen ad is showing; in-flight cues are cut when
 *   an ad starts. In-game cues arrive already filtered by the Sound setting (ViewModels); [playUiTap] gates itself.
 * Banner / native ads do not touch [AdSessionTracker.fullScreenAdShowing], so they never silence anything.
 * Everything runs on the main thread.
 */
@Singleton
class AudioController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val tracker: AdSessionTracker,
) : DefaultLifecycleObserver {

    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val foreground = MutableStateFlow(false)
    private val music = MusicPlayer(context)
    private val sfxLazy = lazy { SfxPlayer(context) }

    @Volatile private var sfxEnabled = true

    /** Call once from `Application.onCreate` (main thread). */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // Music command from (music setting, app foreground, ad showing).
        scope.launch {
            try {
                combine(
                    settings.settings.map { it.music }.distinctUntilChanged(),
                    foreground,
                    tracker.fullScreenAdShowingState,
                ) { musicOn, fg, adShowing -> AudioPolicy.musicCommand(musicOn, fg, adShowing) }
                    .distinctUntilChanged()
                    .collect { command ->
                        when (command) {
                            MusicCommand.PLAY -> music.play()
                            MusicCommand.PAUSE -> music.pause()
                            MusicCommand.STOP -> music.stop()
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                runCatching { music.stop() }
            }
        }

        // Cache the Sound setting for UI taps and warm the SoundPool once sound is on.
        scope.launch {
            try {
                settings.settings.map { it.sfx }.distinctUntilChanged().collect { on ->
                    sfxEnabled = on
                    if (on) runCatching { sfxLazy.value }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // keep last value
            }
        }

        // Cut in-flight SFX the moment a full-screen ad starts.
        scope.launch {
            try {
                tracker.fullScreenAdShowingState.collect { showing ->
                    if (showing && sfxLazy.isInitialized()) runCatching { sfxLazy.value.stopAll() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // ignore
            }
        }
    }

    /** In-game / tutorial cue. Caller has already applied the Sound setting. Ignored while a full-screen ad shows. */
    fun playSfx(kind: SfxKind) {
        if (tracker.fullScreenAdShowing) return
        runCatching { sfxLazy.value.play(kind) }
    }

    /** UI tap. Gated by the Sound setting and by full-screen ads. */
    fun playUiTap() {
        if (!AudioPolicy.sfxAllowed(sfxEnabled, tracker.fullScreenAdShowing)) return
        runCatching { sfxLazy.value.play(SfxKind.UI_TAP) }
    }

    /** Idle attention meow. Gated by the Sound setting and full-screen ads. */
    fun playIdleMeow() {
        if (!AudioPolicy.sfxAllowed(sfxEnabled, tracker.fullScreenAdShowing)) return
        runCatching { sfxLazy.value.play(SfxKind.MEOW) }
    }

    override fun onStart(owner: LifecycleOwner) {
        foreground.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        foreground.value = false
    }
}
