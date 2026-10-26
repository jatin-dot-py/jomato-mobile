package com.application.jomato.entity.zomato.rescue

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.application.jomato.MainActivity
import com.application.jomato.Prefs
import com.application.jomato.R
import com.application.jomato.entity.zomato.service.FoodRescueService
import com.application.jomato.ui.RescueAlertActivity
import com.application.jomato.utils.FileLogger

/**
 * Single place that builds and posts the food-rescue alert.
 *
 * Alert styles (user selectable, see [Prefs.getAlertStyle]):
 *  - ALARM    -> loud full-screen takeover + looping ring on the alarm stream
 *  - SOUND    -> normal loud notification (custom sound), no screen takeover
 *  - SILENT   -> vibration only
 *
 * Three separate channels are used so that switching style never fights the
 * immutable channel settings Android remembers per channel id.
 */
object FoodRescueAlerter {

    private const val TAG = "FoodRescueAlerter"

    const val ALERT_NOTIFICATION_ID = 1002

    const val CHANNEL_ID_RING = "jomato_alerts_channel_v3_ring"
    const val CHANNEL_ID_SOUND = "jomato_alerts_channel_v3_sound"
    const val CHANNEL_ID_SILENT = "jomato_alerts_channel_v3_silent"

    private val VIBRATION_PATTERN = longArrayOf(0, 400, 200, 400, 200, 400)

    private const val TITLE = "Food Rescue Alert!"
    private const val TEXT = "A cancelled order just went up for rescue. Tap to open Zomato."

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (nm.getNotificationChannel(CHANNEL_ID_RING) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID_RING,
                "Food Rescue Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Full-screen alert + ringing when a rescue order appears"
                // Sound is produced by RescueAlertPlayer so it can loop and use the
                // alarm stream; keep the channel silent to avoid a double sound.
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
                enableLights(true)
                lightColor = Color.RED
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            nm.createNotificationChannel(channel)
            FileLogger.log(context, TAG, "Created ring channel")
        }

        if (nm.getNotificationChannel(CHANNEL_ID_SOUND) == null) {
            val soundUri = Uri.parse("android.resource://${context.packageName}/${R.raw.rescue_alert}")
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel(
                CHANNEL_ID_SOUND,
                "Food Rescue Alerts (sound)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Loud notification sound when a rescue order appears"
                setSound(soundUri, attrs)
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
                enableLights(true)
                lightColor = Color.RED
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            nm.createNotificationChannel(channel)
            FileLogger.log(context, TAG, "Created sound channel")
        }

        if (nm.getNotificationChannel(CHANNEL_ID_SILENT) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID_SILENT,
                "Food Rescue Alerts (vibrate only)",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Vibration only, no sound"
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(channel)
            FileLogger.log(context, TAG, "Created silent channel")
        }
    }

    /** Post the alert notification and, for the ALARM style, start the ring + full-screen intent. */
    fun post(context: Context) {
        val app = context.applicationContext
        ensureChannels(app)

        val style = Prefs.getAlertStyle(app)
        val channelId = when (style) {
            Prefs.ALERT_STYLE_SOUND -> CHANNEL_ID_SOUND
            Prefs.ALERT_STYLE_SILENT -> CHANNEL_ID_SILENT
            else -> CHANNEL_ID_RING
        }

        val notificationManager =
            app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        var openApp = app.packageManager.getLaunchIntentForPackage(FoodRescueService.TARGET_PACKAGE)
        if (openApp == null) {
            openApp = Intent(app, MainActivity::class.java)
        }
        openApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openPendingIntent = PendingIntent.getActivity(
            app,
            0,
            openApp,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val dismissPendingIntent = PendingIntent.getBroadcast(
            app,
            1,
            Intent(app, AlertDismissReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val fullScreenPendingIntent = PendingIntent.getActivity(
            app,
            2,
            Intent(app, RescueAlertActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(app, channelId)
            .setSmallIcon(R.drawable.ic_notification_jomato)
            .setContentTitle(TITLE)
            .setContentText(TEXT)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPendingIntent)
            .setAutoCancel(true)

        if (style == Prefs.ALERT_STYLE_ALARM) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        notificationManager.notify(ALERT_NOTIFICATION_ID, builder.build())

        if (style == Prefs.ALERT_STYLE_ALARM) {
            RescueAlertPlayer.start(app)
        }

        FileLogger.log(app, TAG, "Alert posted | style=$style | channel=$channelId")
    }

    /** Stop the ring and remove the alert notification. */
    fun dismiss(context: Context) {
        RescueAlertPlayer.stop(context)
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(ALERT_NOTIFICATION_ID)
        FileLogger.log(context, TAG, "Alert dismissed")
    }

    /**
     * User-initiated preview from the settings screen. Posts a real alert and, because the
     * app is in the foreground, opens the takeover screen directly so the user can see it.
     */
    fun test(context: Context) {
        post(context)
        try {
            context.startActivity(
                Intent(context, RescueAlertActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        } catch (e: Exception) {
            FileLogger.log(context, TAG, "Could not open alert preview", e)
        }
    }

}
