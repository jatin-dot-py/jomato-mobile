package com.application.jomato.entity.zomato

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.application.jomato.sessions.EntityLoginHandler

object ZomatoLoginHandler : EntityLoginHandler() {

    override val route: String = "login/zomato"

    @Composable
    override fun LoginScreen(navController: NavController) {
        ZomatoLoginScreen(navController = navController)
    }
}
