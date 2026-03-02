package com.application.jomato.entity.zomato.rescue

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.application.jomato.entity.zomato.ZomatoManager
import com.application.jomato.entity.zomato.api.TabbedHomeEssentials
import com.application.jomato.entity.zomato.api.UserLocation
import com.application.jomato.entity.zomato.service.FoodRescueService
import com.application.jomato.utils.FileLogger

object RescuePermissionUtils {

    fun checkBattery(context: Context, onShowDialog: () -> Unit, onSuccess: () -> Unit) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val isIgnoring = pm.isIgnoringBatteryOptimizations(context.packageName)
            FileLogger.log(context, "RescuePermissionUtils", "Battery opt ignored: $isIgnoring")
            if (!isIgnoring) {
                onShowDialog()
            } else {
                onSuccess()
            }
        } else {
            onSuccess()
        }
    }

    fun activateRescue(context: Context, essentials: TabbedHomeEssentials, location: UserLocation, sessionId: String) {
        ZomatoManager.saveFoodRescueState(context, essentials, location)
        ZomatoManager.saveFoodRescueSessionId(context, sessionId)
        val intent = Intent(context, FoodRescueService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        FileLogger.log(context, "RescuePermissionUtils", "Food rescue state saved, service started")
    }


    fun deactivateRescue(context: Context) {
        ZomatoManager.stopFoodRescue(context)
        try {
            val intent = Intent(context, FoodRescueService::class.java)
            intent.action = FoodRescueService.ACTION_STOP
            context.startService(intent)
        } catch (e: Exception) {
            FileLogger.log(context, "RescuePermissionUtils", "Service stop failed (may not be running): ${e.message}")
        }
        FileLogger.log(context, "RescuePermissionUtils", "Food rescue deactivated")
    }

    fun openBatterySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                FileLogger.log(context, "RescuePermissionUtils", "Direct battery prompt failed, falling back", e)
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }
}
