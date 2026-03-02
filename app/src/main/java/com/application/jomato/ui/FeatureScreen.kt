package com.application.jomato.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.application.jomato.config.EntityRegistry
import com.application.jomato.config.FeatureRegistry
import com.application.jomato.ui.theme.JomatoTheme

/**
 * Generic feature screen shell.
 *
 * Reads the feature's server config (title, description, health, FAQs) and renders
 * a consistent shell around the feature's actual content from [FeatureRegistry].
 * FAQ button navigates to the dedicated FAQ route instead of a dialog.
 */
@Composable
fun FeatureScreen(featureId: String, sessionId: String?, navController: NavController) {
    val lookup = EntityRegistry.findFeature(featureId)
    val entry = FeatureRegistry.resolve(featureId)

    if (lookup == null || entry == null) {
        FeatureNotFound(navController)
        return
    }

    val config = lookup.config

    AppScreen(
        topBar = {
            FeatureTopBar(
                title = config.name,
                showFaqButton = config.faqs.isNotEmpty(),
                onBack = { navController.navigateUp() },
                onFaqClick = { navController.navigate("feature/$featureId/faq") }
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!config.healthy && config.message.isNotBlank()) {
                FeatureHealthBanner(config.message)
            }

            if (sessionId != null) {
                Box(modifier = Modifier.weight(1f)) {
                    entry.Content(sessionId)
                }
            }
        }
    }
}

// ── Top bar ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeatureTopBar(
    title: String,
    showFaqButton: Boolean,
    onBack: () -> Unit,
    onFaqClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                color = JomatoTheme.BrandBlack,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = JomatoTheme.BrandBlack
                )
            }
        },
        actions = {
            if (showFaqButton) {
                IconButton(onClick = onFaqClick) {
                    Icon(
                        Icons.Rounded.QuestionAnswer,
                        contentDescription = "FAQs",
                        tint = JomatoTheme.TextGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = JomatoTheme.Background,
            titleContentColor = JomatoTheme.BrandBlack
        )
    )
}

// ── Health banner ────────────────────────────────────────────────────────────

@Composable
private fun FeatureHealthBanner(message: String) {
    val warningColor = JomatoTheme.Warning
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(8.dp),
        color = warningColor.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = warningColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = warningColor,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

// ── Fallback ─────────────────────────────────────────────────────────────────

@Composable
private fun FeatureNotFound(navController: NavController) {
    AppScreen(
        title = "Feature",
        showBack = true,
        onBack = { navController.navigateUp() }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "This feature is not available.",
                style = MaterialTheme.typography.bodyMedium,
                color = JomatoTheme.TextGray
            )
        }
    }
}
