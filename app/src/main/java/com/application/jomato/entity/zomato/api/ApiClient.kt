package com.application.jomato.entity.zomato.api

import android.content.Context

object ApiClient {

    fun getUserInfo(context: Context, accessToken: String): UserInfo? =
        UserInfoApi.getUserInfo(context, accessToken)

    fun getUserLocations(context: Context, accessToken: String): UserLocationsResponse =
        UserLocationsApi.getUserLocations(context, accessToken)

    fun getTabbedHomeEssentials(
        context: Context,
        cellId: String,
        addressId: Int,
        accessToken: String
    ): TabbedHomeEssentials? =
        TabbedHomeApi.getTabbedHomeEssentials(context, cellId, addressId, accessToken)

    fun getRestaurantMeta(context: Context, resId: String, accessToken: String): RestaurantMeta? =
        RestaurantMetaApi.getRestaurantMeta(context, resId, accessToken)

    fun getOrderSummary(context: Context, orderId: String, accessToken: String): OrderDetails? =
        OrderSummaryApi.getOrderSummary(context, orderId, accessToken)
}
