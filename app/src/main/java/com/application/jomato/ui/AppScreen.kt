package com.application.jomato.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.application.jomato.ui.dashboard.DashboardBottomBar
import com.application.jomato.ui.theme.JomatoTheme

/**
 * Shared screen shell: Scaffold + bottomBar (config-driven: update + attribution widgets).
 * Supply either a custom [topBar] or [title] + [showBack] for the standard app bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    title: String? = null,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    topBar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Scaffold(
        containerColor = JomatoTheme.Background,
        topBar = {
            when {
                topBar != null -> topBar()
                title != null || showBack -> TopAppBar(
                    title = {
                        if (title != null) {
                            Text(title, color = JomatoTheme.BrandBlack)
                        }
                    },
                    navigationIcon = {
                        if (showBack) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    tint = JomatoTheme.BrandBlack
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = JomatoTheme.Background,
                        titleContentColor = JomatoTheme.BrandBlack
                    )
                )
                else -> { /* no top bar */ }
            }
        },
        bottomBar = { DashboardBottomBar() }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            content()
        }
    }
}
