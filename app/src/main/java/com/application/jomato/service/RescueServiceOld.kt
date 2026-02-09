//package com.application.jomato.service
//
//import android.app.*
//import android.content.Context
//import android.content.Intent
//import android.os.Build
//import android.os.IBinder
//import android.os.PowerManager
//import androidx.core.app.NotificationCompat
//import com.application.jomato.MainActivity
//import com.application.jomato.Prefs
//import com.application.jomato.R
//import com.application.jomato.api.ApiClient
//import com.application.jomato.utils.FileLogger
//import kotlinx.coroutines.*
//import kotlinx.serialization.json.Json
//import kotlinx.serialization.json.jsonObject
//import kotlinx.serialization.json.jsonPrimitive
//import org.eclipse.paho.client.mqttv3.*
//import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
//import java.security.SecureRandom
//import java.security.cert.X509Certificate
//import java.util.concurrent.ConcurrentHashMap
//import javax.net.ssl.SSLContext
//import javax.net.ssl.TrustManager
//import javax.net.ssl.X509TrustManager
//
//
//class FoodRescueServiceOld : Service() {
//
//    private val serviceJob = SupervisorJob()
//    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
//
//    private var wakeLock: PowerManager.WakeLock? = null
//
//    private var mqttClient: MqttClient? = null
//    private val processedMessageIds = ConcurrentHashMap<String, Long>()
//    private var isRunning = false
//
//    companion object {
//        const val ACTION_STOP = "com.application.jomato.STOP_SERVICE"
//        const val CHANNEL_ID_FOREGROUND = "jomato_service_channel"
//        const val CHANNEL_ID_ALERTS = "jomato_alerts_channel"
//        const val NOTIFICATION_ID = 1001
//        const val TARGET_PACKAGE = "com.application.zomato"
//    }
//
//    override fun onCreate() {
//        super.onCreate()
//        FileLogger.log(this, "Service", "Created")
//
//        val pm = getSystemService(POWER_SERVICE) as PowerManager
//        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Jomato:ReliabilityLock")
//        wakeLock?.acquire()
//        FileLogger.log(this, "Service", "WakeLock acquired")
//
//        createNotificationChannels()
//        startForeground(NOTIFICATION_ID, createForegroundNotification("Initializing..."))
//    }
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        if (intent?.action == ACTION_STOP) {
//            FileLogger.log(this, "Service", "Stop Requested")
//            Prefs.stopFoodRescue(this)
//            stopSelf()
//            return START_NOT_STICKY
//        }
//
//        if (!isRunning) {
//            isRunning = true
//            startReliabilityLoop()
//        }
//
//        return START_STICKY
//    }
//
//    private fun startReliabilityLoop() {
//        serviceScope.launch {
//            FileLogger.log(this@FoodRescueService, "Service", "Loop Started")
//
//            while (isActive) {
//                try {
//                    val state = Prefs.getFoodRescueState(this@FoodRescueService)
//
//                    if (state == null) {
//                        FileLogger.log(this@FoodRescueService, "Service", "Feature Disabled. Shutting down.")
//                        stopSelf()
//                        break
//                    }
//
//                    // Get screen state
//                    val pm = getSystemService(POWER_SERVICE) as PowerManager
//                    val isScreenOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
//                        pm.isInteractive
//                    } else {
//                        @Suppress("DEPRECATION")
//                        pm.isScreenOn
//                    }
//                    val screenStatus = if (isScreenOn) "SCREEN_ON" else "SCREEN_OFF"
//
//                    // Check connection status
//                    val isConnected = mqttClient?.isConnected ?: false
//                    val connStatus = if (isConnected) "CONNECTED" else "DISCONNECTED"
//
//                    // Heartbeat log
//                    FileLogger.log(
//                        this@FoodRescueService,
//                        "Heartbeat",
//                        "$connStatus | $screenStatus | Loc: ${state.location.name} | Msgs: ${state.totalMessages} | Reconnects: ${state.totalReconnects}"
//                    )
//
//                    updateNotification("Monitoring: ${state.location.name}")
//
//                    if (mqttClient == null || !mqttClient!!.isConnected) {
//                        FileLogger.log(
//                            this@FoodRescueService,
//                            "Service",
//                            "MQTT not connected, attempting connection..."
//                        )
//                        connectMqtt(state.essentials.foodRescue!!)
//                    }
//
//                    val now = System.currentTimeMillis()
//                    val sizeBefore = processedMessageIds.size
//                    processedMessageIds.entries.removeIf { it.value < now - 300_000 }
//                    val sizeAfter = processedMessageIds.size
//                    if (sizeBefore != sizeAfter) {
//                        FileLogger.log(
//                            this@FoodRescueService,
//                            "Cache",
//                            "Cleaned ${sizeBefore - sizeAfter} old message IDs (${sizeAfter} remaining)"
//                        )
//                    }
//
//                } catch (e: Exception) {
//                    FileLogger.log(this@FoodRescueService, "Service", "Loop Error: ${e.message}", e)
//                }
//
//                delay(30_000)
//            }
//        }
//    }
//
//    private suspend fun connectMqtt(config: com.application.jomato.api.FoodRescueConf) {
//        try {
//            // Clean up old connection
//            if (mqttClient != null) {
//                try {
//                    FileLogger.log(this, "MQTT", "Cleaning up old client...")
//                    mqttClient?.setCallback(null)
//                    mqttClient?.disconnect()
//                    mqttClient?.close()
//                } catch (e: Exception) {
//                    FileLogger.log(this, "MQTT", "Error cleaning up old client: ${e.message}", e)
//                }
//            }
//
//            val brokerUrl = "ssl://hedwig.zomato.com:443"
//            val clientId = "jomato_android_${System.currentTimeMillis()}"
//
//            FileLogger.log(this, "MQTT", "Creating new client with ID: $clientId")
//            mqttClient = MqttClient(brokerUrl, clientId, MemoryPersistence())
//
//            // CRITICAL: Set callback BEFORE connecting
//            FileLogger.log(this, "MQTT", "Setting callback handlers...")
//            mqttClient?.setCallback(object : MqttCallback {
//                override fun connectionLost(cause: Throwable?) {
//                    val timestamp = System.currentTimeMillis()
//                    val reason = cause?.message ?: "Unknown"
//                    val causeClass = cause?.javaClass?.simpleName ?: "Unknown"
//
//                    FileLogger.log(
//                        this@FoodRescueService,
//                        "MQTT",
//                        ">>> CONNECTION LOST <<< at $timestamp | Cause: $causeClass | Reason: $reason"
//                    )
//
//                    // Increment reconnect count
//                    Prefs.incrementReconnCount(this@FoodRescueService)
//
//                    val currentCount = Prefs.getFoodRescueState(this@FoodRescueService)?.totalReconnects ?: 0
//                    FileLogger.log(
//                        this@FoodRescueService,
//                        "MQTT",
//                        "Reconnect count incremented to: $currentCount"
//                    )
//                }
//
//                override fun messageArrived(topic: String?, message: MqttMessage?) {
//                    val timestamp = System.currentTimeMillis()
//                    val size = message?.payload?.size ?: 0
//
//                    FileLogger.log(
//                        this@FoodRescueService,
//                        "MQTT",
//                        "Message arrived at $timestamp | Topic: $topic | Size: $size bytes"
//                    )
//
//                    Prefs.incrementMsgCount(this@FoodRescueService)
//
//                    val currentCount = Prefs.getFoodRescueState(this@FoodRescueService)?.totalMessages ?: 0
//                    FileLogger.log(
//                        this@FoodRescueService,
//                        "MQTT",
//                        "Message count incremented to: $currentCount"
//                    )
//
//                    serviceScope.launch { handleMqttMessage(message) }
//                }
//
//                override fun deliveryComplete(token: IMqttDeliveryToken?) {
//                    FileLogger.log(this@FoodRescueService, "MQTT", "Delivery complete")
//                }
//            })
//
//            val connOpts = MqttConnectOptions().apply {
//                userName = config.client.username
//                password = config.client.password.toCharArray()
//                isCleanSession = true
//                keepAliveInterval = config.client.keepalive
//                isAutomaticReconnect = true
//                connectionTimeout = 30
//                socketFactory = getUnsafeSocketFactory()
//            }
//
//            FileLogger.log(
//                this,
//                "MQTT",
//                "Connecting to $brokerUrl | Username: ${config.client.username} | KeepAlive: ${config.client.keepalive}s | AutoReconnect: ${connOpts.isAutomaticReconnect}"
//            )
//
//            mqttClient?.connect(connOpts)
//
//            FileLogger.log(this, "MQTT", "Connected successfully!")
//
//            FileLogger.log(this, "MQTT", "Subscribing to: ${config.channelName} (QoS ${config.qos})")
//            mqttClient?.subscribe(config.channelName, config.qos)
//
//            FileLogger.log(this, "MQTT", "Subscribed successfully! Connection complete.")
//
//        } catch (e: Exception) {
//            FileLogger.log(
//                this,
//                "MQTT",
//                "Connection failed: ${e.javaClass.simpleName} | ${e.message}",
//                e
//            )
//
//            // Increment reconnect count on connection failure
//            Prefs.incrementReconnCount(this@FoodRescueService)
//
//            val currentCount = Prefs.getFoodRescueState(this)?.totalReconnects ?: 0
//            FileLogger.log(
//                this,
//                "MQTT",
//                "Connection attempt failed, reconnect count: $currentCount"
//            )
//        }
//    }
//
//    private suspend fun handleMqttMessage(message: MqttMessage?) {
//        if (message == null) {
//            FileLogger.log(this, "Logic", "Received null message")
//            return
//        }
//
//        try {
//            val payload = String(message.payload)
//            val preview = if (payload.length > 100) "${payload.take(100)}..." else payload
//            FileLogger.log(this, "Logic", "Processing payload: $preview")
//
//            val json = Json { ignoreUnknownKeys = true }
//            val root = json.parseToJsonElement(payload).jsonObject
//
//            val eventType = root["data"]?.jsonObject?.get("event_type")?.jsonPrimitive?.content
//            val msgId = root["id"]?.jsonPrimitive?.content
//
//            FileLogger.log(this, "Logic", "Parsed | EventType: $eventType | MsgID: $msgId")
//
//            if (eventType != "order_cancelled") {
//                FileLogger.log(this, "Logic", "Ignoring non-cancel event: $eventType")
//                return
//            }
//
//            if (msgId != null) {
//                if (processedMessageIds.containsKey(msgId)) {
//                    FileLogger.log(this, "Logic", "Duplicate message detected: $msgId (already processed)")
//                    return
//                }
//                processedMessageIds[msgId] = System.currentTimeMillis()
//                FileLogger.log(this, "Logic", "New message ID cached: $msgId")
//            }
//
//            FileLogger.log(this, "Logic", ">>> ORDER CANCELLED EVENT DETECTED <<<")
//
//            val state = Prefs.getFoodRescueState(this)
//            if (state == null) {
//                FileLogger.log(this, "Logic", "ERROR: Cannot retrieve Food Rescue state")
//                return
//            }
//
//            val token = Prefs.getToken(this)
//            if (token == null) {
//                FileLogger.log(this, "Logic", "ERROR: Cannot retrieve auth token")
//                return
//            }
//
//            FileLogger.log(this, "Logic", "1. Fetching cart info for location: ${state.location.name}")
//            // 1. Get cart details from the "view" endpoint
//            val cart = ApiClient.getFoodRescueCart(this, state.location, state.essentials, token)
//
//            if (cart != null) {
//                FileLogger.log(
//                    this,
//                    "Logic",
//                    "Cart info retrieved | ResID: ${cart.resId} | Cost: ₹${cart.cartFinalCost}"
//                )
//
//                // 2. ACTUALLY CREATE THE CART (Claim it immediately)
//                FileLogger.log(this, "Logic", "2. Triggering cart creation to claim item...")
//                val creationResponse = ApiClient.createFoodRescueCart(
//                    this,
//                    cart,
//                    state.location,
//                    state.essentials,
//                    token
//                )
//
//                if (creationResponse != null) {
//                    FileLogger.log(this, "Logic", ">>> CART CREATED/CLAIMED SUCCESSFULLY <<<")
//                } else {
//                    FileLogger.log(this, "Logic", ">>> FAILED TO CREATE CART <<< (Will notify anyway)")
//                }
//
//                val meta = ApiClient.getRestaurantMeta(this, cart.resId, token)
//                val resName = meta?.name ?: "Restaurant"
//
//                FileLogger.log(this, "Logic", "Restaurant name: $resName")
//                sendAlertNotification(resName, cart.cartFinalCost, cart.viewersCount)
//            } else {
//                FileLogger.log(this, "Logic", "No cart available for this cancelled order")
//            }
//        } catch (e: Exception) {
//            FileLogger.log(
//                this,
//                "Logic",
//                "Error processing message: ${e.javaClass.simpleName} | ${e.message}",
//                e
//            )
//        }
//    }
//
//    private fun sendAlertNotification(resName: String, price: Double, viewers: Int) {
//        FileLogger.log(
//            this,
//            "Notification",
//            "Creating alert | Restaurant: $resName | Price: ₹$price | Viewers: $viewers"
//        )
//
//        val launchIntent = packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)
//
//        val finalIntent = if (launchIntent != null) {
//            FileLogger.log(this, "Notification", "Targeting Zomato app: $TARGET_PACKAGE")
//            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
//            launchIntent
//        } else {
//            FileLogger.log(this, "Notification", "Zomato app not found. Falling back to Jomato.")
//            Intent(this, MainActivity::class.java).apply {
//                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
//                putExtra("route", "food_rescue")
//            }
//        }
//
//        val pendingIntent = PendingIntent.getActivity(this, 0, finalIntent, PendingIntent.FLAG_IMMUTABLE)
//
//        val text = "CLAIMED! Pay ₹$price fast. $viewers watching."
//
//        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALERTS)
//            .setSmallIcon(R.drawable.ic_launcher_foreground)
//            .setContentTitle("Rescue: $resName")
//            .setContentText(text)
//            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
//            .setPriority(NotificationCompat.PRIORITY_MAX) // High priority for heads-up
//            .setCategory(NotificationCompat.CATEGORY_ALARM)
//            .setDefaults(NotificationCompat.DEFAULT_ALL)
//            .setFullScreenIntent(pendingIntent, true)
//            .setContentIntent(pendingIntent)
//            .setAutoCancel(true)
//            .build()
//
//        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//        val notifId = System.currentTimeMillis().toInt()
//        notificationManager.notify(notifId, notification)
//
//        FileLogger.log(this, "Notification", "Alert notification sent with ID: $notifId")
//    }
//
//    private fun updateNotification(text: String) {
//        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//        notificationManager.notify(NOTIFICATION_ID, createForegroundNotification(text))
//    }
//
//    private fun createForegroundNotification(status: String): Notification {
//        val stopIntent = Intent(this, FoodRescueService::class.java).apply { action = ACTION_STOP }
//        val stopPending = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)
//
//        val appIntent = Intent(this, MainActivity::class.java)
//        val appPending = PendingIntent.getActivity(this, 0, appIntent, PendingIntent.FLAG_IMMUTABLE)
//
//        return NotificationCompat.Builder(this, CHANNEL_ID_FOREGROUND)
//            .setContentTitle("Jomato Food Rescue")
//            .setContentText(status)
//            .setSmallIcon(R.drawable.ic_launcher_foreground)
//            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)
//            .setContentIntent(appPending)
//            .setOnlyAlertOnce(true)
//            .setOngoing(true)
//            .build()
//    }
//
//    private fun createNotificationChannels() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//
//            val serviceChannel = NotificationChannel(
//                CHANNEL_ID_FOREGROUND,
//                "Background Service",
//                NotificationManager.IMPORTANCE_LOW
//            )
//            manager.createNotificationChannel(serviceChannel)
//            FileLogger.log(this, "Service", "Created foreground notification channel")
//
//            val alertChannel = NotificationChannel(
//                CHANNEL_ID_ALERTS,
//                "Food Rescue Alerts",
//                NotificationManager.IMPORTANCE_HIGH
//            ).apply {
//                enableVibration(true)
//                enableLights(true)
//                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
//            }
//            manager.createNotificationChannel(alertChannel)
//            FileLogger.log(this, "Service", "Created alerts notification channel")
//        }
//    }
//
//    private fun getUnsafeSocketFactory(): javax.net.SocketFactory {
//        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
//            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
//            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
//            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
//        })
//        val sslContext = SSLContext.getInstance("SSL")
//        sslContext.init(null, trustAllCerts, SecureRandom())
//        return sslContext.socketFactory
//    }
//
//    override fun onDestroy() {
//        FileLogger.log(this, "Service", ">>> onDestroy called <<<")
//        isRunning = false
//
//        serviceScope.cancel()
//        FileLogger.log(this, "Service", "Coroutine scope cancelled")
//
//        try {
//            if (mqttClient != null) {
//                mqttClient?.setCallback(null)
//                mqttClient?.disconnect()
//                mqttClient?.close()
//                FileLogger.log(this, "Service", "MQTT client disconnected and closed")
//            }
//        } catch (e: Exception) {
//            FileLogger.log(this, "Service", "Error disconnecting MQTT: ${e.message}", e)
//        }
//
//        try {
//            if (wakeLock?.isHeld == true) {
//                wakeLock?.release()
//                FileLogger.log(this, "Service", "WakeLock released")
//            }
//        } catch (e: Exception) {
//            FileLogger.log(this, "Service", "Error releasing WakeLock: ${e.message}", e)
//        }
//
//        super.onDestroy()
//        FileLogger.log(this, "Service", "Service destroyed completely")
//    }
//
//    override fun onBind(intent: Intent?): IBinder? = null
//}