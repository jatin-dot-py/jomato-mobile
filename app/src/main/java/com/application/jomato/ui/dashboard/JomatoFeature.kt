package com.application.jomato.ui.dashboard

import androidx.compose.ui.graphics.vector.ImageVector

data class JomatoFeature(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isNew: Boolean = false,
    val comingSoon: Boolean = false
)