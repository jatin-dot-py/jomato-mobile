package com.application.jomato.widgets

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.application.jomato.config.UiConfigManager
import com.application.jomato.ui.theme.JomatoTheme
import com.application.jomato.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

private sealed class UpdateState {
    object Idle : UpdateState()
    data class Available(val version: String, val apkUrl: String, val sha256: String) : UpdateState()
    data class Downloading(val progress: Int) : UpdateState()
    object Verifying : UpdateState()
    data class ReadyToInstall(val file: File) : UpdateState()
    data class Error(val apkUrl: String, val sha256: String) : UpdateState()
}

class UpdateWidget : BaseWidget() {

    override val type: String = "jomato_update"

    @Composable
    override fun Display(payload: JsonElement) {
        val data = runCatching {
            Json.decodeFromJsonElement<UpdatePayload>(payload)
        }.getOrNull() ?: return

        val context = LocalContext.current
        var updateState by remember(data) {
            mutableStateOf<UpdateState>(
                UpdateState.Available(
                    version = data.version,
                    apkUrl = data.browser_download_url,
                    sha256 = data.digest.removePrefix("sha256:")
                )
            )
        }

        val scope = rememberCoroutineScope()
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    when (val state = updateState) {
                        is UpdateState.Available -> {
                            updateState = UpdateState.Downloading(0)
                            scope.launch(Dispatchers.IO) {
                                val file = downloadAndVerify(
                                    context = context,
                                    apkUrl = state.apkUrl,
                                    sha256 = state.sha256,
                                    onProgress = { progress ->
                                        updateState = if (progress == -1) UpdateState.Verifying
                                        else UpdateState.Downloading(progress)
                                    }
                                )
                                updateState = if (file != null) UpdateState.ReadyToInstall(file)
                                else UpdateState.Error(state.apkUrl, state.sha256)
                            }
                        }
                        is UpdateState.ReadyToInstall -> installApk(context, state.file)
                        is UpdateState.Error -> {
                            updateState = UpdateState.Downloading(0)
                            scope.launch(Dispatchers.IO) {
                                val file = downloadAndVerify(
                                    context = context,
                                    apkUrl = state.apkUrl,
                                    sha256 = state.sha256,
                                    onProgress = { progress ->
                                        updateState = if (progress == -1) UpdateState.Verifying
                                        else UpdateState.Downloading(progress)
                                    }
                                )
                                updateState = if (file != null) UpdateState.ReadyToInstall(file)
                                else UpdateState.Error(state.apkUrl, state.sha256)
                            }
                        }
                        else -> { }
                    }
                },
            color = JomatoTheme.SecondaryBg,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val textToShow = when (val state = updateState) {
                    is UpdateState.Available -> "Tap to update app (${state.version})"
                    is UpdateState.Downloading -> "Downloading update… ${state.progress}%"
                    is UpdateState.Verifying -> "Verifying download…"
                    is UpdateState.ReadyToInstall -> "Tap to install"
                    is UpdateState.Error -> "Download failed. Tap to retry."
                    else -> ""
                }
                Text(
                    text = textToShow,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = JomatoTheme.BrandBlack,
                    fontSize = 14.sp
                )
                if (updateState is UpdateState.Available || updateState is UpdateState.ReadyToInstall) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = JomatoTheme.Brand,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "NEW",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = JomatoTheme.Background,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "UpdateWidget"
        private val httpClient = OkHttpClient()

        private fun downloadAndVerify(
            context: Context,
            apkUrl: String,
            sha256: String,
            onProgress: (Int) -> Unit
        ): File? {
            val apkFile = File(context.cacheDir, "$sha256.apk")
            if (apkFile.exists()) {
                onProgress(-1)
                val computedHash = computeSha256(apkFile)
                if (computedHash == sha256) return apkFile
                apkFile.delete()
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
                onProgress(-1)
                val computedHash = computeSha256(apkFile)
                if (computedHash != sha256) {
                    FileLogger.log(context, TAG, "SHA256 mismatch: expected $sha256, got $computedHash", Exception("SHA256 mismatch"))
                    apkFile.delete()
                    return null
                }
                return apkFile
            } catch (e: Exception) {
                FileLogger.log(context, TAG, "Download failed: ${e.message}", e)
                apkFile.delete()
                return null
            }
        }

        private fun installApk(context: Context, apkFile: File) {
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
}

@Serializable
private data class UpdatePayload(
    val version: String = "",
    val browser_download_url: String = "",
    val digest: String = "",
    val changelog: String = ""
)
