package com.application.jomato.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.application.jomato.MainActivity
import com.application.jomato.Prefs
import com.application.jomato.R
import com.application.jomato.utils.FileLogger
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.io.File
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class FoodRescueService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var wakeLock: PowerManager.WakeLock? = null
    private var mqttClient: MqttClient? = null
    private var isRunning = false

    private val dedupDirName = "zomato_dedup"

    companion object {
        const val ACTION_STOP = "com.application.jomato.STOP_SERVICE"
        const val CHANNEL_ID_FOREGROUND = "jomato_service_channel"
        const val CHANNEL_ID_ALERTS = "jomato_alerts_channel_v2"
        const val NOTIFICATION_ID = 1001
        const val TARGET_PACKAGE = "com.application.zomato"
        const val NOTIFICATION_COOLDOWN_MS = 180_000L
        const val MESSAGE_STALE_MS = 120_000L
    }

    override fun onCreate() {
        super.onCreate()
        FileLogger.log(this, "Service", "Created")

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Jomato:ReliabilityLock")
        wakeLock?.acquire()
        FileLogger.log(this, "Service", "WakeLock acquired")

        createNotificationChannels()

        ensureDedupSystem()

        startForeground(NOTIFICATION_ID, createForegroundNotification("Initializing..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            FileLogger.log(this, "Service", "Stop Requested")
            Prefs.stopFoodRescue(this)
            stopSelf()
            return START_NOT_STICKY
        }

        Prefs.setMqttConnectionStatus(false)

//        serviceScope.launch {
//            delay(10000) // Wait 10 seconds
//            FileLogger.log(this@FoodRescueService, "Test", "Firing Test Notification...")
//            sendAlertNotification()
//        }


        if (!isRunning) {
            isRunning = true
            startReliabilityLoop()
        }

        return START_STICKY
    }

    private fun ensureDedupSystem() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val dir = File(filesDir, dedupDirName)
                if (!dir.exists()) {
                    dir.mkdirs()
                    FileLogger.log(this@FoodRescueService, "Dedup", "Created dedup directory")
                }

                val now = System.currentTimeMillis()
                val cutoff = now - (10 * 60 * 60 * 1000) // 10 hours
                var deletedCount = 0

                dir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoff) {
                        if (file.delete()) deletedCount++
                    }
                }

                if (deletedCount > 0) {
                    FileLogger.log(this@FoodRescueService, "Dedup", "Cleaned up $deletedCount stale ID files")
                }
            } catch (e: Exception) {
                FileLogger.log(this@FoodRescueService, "Dedup", "Error initializing dedup system: ${e.message}")
            }
        }
    }

    private fun isMessageProcessed(msgId: String): Boolean {
        return try {
            val file = File(File(filesDir, dedupDirName), msgId)
            file.exists()
        } catch (e: Exception) {
            false
        }
    }

    private fun markMessageProcessed(msgId: String) {
        try {
            val file = File(File(filesDir, dedupDirName), msgId)
            if (!file.exists()) {
                file.createNewFile()
            }
        } catch (e: Exception) {
            FileLogger.log(this, "Dedup", "Failed to create dedup file: ${e.message}")
        }
    }

    private fun startReliabilityLoop() {
        serviceScope.launch {
            FileLogger.log(this@FoodRescueService, "Service", "Loop Started")

            while (isActive) {
                try {
                    val state = Prefs.getFoodRescueState(this@FoodRescueService)

                    if (state == null) {
                        FileLogger.log(this@FoodRescueService, "Service", "Feature Disabled. Shutting down.")
                        stopSelf()
                        break
                    }

                    val pm = getSystemService(POWER_SERVICE) as PowerManager
                    val isScreenOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                        pm.isInteractive
                    } else {
                        @Suppress("DEPRECATION")
                        pm.isScreenOn
                    }
                    val screenStatus = if (isScreenOn) "SCREEN_ON" else "SCREEN_OFF"

                    val isConnected = mqttClient?.isConnected ?: false
                    val connStatus = if (isConnected) "CONNECTED" else "DISCONNECTED"

                    FileLogger.log(
                        this@FoodRescueService,
                        "Heartbeat",
                        "$connStatus | $screenStatus | Loc: ${state.location.name} | Cancelled: ${state.totalCancelledMessages} | Claimed: ${state.totalClaimedMessages} | Reconnects: ${state.totalReconnects}"
                    )

                    updateNotification("Monitoring: ${state.location.name}")

                    if (mqttClient == null || !mqttClient!!.isConnected) {
                        FileLogger.log(
                            this@FoodRescueService,
                            "Service",
                            "MQTT not connected, attempting connection..."
                        )
                        connectMqtt(state.essentials.foodRescue!!)
                    }

                } catch (e: Exception) {
                    FileLogger.log(this@FoodRescueService, "Service", "Loop Error: ${e.message}", e)
                }

                delay(30_000)
            }
        }
    }

    private suspend fun connectMqtt(config: com.application.jomato.api.FoodRescueConf) {
        try {
            if (mqttClient != null) {
                try {
                    mqttClient?.setCallback(null)
                    mqttClient?.disconnect()
                    mqttClient?.close()
                } catch (e: Exception) { }
            }

            val brokerUrl = "ssl://hedwig.zomato.com:443"
            val clientId = "jomato_android_${System.currentTimeMillis()}"

            mqttClient = MqttClient(brokerUrl, clientId, MemoryPersistence())

            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Prefs.setMqttConnectionStatus(false)

                    val reason = cause?.message ?: "Unknown"
                    FileLogger.log(this@FoodRescueService, "MQTT", "Connection lost: $reason")
                    Prefs.incrementReconnCount(this@FoodRescueService)
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    serviceScope.launch { handleMqttMessage(message) }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            val connOpts = MqttConnectOptions().apply {
                userName = config.client.username
                password = config.client.password.toCharArray()
                isCleanSession = true
                keepAliveInterval = 30 // config.client.keepalive
                isAutomaticReconnect = true
                connectionTimeout = 30
                socketFactory = getUnsafeSocketFactory()
            }

            FileLogger.log(this, "MQTT", "Connecting to broker...")
            mqttClient?.connect(connOpts)

            if (mqttClient?.isConnected == true) {
                Prefs.setMqttConnectionStatus(true)
            }

            FileLogger.log(this, "MQTT", "Connected. Subscribing...")
            mqttClient?.subscribe(config.channelName, config.qos)
            FileLogger.log(this, "MQTT", "Subscribed!")

        } catch (e: Exception) {
            FileLogger.log(this, "MQTT", "Connection failed: ${e.message}")
            Prefs.incrementReconnCount(this@FoodRescueService)
        }
    }

    private fun handleMqttMessage(message: MqttMessage?) {
        if (message == null) return

        try {
            val payload = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String(message.payload, java.nio.charset.StandardCharsets.UTF_8)
            } else {
                String(message.payload)
            }

            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(payload).jsonObject

            val eventType = root["data"]?.jsonObject?.get("event_type")?.jsonPrimitive?.content
            val msgId = root["id"]?.jsonPrimitive?.content

            // 1. STALENESS CHECK (The "Cold Start" Filter)
            // If the message is older than 2 minutes, ignore it immediately.
            val timestamp = root["timestamp"]?.jsonPrimitive?.longOrNull
            if (timestamp != null) {
                val now = System.currentTimeMillis()
                // Convert seconds to ms if needed (Zomato usually sends seconds)
                val eventTimeMs = if (timestamp < 10000000000L) timestamp * 1000 else timestamp

                if (now - eventTimeMs > MESSAGE_STALE_MS) {
                    FileLogger.log(this, "Logic", "Ignored STALE message ($msgId). Age: ${(now - eventTimeMs)/1000}s")
                    return
                }
            }

            if (msgId != null) {
                if (isMessageProcessed(msgId)) {
                    FileLogger.log(this, "Dedup", "Ignored known ID: $msgId")
                    return
                }
                markMessageProcessed(msgId)
            }

            when (eventType) {
                "order_cancelled" -> Prefs.incrementCancelledCount(this)
                "order_claimed" -> Prefs.incrementClaimedCount(this)
                else -> return
            }

            if (eventType != "order_cancelled") {
                return
            }

            FileLogger.log(this, "Logic", ">>> NEW FRESH ORDER CANCELLED ($msgId) <<<")

            // 4. NOTIFICATION COOLDOWN (The "Spam" Filter)
            val lastNotificationTime = Prefs.getLastNotificationTime(this)
            val now = System.currentTimeMillis()
            val timeSinceLast = now - lastNotificationTime

            if (timeSinceLast >= NOTIFICATION_COOLDOWN_MS) {
                FileLogger.log(this, "Logic", "Cooldown expired ($timeSinceLast > $NOTIFICATION_COOLDOWN_MS). Sending Notification.")
                sendAlertNotification()
                Prefs.saveLastNotification(this, now)
            } else {
                val remaining = (NOTIFICATION_COOLDOWN_MS - timeSinceLast) / 1000
                FileLogger.log(this, "Logic", "Notification suppressed. Cooldown active (${remaining}s remaining).")
            }

        } catch (e: Exception) {
            FileLogger.log(this, "Logic", "Error processing: ${e.message}")
        }
    }

    private fun sendAlertNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertChannel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "Food Rescue Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                description = "Notifications for fresh food rescue opportunities"
            }
            notificationManager.createNotificationChannel(alertChannel)
        }

        var launchIntent = packageManager.getLaunchIntentForPackage("com.application.zomato")

        if (launchIntent == null) {
            launchIntent = Intent(this, MainActivity::class.java)
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALERTS)
            .setSmallIcon(R.drawable.ic_notification_jomato)
            .setContentTitle("Food Rescue Alert!")
            .setContentText("Click to open Zomato")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createForegroundNotification(text))
    }

    private fun createForegroundNotification(status: String): Notification {
        val stopIntent = Intent(this, FoodRescueService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val appIntent = Intent(this, MainActivity::class.java)
        val appPending = PendingIntent.getActivity(this, 0, appIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID_FOREGROUND)
            .setContentTitle("Jomato Food Rescue")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_notification_jomato)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)
            .setContentIntent(appPending)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val serviceChannel = NotificationChannel(CHANNEL_ID_FOREGROUND, "Background Service", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun getUnsafeSocketFactory(): javax.net.SocketFactory {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())
        return sslContext.socketFactory
    }

    override fun onDestroy() {
        FileLogger.log(this, "Service", "Destroying Service")
        isRunning = false
        serviceScope.cancel()
        try {
            mqttClient?.disconnect()
            mqttClient?.close()
        } catch (e: Exception) {}
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {}

        Prefs.setMqttConnectionStatus(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null
}