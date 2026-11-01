package com.threetech.catpuzzle.audio

/** What the background music should be doing. */
enum class MusicCommand { PLAY, PAUSE, STOP }

/** Pure gating + alternation rules for audio. No Android imports. */
object AudioPolicy {
    /**
     * Music follows the Music setting only; app background or a full-screen ad pauses it (resumes in place),
     * setting off stops it.
     */
    fun musicCommand(musicEnabled: Boolean, appForeground: Boolean, fullScreenAdShowing: Boolean): MusicCommand = when {
        !musicEnabled -> MusicCommand.STOP
        !appForeground || fullScreenAdShowing -> MusicCommand.PAUSE
        else -> MusicCommand.PLAY
    }

    /** Endless alternation A, B, A, B ... */
    fun nextTrack(current: Int, count: Int): Int = if (count <= 0) 0 else (current + 1) % count

    /** SFX play only when the Sound setting is on and no full-screen ad is showing. */
    fun sfxAllowed(sfxEnabled: Boolean, fullScreenAdShowing: Boolean): Boolean = sfxEnabled && !fullScreenAdShowing

    /** Random cat-meow index in [0, count), never equal to [previous] (when valid). [roll] is any random int. */
    fun nextCatSfx(previous: Int, count: Int, roll: Int): Int = when {
        count <= 1 -> 0
        previous !in 0 until count -> roll.mod(count)
        else -> roll.mod(count - 1).let { if (it >= previous) it + 1 else it }
    }
}
