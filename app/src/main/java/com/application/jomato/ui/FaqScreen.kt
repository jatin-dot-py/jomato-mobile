package com.application.jomato.ui

import com.application.jomato.config.FaqItem
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.application.jomato.config.EntityRegistry
import com.application.jomato.ui.theme.JomatoTheme

/**
 * Generic full-screen FAQ page. Used for both feature FAQs and entity FAQs.
 *
 * Routes:
 * - feature/{featureId}/faq
 * - entity/{entityId}/faq
 */
@Composable
fun FaqScreen(title: String, faqs: List<FaqItem>, navController: NavController) {
    AppScreen(
        title = "$title — FAQs",
        showBack = true,
        onBack = { navController.navigateUp() }
    ) {
        if (faqs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    text = "No FAQs available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JomatoTheme.TextGray
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                faqs.forEach { faq ->
                    if (faq.question.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = faq.question,
                                style = MaterialTheme.typography.bodyMedium,
                                color = JomatoTheme.BrandBlack,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            if (faq.answer.isNotBlank()) {
                                Text(
                                    text = faq.answer,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JomatoTheme.TextGray,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/** Resolves FAQs for a feature ID from EntityRegistry. */
@Composable
fun FeatureFaqRoute(featureId: String, navController: NavController) {
    val lookup = EntityRegistry.findFeature(featureId)
    FaqScreen(
        title = lookup?.config?.name ?: "Feature",
        faqs = lookup?.config?.faqs ?: emptyList(),
        navController = navController
    )
}

/** Resolves FAQs for an entity ID from EntityRegistry. */
@Composable
fun EntityFaqRoute(entityId: String, navController: NavController) {
    val info = EntityRegistry.get(entityId)
    FaqScreen(
        title = info?.displayName ?: entityId.replaceFirstChar { it.uppercase() },
        faqs = info?.faqs ?: emptyList(),
        navController = navController
    )
}
