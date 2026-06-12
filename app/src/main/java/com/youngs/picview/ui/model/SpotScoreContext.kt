package com.youngs.picview.ui.model

data class SpotScoreContext(
    val spot: SpotItem,
    val currentTemp: Double,
    val isGoldenHour: Boolean,
    val userDistance: Double, // 사용자로부터의 거리 (km)
    val direction: String, // "WEST", "EAST", "NONE"
    val bestTime: String   // "MORNING", "AFTERNOON", "SUNSET"
)