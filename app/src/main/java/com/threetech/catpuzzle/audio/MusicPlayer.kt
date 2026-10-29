package com.threetech.catpuzzle.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.threetech.catpuzzle.R

/**
 * Endless background music: track 1, track 2, track 1 ... (each file plays once, then the next starts).
 * Main-thread only. Every MediaPlayer call is wrapped; any failure just drops the player (silent), never throws.
 * [pause] keeps position, [stop] releases the player (next [play] starts the current track from the top).
 */
class MusicPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val tracks = intArrayOf(R.raw.music_track_1, R.raw.music_track_2)
    private var player: MediaPlayer? = null
    private var index = 0

    fun play() {
        val p = player
        if (p == null) {
            startTrack()
            return
        }
        runCatching { if (!p.isPlaying) p.start() }.onFailure { startTrack() }
    }

    fun pause() {
        val p = player ?: return
        runCatching { if (p.isPlaying) p.pause() }.onFailure { releasePlayer() }
    }

    fun stop() {
        releasePlayer()
    }

    fun release() {
        releasePlayer()
    }

    private fun startTrack() {
        releasePlayer()
        val mp = runCatching {
            MediaPlayer.create(
                appContext,
                tracks[index],
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
                0,
            )
        }.getOrNull() ?: return
        mp.setVolume(MUSIC_VOLUME, MUSIC_VOLUME)
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
        runCatching { mp.start() }.onFailure { releasePlayer() }
    }

    private fun releasePlayer() {
        val p = player ?: return
        player = null
        runCatching { p.setOnCompletionListener(null) }
        runCatching { p.setOnErrorListener(null) }
        runCatching { p.release() }
    }

    private companion object {
        const val MUSIC_VOLUME = 0.6f
    }
}
