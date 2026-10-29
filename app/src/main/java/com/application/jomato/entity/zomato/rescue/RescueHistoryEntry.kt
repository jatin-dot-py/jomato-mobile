package com.application.jomato.entity.zomato.rescue

import kotlinx.serialization.Serializable

/**
 * One food-rescue deal, as seen by this device.
 *
 * A deal starts life as an `order_cancelled` event on the MQTT channel. If the user
 * opens the alert we flip [opened]; if the order summary later confirms the rescue was
 * ours we flip [claimed] and fill in the real prices/items.
 *
 * Everything stays on-device (see [RescueHistoryStore]).
 */
@Serializable
data class RescueHistoryEntry(
    /** Message/deal id from the event, or "order-<id>" for a claim we only learnt about later. */
    val id: String,
    /** When the deal appeared (event time when Zomato gave us one). */
    val eventTime: Long,
    val loggedAt: Long,
    /** Order id once we've matched this deal to a real order. */
    val orderId: String? = null,
    val restaurantName: String? = null,
    val restaurantId: String? = null,
    /** What the basket would normally cost. */
    val catalogTotal: Double? = null,
    /** Rescue price (what you pay / paid). */
    val finalCost: Double? = null,
    val items: List<String> = emptyList(),
    /** Whether an alert notification was actually posted for this deal. */
    val notified: Boolean = false,
    /** Notification tapped / full-screen alert opened. */
    val opened: Boolean = false,
    /** Confirmed by order summary as claimed by this account. */
    val claimed: Boolean = false,
    val deeplink: String? = null
) {
    /** Only meaningful for claims where both prices are known. */
    val savings: Double?
        get() {
            if (!claimed) return null
            val catalog = catalogTotal ?: return null
            val paid = finalCost ?: return null
            return (catalog - paid).coerceAtLeast(0.0)
        }

    val hasPrice: Boolean get() = finalCost != null || catalogTotal != null
}

/** Aggregate numbers for the history header. */
data class RescueHistoryStats(
    val claimedCount: Int = 0,
    val savedTotal: Double = 0.0,
    /** Deals that expired or were taken without us claiming them. */
    val missedCount: Int = 0,
    /** Sum of (catalog - rescue price) for missed deals where both are known. */
    val missedSavings: Double = 0.0,
    /** How many missed deals had enough price data to count towards [missedSavings]. */
    val missedPriced: Int = 0,
    /** Missed deals the user actually opened first. */
    val openedButNotClaimed: Int = 0
)
