package com.application.jomato.ui.dashboard

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.application.jomato.Prefs
import com.application.jomato.config.UiConfigManager
import com.application.jomato.ui.theme.JomatoTheme

private const val FALLBACK_ISSUES_URL = "https://github.com/jatin-dot-py/jomato-mobile/issues/new?title=%5BIssue%5D%20Short%20Description%20here%20&body=Describe%20the%20issue%20you%20are%20facing."

@Composable
fun DashboardTopBar() {
    val context = LocalContext.current
    val issuesUrl = UiConfigManager.config?.metadata?.issuesUrl?.takeIf { it.isNotBlank() } ?: FALLBACK_ISSUES_URL
    val themeMode by Prefs.themeMode.collectAsState()

    val themeIcon = when (themeMode) {
        "dark" -> Icons.Rounded.DarkMode
        "light" -> Icons.Rounded.LightMode
        else -> Icons.Rounded.SettingsBrightness
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = JomatoTheme.Background,
        shadowElevation = if (JomatoTheme.isDark) 0.dp else 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                val appName = UiConfigManager.config?.metadata?.name?.takeIf { it.isNotBlank() } ?: "JOMATO"
                Text(
                    text = appName.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = JomatoTheme.Brand,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Welcome back!",
                    style = MaterialTheme.typography.titleLarge,
                    color = JomatoTheme.BrandBlack,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { Prefs.cycleThemeMode(context) }) {
                    Icon(
                        themeIcon,
                        contentDescription = "Theme: $themeMode",
                        tint = JomatoTheme.TextGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(issuesUrl)))
                }) {
                    Icon(
                        Icons.Rounded.BugReport,
                        contentDescription = "Report Issue",
                        tint = JomatoTheme.TextGray,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
