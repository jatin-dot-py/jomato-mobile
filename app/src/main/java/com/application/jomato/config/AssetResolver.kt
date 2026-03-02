package com.application.jomato.config

import com.application.jomato.BuildConfig

/**
 * Resolves relative asset paths (e.g. "assets/github.svg") to full URLs using the two config hosts.
 * Tries primary first; use [urlPrimary] then [urlFallback] when loading (e.g. try fallback on failure).
 */
object AssetResolver {

    fun urlPrimary(relativePath: String): String {
        val path = relativePath.removePrefix("/")
        return "https://${BuildConfig.UI_JSON_HOST_PRIMARY}/$path"
    }

    fun urlFallback(relativePath: String): String {
        val path = relativePath.removePrefix("/")
        return "https://${BuildConfig.UI_JSON_HOST_FALLBACK}/$path"
    }
}
