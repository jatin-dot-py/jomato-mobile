package com.application.jomato.widgets

/**
 * Registry of widgets by type. Register concrete widgets (e.g. FaqWidget) so that
 * config-driven widget entries (type + payload from UiConfig) can be resolved and displayed.
 */
object WidgetRegistry {

    private val byType = mutableMapOf<String, BaseWidget>()

    init {
        register(FaqWidget())
        register(AttributionWidget())
        register(UpdateWidget())
    }

    fun register(widget: BaseWidget) {
        byType[widget.type] = widget
    }

    fun resolve(type: String): BaseWidget? = byType[type]
}
