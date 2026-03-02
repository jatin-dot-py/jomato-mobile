package com.application.jomato.sessions

import androidx.compose.runtime.Composable
import androidx.navigation.NavController

/**
 * Standardized login contract for any entity.
 *
 * Each entity provides a NavHost [route] and a composable [LoginScreen].
 * The login screen is responsible for the full auth flow and saving the
 * resulting session via the entity's [BaseSessionManager].
 */
abstract class EntityLoginHandler {

    /** NavHost route for this entity's login screen (e.g. "login/zomato"). */
    abstract val route: String

    /** The login screen composable. Should save a session on success and navigate to "dashboard". */
    @Composable
    abstract fun LoginScreen(navController: NavController)
}
