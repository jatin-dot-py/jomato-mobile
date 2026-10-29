package com.application.jomato.entity.zomato.rescue

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Stops the ring when the user swipes the alert away or taps it.
 *
 * A receiver (instead of routing through FoodRescueService) means dismissing an
 * alert never spins up the foreground service, and it still works if the service
 * has been stopped.
 */
class AlertDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        FoodRescueAlerter.dismiss(context)
    }
}
