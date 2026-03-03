package com.application.jomato.entity.zomato.api

import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.brotli.BrotliInterceptor
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

internal object ApiBase {
    const val TAG = "ZomatoApiClient"

    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(BrotliInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    private val sessionUuid = UUID.randomUUID().toString()
    private val appSessionId = UUID.randomUUID().toString()
    private val accessUuid = UUID.randomUUID().toString()
    private val androidId = randomHex(16)
    private val firebaseInstanceId = randomHex(32)
    private val jumboSessionId = "${UUID.randomUUID()}${System.currentTimeMillis()}"
    private val appsflyerUid = "${System.currentTimeMillis()}-${Random.nextLong(1_000_000_000_000_000_000, Long.MAX_VALUE)}"

    val commonHeaders: Headers = Headers.Builder()
        // Connection & encoding
        .add("Accept", "image/webp")
        .add("Connection", "Keep-Alive")

        // Zomato core
        .add("X-Zomato-API-Key", "7749b19667964b87a3efc739e254ada2")
        .add("X-Zomato-App-Version", "931")
        .add("X-Zomato-App-Version-Code", "1710019310")
        .add("X-Zomato-Client-Id", "5276d7f1-910b-4243-92ea-d27e758ad02b")
        .add("X-Zomato-UUID", sessionUuid)
        .add("X-Client-Id", "zomato_android_v2")

        // Device fingerprint
        .add("User-Agent", "&source=android_market&version=10&device_manufacturer=Google&device_brand=google&device_model=Android+SDK+built+for+x86_64&api_version=931&app_version=v19.3.1")
        .add("X-Android-Id", androidId)
        .add("X-Device-Height", "2208")
        .add("X-Device-Width", "1080")
        .add("X-Device-Pixel-Ratio", "2.75")
        .add("X-Device-Language", "en")

        // App state
        .add("X-APP-APPEARANCE", "LIGHT")
        .add("X-APP-THEME", "default")
        .add("X-SYSTEM-APPEARANCE", "UNSPECIFIED")
        .add("X-App-Language", "&lang=en&android_language=en&android_country=")
        .add("X-App-Session-Id", appSessionId)

        // Session & tracking IDs
        .add("X-Access-UUID", accessUuid)
        .add("X-Request-Id", UUID.randomUUID().toString())
        .add("X-Jumbo-Session-Id", jumboSessionId)
        .add("X-Appsflyer-UID", appsflyerUid)
        .add("X-FIREBASE-INSTANCE-ID", firebaseInstanceId)
        .add("X-Installer-Package-Name", "cm.aptoide.pt")

        // Location defaults
        .add("X-City-Id", "-1")
        .add("X-O2-City-Id", "-1")
        .add("X-Present-Lat", "0.0")
        .add("X-Present-Long", "0.0")
        .add("X-Present-Horizontal-Accuracy", "-1")
        .add("X-User-Defined-Lat", "0.0")
        .add("X-User-Defined-Long", "0.0")

        // Network & device state
        .add("X-Network-Type", "mobile_UNKNOWN")
        .add("X-Bluetooth-On", "false")
        .add("X-VPN-Active", "1")

        // Accessibility
        .add("X-Accessibility-Dynamic-Text-Scale-Factor", "1.0")
        .add("X-Accessibility-Voice-Over-Enabled", "0")

        // Feature flags
        .add("X-BLINKIT-INSTALLED", "false")
        .add("X-DISTRICT-INSTALLED", "false")
        .add("X-RIDER-INSTALLED", "false")

        // Akamai CDN
        .add("is-akamai-video-optimisation-enabled", "0")
        .add("pragma", "akamai-x-get-request-id,akamai-x-cache-on, akamai-x-check-cacheable")

        // Priority
        .add("USER-BUCKET", "0")
        .add("USER-HIGH-PRIORITY", "0")
        .add("x-perf-class", "PERFORMANCE_AVERAGE")

        .build()

    val jsonParser: Json = Json { ignoreUnknownKeys = true }
    val jsonMediaType: MediaType = "application/json; charset=UTF-8".toMediaType()

    fun authenticatedHeaders(accessToken: String): Headers =
        commonHeaders.newBuilder()
            .add("X-Zomato-Access-Token", accessToken)
            .build()

    private fun randomHex(length: Int): String =
        (1..length).joinToString("") { Random.nextInt(16).toString(16) }
}
