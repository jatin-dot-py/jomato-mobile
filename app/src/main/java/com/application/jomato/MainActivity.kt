package com.application.jomato

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.application.jomato.config.IntegrityResult
import com.application.jomato.ui.IntegrityDialog
import com.application.jomato.config.UiConfigManager
import com.application.jomato.sessions.Entity
import com.application.jomato.sessions.SessionMigration
import com.application.jomato.ui.EntityFaqRoute
import com.application.jomato.ui.EntityScreen
import com.application.jomato.ui.FeatureFaqRoute
import com.application.jomato.ui.FeatureScreen
import com.application.jomato.ui.dashboard.DashboardScreen
import com.application.jomato.ui.PrivacyFaqScreen
import com.application.jomato.ui.theme.JomatoTheme
import com.application.jomato.utils.AnalyticsManager
import com.application.jomato.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()

    var integrityResult by remember { mutableStateOf<IntegrityResult?>(null) }
    var showIntegrityDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Prefs.loadThemeMode(context)

        withContext(Dispatchers.IO) {
            UiConfigManager.fetch(context)
        }

        withContext(Dispatchers.IO) {
            integrityResult = UiConfigManager.checkIntegrity(context)
        }

        if (integrityResult is IntegrityResult.Fail) {
            val fail = integrityResult as IntegrityResult.Fail
            showIntegrityDialog = fail.strict || !Prefs.getHideIntegrity(context)
            if (fail.strict) return@LaunchedEffect
        }

        scope.launch(Dispatchers.IO) {
            AnalyticsManager.pingAppOpen(context)
        }

        val migrated = withContext(Dispatchers.IO) {
            SessionMigration.runIfNeeded(context)
        }

        if (migrated) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Your session has been reset. Please log in again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        navController.navigate("dashboard") {
            popUpTo("splash") { inclusive = true }
        }
    }

    if (showIntegrityDialog && integrityResult is IntegrityResult.Fail) {
        val fail = integrityResult as IntegrityResult.Fail
        IntegrityDialog(
            message = fail.message,
            strict = fail.strict,
            onDismiss = { showIntegrityDialog = false }
        )
    }

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") { SplashScreen() }
        composable("dashboard") { DashboardScreen(navController) }
        composable("privacy_faqs") { PrivacyFaqScreen(navController) }

        Entity.entries.forEach { entity ->
            composable(entity.loginHandler.route) {
                entity.loginHandler.LoginScreen(navController)
            }
        }

        composable("feature/{featureId}/{sessionId}") { backStackEntry ->
            FeatureScreen(
                featureId = backStackEntry.arguments?.getString("featureId")!!,
                sessionId = backStackEntry.arguments?.getString("sessionId")!!,
                navController = navController
            )
        }
        composable("feature/{featureId}") { backStackEntry ->
            FeatureScreen(
                featureId = backStackEntry.arguments?.getString("featureId")!!,
                sessionId = null,
                navController = navController
            )
        }
        composable("feature/{featureId}/faq") { backStackEntry ->
            FeatureFaqRoute(
                featureId = backStackEntry.arguments?.getString("featureId")!!,
                navController = navController
            )
        }
        composable("entity/{entityId}") { backStackEntry ->
            EntityScreen(
                entityId = backStackEntry.arguments?.getString("entityId")!!,
                navController = navController
            )
        }
        composable("entity/{entityId}/faq") { backStackEntry ->
            EntityFaqRoute(
                entityId = backStackEntry.arguments?.getString("entityId")!!,
                navController = navController
            )
        }
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JomatoTheme.Background),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = JomatoTheme.Brand)
    }
}