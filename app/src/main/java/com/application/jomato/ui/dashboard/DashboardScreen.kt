package com.application.jomato.ui.dashboard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.application.jomato.Prefs
import com.application.jomato.auth.AuthClient
import com.application.jomato.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.application.jomato.ui.theme.JomatoTheme

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
            isNew = true,
            comingSoon = false
        ),
        JomatoFeature(
            id = "order_insights",
            title = "Order Insights",
            description = "Track your ordering patterns and spending",
            icon = Icons.Rounded.BarChart,
            isNew = false,
            comingSoon = true
        ),
        JomatoFeature(
            id = "power_tools",
            title = "Power Tools",
            description = "Advanced features for power users",
            icon = Icons.Rounded.Build,
            isNew = false,
            comingSoon = true
        )
    )

    if (showLogDialog) LogViewerDialog(onDismiss = { showLogDialog = false })
    if (showLogoutDialog) LogoutDialog(onDismiss = { showLogoutDialog = false }, onConfirm = { performLogout() })

    Scaffold(
        containerColor = JomatoTheme.Background,
        bottomBar = {
            GitHubPill()
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = JomatoTheme.Background,
                shadowElevation = if (isSystemInDarkTheme()) 0.dp else 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Welcome back,",
                            style = MaterialTheme.typography.bodyLarge,
                            color = JomatoTheme.TextGray.copy(alpha = 0.8f),
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = userName,
                            style = MaterialTheme.typography.headlineMedium,
                            color = JomatoTheme.BrandBlack,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            lineHeight = 32.sp
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showLogDialog = true },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(JomatoTheme.SecondaryBg)
                        ) {
                            Icon(
                                Icons.Rounded.BugReport,
                                contentDescription = "Debug Logs",
                                tint = JomatoTheme.TextGray,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = { showLogoutDialog = true },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(JomatoTheme.Brand.copy(alpha = 0.1f))
                        ) {
                            Icon(
                                Icons.Rounded.ExitToApp,
                                contentDescription = "Logout",
                                tint = JomatoTheme.Brand,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(JomatoTheme.Brand.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.GridView,
                        contentDescription = null,
                        tint = JomatoTheme.Brand,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "Features",
                    style = MaterialTheme.typography.titleLarge,
                    color = JomatoTheme.BrandBlack,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                features.forEach { feature ->
                    FeatureCard(
                        feature = feature,
                        isActive = activeState != null && feature.id == "food_rescue"
                    ) {
                        navController.navigate(feature.id)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}