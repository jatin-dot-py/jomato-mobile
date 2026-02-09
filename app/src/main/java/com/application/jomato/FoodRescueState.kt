package com.application.jomato

import com.application.jomato.api.TabbedHomeEssentials
import com.application.jomato.api.UserLocation

data class FoodRescueState(
    val essentials: TabbedHomeEssentials,
    val location: UserLocation,
    val startedAtTimestamp: Long,
    val totalCancelledMessages: Int,
    val totalClaimedMessages: Int,
    val totalReconnects: Int
)