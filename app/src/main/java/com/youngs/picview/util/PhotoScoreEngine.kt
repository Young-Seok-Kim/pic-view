package com.youngs.picview.ui.main

import com.youngs.picview.ui.model.SpotItem
import com.youngs.picview.ui.model.SpotScoreContext

object PhotoScoreEngine {
    fun calculateScore(context: SpotScoreContext): Int {
        // 1. 기본 점수 (인기도/카테고리 기반)
        var baseScore = 50.0
        when (context.spot.contentTypeId) {
            "12" -> baseScore += 30
            "14" -> baseScore += 40
            "28" -> baseScore += 10
            "39" -> baseScore -= 10
        }

        // 2. 가중치 팩터 (1.0을 기본으로 하여 곱함)
        var weatherFactor = 1.0
        if (context.currentTemp in 15.0..25.0) weatherFactor += 0.2 // 날씨 좋으면 20% 보너스

        var goldenHourFactor = 1.0
        if (context.isGoldenHour) {
            goldenHourFactor += 1.0 // 골든아워면 2배
            if (context.direction == "WEST") goldenHourFactor += 0.8 // 서쪽이면 추가 가중
        }


        // 비가 올 때의 로직
        if (context.isRaining) {
            when (context.spot.contentTypeId) {
                "14", "39" -> weatherFactor += 1.5 // 문화시설, 음식점은 비 올 때 가산점 대폭
                "12" -> weatherFactor -= 0.7      // 자연 스팟은 비 올 때 감점
            }
        } else {
            // 맑을 때의 로직
            if (context.currentTemp in 15.0..25.0) weatherFactor += 0.2
        }

        // 3. 수식 적용: (인기도 기반 점수) * (기상 가중치) * (골든아워 가중치)
        val finalScore = (baseScore * weatherFactor * goldenHourFactor).toInt()

        return finalScore
    }
}