package com.application.jomato.entity.zomato.api

import android.content.Context
import com.application.jomato.utils.FileLogger
import kotlinx.serialization.json.*
import okhttp3.Request

internal object OrderSummaryApi {

    private val amountRegex = Regex("₹[\\d,.]+")

    fun getOrderSummary(context: Context, orderId: String, accessToken: String): OrderDetails? {
        FileLogger.log(context, ApiBase.TAG, "Fetching Order Summary for ID: $orderId")
        try {
            val url = "https://api.zomato.com/gw/order/order_summary?order_id=$orderId&lang=en"

            val request = Request.Builder()
                .url(url)
                .headers(ApiBase.authenticatedHeaders(accessToken))
                .get()
                .build()

            val response = ApiBase.client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                FileLogger.log(context, ApiBase.TAG, "OrderSummary Success: ${response.code}")
                return parseOrderDetails(context, orderId, responseBody)
            }
            FileLogger.log(context, ApiBase.TAG, "OrderSummary Failed. Code: ${response.code}")
            return null
        } catch (e: Exception) {
            FileLogger.log(context, ApiBase.TAG, "OrderSummary Exception", e)
            return null
        }
    }

    private fun parseOrderDetails(context: Context, orderId: String, jsonString: String): OrderDetails? {
        try {
            val root = ApiBase.jsonParser.parseToJsonElement(jsonString).jsonObject
            val items = root["items"]?.jsonArray ?: return null

            var restaurantName: String? = null
            var restaurantImageUrl: String? = null
            var cartTotal: Double? = null
            var paidAmount: Double? = null
            val orderItems = mutableListOf<String>()

            for (element in items) {
                val item = element.jsonObject
                val snippetType = item["type"]?.jsonPrimitive?.content ?: ""
                val snippetConfig = item["snippet_config"]?.jsonObject

                if (snippetType == "crystal_snippet_type_6" &&
                    snippetConfig?.get("identifier")?.jsonPrimitive?.content == "RESTAURANT_V16"
                ) {
                    val snippet = item["crystal_snippet_type_6"]?.jsonObject
                    restaurantName = snippet?.get("title")?.jsonObject
                        ?.get("text")?.jsonPrimitive?.content
                    restaurantImageUrl = snippet?.get("left_image")?.jsonObject
                        ?.get("url")?.jsonPrimitive?.content
                }

                if (snippetType == "crystal_snippet_type_5" &&
                    snippetConfig?.get("identifier")?.jsonPrimitive?.content == "ORDER_ITEM"
                ) {
                    val verticalItems = item["crystal_snippet_type_5"]?.jsonObject
                        ?.get("vertical_subtitles")?.jsonObject
                        ?.get("items")?.jsonArray
                    verticalItems?.forEach { subElement ->
                        val itemName = subElement.jsonObject["title"]?.jsonObject
                            ?.get("text")?.jsonPrimitive?.content
                        if (itemName != null) orderItems.add(itemName)
                    }
                }

                val identityId = snippetConfig?.get("identity")?.jsonObject
                    ?.get("id")?.jsonPrimitive?.content

                if (identityId == "CHARGE_ID_DISH") {
                    val subtitleText = item["text_snippet_type_15"]?.jsonObject
                        ?.get("subtitle")?.jsonObject
                        ?.get("text")?.jsonPrimitive?.content
                    if (subtitleText != null) {
                        cartTotal = amountRegex.find(subtitleText)?.value
                            ?.replace("₹", "")
                            ?.replace(",", "")
                            ?.toDoubleOrNull()
                    }
                }

                if (identityId == "final_cost") {
                    val subtitleText = item["text_snippet_type_15"]?.jsonObject
                        ?.get("subtitle")?.jsonObject
                        ?.get("text")?.jsonPrimitive?.content
                    if (subtitleText != null) {
                        paidAmount = amountRegex.find(subtitleText)?.value
                            ?.replace("₹", "")
                            ?.replace(",", "")
                            ?.toDoubleOrNull()
                    }
                }
            }

            val result = OrderDetails(orderId, restaurantName, restaurantImageUrl, cartTotal, paidAmount, orderItems)
            FileLogger.log(context, ApiBase.TAG, "OrderDetails parsed: restaurant=$restaurantName, image=${restaurantImageUrl != null}, cart=$cartTotal, paid=$paidAmount, items=${orderItems.size}")
            return result
        } catch (e: Exception) {
            FileLogger.log(context, ApiBase.TAG, "OrderDetails parse error: ${e.message}", e)
            return null
        }
    }
}
