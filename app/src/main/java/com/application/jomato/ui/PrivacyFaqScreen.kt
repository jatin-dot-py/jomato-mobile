package com.application.jomato.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.application.jomato.config.UiConfigManager
import com.application.jomato.widgets.WidgetRegistry

@Composable
fun PrivacyFaqScreen(navController: NavController) {
    val faqConfig = UiConfigManager.config?.widgets?.find { it.type == "jomato_privacy_faqs" }

    AppScreen(
        title = "Privacy & FAQs",
        showBack = true,
        onBack = { navController.navigateUp() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            faqConfig?.let { config ->
                WidgetRegistry.resolve("jomato_privacy_faqs")?.Display(config.payload)
            }
        }
    }
}
