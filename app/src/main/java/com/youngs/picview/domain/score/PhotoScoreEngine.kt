package com.youngs.picview.domain.score

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
 *
 * [evaluate] 는 항목별 근거까지 돌려줍니다. 상세 화면이 "왜 이 점수인지"를
 * 설명할 수 있어야 추천을 신뢰하게 되기 때문입니다.
 */
object PhotoScoreEngine {

    /** 총점만 필요할 때. */
    fun calculateScore(context: SpotScoreContext): Int = evaluate(context).total

    fun evaluate(context: SpotScoreContext): PhotoScore {
        val factors = listOf(
            baseFactor(context),
            freshnessFactor(context),
            photoFactor(context),
            lightFactor(context),
            weatherFactor(context),
            temperatureFactor(context),
            distanceFactor(context)
        )
        val total = factors.sumOf { it.earned }.roundToInt().coerceIn(0, 100)
        return PhotoScore(total, factors)
    }

    // ─────────────────────────── 항목별 ───────────────────────────

    /** 카테고리별 기본 매력도. */
    private fun baseFactor(c: SpotScoreContext): ScoreFactor {
        val (score, reason) = when (c.spot.contentTypeId) {
            "12" -> 44.0 to "관광지·자연은 촬영 소재가 넓어요"
            "14" -> 42.0 to "문화시설은 구조물 촬영에 유리해요"
            "28" -> 37.0 to "레포츠 시설이에요"
            "39" -> 34.0 to "음식점이에요"
            else -> 36.0 to "일반 촬영지예요"
        }
        return ScoreFactor(FactorKind.BASE_ATTRACTION, score, 34.0, 44.0, reason)
    }

    /** 정보가 최근에 갱신된 곳일수록 높은 점수. 같은 카테고리 안에서 순위를 벌려 줍니다. */
    private fun freshnessFactor(c: SpotScoreContext): ScoreFactor {
        val rank = c.freshnessRank.coerceIn(0.0, 1.0)
        val score = 10.0 * (1.0 - rank)
        val reason = when {
            rank <= 0.2 -> "정보가 최근에 갱신됐어요"
            rank <= 0.6 -> "정보가 비교적 최신이에요"
            else -> "정보가 갱신된 지 좀 됐어요"
        }
        return ScoreFactor(FactorKind.FRESHNESS, score, 0.0, 10.0, reason)
    }

    private fun photoFactor(c: SpotScoreContext) = ScoreFactor(
        FactorKind.HAS_PHOTO,
        if (c.hasPhoto) 4.0 else 0.0,
        0.0, 4.0,
        if (c.hasPhoto) "대표 사진이 등록돼 있어요" else "등록된 대표 사진이 없어요"
    )

    /** 빛 조건. 골든아워, 그중에서도 서향 스팟에 가장 높은 배점을 줍니다. */
    private fun lightFactor(c: SpotScoreContext): ScoreFactor {
        val (score, reason) = when {
            c.isGoldenHour && c.direction == "WEST" ->
                20.0 to "지금 골든아워이고 서향이에요. 최적 조건이에요"
            c.isGoldenHour -> 14.0 to "지금 골든아워예요"
            c.bestTime == "AFTERNOON" -> 8.0 to "지금 시간대에 무난해요"
            c.bestTime == "SUNSET" -> 3.0 to "해질 무렵 다시 오면 훨씬 좋아요"
            else -> 5.0 to "빛 조건은 보통이에요"
        }
        return ScoreFactor(FactorKind.LIGHT, score, 3.0, 20.0, reason)
    }

    /** 강수 여부. 실내 위주 스팟은 비 올 때 오히려 유리합니다. */
    private fun weatherFactor(c: SpotScoreContext): ScoreFactor {
        val type = c.spot.contentTypeId
        val (score, reason) = when {
            !c.isRaining -> 8.0 to "비가 오지 않아요"
            type == "14" || type == "39" -> 10.0 to "비가 오지만 실내라 오히려 유리해요"
            type == "12" -> -18.0 to "비가 와서 야외 촬영에 불리해요"
            else -> -10.0 to "비가 와서 촬영이 어려워요"
        }
        return ScoreFactor(FactorKind.WEATHER, score, -18.0, 10.0, reason)
    }

    /** 야외 활동 쾌적도. */
    private fun temperatureFactor(c: SpotScoreContext): ScoreFactor {
        val (score, reason) = when (c.currentTemp) {
            in 15.0..25.0 -> 8.0 to "야외 활동하기 좋은 기온이에요"
            in 8.0..30.0 -> 4.0 to "조금 덥거나 쌀쌀해요"
            else -> 0.0 to "야외에 오래 있기 힘든 기온이에요"
        }
        return ScoreFactor(FactorKind.TEMPERATURE, score, 0.0, 8.0, reason)
    }

    /**
     * 접근성. 거리 정보가 아직 없으면(0.0) 중립값을 줍니다.
     * 거리 단위는 km 입니다.
     */
    private fun distanceFactor(c: SpotScoreContext): ScoreFactor {
        val km = c.userDistance
        val (score, reason) = when {
            km <= 0.0 -> 5.0 to "위치 정보를 켜면 거리까지 반영돼요"
            km <= 2.0 -> 10.0 to "아주 가까워요 (${fmt(km)}km)"
            km <= 5.0 -> 8.0 to "가까워요 (${fmt(km)}km)"
            km <= 10.0 -> 5.0 to "조금 떨어져 있어요 (${fmt(km)}km)"
            km <= 20.0 -> 3.0 to "차로 이동이 필요해요 (${fmt(km)}km)"
            else -> 1.0 to "많이 떨어져 있어요 (${fmt(km)}km)"
        }
        return ScoreFactor(FactorKind.ACCESSIBILITY, score, 1.0, 10.0, reason)
    }

    private fun fmt(km: Double) = "%.1f".format(km)
}
