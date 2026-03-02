package com.application.jomato.entity.zomato.rescue

import com.application.jomato.entity.zomato.api.TabbedHomeEssentials
import com.application.jomato.entity.zomato.api.UserLocation

data class FoodRescueState(
    val essentials: TabbedHomeEssentials,
    val location: UserLocation,
    val startedAtTimestamp: Long,
)