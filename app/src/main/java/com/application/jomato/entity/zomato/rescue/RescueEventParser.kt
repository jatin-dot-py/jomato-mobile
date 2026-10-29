package com.application.jomato.entity.zomato.rescue

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Best-effort extraction of deal details from a food-rescue MQTT payload.
 *
 * The exact payload shape is not documented, so this walks the whole event and matches
 * keys after normalising them (`camelCase`, `snake_case` and `kebab-case` all compare
 * equal), accepts both numeric and string prices ("₹199", "199.0"), and follows JSON
 * that Zomato embeds as a string inside a field.
 *
 * We intentionally do NOT call `food-rescue/create-cart` to enrich every event: that
 * endpoint creates a rescue cart on the user's account, so calling it for deals the
 * user never opened could litter their Zomato cart.
 */
object RescueEventParser {

    private const val MAX_DEPTH = 10

    private val lenientJson = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    /** camelCase / snake_case / kebab-case -> flat lowercase. */
    private fun normalize(raw: String): String =
        raw.lowercase().replace("_", "").replace("-", "").replace(" ", "")

    private val NAME_KEYS = setOf(
        "restaurantname", "resname", "storename", "outletname", "vendorname", "brandname", "merchantname"
    )
    private val ID_KEYS = setOf("resid", "restaurantid", "storeid", "vendorid", "merchantid")
    private val CONTAINER_KEYS = setOf("restaurant", "store", "outlet", "vendor", "merchant")
    private val CATALOG_KEYS = setOf(
        "catalogtotalcost", "catalogtotal", "carttotal", "totalcost", "mrp",
        "originalcost", "originalprice", "actualprice"
    )
    private val FINAL_KEYS = setOf(
        "cartfinalcost", "finalcost", "rescueprice", "discountedprice", "offerprice",
        "sellingprice", "payableamount"
    )
    private val ITEM_LIST_KEYS = setOf("items", "itemlist", "cartitems", "products", "dishes")
    private val ITEM_NAME_KEYS = setOf("name", "itemname", "title", "displayname")
    private val DEEPLINK_KEYS = setOf("deeplink", "deeplinkurl", "actionurl", "buttonurl")

    data class Parsed(
        val restaurantName: String? = null,
        val restaurantId: String? = null,
        val catalogTotal: Double? = null,
        val finalCost: Double? = null,
        val items: List<String> = emptyList(),
        val deeplink: String? = null,
        val eventTime: Long? = null
    ) {
        /** True when the event actually told us something worth showing. */
        val hasDetail: Boolean
            get() = restaurantName != null || catalogTotal != null || finalCost != null || items.isNotEmpty()
    }

    fun parse(root: JsonObject): Parsed {
        val strings = mutableMapOf<String, String>()
        val numbers = mutableMapOf<String, Double>()
        var items: List<String> = emptyList()
        var deeplink: String? = null

        fun walk(element: JsonElement, depth: Int) {
            if (depth > MAX_DEPTH) return
            when (element) {
                is JsonObject -> {
                    for ((rawKey, value) in element) {
                        val key = normalize(rawKey)
                        when (value) {
                            is JsonPrimitive -> {
                                val text = value.content
                                // Some Zomato fields carry a whole JSON document as a string.
                                if (value.isString && text.length > 2 && text.startsWith("{")) {
                                    runCatching { lenientJson.parseToJsonElement(text) }
                                        .getOrNull()
                                        ?.let { walk(it, depth + 1) }
                                }
                                if (value.isString) {
                                    if (key in NAME_KEYS) strings.putIfAbsent(key, text)
                                    if (key in ID_KEYS) strings.putIfAbsent(key, text)
                                    if (key in DEEPLINK_KEYS && isZomatoLink(text) && deeplink == null) {
                                        deeplink = text
                                    }
                                }
                                if (key in CATALOG_KEYS || key in FINAL_KEYS) {
                                    val number = value.doubleOrNull ?: parsePrice(text)
                                    if (number != null && number > 0) numbers.putIfAbsent(key, number)
                                }
                            }
                            is JsonArray -> {
                                if (key in ITEM_LIST_KEYS && items.isEmpty()) {
                                    items = parseItems(value)
                                }
                                value.forEach { walk(it, depth + 1) }
                            }
                            is JsonObject -> {
                                if (key in CONTAINER_KEYS) {
                                    (value["name"] as? JsonPrimitive)
                                        ?.takeIf { it.isString }
                                        ?.content
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let { strings.putIfAbsent("restaurantname", it) }
                                    (value["id"] as? JsonPrimitive)
                                        ?.content
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let { strings.putIfAbsent("restaurantid", it) }
                                }
                                walk(value, depth + 1)
                            }
                        }
                    }
                }
                is JsonArray -> element.forEach { walk(it, depth + 1) }
                else -> Unit
            }
        }

        walk(root, 0)

        fun firstString(keys: Set<String>): String? = keys.firstNotNullOfOrNull { strings[it] }
        fun firstNumber(keys: Set<String>): Double? = keys.firstNotNullOfOrNull { numbers[it] }

        return Parsed(
            restaurantName = firstString(NAME_KEYS)?.takeIf { it.isNotBlank() },
            restaurantId = firstString(ID_KEYS)?.takeIf { it.isNotBlank() },
            catalogTotal = firstNumber(CATALOG_KEYS),
            finalCost = firstNumber(FINAL_KEYS),
            items = items,
            deeplink = deeplink,
            eventTime = findTimestamp(root)
        )
    }

    private fun parsePrice(raw: String): Double? =
        raw.trim()
            .removePrefix("₹")
            .removePrefix("Rs.")
            .removePrefix("Rs")
            .replace(",", "")
            .trim()
            .toDoubleOrNull()

    private fun parseItems(array: JsonArray): List<String> {
        val out = mutableListOf<String>()
        for (element in array) {
            when (element) {
                is JsonPrimitive -> if (element.isString && element.content.isNotBlank()) {
                    out.add(element.content.trim())
                }
                is JsonObject -> {
                    val name = element.entries.firstNotNullOfOrNull { (key, value) ->
                        if (normalize(key) in ITEM_NAME_KEYS) {
                            (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
                        } else {
                            null
                        }
                    }
                    if (name != null) out.add(name.trim())
                }
                else -> Unit
            }
            if (out.size >= 12) break
        }
        return out
    }

    private fun isZomatoLink(url: String): Boolean =
        url.startsWith("zomato://") || url.contains("zomato.com", ignoreCase = true)

    /** Top-level event timestamp, seconds or millis. */
    private fun findTimestamp(root: JsonObject): Long? {
        val primitive = root["timestamp"] as? JsonPrimitive ?: return null
        val raw = primitive.longOrNull ?: primitive.content.toLongOrNull() ?: return null
        return if (raw < 10_000_000_000L) raw * 1000 else raw
    }
}
