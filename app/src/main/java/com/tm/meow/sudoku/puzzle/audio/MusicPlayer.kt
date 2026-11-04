package com.tm.meow.sudoku.puzzle.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.tm.meow.sudoku.puzzle.R

/**
 * Endless background music: track 1, track 2, track 1 ... (each file plays once, then the next starts).
 * Main-thread only. Every MediaPlayer call is wrapped; any failure just drops the player (silent), never throws.
 * [pause] keeps position, [stop] releases the player (next [play] starts the current track from the top).
 */
class MusicPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val tracks = intArrayOf(R.raw.music_track_1, R.raw.music_track_2)
    private var player: MediaPlayer? = null
    /** True once [player] finished async preparing; start/pause/isPlaying are only legal then. */
    private var prepared = false
    /** Latest play/pause intent, applied when an in-flight prepare completes. */
    private var wantPlaying = false
    private var index = 0

    fun play() {
        wantPlaying = true
        val p = player
        if (p == null) {
            startTrack()
            return
        }
        if (!prepared) return // onPrepared starts it
        runCatching { if (!p.isPlaying) p.start() }.onFailure { startTrack() }
    }

    fun pause() {
        wantPlaying = false
        val p = player ?: return
        if (!prepared) return // onPrepared sees wantPlaying=false and stays paused
        runCatching { if (p.isPlaying) p.pause() }.onFailure { releasePlayer() }
    }

    fun stop() {
        wantPlaying = false
        releasePlayer()
    }

    fun release() {
        wantPlaying = false
        releasePlayer()
    }

    /** Async prepare: no file decode / prepare on the main thread. Callbacks arrive on the main looper. */
    private fun startTrack() {
        releasePlayer()
        val mp = MediaPlayer()
        val ok = runCatching {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            appContext.resources.openRawResourceFd(tracks[index]).use { afd ->
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            mp.setVolume(MUSIC_VOLUME, MUSIC_VOLUME)
            mp.setOnPreparedListener { m ->
                if (player !== m) {
                    runCatching { m.release() }
                    return@setOnPreparedListener
                }
                prepared = true
                if (wantPlaying) runCatching { m.start() }.onFailure { releasePlayer() }
            }
            mp.setOnCompletionListener { done ->
                if (player === done) {
                    index = AudioPolicy.nextTrack(index, tracks.size)
                    startTrack()
                } else {
                    runCatching { done.release() }
                }
            }
            mp.setOnErrorListener { m, _, _ ->
                if (player === m) releasePlayer() else runCatching { m.release() }
                true
            }
            player = mp
            prepared = false
            mp.prepareAsync()
        }.isSuccess
        if (!ok) {
            if (player === mp) player = null
            prepared = false
            runCatching { mp.release() }
        }
    }

    private fun releasePlayer() {
        val p = player ?: return
        player = null
        prepared = false
        runCatching { p.setOnPreparedListener(null) }
        runCatching { p.setOnCompletionListener(null) }
        runCatching { p.setOnErrorListener(null) }
        runCatching { p.release() }
    }

    private companion object {
        const val MUSIC_VOLUME = 0.6f
    }
}
