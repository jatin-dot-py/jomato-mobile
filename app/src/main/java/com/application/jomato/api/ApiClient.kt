package com.application.jomato.api
import android.content.Context
import com.application.jomato.utils.FileLogger
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import okhttp3.brotli.BrotliInterceptor
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.net.toUri
import com.application.jomato.Prefs

object ApiClient {
    private const val TAG = "ApiClient"

    private val client = OkHttpClient.Builder()
        .addInterceptor(BrotliInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    private val commonHeaders = Headers.Builder()
        .add("Accept", "image/webp")
        .add("Connection", "keep-alive")
        .add("X-Zomato-API-Key", "7749b19667964b87a3efc739e254ada2")
        .add("X-Zomato-App-Version", "931")
        .add("X-Zomato-App-Version-Code", "1710019310")
        .add("X-Zomato-Client-Id", "5276d7f1-910b-4243-92ea-d27e758ad02b")
        .add("X-Zomato-UUID", "b2691abb-5aac-48a5-9f0e-750349080dcb")
        .build()

    private val jsonParser = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=UTF-8".toMediaType()

    fun getUserInfo(context: Context, accessToken: String): UserInfo? {
        FileLogger.log(context, TAG, "Fetching User Info...")
        try {
            val url = "https://api.zomato.com/gw/user/info"
            val requestHeaders = commonHeaders.newBuilder()
                .add("X-Zomato-Access-Token", accessToken)
                .build()

            val request = Request.Builder().url(url).headers(requestHeaders).get().build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                FileLogger.log(context, TAG, "UserInfo Success: ${response.code}")
                return jsonParser.decodeFromString<UserInfo>(responseBody)
            }
            FileLogger.log(context, TAG, "UserInfo Failed. Code: ${response.code}")
            return null
        } catch (e: Exception) {
            FileLogger.log(context, TAG, "UserInfo Exception", e)
            return null
        }
    }

    fun getUserLocations(context: Context, accessToken: String): UserLocationsResponse {
        FileLogger.log(context, TAG, "Fetching User Locations...")

        fun findSnippets(element: JsonElement, targetType: String): List<JsonObject> {
            val matches = mutableListOf<JsonObject>()
            when (element) {
                is JsonObject -> {
                    val type = element["layout_config"]?.jsonObject?.get("snippet_type")?.jsonPrimitive?.content
                    if (type == targetType) {
                        element[targetType]?.jsonObject?.let { matches.add(it) }
                    }
                    element.values.forEach { matches.addAll(findSnippets(it, targetType)) }
                }
                is JsonArray -> {
                    element.forEach { matches.addAll(findSnippets(it, targetType)) }
                }
                else -> {}
            }
            return matches
        }

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
                .headers(commonHeaders.newBuilder().add("X-Zomato-Access-Token", accessToken).build())
                .post(payloadJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string()

            if (!response.isSuccessful || responseString == null) {
                FileLogger.log(context, TAG, "GetLocations Failed. Code: ${response.code}")
                return UserLocationsResponse(success = false, error = "HTTP ${response.code}", statusCode = response.code)
            }

            val rootElement = jsonParser.parseToJsonElement(responseString)
            val locations = findSnippets(rootElement, "location_address_snippet").mapNotNull { snippet ->
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

            FileLogger.log(context, TAG, "Found ${locations.size} locations.")
            return UserLocationsResponse(success = true, data = locations, statusCode = response.code)

        } catch (e: Exception) {
            FileLogger.log(context, TAG, "GetLocations Exception", e)
            return UserLocationsResponse(success = false, error = e.message, statusCode = 0)
        }
    }

    fun getTabbedHomeEssentials(context: Context, cellId: String, addressId: Int, accessToken: String): TabbedHomeEssentials? {
        FileLogger.log(context, TAG, "Fetching HomeEssentials for AddrID: $addressId")
        try {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("api.zomato.com")
                .addPathSegment("gw")
                .addPathSegment("tabbed-home")
                .addQueryParameter("cell_id", cellId)
                .addQueryParameter("address_id", addressId.toString())
                .build()

            val requestHeaders = commonHeaders.newBuilder()
                .add("X-Zomato-Access-Token", accessToken)
                .build()

            val request = Request.Builder()
                .url(url)
                .headers(requestHeaders)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string()

            if (!response.isSuccessful || responseString == null) {
                FileLogger.log(context, TAG, "HomeEssentials Failed. Code: ${response.code}")
                return null
            }

            val root = jsonParser.parseToJsonElement(responseString).jsonObject

            val cityId = root["location"]?.jsonObject
                ?.get("city")?.jsonObject
                ?.get("id")?.jsonPrimitive?.intOrNull ?: 0

            var foodRescue: FoodRescueConf? = null
            val channels = root["subscription_channels"]?.jsonArray

            if (channels != null) {
                for (element in channels) {
                    val channel = element.jsonObject
                    if (channel["type"]?.jsonPrimitive?.content == "food_rescue") {
                        val clientData = channel["client"]?.jsonObject
                        val nameArray = channel["name"]?.jsonArray
                        val channelName = nameArray?.firstOrNull()?.jsonPrimitive?.content ?: ""

                        FileLogger.log(context, TAG, "Food Rescue Channel Found: $channelName")

                        foodRescue = FoodRescueConf(
                            channelName = channelName,
                            qos = channel["qos"]?.jsonPrimitive?.intOrNull ?: 0,
                            validUntil = channel["time"]?.jsonPrimitive?.longOrNull ?: 0L,
                            client = FoodRescueClient(
                                username = clientData?.get("username")?.jsonPrimitive?.content ?: "",
                                password = clientData?.get("password")?.jsonPrimitive?.content ?: "",
                                keepalive = clientData?.get("keepalive")?.jsonPrimitive?.intOrNull ?: 0
                            )
                        )
                        break
                    }
                }
            } else {
                FileLogger.log(context, TAG, "No subscription channels found.")
            }

            return TabbedHomeEssentials(
                cityId = cityId,
                foodRescue = foodRescue
            )

        } catch (e: Exception) {
            FileLogger.log(context, TAG, "HomeEssentials Exception", e)
            return null
        }
    }


    fun getRestaurantMeta(context: Context, resId: String, accessToken: String): RestaurantMeta? {
        FileLogger.log(context, TAG, "Fetching Restaurant Meta for ID: $resId")
        try {
            val url = "https://api.zomato.com/gw/menu/res_info/$resId"

            val payloadJson = buildJsonObject {
                put("should_fetch_res_info_from_agg", true)
            }

            val requestHeaders = commonHeaders.newBuilder()
                .add("X-Zomato-Access-Token", accessToken)
                .add("Content-Type", "application/json; charset=utf-8")
                .build()

            val request = Request.Builder()
                .url(url)
                .headers(requestHeaders)
                .post(payloadJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string()

            if (!response.isSuccessful || responseString == null) {
                FileLogger.log(context, TAG, "GetRestaurantMeta Failed. Code: ${response.code}")
                return null
            }

            val root = jsonParser.parseToJsonElement(responseString).jsonObject
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
                    val firstItem = items[0].jsonObject
                    val titleObj = firstItem["title"]?.jsonObject
                    val titleText = titleObj?.get("text")?.jsonPrimitive?.content
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

            FileLogger.log(context, TAG, "Restaurant Meta: $name ($lat, $lng)")

            return RestaurantMeta(
                resId = resId,
                name = name,
                lat = lat,
                lng = lng
            )

        } catch (e: Exception) {
            FileLogger.log(context, TAG, "GetRestaurantMeta Exception", e)
            return null
        }
    }

    fun getFoodRescueCart(
        context: Context,
        location: UserLocation,
        essentials: TabbedHomeEssentials,
        accessToken: String
    ): FoodRescueCartInfo? {
        FileLogger.log(context, TAG, ">>> TRIGGER: getFoodRescueCart <<<")

        var responseBody = ""

        try {
            val url = "https://api.zomato.com/gw/gamification/food-rescue/create-cart"

            // FIX 1: Renamed to 'requestPayload' to avoid conflict later
            val requestPayload = JSONObject().apply {
                put("identifier", JSONArray()) // Empty array as per original
                put("location", JSONObject().apply {
                    put("entity_type", location.entityType)
                    put("lng", location.lng)
                    put("place_type", if (location.entityId != null) "DSZ" else "PLACE")
                    put("address_id", location.addressId.toString())
                    put("entity_id", location.entityId?.toString())
                    put("cell_id", location.cellId)
                    put("place_id", location.placeId)
                    put("lat", location.lat)
                    put("current_city_id", essentials.cityId.toString())
                    put("city_id", essentials.cityId.toString())
                })
            }

            val payloadString = requestPayload.toString()
            FileLogger.log(context, TAG, "Payload: $payloadString")

            val headersBuilder = commonHeaders.newBuilder()
                .add("X-Zomato-Access-Token", accessToken)
                .add("Content-Type", "application/json; charset=UTF-8")

            if (location.lat != null) headersBuilder.add("X-User-Defined-Lat", location.lat.toString())
            if (location.lng != null) headersBuilder.add("X-User-Defined-Long", location.lng.toString())

            headersBuilder.add("X-City-Id", essentials.cityId.toString())
            headersBuilder.add("X-O2-City-Id", essentials.cityId.toString())

            val request = Request.Builder()
                .url(url)
                .headers(headersBuilder.build())
                .post(payloadString.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            responseBody = response.body?.string() ?: ""

            FileLogger.log(context, TAG, "Response Code: ${response.code}")
            FileLogger.log(context, TAG, "Raw Response Body (Truncated): ${responseBody.take(500)}...")

            if (!response.isSuccessful) {
                FileLogger.log(context, TAG, "FoodRescueCart Failed. Full Body: $responseBody")
                return null
            }

            val json = JSONObject(responseBody)
            val responseObj = json.getJSONObject("response")
            val floatingTimer = responseObj.getJSONObject("floating_timer_view_1")
            val clickAction = floatingTimer.getJSONObject("click_action")
            val openBottomSheet = clickAction.getJSONObject("open_food_rescue_bottom_sheet")
            val results = openBottomSheet.getJSONArray("results")
            val firstResult = results.getJSONObject(0)
            val timerSnippet = firstResult.getJSONObject("timer_snippet_type_4")
            val button = timerSnippet.getJSONObject("button")
            val buttonClick = button.getJSONObject("click_action")
            val deeplink = buttonClick.getJSONObject("deeplink")
            val deeplinkUrl = deeplink.getString("url")
            val postBodyStr = deeplink.getString("post_body")

            val uri = deeplinkUrl.toUri()
            val storeId = uri.getQueryParameter("res_id") ?: throw IllegalStateException("Missing res_id in deeplink URL")

            val postBody = JSONObject(postBodyStr)
            val cartId = postBody.getString("cart_id")
            val contextObj = postBody.getJSONObject("context")
            val cartModification = contextObj.getJSONObject("cart_modification")
            val parentOrderId = cartModification.getString("ParentOrderID")
            val parentCartId = cartModification.getString("ParentCartID")
            val cartModificationType = cartModification.getString("CartModificationType")
            val cartAnalytics = contextObj.getJSONObject("cart_analytics_data")
            val viewersCount = cartAnalytics.getString("number_of_people_watching").toInt()
            val cartExpiryTimestamp = cartAnalytics.getString("cart_expiry_timestamp").toLong()

            val timerContainer = timerSnippet.getJSONObject("timer_container_data")
            val timerCompleteAction = timerContainer.getJSONObject("timer_complete_action")
            val showPopup = timerCompleteAction.getJSONObject("show_snippet_popup")
            val snippets = showPopup.getJSONArray("snippets")
            val firstSnippet = snippets.getJSONObject(0)
            val imageTextSnippet = firstSnippet.getJSONObject("image_text_snippet_type_43")
            val items = imageTextSnippet.getJSONArray("items")
            val item = items.getJSONObject(0)
            val trackingData = item.getJSONArray("tracking_data")
            val trackingObj = trackingData.getJSONObject(0)
            val payloadStr = trackingObj.getString("payload")

            val trackingPayloadJson = JSONObject(payloadStr)

            val value = trackingPayloadJson.getJSONObject("value")
            val cartFinalCost = value.getDouble("cart_final_cost")
            val catalogTotalCost = value.optDouble("catalog_total_cost", -1.0).takeIf { it >= 0 }


            FileLogger.log(context, TAG, "Extraction Success! StoreID: $storeId, Cost: $cartFinalCost, Viewers: $viewersCount, CartID: $cartId")

            return FoodRescueCartInfo(
                resId = storeId,
                cartFinalCost = cartFinalCost,
                viewersCount = viewersCount,
                cartId = cartId,
                parentOrderId = parentOrderId,
                parentCartId = parentCartId,
                cartModificationType = cartModificationType,
                cartExpiryTimestamp = cartExpiryTimestamp,
                catalogTotalCost = catalogTotalCost
            )

        } catch (e: Exception) {
            FileLogger.log(context, TAG, "getFoodRescueCart Exception: ${e.message}. Full Response for Debug: $responseBody", e)
            return null
        }
    }


    fun createFoodRescueCart(
        context: Context,
        foodRescueCartInfo: FoodRescueCartInfo,
        location: UserLocation,
        essentials: TabbedHomeEssentials,
        accessToken: String
    ): String? {
        FileLogger.log(context, TAG, ">>> TRIGGER: createCart <<<")

        try {
            val url = "https://api.zomato.com/gw/cart/create"

            val customerId = Prefs.getUserId(context) ?: run {
                FileLogger.log(context, TAG, "Missing customer ID from preferences")
                return null
            }

            val payload = JSONObject().apply {
                put("payment_method_type", "")
                put("cart_id", foodRescueCartInfo.cartId)
                put("payment_method_id", "")
                put("request_type", "initial")
                put("catalog", JSONArray().apply {
                    put(JSONObject().apply {
                        put("applied_filter_slugs", JSONArray())
                        put("healthy_enabled", false)
                        put("is_bxgy_offer_fully_availed", false)
                        put("store", JSONObject().apply {
                            put("store_id", foodRescueCartInfo.resId.toIntOrNull() ?: throw IllegalArgumentException("Invalid resId"))
                        })
                    })
                })
                put("context", JSONObject().apply {
                    put("cart_modification", JSONObject().apply {
                        put("ParentOrderID", foodRescueCartInfo.parentOrderId)
                        put("ParentCartID", foodRescueCartInfo.parentCartId)
                        put("CartModificationType", foodRescueCartInfo.cartModificationType)
                    })
                    put("cart_analytics_data", JSONObject().apply {
                        put("number_of_people_watching", foodRescueCartInfo.viewersCount.toString())
                        put("cart_expiry_timestamp", foodRescueCartInfo.cartExpiryTimestamp.toString())
                    })
                })
                put("view_type", "bottom_sheet_cart")
                put("payment", JSONObject().apply {
                    put("payment_info", JSONObject())
                })
                put("location", JSONObject().apply {
                    put("address_id", location.addressId)
                    put("cell_id", location.cellId)
                    put("city_id", essentials.cityId)
                    put("country_id", 1)
                    put("is_gps_enabled", false)
                    put("user_defined_latitude", location.lat)
                    put("user_defined_longitude", location.lng)
                })
                put("store", JSONObject())
                put("init_foreground_call", true)
                put("customer", JSONObject().apply {
                    put("id", customerId.toIntOrNull() ?: throw IllegalArgumentException("Invalid customerId"))
                })
            }

            val payloadString = payload.toString()
            FileLogger.log(context, TAG, "Payload: $payloadString")

            val headersBuilder = commonHeaders.newBuilder()
                .add("X-Zomato-Access-Token", accessToken)
                .add("Content-Type", "application/json; charset=UTF-8")

            if (location.lat != null) headersBuilder.add("X-User-Defined-Lat", location.lat.toString())
            if (location.lng != null) headersBuilder.add("X-User-Defined-Long", location.lng.toString())

            headersBuilder.add("X-City-Id", essentials.cityId.toString())
            headersBuilder.add("X-O2-City-Id", essentials.cityId.toString())

            val request = Request.Builder()
                .url(url)
                .headers(headersBuilder.build())
                .post(payloadString.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            FileLogger.log(context, TAG, "Response Code: ${response.code}")
            FileLogger.log(context, TAG, "Raw Response Body (Truncated): ${responseBody}...")

            if (!response.isSuccessful) {
                FileLogger.log(context, TAG, "createFoodRescueCart Failed. Full Body: $responseBody")
                return null
            }

            return responseBody

        } catch (e: Exception) {
            FileLogger.log(context, TAG, "createFoodRescueCart Exception", e)
            return null
        }
    }



}