package com.application.jomato.utils

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.application.jomato.BuildConfig
import com.application.jomato.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object AnalyticsManager {

    private val client = OkHttpClient()


    suspend fun pingAppOpen(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val installId = Prefs.getInstallId(context)
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val versionCode = PackageInfoCompat.getLongVersionCode(pInfo).toInt()


                val payload = JSONObject().apply {
                    put("install_id", installId)
                    put("version_name", pInfo.versionName)
                    put("version_code", versionCode)
                    put("android_version", Build.VERSION.RELEASE)
                    put("event", "app_open")
                }

                val request = Request.Builder()
                    .url(BuildConfig.WORKER_URL)
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().close()
            } catch (e: Exception) {
                FileLogger.log(context, "AnalyticsManager", "Ping failed: ${e.message}", e)
            }
        }
    }
}