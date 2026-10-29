package com.application.jomato.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.application.jomato.ui.theme.JomatoTheme
import com.application.jomato.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shows the tail of the local log and lets the user share it.
 *
 * The service writes each food-rescue event with a `RAW EVENT:` prefix so an
 * unrecognised payload can be diagnosed without asking users to run adb.
 * [FileLogger] already redacts tokens, phone numbers and OTPs.
 */
@Composable
fun DiagnosticsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var logText by remember { mutableStateOf("Loading…") }

    LaunchedEffect(Unit) {
        logText = withContext(Dispatchers.IO) {
            FileLogger.getLastNLines(context, 300)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JomatoTheme.Background,
        title = {
            Text("Diagnostics", fontWeight = FontWeight.Bold, color = JomatoTheme.BrandBlack)
        },
        text = {
            Column {
                Text(
                    text = "Food-rescue events are logged with a RAW EVENT prefix. " +
                        "Tokens, phone numbers and OTPs are redacted automatically.",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = JomatoTheme.TextGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = logText,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = JomatoTheme.BrandBlack
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { shareLogs(context, logText) }) {
                Text("Share", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun shareLogs(context: Context, text: String) {
    try {
        val file = File(context.cacheDir, "jomato_diagnostics.txt")
        file.writeText(text)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Jomato diagnostics")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share diagnostics"))
    } catch (e: Exception) {
        FileLogger.log(context, "Diagnostics", "Could not share logs: ${e.message}", e)
    }
}
