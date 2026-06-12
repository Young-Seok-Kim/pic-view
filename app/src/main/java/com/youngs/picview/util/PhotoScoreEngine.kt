package com.youngs.picview.ui.main

import com.youngs.picview.ui.model.SpotItem

object PhotoScoreEngine {
    fun calculateScore(spot: SpotItem): Int {
        var score = 50 // 기본 점수

        // API contentTypeId 기준 가중치
        when (spot.contentTypeId) {
            "12" -> score += 30 // 관광지 (자연/문화)
            "14" -> score += 40 // 문화시설 (박물관 등)
            "28" -> score += 10 // 레포츠 (캠핑장 등)
            "39" -> score -= 10 // 음식점 (출사 가이드 앱이므로 상대적 감점)
        }

        // 제목 키워드 가산점
        if (spot.title.contains("정읍")) score += 20
        if (spot.title.contains("내장산")) score += 30

        return score
    }
}