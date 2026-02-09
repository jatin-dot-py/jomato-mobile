package com.application.jomato.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object JomatoTheme {
    // Brand Colors
    val Brand @Composable get() = if (isSystemInDarkTheme()) Color(0xFFD18BAA) else Color(0xFF6F304F)
    val BrandLight @Composable get() = if (isSystemInDarkTheme()) Color(0xFF311B27) else Color(0xFFF3E5F5)
    val BrandBlack @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFFFFFF) else Color(0xFF1C1C1C)

    // UI Neutrals
    val TextGray @Composable get() = if (isSystemInDarkTheme()) Color(0xFFAAAAAA) else Color(0xFF696969)
    val Background @Composable get() = if (isSystemInDarkTheme()) Color(0xFF121212) else Color(0xFFF9F9F9)
    val SecondaryBg @Composable get() = if (isSystemInDarkTheme()) Color(0xFF242424) else Color(0xFFF4F4F4)
    val Divider @Composable get() = if (isSystemInDarkTheme()) Color(0xFF333333) else Color(0xFFE0E0E0)

    // State Colors
    val Success @Composable get() = if (isSystemInDarkTheme()) Color(0xFFD18BAA) else Color(0xFF6F304F)
    val Error @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFF5252) else Color(0xFFB00020)
}