package com.application.jomato

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.application.jomato.api.ApiClient
import com.application.jomato.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FileLogger.log(this, "MainActivity", "Application Launched (onCreate)")
        setContent { JomatoApp() }
    }
}

@Composable
fun JomatoApp() {
    val navController = rememberNavController()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        FileLogger.log(context, "JomatoApp", "Starting Session Check...")

        val token = withContext(Dispatchers.IO) { Prefs.getToken(context) }
        val refreshToken = withContext(Dispatchers.IO) { Prefs.getRefreshToken(context) ?: "" }

        if (token == null) {
            FileLogger.log(context, "JomatoApp", "No token found. Navigating to Login.")
            navController.navigate("login") { popUpTo("splash") { inclusive = true } }
        } else {
            FileLogger.log(context, "JomatoApp", "Token found. Validating session...")

            // Updated to pass context for logging inside ApiClient
            val userInfo = withContext(Dispatchers.IO) { ApiClient.getUserInfo(context, token) }

            if (userInfo != null) {
                FileLogger.log(context, "JomatoApp", "Session Valid. Welcome, ${userInfo.name}.")
                Prefs.saveTokenAndUser(context, token, refreshToken, userInfo.name, userInfo.id.toString())
                navController.navigate("dashboard") { popUpTo("splash") { inclusive = true } }
            } else {
                FileLogger.log(context, "JomatoApp", "Session Invalid/Expired. Clearing data.")
                Prefs.clear(context)
                navController.navigate("login") { popUpTo("splash") { inclusive = true } }
            }
        }
    }

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") { SplashScreen() }
        composable("login") { LoginScreen(navController) }
        composable("dashboard") { DashboardScreen(navController) }
        composable("food_rescue") { FoodRescueScreen(navController) }
    }
}

@Composable
fun SplashScreen() {
    Box(modifier = Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = BrandColor)
    }
}