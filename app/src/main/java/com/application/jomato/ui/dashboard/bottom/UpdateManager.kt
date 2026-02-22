package com.application.jomato.ui.dashboard.bottom

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.application.jomato.BuildConfig
import com.application.jomato.utils.FileLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object UpdateManager {

    private val httpClient = OkHttpClient()

    suspend fun checkForUpdate(context: Context): UpdateState {
        val urls = listOf(
            "https://api.github.com/repos/jatin-dot-py/jomato-mobile/releases/latest",
            BuildConfig.FALLBACK_UPDATE_URL
        )

        for (url in urls) {
            try {
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    continue
                }
                val body = response.body?.string()
                response.close()
                if (body.isNullOrBlank()) continue

                val json = JSONObject(body)
                val latestTag = json.getString("tag_name")
                val asset = json.getJSONArray("assets").getJSONObject(0)
                val apkUrl = asset.getString("browser_download_url")
                val digest = asset.getString("digest")
                val sha256 = digest.removePrefix("sha256:")

                @Suppress("DEPRECATION")
                val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName

                return if (latestTag != currentVersion) {
                    UpdateState.Available(latestTag, apkUrl, sha256)
                } else {
                    UpdateState.Idle
                }
            } catch (e: Exception) {
                FileLogger.log(context, "UpdateManager", "Update check failed for $url: ${e.message}", e)
                continue
            }
        }
        return UpdateState.Idle
    }

    suspend fun downloadAndVerify(
        context: Context,
        apkUrl: String,
        sha256: String,
        onProgress: (Int) -> Unit
    ): File? {
        val apkFile = File(context.cacheDir, "$sha256.apk")

        // If file already exists, verify hash and reuse it
        if (apkFile.exists()) {
            onProgress(-1)
            val computedHash = computeSha256(apkFile)
            if (computedHash == sha256) {
                return apkFile
            } else {
                // File is corrupt, delete and re-download
                apkFile.delete()
            }
        }

        try {
            val request = Request.Builder().url(apkUrl).build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                response.close()
                return null
            }

            val responseBody = response.body ?: run { response.close(); return null }
            val contentLength = responseBody.contentLength()

            FileOutputStream(apkFile).use { fos ->
                responseBody.byteStream().use { inputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Long = 0
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        fos.write(buffer, 0, read)
                        bytesRead += read
                        if (contentLength > 0) {
                            onProgress(((bytesRead * 100) / contentLength).toInt())
                        }
                    }
                }
            }
            response.close()

            // Verify sha256 after download
            onProgress(-1)
            val computedHash = computeSha256(apkFile)
            if (computedHash != sha256) {
                FileLogger.log(
                    context,
                    "UpdateManager",
                    "SHA256 mismatch: expected $sha256, got $computedHash",
                    Exception("SHA256 mismatch")
                )
                apkFile.delete()
                return null
            }

            return apkFile

        } catch (e: Exception) {
            FileLogger.log(context, "UpdateManager", "Download failed: ${e.message}", e)
            apkFile.delete()
            return null
        }
    }

    fun installApk(context: Context, apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { inputStream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}