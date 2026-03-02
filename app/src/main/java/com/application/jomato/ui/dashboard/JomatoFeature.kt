package com.application.jomato.ui.dashboard

import com.application.jomato.config.FeatureConfig
import com.application.jomato.config.UiConfigManager
import com.application.jomato.sessions.Entity

/**
 * View model for a feature card on the dashboard.
 * Every field (except [entity]) maps 1:1 to a server [FeatureConfig] field.
 * FAQs are intentionally excluded — they have their own display path.
 */
data class JomatoFeature(
    val id: String,
    val title: String,
    val description: String,
    val iconLink: String? = null,
    val isNew: Boolean = false,
    val comingSoon: Boolean = false,
    val disabled: Boolean = false,
    val healthy: Boolean = true,
    val message: String = "",
    val loginRequired: Boolean = false,
    val entity: Entity? = null
) {
    /** True when the card should be visually dimmed and non-navigable. */
    val isDimmed: Boolean get() = disabled || comingSoon

    companion object {
        fun fromConfig(entity: Entity, featureId: String, fc: FeatureConfig): JomatoFeature {
            return JomatoFeature(
                id = featureId,
                title = fc.name.ifBlank { featureId },
                description = fc.description,
                iconLink = fc.iconLink.ifEmpty { fc.icon }.ifBlank { null },
                isNew = fc.isNew,
                comingSoon = fc.comingSoon,
                disabled = fc.disabled,
                healthy = fc.healthy,
                message = fc.message,
                loginRequired = fc.login || fc.loginRequired,
                entity = entity
            )
        }

        fun buildAll(): List<JomatoFeature> {
            UiConfigManager.config ?: return emptyList()
            return Entity.values().flatMap { entity ->
                val info = entity.info ?: return@flatMap emptyList()
                if (!info.enabled) return@flatMap emptyList()
                info.features.map { (id, fc) -> fromConfig(entity, id, fc) }
            }
        }
    }
}
