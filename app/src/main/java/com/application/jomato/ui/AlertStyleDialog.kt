package com.application.jomato.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.application.jomato.Prefs
import com.application.jomato.entity.zomato.rescue.FoodRescueAlerter
import com.application.jomato.entity.zomato.rescue.RescuePermissionUtils
import com.application.jomato.ui.theme.JomatoTheme

/**
 * Lets the user pick how aggressive the rescue alert should be.
 * "Save & test" fires a real alert so the choice can be verified immediately.
 */
@Composable
fun AlertStyleDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(Prefs.getAlertStyle(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JomatoTheme.Background,
        title = {
            Text(
                text = "Alert style",
                fontWeight = FontWeight.Bold,
                color = JomatoTheme.BrandBlack
            )
        },
        text = {
            Column {
                AlertStyleOption(
                    title = "Ring + full screen",
                    description = "Alarm-style ringtone that loops until you act, plus a screen takeover. Most reliable.",
                    selected = selected == Prefs.ALERT_STYLE_ALARM,
                    onSelect = { selected = Prefs.ALERT_STYLE_ALARM }
                )
                AlertStyleOption(
                    title = "Sound + notification",
                    description = "One loud notification sound and a heads-up banner. No screen takeover.",
                    selected = selected == Prefs.ALERT_STYLE_SOUND,
                    onSelect = { selected = Prefs.ALERT_STYLE_SOUND }
                )
                AlertStyleOption(
                    title = "Vibrate only",
                    description = "No sound. Notification and vibration only.",
                    selected = selected == Prefs.ALERT_STYLE_SILENT,
                    onSelect = { selected = Prefs.ALERT_STYLE_SILENT }
                )

                if (selected == Prefs.ALERT_STYLE_ALARM &&
                    !RescuePermissionUtils.canUseFullScreenIntent(context)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Full-screen alerts are turned off for Jomato in Android settings. " +
                            "The ringtone will still play, but the screen will not wake.",
                        color = JomatoTheme.Warning,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    TextButton(onClick = {
                        RescuePermissionUtils.openFullScreenIntentSettings(context)
                    }) {
                        Text(
                            text = "Allow full-screen alerts",
                            color = JomatoTheme.Brand,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Prefs.setAlertStyle(context, selected)
                onDismiss()
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                Prefs.setAlertStyle(context, selected)
                FoodRescueAlerter.test(context)
                onDismiss()
            }) {
                Text(
                    text = "Save & test",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
private fun AlertStyleOption(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = androidx.compose.material3.RadioButtonDefaults.colors(
                selectedColor = JomatoTheme.Brand
            )
        )
        Column(modifier = Modifier.padding(start = 4.dp, top = 10.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = JomatoTheme.BrandBlack
            )
            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = JomatoTheme.TextGray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
