package com.tm.meow.sudoku.puzzle.audio

import com.tm.meow.sudoku.puzzle.di.IoDispatcher
import com.tm.meow.sudoku.puzzle.di.MainDispatcher
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.repository.SettingsRepository
import com.tm.meow.sudoku.puzzle.ui.screens.game.SfxKind
import com.tm.meow.sudoku.puzzle.util.SfxPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import com.tm.meow.sudoku.puzzle.domain.crash.CrashReporter
import com.tm.meow.sudoku.puzzle.domain.crash.NoopCrashReporter
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App-wide audio owner (process lifetime, started from the Application like the other lifecycle observers).
 *
 * - Music: follows the Music setting; pauses while the process is backgrounded or a full-screen ad is showing
 *   ([AdSessionTracker.fullScreenAdShowingState]); resumes in place afterwards.
 * - SFX: one shared [SfxPlayer]. Requests are ignored while a full-screen ad is showing; in-flight cues are cut when
 *   an ad starts. In-game cues arrive already filtered by the Sound setting (ViewModels); [playUiTap] gates itself.
 * - Audio focus (music only): requested (transient, may duck) when music starts playing, released when it pauses / stops.
 *   Focus loss pauses (or ducks) the music, regain resumes it. No request while the Music setting is off. SFX never request focus.
 * Native ads do not touch [AdSessionTracker.fullScreenAdShowing], so they never silence anything.
 * Everything runs on the main thread.
 */
@Singleton
class AudioController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val tracker: AdSessionTracker,
    @MainDispatcher main: CoroutineDispatcher,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val crash: CrashReporter = NoopCrashReporter,
) : DefaultLifecycleObserver {

    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + main)
    private val foreground = MutableStateFlow(false)
    private val music = MusicPlayer(context)
    private val sfxLazy = lazy { SfxPlayer(context) }

    @Volatile private var sfxEnabled = true

    private val audioManager: AudioManager? = context.getSystemService(AudioManager::class.java)
    private var focusRequest: AudioFocusRequest? = null

    /** Latest music command is PLAY (focus callbacks may resume only then). */
    private var wantPlay = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> if (wantPlay) {
                music.setDucked(false)
                music.play()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> music.setDucked(true)
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> music.pause()
        }
    }

    /** True = may play now. Delayed grant plays from [focusListener] on AUDIOFOCUS_GAIN. Fails open when audio service is unavailable. */
    private fun requestMusicFocus(): Boolean {
        val am = audioManager ?: return true
        return try {
            val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusListener, Handler(Looper.getMainLooper()))
                .build()
                .also { focusRequest = it }
            am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (t: Throwable) {
            crash.recordNonFatal(t, "audio.focusRequest")
            true
        }
    }

    private fun abandonMusicFocus() {
        val request = focusRequest ?: return
        focusRequest = null
        runCatching { audioManager?.abandonAudioFocusRequest(request) }
        music.setDucked(false)
    }

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
                            MusicCommand.PLAY -> {
                                wantPlay = true
                                if (requestMusicFocus()) music.play()
                            }
                            MusicCommand.PAUSE -> {
                                wantPlay = false
                                music.pause()
                                abandonMusicFocus()
                            }
                            MusicCommand.STOP -> {
                                wantPlay = false
                                music.stop()
                                abandonMusicFocus()
                            }
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                crash.recordNonFatal(t, "audio.music")
                runCatching { music.stop() }
            }
        }

        // Cache the Sound setting for UI taps and warm the SoundPool once sound is on.
        scope.launch {
            try {
                settings.settings.map { it.sfx }.distinctUntilChanged().collect { on ->
                    sfxEnabled = on
                    // Build the SoundPool (opens ~17 raw fds) off the main thread; lazy is thread-safe.
                    if (on) withContext(io) { runCatching { sfxLazy.value } }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                crash.recordNonFatal(t, "audio.sfxSetting")
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
                crash.recordNonFatal(t, "audio.adSilence")
                // ignore
            }
        }
    }

    /** In-game / tutorial cue. Caller has already applied the Sound setting. Ignored while a full-screen ad shows. */
    fun playSfx(kind: SfxKind, rate: Float = 1f) {
        if (tracker.fullScreenAdShowing) return
        runCatching { sfxLazy.value.play(kind, rate) }
    }

    /** Cut every in-flight SFX (win jingle, 7.6 s award cue, ...). Music untouched. No-op if SFX never initialised. */
    fun stopSfx() {
        if (sfxLazy.isInitialized()) runCatching { sfxLazy.value.stopAll() }
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
