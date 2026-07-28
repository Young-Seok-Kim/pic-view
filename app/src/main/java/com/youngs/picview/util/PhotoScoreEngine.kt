package com.youngs.picview.ui.main

import com.youngs.picview.ui.model.SpotScoreContext
import kotlin.math.roundToInt

/**
 * 촬영지의 "지금 찍기 좋은 정도"를 0~100 으로 환산합니다.
 *
 * 이전 구현은 가중치를 곱셈으로 쌓아 골든아워에 300점대까지 튀었고,
 * 그 결과 상위권이 전부 같은 점수처럼 보여 변별력이 없었습니다.
 * 그래서 항목별 배점을 정해 더하는 방식으로 바꿨습니다.
 *
 * 배점(합계 100 기준)
 *  - 장소 기본 매력도 : 34 ~ 44
 *  - 정보 최신성      :  0 ~ 10
 *  - 대표 사진 보유    :  0 ~ 4
 *  - 빛(골든아워)     :  3 ~ 20
 *  - 날씨 적합도      : -18 ~ +8
 *  - 기온 쾌적도      :  0 ~ 8
 *  - 거리             :  1 ~ 10
 */
object PhotoScoreEngine {

    fun calculateScore(context: SpotScoreContext): Int {
        val score = baseScore(context.spot.contentTypeId) +
                freshnessScore(context.freshnessRank) +
                photoScore(context.hasPhoto) +
                lightScore(context) +
                weatherScore(context) +
                temperatureScore(context.currentTemp) +
                distanceScore(context.userDistance)

        return score.roundToInt().coerceIn(0, 100)
    }

    /** 정보가 최근에 갱신된 곳일수록 높은 점수. 같은 카테고리 안에서 순위를 벌려 줍니다. */
    private fun freshnessScore(rank: Double) = 10.0 * (1.0 - rank.coerceIn(0.0, 1.0))

    private fun photoScore(hasPhoto: Boolean) = if (hasPhoto) 4.0 else 0.0

    /** 카테고리별 기본 매력도. */
    private fun baseScore(contentTypeId: String?): Double = when (contentTypeId) {
        "12" -> 44.0 // 관광지·자연
        "14" -> 42.0 // 문화시설
        "28" -> 37.0 // 레포츠
        "39" -> 34.0 // 음식점
        else -> 36.0
    }

    /** 빛 조건. 골든아워, 그중에서도 서향 스팟에 가장 높은 배점을 줍니다. */
    private fun lightScore(context: SpotScoreContext): Double = when {
        context.isGoldenHour && context.direction == "WEST" -> 20.0
        context.isGoldenHour -> 14.0
        context.bestTime == "AFTERNOON" -> 8.0
        context.bestTime == "SUNSET" -> 3.0 // 지금은 아니지만 해질 무렵 다시 오면 좋은 곳
        else -> 5.0
    }

    /** 강수 여부. 실내 위주 스팟은 비 올 때 오히려 유리합니다. */
    private fun weatherScore(context: SpotScoreContext): Double = when {
        !context.isRaining -> 8.0
        context.spot.contentTypeId == "14" || context.spot.contentTypeId == "39" -> 10.0
        context.spot.contentTypeId == "12" -> -18.0
        else -> -10.0
    }

    /** 야외 활동 쾌적도. */
    private fun temperatureScore(temp: Double): Double = when (temp) {
        in 15.0..25.0 -> 8.0
        in 8.0..30.0 -> 4.0
        else -> 0.0
    }

    /**
     * 접근성. 거리 정보가 아직 없으면(0.0) 중립값을 줍니다.
     * 거리 단위는 km 입니다.
     */
    private fun distanceScore(distanceKm: Double): Double = when {
        distanceKm <= 0.0 -> 5.0 // 미측정
        distanceKm <= 2.0 -> 10.0
        distanceKm <= 5.0 -> 8.0
        distanceKm <= 10.0 -> 5.0
        distanceKm <= 20.0 -> 3.0
        else -> 1.0
    }
}
