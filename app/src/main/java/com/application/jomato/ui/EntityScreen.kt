package com.application.jomato.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.application.jomato.config.EntityInfo
import com.application.jomato.config.EntityRegistry
import com.application.jomato.sessions.Entity
import com.application.jomato.ui.AccountManagerDialog
import com.application.jomato.ui.theme.JomatoTheme

@Composable
fun EntityScreen(entityId: String, navController: NavController) {
    val entity = Entity.values().firstOrNull { it.keyPrefix == entityId }
    val info = EntityRegistry.get(entityId)

    if (entity == null) {
        AppScreen(title = "Not Found", showBack = true, onBack = { navController.navigateUp() }) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Unknown entity.", color = JomatoTheme.TextGray)
            }
        }
        return
    }

    val context = LocalContext.current
    var showAccountDialog by remember { mutableStateOf(false) }

    if (showAccountDialog) {
        AccountManagerDialog(
            entity = entity,
            onDismiss = { showAccountDialog = false },
            onAddAccount = {
                showAccountDialog = false
                navController.navigate(entity.loginHandler.route)
            }
        )
    }

    AppScreen(
        title = info?.displayName ?: entity.displayName,
        showBack = true,
        onBack = { navController.navigateUp() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            if (info != null) {
                StatusRow(info)
                InfoDivider()

                if (info.message.isNotBlank()) {
                    MessageRow(info.message)
                    InfoDivider()
                }

                if (info.websiteLink.isNotBlank()) {
                    InfoRow(
                        icon = Icons.Rounded.Language,
                        label = "Website",
                        trailing = info.websiteLink
                            .removePrefix("https://")
                            .removePrefix("http://")
                            .removeSuffix("/"),
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.websiteLink))) }
                    )
                    InfoDivider()
                }

                if (info.appLink.isNotBlank()) {
                    InfoRow(
                        icon = Icons.Rounded.GetApp,
                        label = "App Store",
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.appLink))) }
                    )
                    InfoDivider()
                }

                if (info.faqs.isNotEmpty()) {
                    InfoRow(
                        icon = Icons.Rounded.QuestionAnswer,
                        label = "FAQs",
                        trailing = "${info.faqs.size}",
                        onClick = { navController.navigate("entity/$entityId/faq") }
                    )
                    InfoDivider()
                }
            }

            val sessionCount = entity.sessionManager.listSessions(context).size
            InfoRow(
                icon = Icons.Rounded.People,
                label = "Accounts",
                trailing = "$sessionCount",
                onClick = { showAccountDialog = true }
            )
            InfoDivider()

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ── Rows ──────────────────────────────────────────────────────────────────────

@Composable
private fun StatusRow(info: EntityInfo) {
    val dotColor = when {
        !info.enabled -> JomatoTheme.Error
        !info.healthy -> JomatoTheme.Warning
        else -> Color(0xFF4CAF50)
    }
    val statusLabel = when {
        !info.enabled -> "Unavailable"
        !info.healthy -> "Issues reported"
        else -> "Operational"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            Icons.Rounded.Dns,
            contentDescription = null,
            tint = JomatoTheme.TextGray,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = "Status",
            style = MaterialTheme.typography.bodyMedium,
            color = JomatoTheme.BrandBlack,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = dotColor,
                modifier = Modifier.size(8.dp)
            ) {}
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.bodySmall,
                color = JomatoTheme.TextGray,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun MessageRow(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            Icons.Rounded.Info,
            contentDescription = null,
            tint = JomatoTheme.TextGray,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = JomatoTheme.TextGray,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    trailing: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = JomatoTheme.TextGray,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = JomatoTheme.BrandBlack,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodySmall,
                color = JomatoTheme.TextGray,
                fontSize = 13.sp
            )
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = JomatoTheme.TextGray.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun InfoDivider() {
    Divider(
        color = JomatoTheme.Divider,
        thickness = 0.5.dp,
        modifier = Modifier.padding(horizontal = 20.dp)
    )
}
