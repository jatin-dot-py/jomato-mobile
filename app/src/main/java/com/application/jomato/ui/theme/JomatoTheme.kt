package com.application.jomato.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.application.jomato.Prefs

object JomatoTheme {

    val isDark @Composable get(): Boolean {
        val mode by Prefs.themeMode.collectAsState()
        return when (mode) {
            "dark" -> true
            "light" -> false
            else -> isSystemInDarkTheme()
        }
    }

    // Brand Colors
    val Brand @Composable get() = if (isDark) Color(0xFFD18BAA) else Color(0xFF6F304F)
    val BrandLight @Composable get() = if (isDark) Color(0xFF311B27) else Color(0xFFF3E5F5)
    val BrandBlack @Composable get() = if (isDark) Color(0xFFFFFFFF) else Color(0xFF1C1C1C)

    // UI Neutrals
    val TextGray @Composable get() = if (isDark) Color(0xFFAAAAAA) else Color(0xFF696969)
    val Background @Composable get() = if (isDark) Color(0xFF121212) else Color(0xFFF9F9F9)
    val SecondaryBg @Composable get() = if (isDark) Color(0xFF242424) else Color(0xFFF4F4F4)
    val Divider @Composable get() = if (isDark) Color(0xFF333333) else Color(0xFFE0E0E0)

    // State Colors
    val Success @Composable get() = if (isDark) Color(0xFFD18BAA) else Color(0xFF6F304F)
    val Warning @Composable get() = if (isDark) Color(0xFFFFAB40) else Color(0xFFE65100)
    val Error @Composable get() = if (isDark) Color(0xFFFF5252) else Color(0xFFB00020)
}
