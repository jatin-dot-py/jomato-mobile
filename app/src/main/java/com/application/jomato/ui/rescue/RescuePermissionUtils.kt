package com.application.jomato.ui.rescue

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.application.jomato.Prefs
import com.application.jomato.api.TabbedHomeEssentials
import com.application.jomato.api.UserLocation
import com.application.jomato.utils.FileLogger

object RescuePermissionUtils {

    fun checkBattery(context: Context, onShowDialog: () -> Unit, onSuccess: () -> Unit) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val isIgnoring = pm.isIgnoringBatteryOptimizations(context.packageName)
            FileLogger.log(context, "RescuePermissionUtils", "Checking battery opt for package: ${context.packageName} | Result: $isIgnoring")
            if (!isIgnoring) {
                onShowDialog()
            } else {
                onSuccess()
            }
        } else {
            onSuccess()
        }
    }

    fun startRescueService(context: Context, essentials: TabbedHomeEssentials, location: UserLocation) {
        Prefs.activateFoodRescue(context, essentials, location)
        val intent = Intent(context, com.application.jomato.service.FoodRescueService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopRescueService(context: Context) {
        Prefs.stopFoodRescue(context)
        val intent = Intent(context, com.application.jomato.service.FoodRescueService::class.java)
        intent.action = com.application.jomato.service.FoodRescueService.ACTION_STOP
        context.startService(intent)
    }

    fun openBatterySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                FileLogger.log(context, "RescuePermissionUtils", "Failed to open direct battery settings, falling back to list.", e)
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }
}