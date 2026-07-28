package com.youngs.picview.ui.model

data class SpotScoreContext(
    val spot: SpotItem,
    val currentTemp: Double,
    val isGoldenHour: Boolean,
    val isRaining: Boolean, // 현재 비가 오는지 여부
    val userDistance: Double, // 사용자로부터의 거리 (km). 0 이하면 미측정
    val direction: String, // "WEST", "EAST", "NONE"
    val bestTime: String,  // "MORNING", "AFTERNOON", "SUNSET"

    /**
     * 관광공사 목록(수정일순)에서의 순위를 0.0(최신) ~ 1.0(오래됨) 으로 환산한 값.
     * 정보가 최근에 갱신된 곳일수록 실제로 가 볼 만하다고 보고,
     * 같은 카테고리 안에서 점수를 벌어지게 하는 데 씁니다.
     */
    val freshnessRank: Double = 0.5,

    /** 대표 사진이 등록되어 있는지. 사진이 있는 곳이 촬영지로서 검증된 편입니다. */
    val hasPhoto: Boolean = false
)
