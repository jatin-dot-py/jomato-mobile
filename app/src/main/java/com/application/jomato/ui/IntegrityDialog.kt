package com.application.jomato.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.application.jomato.Prefs
import com.application.jomato.ui.theme.JomatoTheme

@Composable
fun IntegrityDialog(
    message: String,
    strict: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hideChecked by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!strict) onDismiss() },
        title = {
            Text(
                text = if (strict) "Security Warning" else "Warning",
                color = JomatoTheme.BrandBlack,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column {
                Text(
                    text = message + if (strict) "\n\nThis app cannot continue." else "",
                    color = JomatoTheme.BrandBlack,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                if (!strict) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = hideChecked,
                            onCheckedChange = { checked ->
                                hideChecked = checked
                                Prefs.setHideIntegrity(context, checked)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = JomatoTheme.Brand,
                                uncheckedColor = JomatoTheme.TextGray
                            )
                        )
                        Text(
                            text = "Don't show this again",
                            color = JomatoTheme.TextGray,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        },
        containerColor = JomatoTheme.Background,
        titleContentColor = JomatoTheme.BrandBlack,
        textContentColor = JomatoTheme.BrandBlack,
        confirmButton = {
            if (!strict) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = JomatoTheme.Brand)
                ) {
                    Text("I understand", color = JomatoTheme.Background, fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}
