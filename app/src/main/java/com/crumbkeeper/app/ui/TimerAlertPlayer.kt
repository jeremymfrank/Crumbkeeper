package com.crumbkeeper.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build

/**
 * Foreground timer completion audio, driven by the in-place Cooking Mode.
 *
 * Uses the user's Android alarm ringtone and alarm audio stream; volume and
 * Do Not Disturb policies remain under device control. If no playable system
 * alarm is configured, falls back to a short alarm-stream tone.
 *
 * This is NOT a background alarm scheduler. AlarmManager/notifications still
 * require a separate implementation and real-device testing.
 */
internal class TimerAlertPlayer(context: Context) {
    private val appContext = context.applicationContext
    private var ringtone: Ringtone? = null
    private var fallback: ToneGenerator? = null
    private var lookedUpRingtone = false

    private fun loadRingtone(): Ringtone? {
        if (lookedUpRingtone) return ringtone
        lookedUpRingtone = true
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val candidates = listOfNotNull(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        )
        for (uri in candidates) {
            val candidate = runCatching {
                RingtoneManager.getRingtone(appContext, uri)?.apply {
                    audioAttributes = attributes
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        isLooping = true
                    }
                }
            }.getOrNull()
            if (candidate != null) {
                ringtone = candidate
                return candidate
            }
        }
        return null
    }

    /** Called repeatedly while one or more timers are finished and not dismissed. */
    fun pulse() {
        val sound = loadRingtone()
        if (sound != null) {
            val playing = runCatching { sound.isPlaying }.getOrDefault(false)
            if (playing) return
            if (runCatching { sound.play() }.isSuccess) return
        }
        if (fallback == null) {
            fallback = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 85) }.getOrNull()
        }
        runCatching { fallback?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300) }
    }

    fun stop() {
        runCatching { ringtone?.stop() }
        runCatching { fallback?.stopTone() }
    }

    fun release() {
        stop()
        runCatching { fallback?.release() }
        fallback = null
    }
}
