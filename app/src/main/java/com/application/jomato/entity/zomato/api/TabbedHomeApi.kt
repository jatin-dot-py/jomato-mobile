package com.application.jomato.entity.zomato.api

import android.content.Context
import com.application.jomato.utils.FileLogger
import kotlinx.serialization.json.*
import okhttp3.Request

internal object TabbedHomeApi {

    fun getTabbedHomeEssentials(
        context: Context,
        cellId: String,
        addressId: Int,
        accessToken: String
    ): TabbedHomeEssentials? {
        FileLogger.log(context, ApiBase.TAG, "Fetching HomeEssentials for AddrID: $addressId")
        try {
            val url = "https://api.zomato.com/gw/tabbed-home?cell_id=$cellId&address_id=$addressId"

            val request = Request.Builder()
                .url(url)
                .headers(ApiBase.authenticatedHeaders(accessToken))
                .get()
                .build()

            val response = ApiBase.client.newCall(request).execute()
            val responseString = response.body?.string()

            if (!response.isSuccessful || responseString == null) {
                FileLogger.log(context, ApiBase.TAG, "HomeEssentials Failed. Code: ${response.code}")
                return null
            }

            val root = ApiBase.jsonParser.parseToJsonElement(responseString).jsonObject

            val cityId = root["location"]?.jsonObject
                ?.get("city")?.jsonObject
                ?.get("id")?.jsonPrimitive?.intOrNull ?: 0

            val foodRescue = parseFoodRescueChannel(context, root)

            return TabbedHomeEssentials(
                cityId = cityId,
                foodRescue = foodRescue
            )

        } catch (e: Exception) {
            FileLogger.log(context, ApiBase.TAG, "HomeEssentials Exception", e)
            return null
        }
    }

    private fun parseFoodRescueChannel(context: Context, root: JsonObject): FoodRescueConf? {
        val channels = root["subscription_channels"]?.jsonArray
        if (channels == null) {
            FileLogger.log(context, ApiBase.TAG, "No subscription channels found.")
            return null
        }

        for (element in channels) {
            val channel = element.jsonObject
            if (channel["type"]?.jsonPrimitive?.content != "food_rescue") continue

            val clientData = channel["client"]?.jsonObject
            val channelName = channel["name"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content ?: ""

            FileLogger.log(context, ApiBase.TAG, "Food Rescue Channel Found: $channelName")

            return FoodRescueConf(
                channelName = channelName,
                qos = channel["qos"]?.jsonPrimitive?.intOrNull ?: 0,
                validUntil = channel["time"]?.jsonPrimitive?.longOrNull ?: 0L,
                client = FoodRescueClient(
                    username = clientData?.get("username")?.jsonPrimitive?.content ?: "",
                    password = clientData?.get("password")?.jsonPrimitive?.content ?: "",
                    keepalive = clientData?.get("keepalive")?.jsonPrimitive?.intOrNull ?: 0
                )
            )
        }
        return null
    }
}
