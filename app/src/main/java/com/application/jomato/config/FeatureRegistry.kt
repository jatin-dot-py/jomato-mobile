package com.application.jomato.config

import androidx.compose.runtime.Composable
import com.application.jomato.entity.zomato.rescue.FoodRescueContent

object FeatureRegistry {

    private val byId: Map<String, FeatureEntry> = listOf(
        ZomatoFoodRescueFeature
    ).associateBy { it.id }

    fun resolve(id: String): FeatureEntry? = byId[id]
}

interface FeatureEntry {
    val id: String

    @Composable
    fun Content(sessionId: String)
}

object ZomatoFoodRescueFeature : FeatureEntry {
    override val id: String = "zomato_food_rescue"

    @Composable
    override fun Content(sessionId: String) {
        FoodRescueContent(sessionId)
    }
}
