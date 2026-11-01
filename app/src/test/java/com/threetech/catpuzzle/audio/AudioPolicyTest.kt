package com.threetech.catpuzzle.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPolicyTest {
    @Test
    fun musicOffAlwaysStops() {
        assertEquals(MusicCommand.STOP, AudioPolicy.musicCommand(false, true, false))
        assertEquals(MusicCommand.STOP, AudioPolicy.musicCommand(false, false, true))
    }

    @Test
    fun backgroundOrAdPauses() {
        assertEquals(MusicCommand.PAUSE, AudioPolicy.musicCommand(true, false, false))
        assertEquals(MusicCommand.PAUSE, AudioPolicy.musicCommand(true, true, true))
    }

    @Test
    fun foregroundNoAdPlays() {
        assertEquals(MusicCommand.PLAY, AudioPolicy.musicCommand(true, true, false))
    }

    @Test
    fun tracksAlternate() {
        assertEquals(1, AudioPolicy.nextTrack(0, 2))
        assertEquals(0, AudioPolicy.nextTrack(1, 2))
        assertEquals(0, AudioPolicy.nextTrack(0, 0))
    }

    @Test
    fun sfxNeedsSettingAndNoAd() {
        assertTrue(AudioPolicy.sfxAllowed(true, false))
        assertFalse(AudioPolicy.sfxAllowed(false, false))
        assertFalse(AudioPolicy.sfxAllowed(true, true))
    }

    @Test
    fun catSfxNeverRepeatsAndStaysInRange() {
        for (prev in -1..6) for (roll in -20..20) {
            val n = AudioPolicy.nextCatSfx(prev, 7, roll)
            assertTrue(n in 0..6)
            if (prev >= 0) assertTrue(n != prev)
        }
    }
}
