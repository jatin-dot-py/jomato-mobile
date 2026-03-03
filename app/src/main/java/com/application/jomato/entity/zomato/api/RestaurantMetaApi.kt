package com.application.jomato.entity.zomato.api

import android.content.Context
import com.application.jomato.utils.FileLogger
import kotlinx.serialization.json.*
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal object RestaurantMetaApi {

    fun getRestaurantMeta(context: Context, resId: String, accessToken: String): RestaurantMeta? {
        FileLogger.log(context, ApiBase.TAG, "Fetching Restaurant Meta for ID: $resId")
        try {
            val url = "https://api.zomato.com/gw/menu/res_info/$resId"

            val payloadJson = buildJsonObject {
                put("should_fetch_res_info_from_agg", true)
            }

            val requestHeaders = ApiBase.commonHeaders.newBuilder()
                .add("X-Zomato-Access-Token", accessToken)
                .add("Content-Type", "application/json; charset=utf-8")
                .build()

            val request = Request.Builder()
                .url(url)
                .headers(requestHeaders)
                .post(payloadJson.toString().toRequestBody(ApiBase.jsonMediaType))
                .build()

            val response = ApiBase.client.newCall(request).execute()
            val responseString = response.body?.string()

            if (!response.isSuccessful || responseString == null) {
                FileLogger.log(context, ApiBase.TAG, "GetRestaurantMeta Failed. Code: ${response.code}")
                return null
            }

            return parseRestaurantMeta(context, resId, responseString)

        } catch (e: Exception) {
            FileLogger.log(context, ApiBase.TAG, "GetRestaurantMeta Exception", e)
            return null
        }
    }

    private fun parseRestaurantMeta(context: Context, resId: String, jsonString: String): RestaurantMeta? {
        val root = ApiBase.jsonParser.parseToJsonElement(jsonString).jsonObject
        val results = root["results"]?.jsonArray ?: return null

        var name = "Unknown Restaurant"
        var lat: Double? = null
        var lng: Double? = null
        var foundName = false
        var foundCoords = false

        for (result in results) {
            val snippet = result.jsonObject["v4_image_text_snippet_type_3"]?.jsonObject ?: continue
            val items = snippet["items"]?.jsonArray ?: continue

            if (!foundName && items.isNotEmpty()) {
                val titleText = items[0].jsonObject["title"]?.jsonObject
                    ?.get("text")?.jsonPrimitive?.content
                if (titleText != null) {
                    name = titleText
                    foundName = true
                }
            }

            if (!foundCoords) {
                for (item in items) {
                    val containers = item.jsonObject["icon_text_containers"]?.jsonArray ?: continue
                    for (container in containers) {
                        val clickAction = container.jsonObject["click_action"]?.jsonObject
                        if (clickAction?.get("type")?.jsonPrimitive?.content == "open_map") {
                            val openMap = clickAction["open_map"]?.jsonObject
                            lat = openMap?.get("latitude")?.jsonPrimitive?.doubleOrNull
                            lng = openMap?.get("longitude")?.jsonPrimitive?.doubleOrNull
                            if (lat != null && lng != null) {
                                foundCoords = true
                                break
                            }
                        }
                    }
                    if (foundCoords) break
                }
            }

            if (foundName && foundCoords) break
        }

        FileLogger.log(context, ApiBase.TAG, "Restaurant Meta: $name ($lat, $lng)")

        return RestaurantMeta(
            resId = resId,
            name = name,
            lat = lat,
            lng = lng
        )
    }
}
