package com.application.jomato.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.application.jomato.config.UiConfigManager
import com.application.jomato.widgets.WidgetRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Bottom bar: shows exactly one of [jomato_update] or [jomato_attribution].
 * Update when app versionName != update widget payload version; else attribution.
 * When config is null (e.g. fetch failed), shows a minimal bar so the layout is never empty.
 */
@Composable
fun DashboardBottomBar() {
    val context = LocalContext.current
    val config = UiConfigManager.config
    val updateConfig = config?.widgets?.find { it.type == "jomato_update" }
    val attributionConfig = config?.widgets?.find { it.type == "jomato_attribution" }

    var showUpdate by remember { mutableStateOf(false) }

    LaunchedEffect(updateConfig) {
        if (updateConfig == null) {
            showUpdate = false
            return@LaunchedEffect
        }
        val payload = runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromJsonElement<UpdatePayloadForCheck>(updateConfig.payload)
        }.getOrNull() ?: run {
            showUpdate = false
            return@LaunchedEffect
        }
        val installedVersion = withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: ""
        }
        showUpdate = installedVersion != payload.version
    }

    when {
        showUpdate && updateConfig != null ->
            WidgetRegistry.resolve("jomato_update")?.Display(updateConfig.payload)
        attributionConfig != null ->
            WidgetRegistry.resolve("jomato_attribution")?.Display(attributionConfig.payload)
        else ->
            Box(Modifier.fillMaxWidth().height(56.dp))
    }
}

@Serializable
private data class UpdatePayloadForCheck(val version: String = "")
