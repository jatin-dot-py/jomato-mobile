package com.application.jomato.config

import android.content.Context
import com.application.jomato.BuildConfig
import com.application.jomato.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * Singleton that fetches ui config from the server and runs integrity checks.
 * Use [fetch] then [checkIntegrity] (integrity uses the digest of the installed APK, computed internally).
 */
object UiConfigManager {

    private const val TAG = "UiConfigManager"
    private const val PATH = "/ui.json"

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    var config: UiConfig? = null
        private set

    suspend fun fetch(context: Context) {
        val hosts = listOf(
            "https://${BuildConfig.UI_JSON_HOST_PRIMARY}",
            "https://${BuildConfig.UI_JSON_HOST_FALLBACK}"
        )

        for (host in hosts) {
            try {
                val request = Request.Builder().url("$host$PATH").build()
                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                if (!response.isSuccessful) { response.close(); continue }
                val body = response.body?.string()
                response.close()
                if (body.isNullOrBlank()) continue
                config = json.decodeFromString<UiConfig>(body)
                EntityRegistry.updateFrom(config)
                FileLogger.log(context, TAG, "ui fetched from $host")
                return
            } catch (e: Exception) {
                FileLogger.log(context, TAG, "Failed to fetch from $host | ${e.message}", e)
                continue
            }
        }

        FileLogger.log(context, TAG, "All hosts failed, running without ui.json")
    }

    /**
     * Runs integrity check using the installed APK's SHA-256 digest (computed here).
     * Call after [fetch]; uses [config] from the last successful fetch.
     */
    fun checkIntegrity(context: Context): IntegrityResult {
        val installedDigest = installedApkDigest(context) ?: run {
            FileLogger.log(context, TAG, "Could not compute APK digest, skipping integrity check")
            return IntegrityResult.Unknown
        }

        val integrity = config?.integrity ?: run {
            FileLogger.log(context, TAG, "No config loaded, skipping integrity check")
            return IntegrityResult.Unknown
        }

        val match = integrity.versions.any { it.digest.equals(installedDigest, ignoreCase = true) }
        return if (match) {
            FileLogger.log(context, TAG, "Integrity check passed for digest: $installedDigest")
            IntegrityResult.Pass
        } else {
            FileLogger.log(context, TAG, "Integrity check FAILED for digest: $installedDigest")
            IntegrityResult.Fail(
                message = integrity.onMismatch.message,
                strict = integrity.onMismatch.strict
            )
        }
    }

    fun getInstalledApkDigest(context: Context): String? = installedApkDigest(context)

    private fun installedApkDigest(context: Context): String? = try {
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        val apkFile = File(appInfo.sourceDir)
        val digest = MessageDigest.getInstance("SHA-256")
        apkFile.inputStream().use { inputStream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        "sha256:" + digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        FileLogger.log(context, TAG, "Failed to compute APK digest: ${e.message}", e)
        null
    }
}

sealed class IntegrityResult {
    object Pass : IntegrityResult()
    object Unknown : IntegrityResult()
    data class Fail(val message: String, val strict: Boolean) : IntegrityResult()
}