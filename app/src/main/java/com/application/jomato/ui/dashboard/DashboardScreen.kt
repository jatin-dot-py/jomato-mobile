package com.application.jomato.ui.dashboard

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.application.jomato.config.FeatureRegistry
import com.application.jomato.config.UiConfigManager
import com.application.jomato.ui.AccountManagerDialog
import com.application.jomato.ui.AppScreen
import com.application.jomato.ui.theme.JomatoTheme

@Composable
fun DashboardScreen(navController: NavController) {
    val context = LocalContext.current

    val config = UiConfigManager.config
    val features by remember(config) { mutableStateOf(JomatoFeature.buildAll()) }

    var pendingFeature by remember { mutableStateOf<JomatoFeature?>(null) }

    // ── AccountManager for feature session selection ─────────────────────────
    pendingFeature?.entity?.let { entity ->
        val capturedFeatureId = pendingFeature!!.id
        AccountManagerDialog(
            entity = entity,
            onDismiss = { pendingFeature = null },
            onSessionSelected = { sessionId ->
                pendingFeature = null
                navController.navigate("feature/$capturedFeatureId/$sessionId")
            },
            onAddAccount = {
                pendingFeature = null
                navController.navigate(entity.loginHandler.route)
            }
        )
    }

    AppScreen(
        topBar = { DashboardTopBar() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            SectionHeader(title = "Features")
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                features.forEach { feature ->
                    FeatureCard(
                        feature = feature,
                        onClick = {
                            val entry = FeatureRegistry.resolve(feature.id)
                            if (entry == null) {
                                Toast.makeText(context, "Coming soon!", Toast.LENGTH_SHORT).show()
                                return@FeatureCard
                            }

                            val entity = feature.entity
                            if (feature.loginRequired && entity != null) {
                                if (!entity.isEnabled) {
                                    val msg = entity.info?.message?.takeIf { it.isNotBlank() }
                                        ?: "${entity.displayName} is currently unavailable."
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                } else {
                                    pendingFeature = feature
                                }
                            } else {
                                navController.navigate("feature/${feature.id}")
                            }
                        },
                        onEntityClick = feature.entity?.let { entity ->
                            { navController.navigate("entity/${entity.keyPrefix}") }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader(title = "Support")
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SupportRow(navController = navController)
                UiConfigManager.config?.widgets?.find { it.type == "jomato_privacy_faqs" }?.let {
                    PrivacyFaqRow(navController = navController)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        modifier = Modifier.padding(horizontal = 20.dp),
        style = MaterialTheme.typography.labelSmall,
        color = JomatoTheme.TextGray,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.sp
    )
}
