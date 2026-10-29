package com.application.jomato.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.application.jomato.entity.zomato.rescue.FoodRescueAlerter
import com.application.jomato.entity.zomato.rescue.RescueHistoryStore
import com.application.jomato.entity.zomato.service.FoodRescueService
import com.application.jomato.utils.FileLogger

/**
 * Invisible trampoline for a tap on the alert notification.
 *
 * It exists for two reasons:
 *  - it records the "opened" signal for [RescueHistoryStore], and
 *  - it hands the tap straight to Zomato.
 *
 * Android 12+ blocks apps from launching activities out of services/broadcast receivers
 * when a notification is tapped (the "notification trampoline" restriction), so the
 * PendingIntent has to point at an activity. This one is translucent and finishes
 * immediately, so the user only sees Zomato opening.
 */
class AlertOpenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        RescueHistoryStore.markOpened(
            this,
            intent?.getStringExtra(FoodRescueAlerter.EXTRA_EVENT_ID)
        )
        FoodRescueAlerter.dismiss(this)

        val launch = packageManager.getLaunchIntentForPackage(FoodRescueService.TARGET_PACKAGE)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            try {
                startActivity(launch)
            } catch (e: Exception) {
                FileLogger.log(this, "AlertOpenActivity", "Could not open Zomato: ${e.message}", e)
            }
        } else {
            FileLogger.log(this, "AlertOpenActivity", "Zomato app not installed")
        }

        finish()
    }
}
