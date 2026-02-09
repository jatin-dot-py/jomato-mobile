package com.application.jomato.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.application.jomato.ui.theme.JomatoTheme

@Composable
fun LogoutDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = JomatoTheme.Background,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Session Options",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = JomatoTheme.BrandBlack
                )

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    onClick = onConfirm,
                    color = JomatoTheme.SecondaryBg,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PowerSettingsNew, null, tint = JomatoTheme.Brand)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "End Session",
                                fontWeight = FontWeight.Bold,
                                color = JomatoTheme.BrandBlack
                            )
                            Text(
                                "Logout from this device only.",
                                fontSize = 12.sp,
                                color = JomatoTheme.TextGray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel", color = JomatoTheme.TextGray)
                }
            }
        }
    }
}