package com.youngs.picview.ui.model

data class SpotScoreContext(
    val spot: SpotItem,
    val currentTemp: Double,
    val isGoldenHour: Boolean,
    val isRaining: Boolean, // 현재 비가 오는지 여부
    val userDistance: Double, // 사용자로부터의 거리 (km)
    val direction: String, // "WEST", "EAST", "NONE"
    val bestTime: String   // "MORNING", "AFTERNOON", "SUNSET"
)