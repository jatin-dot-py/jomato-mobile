package com.application.jomato.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonElement

@Serializable
data class UiConfig(
    val integrity: Integrity,
    val widgets: List<WidgetConfig> = emptyList(),
    val metadata: Metadata? = null,
    val entities: Map<String, EntityConfig> = emptyMap()
)

/** Entity id (e.g. "zomato") -> config. Forgiving: all fields optional with defaults. */
@Serializable
data class EntityConfig(
    val name: String = "",
    @SerialName("max_accounts") val maxAccounts: Int = 1,
    val healthy: Boolean = true,
    val message: String = "",
    @SerialName("website_link") val websiteLink: String = "",
    @SerialName("app_link") val appLink: String = "",
    val color: String = "",
    val enabled: Boolean = true,
    val faqs: List<FaqItem> = emptyList(),
    val features: Map<String, FeatureConfig> = emptyMap()
)

/** Feature id (e.g. "zomato_food_rescue") -> config. Forgiving: supports both "login"/"icon" and "login_required"/"icon_link". */
@Serializable
data class FeatureConfig(
    val name: String = "",
    val login: Boolean = false,
    @SerialName("login_required") val loginRequired: Boolean = false,
    val description: String = "",
    val icon: String = "",
    @SerialName("icon_link") val iconLink: String = "",
    val disabled: Boolean = false,
    val healthy: Boolean = true,
    @SerialName("coming_soon") val comingSoon: Boolean = false,
    @SerialName("new") val isNew: Boolean = false,
    val message: String = "",
    val faqs: List<FaqItem> = emptyList()
)

@Serializable
data class FaqItem(
    val question: String = "",
    val answer: String = ""
)

/** Server metadata. UiConfigManager uses ignoreUnknownKeys = true, so new server fields won't break the app. */
@Serializable
data class Metadata(
    val name: String = "",
    val description: String = "",
    @SerialName("package_name") val packageName: String = "",
    val website: String = "",
    @SerialName("lifetime_users") val lifetimeUsers: Int? = null,
    @SerialName("total_active_last_30_days") val totalActiveLast30Days: Int? = null,
    @SerialName("total_active_last_7_days") val totalActiveLast7Days: Int? = null,
    @SerialName("issues_url") val issuesUrl: String = "",
    @SerialName("request_feature_url") val requestFeatureUrl: String = ""
)

/** Server sends type + payload; payload is arbitrary JSON per widget. */
@Serializable
data class WidgetConfig(
    val type: String,
    val payload: JsonElement
)

/** Matches sdui.json integrity block from server */
@Serializable
data class Integrity(
    val versions: List<IntegrityVersion>,
    @SerialName("on_integrity_mismatch") val onMismatch: IntegrityMismatch
)

@Serializable
data class IntegrityVersion(
    val digest: String,
    val version: String
)

@Serializable
data class IntegrityMismatch(
    val message: String,
    val strict: Boolean
)