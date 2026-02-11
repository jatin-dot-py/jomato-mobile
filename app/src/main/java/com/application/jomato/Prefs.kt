package com.application.jomato

import android.content.Context
import android.content.SharedPreferences
import com.application.jomato.ui.rescue.FoodRescueState
import com.application.jomato.api.TabbedHomeEssentials
import com.application.jomato.api.UserLocation
import com.application.jomato.utils.FileLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object Prefs {
    private const val PREFS_NAME = "zomato_prefs"
    private const val TAG = "Prefs"

    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_ID = "user_id"

    private const val KEY_FR_ESSENTIALS_JSON = "fr_essentials_json"
    private const val KEY_FR_LOCATION_JSON = "fr_location_json"
    private const val KEY_FR_STARTED_AT = "fr_started_at"

    private const val KEY_FR_CANCELLED_COUNT = "fr_cancelled_count"
    private const val KEY_FR_CLAIMED_COUNT = "fr_claimed_count"
    private const val KEY_FR_RECONN_COUNT = "fr_reconn_count"

    private const val KEY_FR_LAST_NOTIFICATION_AT = "fr_last_notification_at"


    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveTokenAndUser(context: Context, accessToken: String, refreshToken: String, userName: String, userId: String) {
        FileLogger.log(context, TAG, "Saving new user session for: $userName")
        getPrefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_USER_NAME, userName)
            .putString(KEY_USER_ID, userId)
            .apply()
    }

    fun getToken(context: Context): String? = getPrefs(context).getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(context: Context): String? = getPrefs(context).getString(KEY_REFRESH_TOKEN, null)
    fun getUserName(context: Context): String? = getPrefs(context).getString(KEY_USER_NAME, null)
    fun getUserId(context: Context): String? = getPrefs(context).getString(KEY_USER_ID, null)

    fun clear(context: Context) {
        FileLogger.log(context, TAG, "Clearing all preferences")
        getPrefs(context).edit().clear().apply()
    }

    fun activateFoodRescue(context: Context, essentials: TabbedHomeEssentials, location: UserLocation) {
        FileLogger.log(context, TAG, "Activating Food Rescue for location: ${location.name}")
        try {
            val essentialsJson = json.encodeToString(essentials)
            val locationJson = json.encodeToString(location)
            val now = System.currentTimeMillis()

            getPrefs(context).edit()
                .putString(KEY_FR_ESSENTIALS_JSON, essentialsJson)
                .putString(KEY_FR_LOCATION_JSON, locationJson)
                .putLong(KEY_FR_STARTED_AT, now)
                .putInt(KEY_FR_CANCELLED_COUNT, 0)
                .putInt(KEY_FR_CLAIMED_COUNT, 0)
                .putInt(KEY_FR_RECONN_COUNT, 0)
                .putLong(KEY_FR_LAST_NOTIFICATION_AT, 0)
                .apply()

            FileLogger.log(context, TAG, "Food Rescue activated | Started at: $now | Initial counts: Cancelled=0, Claimed=0, Reconnects=0")
        } catch (e: Exception) {
            FileLogger.log(context, TAG, "Failed to save FR activation: ${e.message}", e)
        }
    }

    fun getFoodRescueState(context: Context): FoodRescueState? {
        val prefs = getPrefs(context)
        val essJson = prefs.getString(KEY_FR_ESSENTIALS_JSON, null)
        val locJson = prefs.getString(KEY_FR_LOCATION_JSON, null)

        if (essJson == null || locJson == null) {
            FileLogger.log(context, TAG, "Food Rescue state not found (inactive)")
            return null
        }

        return try {
            val state = FoodRescueState(
                essentials = json.decodeFromString(essJson),
                location = json.decodeFromString(locJson),
                startedAtTimestamp = prefs.getLong(KEY_FR_STARTED_AT, 0),
                totalCancelledMessages = prefs.getInt(KEY_FR_CANCELLED_COUNT, 0),
                totalClaimedMessages = prefs.getInt(KEY_FR_CLAIMED_COUNT, 0),
                totalReconnects = prefs.getInt(KEY_FR_RECONN_COUNT, 0)
            )
            FileLogger.log(
                context,
                TAG,
                "Food Rescue state retrieved | Location: ${state.location.name} | Cancelled: ${state.totalCancelledMessages} | Claimed: ${state.totalClaimedMessages} | Reconnects: ${state.totalReconnects}"
            )
            state
        } catch (e: Exception) {
            FileLogger.log(context, TAG, "Error parsing FR state, resetting: ${e.message}", e)
            stopFoodRescue(context)
            null
        }
    }

    fun stopFoodRescue(context: Context) {
        FileLogger.log(context, TAG, "Stopping Food Rescue persistence")
        getPrefs(context).edit()
            .remove(KEY_FR_ESSENTIALS_JSON)
            .remove(KEY_FR_LOCATION_JSON)
            .remove(KEY_FR_STARTED_AT)
            .remove(KEY_FR_CANCELLED_COUNT)
            .remove(KEY_FR_CLAIMED_COUNT)
            .remove(KEY_FR_RECONN_COUNT)
            .remove(KEY_FR_LAST_NOTIFICATION_AT)
            .apply()
        FileLogger.log(context, TAG, "Food Rescue state cleared")
    }

    fun isFoodRescueActive(context: Context): Boolean {
        val active = getPrefs(context).contains(KEY_FR_ESSENTIALS_JSON)
        FileLogger.log(context, TAG, "Food Rescue active check: $active")
        return active
    }

    fun incrementCancelledCount(context: Context) {
        val prefs = getPrefs(context)
        val current = prefs.getInt(KEY_FR_CANCELLED_COUNT, 0)
        val newCount = current + 1
        prefs.edit().putInt(KEY_FR_CANCELLED_COUNT, newCount).apply()
        FileLogger.log(context, TAG, ">>> Cancelled message count: $current -> $newCount <<<")
    }

    fun incrementClaimedCount(context: Context) {
        val prefs = getPrefs(context)
        val current = prefs.getInt(KEY_FR_CLAIMED_COUNT, 0)
        val newCount = current + 1
        prefs.edit().putInt(KEY_FR_CLAIMED_COUNT, newCount).apply()
        FileLogger.log(context, TAG, ">>> Claimed message count: $current -> $newCount <<<")
    }

    fun incrementReconnCount(context: Context) {
        val prefs = getPrefs(context)
        val current = prefs.getInt(KEY_FR_RECONN_COUNT, 0)
        val newCount = current + 1
        prefs.edit().putInt(KEY_FR_RECONN_COUNT, newCount).apply()
        FileLogger.log(context, TAG, ">>> Reconnect count: $current -> $newCount <<<")
    }

    fun saveLastNotification(context: Context, timestamp: Long) {
        getPrefs(context).edit()
            .putLong(KEY_FR_LAST_NOTIFICATION_AT, timestamp)
            .apply()
        FileLogger.log(context, TAG, "Saved last notification | Timestamp: $timestamp")
    }

    fun getLastNotificationTime(context: Context): Long {
        val prefs = getPrefs(context)
        val timestamp = prefs.getLong(KEY_FR_LAST_NOTIFICATION_AT, 0)
        FileLogger.log(context, TAG, "Retrieved last notification | Timestamp: $timestamp")
        return timestamp
    }

    private val _mqttStatus = MutableStateFlow(false)
    val mqttStatus = _mqttStatus.asStateFlow()

    fun setMqttConnectionStatus(isConnected: Boolean) {
        _mqttStatus.value = isConnected
    }

}