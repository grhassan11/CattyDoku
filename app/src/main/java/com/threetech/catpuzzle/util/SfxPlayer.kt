package com.threetech.catpuzzle.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.audio.AudioPolicy
import com.threetech.catpuzzle.ui.screens.game.SfxKind

/**
 * Thin SoundPool wrapper for short one-shot cues. [SfxKind.AWARD] (7.6 s) is too long for SoundPool
 * and plays through a one-shot [MediaPlayer] instead. Owned by the app-wide AudioController (single instance, main thread).
 * Silent no-op if a sample is missing, failed to load, or has not finished loading yet.
 * The sfx setting is checked by the ViewModel before emitting the event, not here.
 */
class SfxPlayer(context: Context) {
    private val pool: SoundPool? = runCatching {
        SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
    }.getOrNull()

    private val appContext = context.applicationContext
    private val soundIds = HashMap<SfxKind, Int>()
    private val loaded = HashSet<Int>()
    private var awardPlayer: MediaPlayer? = null
    private val catIds = ArrayList<Int>()
    private var lastCat = -1

    init {
        val p = pool
        if (p != null) {
            p.setOnLoadCompleteListener { _, id, status ->
                if (status == 0) synchronized(loaded) { loaded.add(id) }
            }
            // Missing raw resource is skipped so the app never crashes without the file.
            fun load(kind: SfxKind, resId: Int) {
                runCatching { p.load(context, resId, 1) }.getOrNull()?.takeIf { it > 0 }?.let { soundIds[kind] = it }
            }
            load(SfxKind.MEOW, R.raw.sfx_meow)
            load(SfxKind.WRONG, R.raw.sfx_wrong_placement)
            load(SfxKind.HINT, R.raw.sfx_hint)
            load(SfxKind.CHIME, R.raw.sfx_hint) // no dedicated chime asset: reuse the hint ding (played softer + higher, see play)
            load(SfxKind.WIN, R.raw.sfx_win)
            load(SfxKind.BLOCKED, R.raw.sfx_blocked)
            load(SfxKind.UI_TAP, R.raw.sfx_ui_tap)
            load(SfxKind.CROSS, R.raw.sfx_cross)
            load(SfxKind.MOUSE, R.raw.sfx_mouse)
            // Random cat meows layered on the correct-placement chime (see play).
            intArrayOf(
                R.raw.sfx_cat_1, R.raw.sfx_cat_2, R.raw.sfx_cat_3, R.raw.sfx_cat_4,
                R.raw.sfx_cat_5, R.raw.sfx_cat_6, R.raw.sfx_cat_7,
            ).forEach { res -> runCatching { p.load(context, res, 1) }.getOrNull()?.takeIf { it > 0 }?.let { catIds.add(it) } }
        }
    }

    /** Recent SoundPool stream ids so [stopAll] can silence in-flight cues (cues are short; 8 is plenty). */
    private val streams = ArrayDeque<Int>()

    fun play(kind: SfxKind) {
        if (kind == SfxKind.AWARD) {
            playAward()
            return
        }
        val p = pool ?: return
        val id = soundIds[kind] ?: return
        if (!synchronized(loaded) { id in loaded }) return
        val soft = kind == SfxKind.CHIME
        val vol = if (soft) CHIME_VOLUME else 1f
        val rate = if (soft) CHIME_RATE else 1f
        track(runCatching { p.play(id, vol, vol, 1, 0, rate) }.getOrDefault(0))
        if (kind == SfxKind.CHIME) playRandomCat(p)
    }

    private fun track(streamId: Int) {
        if (streamId != 0) {
            streams.addLast(streamId)
            if (streams.size > 8) streams.removeFirst()
        }
    }

    /** One random cat meow (never the same twice in a row). Silent if none loaded. */
    private fun playRandomCat(p: SoundPool) {
        if (catIds.isEmpty()) return
        val i = AudioPolicy.nextCatSfx(lastCat, catIds.size, kotlin.random.Random.nextInt())
        val id = catIds[i]
        if (!synchronized(loaded) { id in loaded }) return
        lastCat = i
        track(runCatching { p.play(id, 1f, 1f, 1, 0, 1f) }.getOrDefault(0))
    }

    /** Silence everything in flight (SoundPool streams + award cue). Used while a full-screen ad is showing. */
    fun stopAll() {
        stopAward()
        val p = pool ?: return
        while (streams.isNotEmpty()) {
            val s = streams.removeFirst()
            runCatching { p.stop(s) }
        }
    }

    private fun playAward() {
        stopAward()
        awardPlayer = runCatching {
            MediaPlayer.create(
                appContext,
                R.raw.sfx_award,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
                0,
            )
        }.getOrNull()?.also { mp ->
            mp.setOnCompletionListener {
                it.release()
                if (awardPlayer === it) awardPlayer = null
            }
            runCatching { mp.start() }.onFailure { stopAward() }
        }
    }

    private fun stopAward() {
        awardPlayer?.let { runCatching { it.release() } }
        awardPlayer = null
    }

    private companion object {
        const val CHIME_VOLUME = 0.55f
        const val CHIME_RATE = 1.35f
    }

    fun release() {
        stopAward()
        pool?.release()
        soundIds.clear()
        catIds.clear()
        synchronized(loaded) { loaded.clear() }
    }
}
