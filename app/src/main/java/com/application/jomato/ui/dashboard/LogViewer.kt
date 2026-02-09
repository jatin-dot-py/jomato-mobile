package com.application.jomato.ui.dashboard

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.application.jomato.ui.theme.JomatoTheme
import com.application.jomato.utils.FileLogger
import com.application.jomato.utils.saveLogsToLegacyDownloads
import com.application.jomato.utils.saveLogsUsingMediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

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
                    if (savedPath != null) Toast.makeText(context, "Logs saved to: $savedPath", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            } finally {
                isSaving = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = JomatoTheme.Background // Proper screen bg
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "System Logs",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = JomatoTheme.BrandBlack
                    )
                    IconButton(onClick = onDismiss) {
                        Text("✕", fontSize = 20.sp, color = JomatoTheme.BrandBlack)
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp), color = JomatoTheme.Divider)

                Text(
                    "Last 50 lines",
                    fontSize = 12.sp,
                    color = JomatoTheme.TextGray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // The Console Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(JomatoTheme.SecondaryBg, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = logPreview,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = JomatoTheme.BrandBlack, // Black in Light, White in Dark
                        modifier = Modifier.verticalScroll(scrollState)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { saveLogsToDownloads() },
                        modifier = Modifier.weight(1f),
                        // Using BrandBlack so it flips to White in dark mode
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JomatoTheme.BrandBlack,
                            contentColor = JomatoTheme.Background
                        ),
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = JomatoTheme.Background, strokeWidth = 2.dp)
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
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = JomatoTheme.Brand)
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