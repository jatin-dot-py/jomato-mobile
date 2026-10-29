package com.application.jomato.entity.zomato.rescue

import android.content.Context
import com.application.jomato.entity.zomato.api.OrderDetails
import com.application.jomato.utils.FileLogger
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * On-device ledger of food-rescue deals and what happened to them.
 *
 * Backed by a small JSON file in the app's private storage; nothing is uploaded.
 * Writes are guarded so the service thread and the UI can't clobber each other.
 */
object RescueHistoryStore {

    private const val TAG = "RescueHistory"
    private const val FILE_NAME = "rescue_history.json"
    private const val MAX_ENTRIES = 400

    /** How long a claim can arrive after the deal and still be matched to it. */
    private const val CLAIM_MATCH_WINDOW_MS = 45 * 60 * 1000L

    /** After this long an unclaimed deal is counted as missed. */
    const val MISS_WINDOW_MS = 30 * 60 * 1000L

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val lock = Any()

    fun entries(context: Context): List<RescueHistoryEntry> =
        synchronized(lock) { read(context) }

    /** A new deal appeared on the channel. */
    fun recordObserved(
        context: Context,
        eventId: String,
        eventTime: Long,
        notified: Boolean,
        parsed: RescueEventParser.Parsed
    ) {
        synchronized(lock) {
            val list = read(context).toMutableList()
            val index = list.indexOfFirst { it.id == eventId }
            if (index >= 0) {
                val old = list[index]
                list[index] = old.copy(
                    restaurantName = old.restaurantName ?: parsed.restaurantName,
                    restaurantId = old.restaurantId ?: parsed.restaurantId,
                    catalogTotal = old.catalogTotal ?: parsed.catalogTotal,
                    finalCost = old.finalCost ?: parsed.finalCost,
                    items = old.items.ifEmpty { parsed.items },
                    deeplink = old.deeplink ?: parsed.deeplink,
                    notified = old.notified || notified
                )
            } else {
                list.add(
                    RescueHistoryEntry(
                        id = eventId,
                        eventTime = eventTime,
                        loggedAt = System.currentTimeMillis(),
                        restaurantName = parsed.restaurantName,
                        restaurantId = parsed.restaurantId,
                        catalogTotal = parsed.catalogTotal,
                        finalCost = parsed.finalCost,
                        items = parsed.items,
                        notified = notified,
                        deeplink = parsed.deeplink
                    )
                )
            }
            write(context, list)
        }
    }

    /**
     * The user engaged with the alert for this specific deal.
     * A null/unknown id is ignored on purpose so test alerts and stale intents can't
     * mark an unrelated real deal as opened.
     */
    fun markOpened(context: Context, eventId: String?) {
        if (eventId == null) return
        synchronized(lock) {
            val list = read(context).toMutableList()
            val index = list.indexOfFirst { it.id == eventId }
            if (index < 0) return
            if (list[index].opened) return
            list[index] = list[index].copy(opened = true)
            write(context, list)
        }
    }

    /** The order summary confirmed this rescue order belongs to us. */
    fun markClaimed(context: Context, orderId: String, details: OrderDetails) {
        synchronized(lock) {
            val list = read(context).toMutableList()
            if (list.any { it.orderId == orderId }) return

            val now = System.currentTimeMillis()
            val target = list.indices
                .filter {
                    !list[it].claimed && list[it].orderId == null &&
                        now - list[it].eventTime <= CLAIM_MATCH_WINDOW_MS
                }
                .maxByOrNull { list[it].eventTime }

            if (target != null) {
                val old = list[target]
                list[target] = old.copy(
                    orderId = orderId,
                    claimed = true,
                    opened = true,
                    restaurantName = old.restaurantName ?: details.restaurantName,
                    catalogTotal = details.cartTotal ?: old.catalogTotal,
                    finalCost = details.paidAmount ?: old.finalCost,
                    items = details.items.ifEmpty { old.items }
                )
            } else {
                // Claim seen without a matching cancelled event in our window.
                list.add(
                    RescueHistoryEntry(
                        id = "order-$orderId",
                        eventTime = now,
                        loggedAt = now,
                        orderId = orderId,
                        claimed = true,
                        opened = true,
                        restaurantName = details.restaurantName,
                        catalogTotal = details.cartTotal,
                        finalCost = details.paidAmount,
                        items = details.items
                    )
                )
            }
            write(context, list)
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            runCatching { file(context).delete() }
                .onFailure { FileLogger.log(context, TAG, "Could not clear history: ${it.message}") }
        }
    }

    fun stats(context: Context): RescueHistoryStats {
        val list = entries(context)
        val now = System.currentTimeMillis()
        var claimed = 0
        var saved = 0.0
        var missed = 0
        var missedSavings = 0.0
        var missedPriced = 0
        var openedNotClaimed = 0

        for (entry in list) {
            if (entry.claimed) {
                claimed++
                saved += entry.savings ?: 0.0
                continue
            }
            if (now - entry.eventTime <= MISS_WINDOW_MS) continue

            missed++
            if (entry.opened) openedNotClaimed++
            val catalog = entry.catalogTotal
            val rescuePrice = entry.finalCost
            if (catalog != null && rescuePrice != null) {
                missedSavings += (catalog - rescuePrice).coerceAtLeast(0.0)
                missedPriced++
            }
        }

        return RescueHistoryStats(
            claimedCount = claimed,
            savedTotal = saved,
            missedCount = missed,
            missedSavings = missedSavings,
            missedPriced = missedPriced,
            openedButNotClaimed = openedNotClaimed
        )
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private fun read(context: Context): List<RescueHistoryEntry> {
        return try {
            val f = file(context)
            if (!f.exists() || f.length() == 0L) {
                emptyList()
            } else {
                json.decodeFromString<List<RescueHistoryEntry>>(f.readText())
                    .sortedByDescending { it.eventTime }
            }
        } catch (e: Exception) {
            FileLogger.log(context, TAG, "Failed to read history: ${e.message}")
            emptyList()
        }
    }

    private fun write(context: Context, entries: List<RescueHistoryEntry>) {
        try {
            val trimmed = entries.sortedByDescending { it.eventTime }.take(MAX_ENTRIES)
            val target = file(context)
            val tmp = File(context.filesDir, "$FILE_NAME.tmp")
            tmp.writeText(json.encodeToString(trimmed))
            if (!tmp.renameTo(target)) {
                target.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (e: Exception) {
            FileLogger.log(context, TAG, "Failed to save history: ${e.message}")
        }
    }
}
