package com.application.jomato.config

import androidx.compose.ui.graphics.Color
import com.application.jomato.sessions.Entity

/**
 * Registry of entity config driven by server (entities).
 * Updated when [UiConfigManager] fetches config; use [get] to read name, brand color, features, etc.
 * Brand color is parsed from hex to a literal [Color] for use in the app (e.g. theming).
 */
object EntityRegistry {

    private var byId: Map<String, EntityInfo> = emptyMap()

    /** Call after config is loaded (e.g. from UiConfigManager.fetch). */
    fun updateFrom(config: UiConfig?) {
        byId = config?.entities?.mapValues { (_, entity) ->
            EntityInfo(
                displayName = entity.name,
                healthy = entity.healthy,
                message = entity.message,
                websiteLink = entity.websiteLink,
                appLink = entity.appLink,
                brandColor = parseHexColor(entity.color),
                enabled = entity.enabled,
                maxAccounts = entity.maxAccounts,
                faqs = entity.faqs,
                features = entity.features
            )
        } ?: emptyMap()
    }

    fun get(entityId: String): EntityInfo? = byId[entityId]

    /** Look up by enum (e.g. Entity.ZOMATO). */
    fun get(entity: Entity): EntityInfo? = get(entity.keyPrefix)

    fun all(): Map<String, EntityInfo> = byId

    /** Scan all entities to find a feature config by ID (needed by FeatureScreen). */
    fun findFeature(featureId: String): FeatureLookup? {
        for (entity in Entity.values()) {
            val info = get(entity) ?: continue
            val config = info.features[featureId] ?: continue
            return FeatureLookup(entity, info, config)
        }
        return null
    }
}

data class FeatureLookup(
    val entity: Entity,
    val entityInfo: EntityInfo,
    val config: FeatureConfig
)

data class EntityInfo(
    val displayName: String,
    val healthy: Boolean,
    val message: String,
    val websiteLink: String,
    val appLink: String,
    /** Parsed from server hex (e.g. "#CB202D"); use in UI / theme. */
    val brandColor: Color,
    val enabled: Boolean,
    val maxAccounts: Int,
    val faqs: List<FaqItem>,
    val features: Map<String, FeatureConfig>
)

/** Parses hex string (e.g. "#CB202D" or "CB202D") to [Color]. Invalid input returns a neutral gray. */
fun parseHexColor(hex: String): Color {
    val s = hex.trim().removePrefix("#")
    if (s.length != 6 && s.length != 8) return Color(0xFF808080L)
    return try {
        val int = s.toLong(16).toInt()
        val withAlpha = if (s.length == 6) 0xFF000000.toInt() or int else int
        Color((withAlpha.toLong() and 0xFFFFFFFFL))
    } catch (_: NumberFormatException) {
        Color(0xFF808080L)
    }
}
