package com.youngs.picview.ui.main

import com.youngs.picview.ui.model.SpotItem
import com.youngs.picview.ui.model.SpotScoreContext

object PhotoScoreEngine {
    fun calculateScore(context: SpotScoreContext): Int {
        var score = 50

        // 1. 카테고리 기본 가중치
        when (context.spot.contentTypeId) {
            "12" -> score += 30 // 자연
            "14" -> score += 40 // 문화시설
            "28" -> score += 10 // 레포츠
            "39" -> score -= 10 // 음식점
        }

        // 2. 골든아워 & 방향성 결합 (일몰 시 서쪽 스팟 보너스)
        if (context.isGoldenHour) {
            score += 50
            if (context.direction == "WEST") score += 40 // 서쪽은 일몰에 최고!
        }

        // 3. 최적 시간대 반영
        val nowHour = java.time.LocalTime.now().hour
        when (context.bestTime) {
            "MORNING" -> if (nowHour in 6..11) score += 30
            "AFTERNOON" -> if (nowHour in 12..16) score += 30
            "SUNSET" -> if (nowHour in 17..19) score += 30
        }

        // 4. 기타 가중치
        if (context.currentTemp in 15.0..25.0) score += 20
        if (context.userDistance < 5.0) score += 30

        if (context.spot.title.contains("정읍")) score += 20
        if (context.spot.title.contains("내장산")) score += 30

        return score
    }
}