package com.application.jomato.entity.zomato.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.application.jomato.utils.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Loops an alarm-stream ringtone + vibration on top of the alert notification so a
// Food Rescue opportunity is impossible to miss under a pile of other notifications.
// Alarm stream (not ringtone/notification stream) so it plays even with the ringer on silent.
object RescueRinger {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var stopJob: Job? = null

    private val vibrationPattern = longArrayOf(0, 1000, 500)

    fun start(context: Context, scope: CoroutineScope, maxDurationMs: Long) {
        stop(context)

        try {
            val ringUri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, ringUri)
                isLooping = true
                setOnPreparedListener { prepared ->
                    // Guard against stop() having already released this instance while
                    // prepareAsync() was still decoding (e.g. order_claimed arrived first).
                    if (prepared === mediaPlayer) {
                        try { prepared.start() } catch (e: Exception) { }
                    }
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            FileLogger.log(context, "Ringer", "Failed to start ring tone: ${e.message}")
        }

        try {
            vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(vibrationPattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(vibrationPattern, 0)
            }
        } catch (e: Exception) {
            FileLogger.log(context, "Ringer", "Failed to start vibration: ${e.message}")
        }

        stopJob = scope.launch {
            delay(maxDurationMs)
            FileLogger.log(context, "Ringer", "Safety cap (${maxDurationMs}ms) reached, stopping ring")
            stop(context)
        }

        FileLogger.log(context, "Ringer", "Ring started (max ${maxDurationMs}ms)")
    }

    fun stop(context: Context) {
        stopJob?.cancel()
        stopJob = null

        mediaPlayer?.let {
            try { it.stop() } catch (e: Exception) { }
            try { it.release() } catch (e: Exception) { }
        }
        mediaPlayer = null

        vibrator?.let {
            try { it.cancel() } catch (e: Exception) { }
        }
        vibrator = null
    }
}
