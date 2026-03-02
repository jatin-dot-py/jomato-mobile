package com.application.jomato.widgets

import androidx.compose.runtime.Composable
import kotlinx.serialization.json.JsonElement

/**
 * Defines a widget: a type/id from the server and a display that renders the payload.
 * Concrete widgets (e.g. FaqWidget) provide [type] and implement [Display].
 */
abstract class BaseWidget {

    /** Type string the server sends (e.g. "jomato_privacy_faqs"). Used to match config widgets. */
    abstract val type: String

    /** Renders the widget UI from the server payload. */
    @Composable
    abstract fun Display(payload: JsonElement)
}
