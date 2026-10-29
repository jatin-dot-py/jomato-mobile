package com.application.jomato.entity.zomato.rescue

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.application.jomato.Prefs
import com.application.jomato.R
import com.application.jomato.utils.FileLogger

/**
 * Plays the looping food-rescue ring.
 *
 * Everything here runs on the ALARM audio stream on purpose:
 *  - it is a separate volume from the ringer/notification streams, so a phone on
 *    "silent" still rings (silent mode mutes the ringer, not the alarm stream);
 *  - Do Not Disturb exempts alarms by default, so the alert still gets through;
 *  - it is the stream users expect a "wake up, act now" alert to use.
 *
 * A notification channel sound alone cannot do this reliably: the channel is a
 * one-shot sound, can land on a muted channel, and is silenced by OEM battery /
 * DND rules. A looping player is deterministic.
 *
 * The object is a process-wide singleton, so the service and the full-screen
 * alert activity can both call [start] without double-ringing.
 */
object RescueAlertPlayer {

    private const val TAG = "RescueAlertPlayer"

    /** Ring for this long, then stop on our own so an unattended phone does not ring forever. */
    const val DEFAULT_RING_TIMEOUT_MS = 45_000L

    /** If the alarm stream is muted we raise it to this fraction of max, then restore it. */
    private const val VOLUME_ESCALATION_FRACTION = 0.85f

    private val handler = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private var timeoutRunnable: Runnable? = null

    /** -1 = we did not change the volume. */
    private var previousAlarmVolume: Int = -1
    private var escalatedAlarmVolume: Int = -1

    val isRinging: Boolean get() = player != null

    /**
     * Start (or extend) the ring. Safe to call repeatedly.
     * No-op when the user picked the silent alert style.
     */
    fun start(context: Context, timeoutMs: Long = DEFAULT_RING_TIMEOUT_MS) {
        val app = context.applicationContext
        if (Prefs.getAlertStyle(app) == Prefs.ALERT_STYLE_SILENT) {
            FileLogger.log(app, TAG, "Skipped: silent alert style")
            return
        }

        handler.post {
            if (player != null) {
                // Already ringing (service + activity both asked) - just extend the timeout.
                scheduleTimeout(app, timeoutMs)
                return@post
            }
            try {
                escalateAlarmVolumeIfMuted(app)
                acquireWakeLock(app, timeoutMs)
                requestAudioFocus(app)

                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val mp = MediaPlayer()
                mp.setAudioAttributes(attrs)
                app.resources.openRawResourceFd(R.raw.rescue_alert).use { afd ->
                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    mp.isLooping = true
                    mp.setVolume(1f, 1f)
                    mp.prepare()
                }
                mp.setOnErrorListener { _, what, extra ->
                    FileLogger.log(app, TAG, "MediaPlayer error what=$what extra=$extra")
                    stopInternal(app)
                    true
                }
                mp.start()
                player = mp
                scheduleTimeout(app, timeoutMs)
                FileLogger.log(app, TAG, "Ring started (timeout=${timeoutMs}ms)")
            } catch (e: Exception) {
                FileLogger.log(app, TAG, "Unable to start ring", e)
                stopInternal(app)
            }
        }
    }

    /** Stop the ring immediately (user dismissed, opened Zomato, or notification was removed). */
    fun stop(context: Context) {
        val app = context.applicationContext
        handler.post { stopInternal(app) }
    }

    private fun stopInternal(app: Context) {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null

        player?.let { mp ->
            try {
                if (mp.isPlaying) mp.stop()
            } catch (_: IllegalStateException) {
                // Already torn down.
            } catch (e: Exception) {
                FileLogger.log(app, TAG, "Error stopping player: ${e.message}")
            } finally {
                try {
                    mp.release()
                } catch (_: Exception) {
                }
            }
        }
        player = null

        releaseWakeLock()
        abandonAudioFocus(app)
        restoreAlarmVolume(app)
        FileLogger.log(app, TAG, "Ring stopped")
    }

    private fun scheduleTimeout(app: Context, timeoutMs: Long) {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            FileLogger.log(app, TAG, "Ring timed out after ${timeoutMs}ms")
            stopInternal(app)
        }
        timeoutRunnable = runnable
        handler.postDelayed(runnable, timeoutMs)
    }

    private fun escalateAlarmVolumeIfMuted(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val current = am.getStreamVolume(AudioManager.STREAM_ALARM)
            if (current != 0) return

            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val target = (max * VOLUME_ESCALATION_FRACTION).toInt().coerceIn(1, max)
            previousAlarmVolume = current
            escalatedAlarmVolume = target
            am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
            FileLogger.log(context, TAG, "Alarm volume was 0, raised to $target/$max for the alert")
        } catch (e: SecurityException) {
            FileLogger.log(context, TAG, "Could not raise alarm volume (system refused)", e)
        } catch (e: Exception) {
            FileLogger.log(context, TAG, "Could not raise alarm volume: ${e.message}")
        }
    }

    private fun restoreAlarmVolume(context: Context) {
        if (previousAlarmVolume < 0) return
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            // Only restore if the volume is still what we set - never stomp a user change.
            if (escalatedAlarmVolume < 0 || am.getStreamVolume(AudioManager.STREAM_ALARM) == escalatedAlarmVolume) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, previousAlarmVolume, 0)
            }
        } catch (e: Exception) {
            FileLogger.log(context, TAG, "Could not restore alarm volume: ${e.message}")
        } finally {
            previousAlarmVolume = -1
            escalatedAlarmVolume = -1
        }
    }

    private fun acquireWakeLock(context: Context, timeoutMs: Long) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Jomato:AlertRing").apply {
                setReferenceCounted(false)
                acquire(timeoutMs)
            }
        } catch (e: Exception) {
            FileLogger.log(context, TAG, "Could not acquire wake lock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Could not release wake lock: ${e.message}")
        } finally {
            wakeLock = null
        }
    }

    private fun requestAudioFocus(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener { }
                .build()
            focusRequest = request
            am.requestAudioFocus(request)
        } catch (e: Exception) {
            FileLogger.log(context, TAG, "Audio focus request failed: ${e.message}")
        }
    }

    private fun abandonAudioFocus(context: Context) {
        val request = focusRequest ?: return
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.abandonAudioFocusRequest(request)
        } catch (e: Exception) {
            FileLogger.log(context, TAG, "Audio focus abandon failed: ${e.message}")
        } finally {
            focusRequest = null
        }
    }
}
