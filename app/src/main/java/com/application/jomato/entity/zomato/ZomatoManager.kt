package com.application.jomato.entity.zomato

import android.content.Context
import com.application.jomato.entity.zomato.api.OrderDetails
import com.application.jomato.entity.zomato.api.TabbedHomeEssentials
import com.application.jomato.entity.zomato.api.UserLocation
import com.application.jomato.entity.zomato.rescue.FoodRescueState
import com.application.jomato.sessions.BaseSessionManager
import com.application.jomato.sessions.Entity
import com.application.jomato.utils.FileLogger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object ZomatoManager : BaseSessionManager<ZomatoSession>() {

    override val entity: Entity get() = Entity.ZOMATO

    private const val TAG = "ZomatoManager"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun sessionKey(sessionId: String, field: String) = "${Entity.ZOMATO.keyPrefix}-$sessionId-$field"
    private fun frKey(field: String) = "${Entity.ZOMATO.keyPrefix}-$field"


    override fun saveSession(context: Context, session: ZomatoSession) {
        FileLogger.log(context, TAG, "Saving session: ${session.sessionId} | user: ${session.userName}")
        prefs(context).edit()
            .putString(sessionKey(session.sessionId, "access_token"), session.accessToken)
            .putString(sessionKey(session.sessionId, "refresh_token"), session.refreshToken)
            .putString(sessionKey(session.sessionId, "user_name"), session.userName)
            .putString(sessionKey(session.sessionId, "user_id"), session.userId)
            .putLong(sessionKey(session.sessionId, "created_at"), session.createdAt)
            .apply()
    }

    override fun getSession(context: Context, sessionId: String): ZomatoSession? {
        val p = prefs(context)
        val accessToken = p.getString(sessionKey(sessionId, "access_token"), null) ?: return null
        val refreshToken = p.getString(sessionKey(sessionId, "refresh_token"), null) ?: return null
        val userName = p.getString(sessionKey(sessionId, "user_name"), null) ?: return null
        val userId = p.getString(sessionKey(sessionId, "user_id"), null) ?: return null
        val createdAt = p.getLong(sessionKey(sessionId, "created_at"), 0)
        return ZomatoSession(sessionId, createdAt, accessToken, refreshToken, userName, userId)
    }

    override fun listSessions(context: Context): List<ZomatoSession> {
        val prefix = Entity.ZOMATO.keyPrefix
        val sessions = prefs(context).all.keys
            .filter { it.startsWith("$prefix-") && it.endsWith("-user_name") }
            .mapNotNull { k ->
                val sessionId = k.removePrefix("$prefix-").removeSuffix("-user_name")
                getSession(context, sessionId)
            }
        FileLogger.log(context, TAG, "Listed ${sessions.size} Zomato sessions")
        return sessions
    }

    override fun deleteSession(context: Context, sessionId: String) {
        FileLogger.log(context, TAG, "Deleting session: $sessionId")
        val keysToRemove = prefs(context).all.keys.filter { it.startsWith("${Entity.ZOMATO.keyPrefix}-$sessionId-") }
        prefs(context).edit().apply {
            keysToRemove.forEach { remove(it) }
        }.apply()
        FileLogger.log(context, TAG, "Deleted ${keysToRemove.size} keys for session: $sessionId")
    }


    fun saveFoodRescueState(context: Context, essentials: TabbedHomeEssentials, location: UserLocation) {
        FileLogger.log(context, TAG, "Saving FR state | location: ${location.name}")
        try {
            prefs(context).edit()
                .putString(frKey("fr_essentials"), json.encodeToString(essentials))
                .putString(frKey("fr_location"), json.encodeToString(location))
                .putLong(frKey("fr_started_at"), System.currentTimeMillis())
                .putLong(frKey("fr_last_notification_at"), 0)
                .apply()
            FileLogger.log(context, TAG, "FR state saved")
        } catch (e: Exception) {
            FileLogger.log(context, TAG, "Failed to save FR state | ${e.message}", e)
        }
    }

    fun getFoodRescueState(context: Context): FoodRescueState? {
        val p = prefs(context)
        val essJson = p.getString(frKey("fr_essentials"), null) ?: return null
        val locJson = p.getString(frKey("fr_location"), null) ?: return null
        return try {
            val state = FoodRescueState(
                essentials = json.decodeFromString(essJson),
                location = json.decodeFromString(locJson),
                startedAtTimestamp = p.getLong(frKey("fr_started_at"), 0)
            )
            FileLogger.log(context, TAG, "FR state retrieved | location: ${state.location.name}")
            state
        } catch (e: Exception) {
            FileLogger.log(context, TAG, "Error parsing FR state, resetting | ${e.message}", e)
            stopFoodRescue(context)
            null
        }
    }

    fun stopFoodRescue(context: Context) {
        FileLogger.log(context, TAG, "Stopping FR")
        prefs(context).edit()
            .remove(frKey("fr_essentials"))
            .remove(frKey("fr_location"))
            .remove(frKey("fr_started_at"))
            .remove(frKey("fr_last_notification_at"))
            .remove(frKey("fr_session_id"))
            .apply()
        FileLogger.log(context, TAG, "FR stopped")
    }

    fun isFoodRescueActive(context: Context): Boolean {
        val active = prefs(context).contains(frKey("fr_essentials"))
        FileLogger.log(context, TAG, "FR active check | active: $active")
        return active
    }

    fun saveLastNotification(context: Context, timestamp: Long) {
        prefs(context).edit().putLong(frKey("fr_last_notification_at"), timestamp).apply()
        FileLogger.log(context, TAG, "Last notification saved | timestamp: $timestamp")
    }

    fun getLastNotificationTime(context: Context): Long {
        val timestamp = prefs(context).getLong(frKey("fr_last_notification_at"), 0)
        FileLogger.log(context, TAG, "Last notification retrieved | timestamp: $timestamp")
        return timestamp
    }

    fun saveFoodRescueSessionId(context: Context, sessionId: String) {
        FileLogger.log(context, TAG, "Saving FR session ID: $sessionId")
        prefs(context).edit().putString(frKey("fr_session_id"), sessionId).apply()
    }

    fun getFoodRescueSessionId(context: Context): String? {
        val sessionId = prefs(context).getString(frKey("fr_session_id"), null)
        FileLogger.log(context, TAG, "FR session ID retrieved: $sessionId")
        return sessionId
    }

    fun saveOrderClaimedState(context: Context, identifier: String, orderDetails: OrderDetails?) {
        FileLogger.log(context, TAG, "Saving order claimed state | id: $identifier | hasPayload: ${orderDetails != null}")
        prefs(context).edit()
            .putString(frKey("fr_order_claimed_$identifier"), if (orderDetails != null) json.encodeToString(orderDetails) else "")
            .apply()
    }

    fun getFrClaimedOrders(context: Context): List<OrderDetails> {
        val keyPrefix = frKey("fr_order_claimed_")
        return prefs(context).all.entries
            .filter { it.key.startsWith(keyPrefix) }
            .mapNotNull { entry ->
                val payload = entry.value as? String ?: return@mapNotNull null
                if (payload.isEmpty()) return@mapNotNull null
                try {
                    json.decodeFromString<OrderDetails>(payload)
                } catch (e: Exception) {
                    FileLogger.log(context, TAG, "Failed to parse claimed order: ${e.message}")
                    null
                }
            }
            .sortedByDescending { it.orderId }
    }

    fun getFrTotalSaved(context: Context): Double {
        return getFrClaimedOrders(context).sumOf { order ->
            val cart = order.cartTotal ?: 0.0
            val paid = order.paidAmount ?: 0.0
            (cart - paid).coerceAtLeast(0.0)
        }
    }

    fun clearClaimedOrders(context: Context) {
        val keyPrefix = frKey("fr_order_claimed_")
        val keysToRemove = prefs(context).all.keys.filter { it.startsWith(keyPrefix) }
        prefs(context).edit().apply {
            keysToRemove.forEach { remove(it) }
        }.apply()
        FileLogger.log(context, TAG, "Cleared ${keysToRemove.size} claimed order entries")
    }



}
