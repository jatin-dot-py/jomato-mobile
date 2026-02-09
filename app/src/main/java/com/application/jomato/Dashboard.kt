package com.application.jomato

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.application.jomato.auth.AuthClient
import com.application.jomato.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

private val LightBg = Color(0xFFF4F4F4)

data class JomatoFeature(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isNew: Boolean = false
)

@Composable
fun DashboardScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userName = Prefs.getUserName(context) ?: "Foodie"
    var activeState by remember { mutableStateOf(Prefs.getFoodRescueState(context)) }
    var showLogDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    fun performLogout() {
        FileLogger.log(context, "Dashboard", "User initiated logout")
        showLogoutDialog = false
        val access = Prefs.getToken(context) ?: ""
        val refresh = Prefs.getRefreshToken(context) ?: ""
        scope.launch(Dispatchers.IO) {
            try {
                AuthClient.logout(context, access, refresh)
            } catch (e: Exception) {
                FileLogger.log(context, "Dashboard", "Logout error", e)
            }
        }
        Prefs.stopFoodRescue(context)
        Prefs.clear(context)
        FileLogger.clearLogs(context)
        navController.navigate("login") {
            popUpTo("dashboard") { inclusive = true }
            launchSingleTop = true
        }
        Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        while (true) {
            activeState = Prefs.getFoodRescueState(context)
            delay(2000)
        }
    }

    val features = listOf(
        JomatoFeature(
            id = "food_rescue",
            title = "Food Rescue",
            description = "Get notified when orders are cancelled near you",
            icon = Icons.Rounded.RestaurantMenu,
            isNew = true
        )
    )

    if (showLogDialog) LogViewerDialog(onDismiss = { showLogDialog = false })

    if (showLogoutDialog) {
        Dialog(onDismissRequest = { showLogoutDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Session Options", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        onClick = { performLogout() },
                        color = LightBg,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.PowerSettingsNew, null, tint = BrandColor)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("End Session", fontWeight = FontWeight.Bold)
                                Text("Logout from this device only.", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    TextButton(onClick = { showLogoutDialog = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            }
        }
    }

    Scaffold(containerColor = Color.White) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hello,", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                        Text(
                            text = userName,
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.Black
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconButton(
                            onClick = { showLogDialog = true },
                            modifier = Modifier.background(LightBg, CircleShape).size(40.dp)
                        ) {
                            Icon(Icons.Rounded.BugReport, "Logs", tint = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { showLogoutDialog = true },
                            modifier = Modifier.background(LightBg, CircleShape).size(40.dp)
                        ) {
                            Icon(Icons.Rounded.ExitToApp, "Logout", tint = BrandColor, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            if (activeState != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = BrandColor.copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(BrandColor)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Monitoring ${activeState!!.location.name}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = Color.Black
                            )
                            Text(
                                "${activeState!!.totalCancelledMessages} cancelled orders detected",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                features.forEach { feature ->
                    SimplifiedFeatureCard(feature, activeState != null) {
                        navController.navigate(feature.id)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimplifiedFeatureCard(feature: JomatoFeature, isActive: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color(0xFFE0E0E0)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrandColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(feature.icon, null, tint = BrandColor, modifier = Modifier.size(28.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    feature.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    feature.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }

            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(BrandColor)
                )
            } else {
                Icon(
                    Icons.Rounded.ArrowForward,
                    null,
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun LogViewerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logPreview by remember { mutableStateOf("Loading...") }
    var isSaving by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        logPreview = withContext(Dispatchers.IO) {
            try {
                FileLogger.getLastNLines(context, 50)
            } catch (e: Exception) {
                "Error loading logs: ${e.message}"
            }
        }
    }

    fun saveLogsToDownloads() {
        scope.launch(Dispatchers.IO) {
            isSaving = true
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "jomato_logs_$timestamp.txt"

                val logFile = FileLogger.getLogFile(context)
                if (!logFile.exists() || logFile.length() == 0L) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No logs to save", Toast.LENGTH_SHORT).show()
                    }
                    isSaving = false
                    return@launch
                }

                val savedPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveLogsUsingMediaStore(context, logFile, fileName)
                } else {
                    saveLogsToLegacyDownloads(context, logFile, fileName)
                }

                withContext(Dispatchers.Main) {
                    if (savedPath != null) {
                        Toast.makeText(context, "Logs saved to: $savedPath", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to save logs", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                FileLogger.log(context, "LogViewer", "Error saving logs", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isSaving = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize().padding(16.dp), shape = RoundedCornerShape(16.dp), color = Color.White) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("System Logs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Text("✕", fontSize = 20.sp) }
                }
                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    "Last 50 lines (full logs available via Save button)",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp)).padding(8.dp)) {
                    Text(text = logPreview, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.verticalScroll(scrollState))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { saveLogsToDownloads() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Rounded.Download, null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isSaving) "Saving..." else "Save")
                    }

                    Button(
                        onClick = {
                            FileLogger.clearLogs(context)
                            logPreview = "No logs found."
                            Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandColor)
                    ) {
                        Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear")
                    }
                }
            }
        }
    }
}

private fun saveLogsUsingMediaStore(context: Context, logFile: File, fileName: String): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

    try {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: return null

        resolver.openOutputStream(uri)?.use { outputStream ->
            copyFile(logFile, outputStream)
        }

        return "Downloads/$fileName"
    } catch (e: Exception) {
        FileLogger.log(context, "LogViewer", "MediaStore save error", e)
        return null
    }
}

private fun saveLogsToLegacyDownloads(context: Context, logFile: File, fileName: String): String? {
    try {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        val destFile = File(downloadsDir, fileName)
        logFile.copyTo(destFile, overwrite = true)

        return destFile.absolutePath
    } catch (e: Exception) {
        FileLogger.log(context, "LogViewer", "Legacy save error", e)
        return null
    }
}

private fun copyFile(source: File, outputStream: OutputStream) {
    FileInputStream(source).use { input ->
        input.copyTo(outputStream)
    }
}