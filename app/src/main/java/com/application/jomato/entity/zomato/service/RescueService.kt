package com.application.jomato.entity.zomato.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.application.jomato.MainActivity
import com.application.jomato.R
import com.application.jomato.entity.zomato.ZomatoManager
import com.application.jomato.entity.zomato.api.ApiClient
import com.application.jomato.entity.zomato.api.FoodRescueConf
import com.application.jomato.entity.zomato.rescue.FoodRescueAlerter
import com.application.jomato.entity.zomato.rescue.RescueEventParser
import com.application.jomato.entity.zomato.rescue.RescueHistoryStore
import com.application.jomato.utils.AnalyticsManager
import com.application.jomato.utils.FileLogger
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.io.File

class FoodRescueService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var wakeLock: PowerManager.WakeLock? = null
    private var mqttClient: MqttClient? = null
    private var isRunning = false
    private var lastConnectedAt: Long = 0L

    /** Consecutive MQTT handshake failures; drives credential refresh. */
    private var consecutiveConnectFailures = 0
    private var lastCredentialRefreshAt = 0L

    private val dedupDirName = "zomato_dedup"

    companion object {
        const val ACTION_STOP = "com.application.jomato.STOP_SERVICE"
        const val ACTION_TEST = "com.application.jomato.TEST_NOTIFICATION"

        const val CHANNEL_ID_FOREGROUND = "jomato_service_channel"
        const val NOTIFICATION_ID = 1001

        const val TARGET_PACKAGE = "com.application.zomato"

        // After this many failed MQTT handshakes, fetch fresh channel credentials.
        const val FAILURES_BEFORE_CREDENTIAL_REFRESH = 2

        // Don't hammer the Zomato API if refreshes keep failing.
        const val CREDENTIAL_REFRESH_COOLDOWN_MS = 5 * 60 * 1000L

        // How long to suppress notifications after one fires.
        // Increase if users report too many alerts back-to-back.
        // 180_000 = 3 minutes | 300_000 = 5 minutes | 60_000 = 1 minute
        const val NOTIFICATION_COOLDOWN_MS = 300_000L

        // Maximum age of an order_cancelled message before we ignore it.
        // Prevents cold-start replays from firing stale notifications.
        // 120_000 = 2 minutes | 60_000 = 1 minute | 300_000 = 5 minutes
        const val MESSAGE_STALE_MS = 120_000L

        // How often to force a full MQTT reconnect even if connection appears healthy.
        // Lower = more reliable but slightly more battery. Higher = less battery but flakier on long runs.
        // 10 * 60 * 1000 = 10 minutes | 5 * 60 * 1000 = 5 minutes | 15 * 60 * 1000 = 15 minutes
        const val RECONNECT_INTERVAL_MS = 20 * 60 * 1000L
    }

    override fun onCreate() {
        super.onCreate()
        FileLogger.log(this, "Service", "Created")

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Jomato:ReliabilityLock")
        wakeLock?.acquire(10 * 60 * 1000L)
        FileLogger.log(this, "Service", "WakeLock acquired")

        createNotificationChannels()

        ensureDedupSystem()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                createForegroundNotification("Initializing..."),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, createForegroundNotification("Initializing..."))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            FileLogger.log(this, "Service", "Stop Requested")
            ZomatoManager.stopFoodRescue(this)
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_TEST) {
            FileLogger.log(this, "Service", "Manual Test Triggered")
            FoodRescueAlerter.post(this)
            // A test with monitoring switched off must not leave a foreground service running.
            if (!ZomatoManager.isFoodRescueActive(this)) stopSelf()
            return START_NOT_STICKY
        }

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
                    val state = ZomatoManager.getFoodRescueState(this@FoodRescueService)

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
                        "$connStatus | $screenStatus | Loc: ${state.location.name}"
                    )

                    val now = System.currentTimeMillis()
                    val shouldForceReconnect = lastConnectedAt > 0L && (now - lastConnectedAt) >= RECONNECT_INTERVAL_MS

                    if (mqttClient == null || !mqttClient!!.isConnected || shouldForceReconnect) {
                        if (shouldForceReconnect) {
                            FileLogger.log(this@FoodRescueService, "Service", "Force reconnect triggered (every ${RECONNECT_INTERVAL_MS / 60_000} min)")
                        } else {
                            FileLogger.log(this@FoodRescueService, "Service", "MQTT not connected, attempting connection...")
                        }
                        // Zomato-issued channel credentials expire; fall back to a refresh
                        // instead of crashing on a missing config.
                        val conf = state.essentials.foodRescue ?: refreshCredentials()
                        if (conf != null) {
                            connectMqtt(conf)
                        } else {
                            FileLogger.log(this@FoodRescueService, "MQTT", "No food-rescue credentials available; will retry next cycle")
                        }
                    }

                    updateNotification(buildNotificationText(state.location.name))

                } catch (e: Exception) {
                    FileLogger.log(this@FoodRescueService, "Service", "Loop Error: ${e.message}", e)
                }

                delay(30_000)
            }
        }
    }

    private fun buildNotificationText(locationName: String): String {
        if (lastConnectedAt == 0L) return "Connecting..."
        val minutesAgo = (System.currentTimeMillis() - lastConnectedAt) / 60000
        val connText = when {
            minutesAgo < 1 -> "Connected just now"
            minutesAgo == 1L -> "Connected 1 min ago"
            else -> "Connected ${minutesAgo} min ago"
        }
        return "$locationName · $connText"
    }

    private suspend fun connectMqtt(config: FoodRescueConf) {
        try {
            if (mqttClient != null) {
                try {
                    mqttClient?.setCallback(null)
                    mqttClient?.disconnect()
                    mqttClient?.close()
                } catch (e: Exception) { }
                mqttClient = null
            }

           val brokerUrl = "ssl://hedwig.zomato.com:443"

            val clientId = "user${System.currentTimeMillis()}"

            mqttClient = MqttClient(brokerUrl, clientId, MemoryPersistence())

            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    val reason = cause?.message ?: "Unknown"
                    FileLogger.log(this@FoodRescueService, "MQTT", "Connection lost: $reason")
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
               keepAliveInterval = 30
               isAutomaticReconnect = false
               connectionTimeout = 30
           }

            FileLogger.log(this, "MQTT", "Connecting to broker...")
            mqttClient?.connect(connOpts)

            if (mqttClient?.isConnected == true) {
                lastConnectedAt = System.currentTimeMillis()
                consecutiveConnectFailures = 0
                FileLogger.log(this, "MQTT", "Connected. Subscribing...")
                mqttClient?.subscribe(config.channelName, config.qos)
                FileLogger.log(this, "MQTT", "Subscribed!")
            }

        } catch (e: Exception) {
            consecutiveConnectFailures++
            FileLogger.log(this, "MQTT", "Connection failed (attempt $consecutiveConnectFailures): ${e.message}")
            if (consecutiveConnectFailures >= FAILURES_BEFORE_CREDENTIAL_REFRESH) {
                refreshCredentials()
            }
        }
    }

    /**
     * Re-fetches tabbed-home so we get a fresh food_rescue channel name + MQTT
     * credentials. Zomato rotates these; without this the service silently keeps
     * retrying with dead credentials and the user stops getting alerts.
     */
    private suspend fun refreshCredentials(): FoodRescueConf? {
        val now = System.currentTimeMillis()
        if (now - lastCredentialRefreshAt < CREDENTIAL_REFRESH_COOLDOWN_MS) {
            FileLogger.log(this, "MQTT", "Credential refresh skipped (cooldown)")
            return null
        }
        lastCredentialRefreshAt = now

        val sessionId = ZomatoManager.getFoodRescueSessionId(this) ?: run {
            FileLogger.log(this, "MQTT", "Credential refresh skipped: no session")
            return null
        }
        val session = ZomatoManager.getSession(this, sessionId) ?: run {
            FileLogger.log(this, "MQTT", "Credential refresh skipped: session not found")
            return null
        }
        val state = ZomatoManager.getFoodRescueState(this) ?: run {
            FileLogger.log(this, "MQTT", "Credential refresh skipped: no rescue state")
            return null
        }

        FileLogger.log(this, "MQTT", "Refreshing food-rescue credentials...")
        val essentials = try {
            ApiClient.getTabbedHomeEssentials(
                this,
                state.location.cellId,
                state.location.addressId,
                session.accessToken
            )
        } catch (e: Exception) {
            FileLogger.log(this, "MQTT", "Credential refresh failed: ${e.message}", e)
            null
        } ?: return null

        val conf = essentials.foodRescue
        if (conf == null) {
            FileLogger.log(this, "MQTT", "Credential refresh returned no food_rescue channel")
            return null
        }

        ZomatoManager.updateFoodRescueEssentials(this, essentials)
        consecutiveConnectFailures = 0
        FileLogger.log(this, "MQTT", "Credentials refreshed | channel=${conf.channelName}")
        return conf
    }

    private suspend fun handleMqttMessage(message: MqttMessage?) {
        if (message == null) return

        try {
            val payload = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String(message.payload, java.nio.charset.StandardCharsets.UTF_8)
            } else {
                String(message.payload)
            }

            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(payload).jsonObject

            // Keep the raw event locally so a payload shape we don't recognise can be
            // diagnosed from the in-app Diagnostics screen (FileLogger redacts tokens,
            // phone numbers and OTPs).
            FileLogger.log(this, "RawEvent", "RAW EVENT: ${payload.take(3000)}")

            val eventType = root["data"]?.jsonObject?.get("event_type")?.jsonPrimitive?.content
            val msgId = root["id"]?.jsonPrimitive?.content

            // Staleness check only applies to order_cancelled.
            // order_claimed is historical confirmation — filtering it loses analytics.
            val timestamp = root["timestamp"]?.jsonPrimitive?.longOrNull
            if (eventType == "order_cancelled" && timestamp != null) {
                val now = System.currentTimeMillis()
                val eventTimeMs = if (timestamp < 10000000000L) timestamp * 1000 else timestamp
                if (now - eventTimeMs > MESSAGE_STALE_MS) {
                    FileLogger.log(this, "Logic", "Ignored STALE message ($msgId). Age: ${(now - eventTimeMs) / 1000}s")
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
                "order_cancelled" -> handleOrderCancelled(msgId, root)
                "order_claimed" -> handleOrderClaimed(root)
                else -> return
            }

        } catch (e: Exception) {
            FileLogger.log(this, "Logic", "Error processing: ${e.message}")
        }
    }

    private fun handleOrderCancelled(msgId: String?, root: JsonObject) {
        FileLogger.log(this, "Logic", ">>> NEW FRESH ORDER CANCELLED ($msgId) <<<")

        val parsed = RescueEventParser.parse(root)
        FileLogger.log(
            this,
            "History",
            "Parsed deal | name=${parsed.restaurantName} id=${parsed.restaurantId} " +
                "final=${parsed.finalCost} catalog=${parsed.catalogTotal} items=${parsed.items.size} " +
                "detail=${parsed.hasDetail}"
        )
        val eventTime = parsed.eventTime ?: System.currentTimeMillis()
        val eventId = msgId ?: "evt-$eventTime"

        val lastNotificationTime = ZomatoManager.getLastNotificationTime(this)
        val now = System.currentTimeMillis()
        val timeSinceLast = now - lastNotificationTime
        val shouldNotify = timeSinceLast >= NOTIFICATION_COOLDOWN_MS

        // Record every deal, including the ones the cooldown keeps quiet.
        RescueHistoryStore.recordObserved(this, eventId, eventTime, shouldNotify, parsed)

        if (shouldNotify) {
            FileLogger.log(this, "Logic", "Cooldown expired ($timeSinceLast > $NOTIFICATION_COOLDOWN_MS). Sending Notification.")
            FoodRescueAlerter.post(this, eventId)
            ZomatoManager.saveLastNotification(this, now)
        } else {
            val remaining = (NOTIFICATION_COOLDOWN_MS - timeSinceLast) / 1000
            FileLogger.log(this, "Logic", "Notification suppressed. Cooldown active (${remaining}s remaining).")
        }
    }

    private suspend fun handleOrderClaimed(root: JsonObject) {
        val identifier = root["data"]?.jsonObject
            ?.get("success_actions")?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("food_rescue_order_claimed")?.jsonObject
            ?.get("identifier")?.jsonPrimitive?.content

        if (identifier == null) {
            FileLogger.log(this, "Logic", "order_claimed: missing identifier")
            return
        }

        FileLogger.log(this, "Logic", "order_claimed: checking identifier $identifier")

        val sessionId = ZomatoManager.getFoodRescueSessionId(this)
        if (sessionId == null) {
            FileLogger.log(this, "Logic", "order_claimed: no session ID stored")
            return
        }

        val session = ZomatoManager.getSession(this, sessionId)
        if (session == null) {
            FileLogger.log(this, "Logic", "order_claimed: session not found for $sessionId")
            return
        }

        val orderDetails = ApiClient.getOrderSummary(this, identifier, session.accessToken)

        if (orderDetails != null) {
            FileLogger.log(this, "Logic", "Order $identifier claimed by our user. Cart: ${orderDetails.cartTotal}, Paid: ${orderDetails.paidAmount}")
            ZomatoManager.saveOrderClaimedState(this, identifier, orderDetails)
            RescueHistoryStore.markClaimed(this, identifier, orderDetails)
            if (orderDetails.cartTotal != null && orderDetails.paidAmount != null) {
                AnalyticsManager.pingFoodRescue(this, identifier, orderDetails.cartTotal, orderDetails.paidAmount)
            } else {
                FileLogger.log(this, "Analytics", "Skipping: cartTotal or paidAmount null for $identifier")
            }
        } else {
            FileLogger.log(this, "Logic", "Order $identifier not owned by our user")
            ZomatoManager.saveOrderClaimedState(this, identifier, null)
        }
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

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null
}