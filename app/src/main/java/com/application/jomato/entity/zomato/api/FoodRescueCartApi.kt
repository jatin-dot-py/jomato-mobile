package com.application.jomato.entity.zomato.api

import android.content.Context
import androidx.core.net.toUri
import com.application.jomato.utils.FileLogger
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

internal object FoodRescueCartApi {

    fun getFoodRescueCart(
        context: Context,
        location: UserLocation,
        essentials: TabbedHomeEssentials,
        accessToken: String
    ): FoodRescueCartInfo? {
        FileLogger.log(context, ApiBase.TAG, ">>> TRIGGER: getFoodRescueCart <<<")

        var responseBody = ""

        try {
            val url = "https://api.zomato.com/gw/gamification/food-rescue/create-cart"

            val requestPayload = JSONObject().apply {
                put("identifier", JSONArray())
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
            FileLogger.log(context, ApiBase.TAG, "Payload: $payloadString")

            val headersBuilder = ApiBase.commonHeaders.newBuilder()
                .add("X-Zomato-Access-Token", accessToken)
                .add("Content-Type", "application/json; charset=UTF-8")

            if (location.lat != null) headersBuilder.add("X-User-Defined-Lat", location.lat.toString())
            if (location.lng != null) headersBuilder.add("X-User-Defined-Long", location.lng.toString())

            headersBuilder.add("X-City-Id", essentials.cityId.toString())
            headersBuilder.add("X-O2-City-Id", essentials.cityId.toString())

            val request = Request.Builder()
                .url(url)
                .headers(headersBuilder.build())
                .post(payloadString.toRequestBody(ApiBase.jsonMediaType))
                .build()

            val response = ApiBase.client.newCall(request).execute()
            responseBody = response.body?.string() ?: ""

            FileLogger.log(context, ApiBase.TAG, "Response Code: ${response.code}")
            FileLogger.log(context, ApiBase.TAG, "Raw Response Body (Truncated): ${responseBody.take(500)}...")

            if (!response.isSuccessful) {
                FileLogger.log(context, ApiBase.TAG, "FoodRescueCart Failed. Full Body: $responseBody")
                return null
            }

            return parseCartResponse(context, responseBody)

        } catch (e: Exception) {
            FileLogger.log(context, ApiBase.TAG, "getFoodRescueCart Exception: ${e.message}. Full Response for Debug: $responseBody", e)
            return null
        }
    }

    private fun parseCartResponse(context: Context, responseBody: String): FoodRescueCartInfo {
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
        val storeId = uri.getQueryParameter("res_id")
            ?: throw IllegalStateException("Missing res_id in deeplink URL")

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

        FileLogger.log(context, ApiBase.TAG, "Extraction Success! StoreID: $storeId, Cost: $cartFinalCost, Viewers: $viewersCount, CartID: $cartId")

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
    }

}
