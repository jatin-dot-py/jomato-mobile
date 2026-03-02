package com.application.jomato.sessions

import com.application.jomato.config.EntityInfo
import com.application.jomato.config.EntityRegistry
import com.application.jomato.entity.zomato.ZomatoLoginHandler
import com.application.jomato.entity.zomato.ZomatoLogoutHandler
import com.application.jomato.entity.zomato.ZomatoManager

enum class Entity(
    val keyPrefix: String,
    val sessionManager: BaseSessionManager<out BaseSession>,
    val loginHandler: EntityLoginHandler,
    val logoutHandler: EntityLogoutHandler
) {
    ZOMATO("zomato", ZomatoManager, ZomatoLoginHandler, ZomatoLogoutHandler);

    /** Config-driven entity info (display name, brand color, etc.) from server. Null until config is loaded. */
    val info: EntityInfo? get() = EntityRegistry.get(this)

    val displayName: String
        get() = info?.displayName ?: keyPrefix.replaceFirstChar { it.uppercase() }

    val maxAccounts: Int
        get() = info?.maxAccounts ?: 2

    val isEnabled: Boolean
        get() = info?.enabled ?: true
}
