package com.application.jomato.entity.zomato.api

import android.content.Context
import com.application.jomato.utils.FileLogger
import kotlinx.serialization.json.*
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal object UserLocationsApi {

    fun getUserLocations(context: Context, accessToken: String): UserLocationsResponse {
        FileLogger.log(context, ApiBase.TAG, "Fetching User Locations...")
        try {
            val url = "https://api.zomato.com/gw/user/location/selection"
            val payloadJson = buildJsonObject {
                put("android_country", "")
                putJsonObject("location_permissions") {
                    put("device_location_on", false)
                    put("location_permission_available", false)
                    put("precise_location_permission_available", false)
                }
                put("current_app_address_id", JsonNull)
                put("incremental_call", false)
                put("source", "delivery_home")
                put("lang", "en")
                put("android_language", "en")
                put("postback_params", "{}")
                putJsonArray("recent_locations") {}
                put("city_id", "1")
            }

            val request = Request.Builder()
                .url(url)
                .headers(ApiBase.authenticatedHeaders(accessToken))
                .post(payloadJson.toString().toRequestBody(ApiBase.jsonMediaType))
                .build()

            val response = ApiBase.client.newCall(request).execute()
            val responseString = response.body?.string()

            if (!response.isSuccessful || responseString == null) {
                FileLogger.log(context, ApiBase.TAG, "GetLocations Failed. Code: ${response.code}")
                return UserLocationsResponse(success = false, error = "HTTP ${response.code}", statusCode = response.code)
            }

            val rootElement = ApiBase.jsonParser.parseToJsonElement(responseString)
            val locations = findLocationSnippets(rootElement).mapNotNull { snippet ->
                val updateResult = snippet["click_action"]?.jsonObject?.get("update_location_result")?.jsonObject
                val address = updateResult?.get("address")?.jsonObject ?: return@mapNotNull null
                val place = address["place"]?.jsonObject
                val addressId = address["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null

                UserLocation(
                    name = snippet["title"]?.jsonObject?.get("text")?.jsonPrimitive?.content
                        ?: address["alias"]?.jsonPrimitive?.content ?: "",
                    fullAddress = snippet["subtitle"]?.jsonObject?.get("text")?.jsonPrimitive?.content
                        ?: address["display_subtitle"]?.jsonPrimitive?.content ?: "",
                    addressId = addressId,
                    cellId = place?.get("cell_id")?.jsonPrimitive?.content ?: "",
                    entityId = address["subzone_id"]?.jsonPrimitive?.intOrNull,
                    placeId = (address["delivery_subzone_id"]?.jsonPrimitive?.content
                        ?: place?.get("place_id")?.jsonPrimitive?.content),
                    lat = place?.get("latitude")?.jsonPrimitive?.doubleOrNull,
                    lng = place?.get("longitude")?.jsonPrimitive?.doubleOrNull
                )
            }

            FileLogger.log(context, ApiBase.TAG, "Found ${locations.size} locations.")
            return UserLocationsResponse(success = true, data = locations, statusCode = response.code)

        } catch (e: Exception) {
            FileLogger.log(context, ApiBase.TAG, "GetLocations Exception", e)
            return UserLocationsResponse(success = false, error = e.message, statusCode = 0)
        }
    }

    private fun findLocationSnippets(element: JsonElement): List<JsonObject> {
        val targetType = "location_address_snippet"
        val matches = mutableListOf<JsonObject>()
        when (element) {
            is JsonObject -> {
                val type = element["layout_config"]?.jsonObject?.get("snippet_type")?.jsonPrimitive?.content
                if (type == targetType) {
                    element[targetType]?.jsonObject?.let { matches.add(it) }
                }
                element.values.forEach { matches.addAll(findLocationSnippets(it)) }
            }
            is JsonArray -> {
                element.forEach { matches.addAll(findLocationSnippets(it)) }
            }
            else -> {}
        }
        return matches
    }
}
