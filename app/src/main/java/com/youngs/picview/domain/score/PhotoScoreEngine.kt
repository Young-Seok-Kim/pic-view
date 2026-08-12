package com.youngs.picview.domain.score

import com.youngs.picview.domain.season.SeasonHighlights
import com.youngs.picview.domain.spot.Facing
import com.youngs.picview.domain.spot.SpotFactsTable
import com.youngs.picview.ui.model.SpotScoreContext
import kotlin.math.roundToInt

/**
 * 촬영지의 "지금 찍기 좋은 정도"를 0~100 으로 환산합니다.
 *
 * ## 왜 다시 짰나
 *
 * 이전 배점은 100점 중 사진과 직결된 항목이 빛 20점뿐이었습니다. 나머지는
 * 관광공사 정보 갱신일(10점), API 에 대표 사진이 있는지(4점), 사용자와의
 * 거리(10점) 처럼 **사진이 잘 나오는지와 무관한 값**이었습니다. 그래서
 * "정보가 최근에 갱신됐어요" 같은 문장이 점수 근거로 올라왔습니다.
 *
 * 앱은 이미 스팟마다 촬영 특성(방위·최적 시간대·구도)과 계절 절정기를
 * 갖고 있는데 점수 엔진만 그걸 쓰지 않았습니다. 그 정보를 점수의 중심으로
 * 옮겼습니다.
 *
 * ## 배점 (합계 100 기준)
 *
 * | 항목 | 배점 | 사진과의 관계 |
 * |---|---|---|
 * | 빛 구간 적합 | 6 ~ 26 | 지금 빛이 이 장소에 맞는가 |
 * | 방위 적합 | 0 ~ 16 | 해가 있는 쪽과 장소가 바라보는 쪽 |
 * | 계절 적합 | 0 ~ 18 | 단풍·구절초 같은 피사체의 절정기인가 |
 * | 날씨 | -18 ~ +10 | 비·흐림. 실내는 비가 오히려 유리 |
 * | 소재 폭 | 12 ~ 20 | 유형별로 담을 수 있는 것의 넓이 |
 * | 머무는 여건 | 0 ~ 6 | 기온. 오래 못 있으면 기다릴 수 없음 |
 * | 접근성 | 1 ~ 4 | 거리. 사진 품질이 아니라 실행 가능성 |
 *
 * 곱셈이 아니라 덧셈입니다. 곱하면 골든아워에 전부 만점 근처로 몰려
 * 상위권 변별력이 사라집니다.
 *
 * [evaluate] 는 항목별 근거까지 돌려줍니다. 추천을 신뢰하려면 "왜 이 점수인지"를
 * 볼 수 있어야 합니다.
 */
object PhotoScoreEngine {

    /** 총점만 필요할 때. */
    fun calculateScore(context: SpotScoreContext): Int = evaluate(context).total

    fun evaluate(context: SpotScoreContext): PhotoScore {
        val factors = listOf(
            subjectFactor(context),
            lightFactor(context),
            facingFactor(context),
            seasonFactor(context),
            weatherFactor(context),
            comfortFactor(context),
            distanceFactor(context)
        )
        val total = factors.sumOf { it.earned }.roundToInt().coerceIn(0, 100)
        return PhotoScore(total, factors)
    }

    // ─────────────────────────── 항목별 ───────────────────────────

    /**
     * 소재 폭 — 이 유형에서 담을 수 있는 그림의 넓이.
     *
     * 자연·관광지는 풍경·접사·인물이 모두 되지만 음식점은 대개 접사 한 가지입니다.
     */
    private fun subjectFactor(c: SpotScoreContext): ScoreFactor {
        val (score, reason) = when (c.spot.contentTypeId) {
            "12" -> 20.0 to "풍경·인물·접사를 두루 담을 수 있어요"
            "14" -> 18.0 to "건축 선과 대칭을 담기 좋아요"
            "28" -> 15.0 to "움직임을 담기 좋은 곳이에요"
            "39" -> 12.0 to "접사 위주로 담게 되는 곳이에요"
            else -> 14.0 to "일반 촬영지예요"
        }
        return ScoreFactor(FactorKind.SUBJECT, score, 12.0, 20.0, reason)
    }

    /**
     * 빛 구간 적합 — 지금의 빛이 이 장소가 가장 좋은 시간대와 맞는가.
     *
     * 골든아워라는 사실만 보지 않고 **이 장소의 최적 시간대와 일치하는지**를
     * 봅니다. 실내 위주 스팟에 골든아워 만점을 주면 뜻이 없습니다.
     */
    private fun lightFactor(c: SpotScoreContext): ScoreFactor {
        val facts = SpotFactsTable.of(c.spot.title, c.spot.contentTypeId)
        val indoor = facts.facing == Facing.INDOOR

        val (score, reason) = when {
            indoor && c.isGoldenHour ->
                10.0 to "실내라 골든아워의 덕은 덜 봐요"
            indoor ->
                12.0 to "실내라 시간대를 크게 타지 않아요"
            c.isGoldenHour && c.bestTime == "SUNSET" ->
                26.0 to "지금 골든아워이고 이곳이 가장 좋은 시간대예요"
            c.isGoldenHour ->
                21.0 to "지금 골든아워라 빛이 부드러워요"
            c.bestTime == "SUNSET" ->
                8.0 to "해질 무렵에 다시 오면 훨씬 좋아요"
            c.bestTime == "AFTERNOON" ->
                16.0 to "지금 시간대의 빛과 맞아요"
            else ->
                11.0 to "빛 조건은 보통이에요"
        }
        return ScoreFactor(FactorKind.LIGHT, score, 6.0, 26.0, reason)
    }

    /**
     * 방위 적합 — 해가 있는 쪽과 장소가 바라보는 쪽의 관계.
     *
     * 일출에는 동향, 일몰에는 서향이 역광·실루엣이 살아납니다.
     * 이 정보는 API 가 주지 않아 [SpotFactsTable] 에 직접 정리해 둔 값입니다.
     */
    private fun facingFactor(c: SpotScoreContext): ScoreFactor {
        val facing = SpotFactsTable.of(c.spot.title, c.spot.contentTypeId).facing

        val (score, reason) = when {
            facing == Facing.INDOOR ->
                6.0 to "실내라 방위를 타지 않아요"
            c.isGoldenHour && facing.name == c.direction ->
                16.0 to "${facing.label}이라 지금 해를 정면으로 받아요"
            facing.name == c.direction ->
                11.0 to "${facing.label}이라 해가 드는 방향이에요"
            facing == Facing.ANY ->
                8.0 to "어느 방향에서든 담을 수 있어요"
            else ->
                5.0 to "${facing.label}이라 지금은 빛이 비껴들어요"
        }
        return ScoreFactor(FactorKind.FACING, score, 0.0, 16.0, reason)
    }

    /**
     * 계절 적합 — 이 장소의 피사체가 지금 절정인가.
     *
     * 내장산 단풍을 한여름에 가면 사진은 평범합니다. 절정기 정보는
     * [SeasonHighlights] 에 있는데 점수 엔진이 쓰지 않고 있었습니다.
     */
    private fun seasonFactor(c: SpotScoreContext): ScoreFactor {
        val highlight = SeasonHighlights.ALL.firstOrNull {
            c.spot.title.contains(it.spotKeyword)
        } ?: return ScoreFactor(
            FactorKind.SEASON, 9.0, 0.0, 18.0, "계절을 크게 타지 않는 곳이에요"
        )

        val days = highlight.daysUntilPeak()
        val (score, reason) = when {
            highlight.isPeakNow() -> 18.0 to "지금이 ${highlight.subject} 절정이에요"
            days <= 14 -> 13.0 to "${days}일 뒤면 ${highlight.subject} 절정이에요"
            days <= 45 -> 7.0 to "${highlight.subject} 철까지는 아직 남았어요"
            else -> 3.0 to "${highlight.subject}는 지금 철이 아니에요"
        }
        return ScoreFactor(FactorKind.SEASON, score, 0.0, 18.0, reason)
    }

    /** 강수 여부. 실내 위주 스팟은 비 올 때 오히려 유리합니다. */
    private fun weatherFactor(c: SpotScoreContext): ScoreFactor {
        val type = c.spot.contentTypeId
        val (score, reason) = when {
            !c.isRaining -> 8.0 to "비 없이 맑아요"
            type == "14" || type == "39" -> 10.0 to "비가 오지만 실내라 오히려 유리해요"
            type == "12" -> -18.0 to "비가 와서 야외 촬영에 불리해요"
            else -> -10.0 to "비가 와서 촬영이 어려워요"
        }
        return ScoreFactor(FactorKind.WEATHER, score, -18.0, 10.0, reason)
    }

    /**
     * 머무는 여건 — 기온.
     *
     * 사진 품질 자체는 아니지만, 빛을 기다릴 수 있느냐를 가릅니다.
     * 삼각대를 펴고 30분을 버텨야 나오는 그림이 있습니다. 배점은 낮게 둡니다.
     */
    private fun comfortFactor(c: SpotScoreContext): ScoreFactor {
        val (score, reason) = when (c.currentTemp) {
            in 15.0..25.0 -> 6.0 to "빛을 기다리기 좋은 기온이에요"
            in 8.0..30.0 -> 3.0 to "조금 덥거나 쌀쌀해요"
            else -> 0.0 to "오래 서 있기 힘든 기온이에요"
        }
        return ScoreFactor(FactorKind.COMFORT, score, 0.0, 6.0, reason)
    }

    /**
     * 접근성. 사진 품질이 아니라 "지금 갈 수 있느냐"입니다.
     * 그래서 배점을 4점으로 낮췄습니다. 거리 단위는 km 입니다.
     */
    private fun distanceFactor(c: SpotScoreContext): ScoreFactor {
        val km = c.userDistance
        val (score, reason) = when {
            km <= 0.0 -> 2.0 to "위치 정보를 켜면 거리까지 반영돼요"
            km <= 2.0 -> 4.0 to "아주 가까워요 (${fmt(km)}km)"
            km <= 5.0 -> 3.5 to "가까워요 (${fmt(km)}km)"
            km <= 10.0 -> 2.5 to "조금 떨어져 있어요 (${fmt(km)}km)"
            km <= 20.0 -> 1.5 to "차로 이동이 필요해요 (${fmt(km)}km)"
            else -> 1.0 to "많이 떨어져 있어요 (${fmt(km)}km)"
        }
        return ScoreFactor(FactorKind.ACCESSIBILITY, score, 1.0, 4.0, reason)
    }

    private fun fmt(km: Double) = "%.1f".format(km)
}
