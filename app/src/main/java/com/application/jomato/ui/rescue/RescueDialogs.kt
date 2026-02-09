package com.application.jomato.ui.rescue

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.application.jomato.ui.theme.JomatoTheme

@Composable
fun RescueBatteryDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Power Settings") },
        text = { Text("To monitor in the background, please allow unrestricted battery usage.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = JomatoTheme.Brand)
            ) {
                Text("Open Settings", color = Color.White)
            }
        }
    )
}