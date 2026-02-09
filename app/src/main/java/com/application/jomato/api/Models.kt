package com.application.jomato.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserInfo(
    val id: Int,
    val name: String,
    val mobile: String,
    @SerialName("mobile_country_isd") val mobileCountryIsd: Int,
    val email: String? = null,
    val theme: String? = null
)

@Serializable
data class UserLocation(
    val name: String,
    @SerialName("full_address") val fullAddress: String,
    @SerialName("address_id") val addressId: Int,
    @SerialName("cell_id") val cellId: String,

    @SerialName("entity_type") val entityType: String = "subzone",
    @SerialName("entity_id") val entityId: Int? = null,
    @SerialName("place_type") val placeType: String = "DSZ",
    @SerialName("place_id") val placeId: String? = null,

    val lat: Double? = null,
    val lng: Double? = null
)

@Serializable
data class UserLocationsResponse(
    val success: Boolean,
    val data: List<UserLocation> = emptyList(),
    val error: String? = null,
    val statusCode: Int = 0
)


@Serializable
data class TabbedHomeEssentials(
    @SerialName("city_id") val cityId: Int,
    @SerialName("food_rescue") val foodRescue: FoodRescueConf? = null
)

@Serializable
data class FoodRescueConf(
    @SerialName("channel_name") val channelName: String,
    val qos: Int,
    @SerialName("valid_until") val validUntil: Long,
    val client: FoodRescueClient
)

@Serializable
data class FoodRescueClient(
    val username: String,
    val password: String,
    val keepalive: Int
)

@Serializable
data class RestaurantMeta(
    val resId: String,
    val name: String,
    val lat: Double?,
    val lng: Double?
)

@Serializable
data class FoodRescueCartInfo(
    val resId: String,
    val cartFinalCost: Double,
    val viewersCount: Int,
    val cartId: String,
    val parentOrderId: String,
    val parentCartId: String,
    val cartModificationType: String,
    val cartExpiryTimestamp: Long,
    val catalogTotalCost: Double? = null
)