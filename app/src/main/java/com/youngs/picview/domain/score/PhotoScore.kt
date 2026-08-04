package com.youngs.picview.domain.score

import androidx.annotation.StringRes
import com.youngs.picview.R

/**
 * 포토스코어 항목 하나.
 *
 * 총점만 보여주면 "왜 87점인지" 설명할 수 없어서, 항목별로 쪼개 둡니다.
 * 상세 화면에서 이 목록을 그대로 막대그래프로 그립니다.
 *
 * @param earned 실제 획득 점수
 * @param min    이 항목이 받을 수 있는 최저점 (날씨는 음수가 나올 수 있습니다)
 * @param max    이 항목의 최고점
 */
data class ScoreFactor(
    val kind: FactorKind,
    val earned: Double,
    val min: Double,
    val max: Double,
    /** 왜 이 점수인지 한 줄 설명. */
    val reason: String
) {
    /** 막대 채움 비율 0~1. 음수 구간이 있는 항목도 정상 표시되도록 정규화합니다. */
    val ratio: Float
        get() {
            val span = max - min
            if (span <= 0.0) return 0f
            return ((earned - min) / span).toFloat().coerceIn(0f, 1f)
        }
}

enum class FactorKind(@StringRes val labelRes: Int) {
    BASE_ATTRACTION(R.string.factor_base),
    FRESHNESS(R.string.factor_freshness),
    HAS_PHOTO(R.string.factor_photo),
    LIGHT(R.string.factor_light),
    WEATHER(R.string.factor_weather),
    TEMPERATURE(R.string.factor_temp),
    ACCESSIBILITY(R.string.factor_access)
}

/**
 * 포토스코어 산출 결과.
 * 총점과 근거를 함께 들고 다닙니다.
 */
data class PhotoScore(
    val total: Int,
    val factors: List<ScoreFactor>
) {
    /**
     * 이 장소의 점수를 설명하는 데 가장 쓸모 있는 항목.
     *
     * 단순히 `earned - min` 이 큰 걸 고르면 날씨가 항상 1위가 됩니다.
     * 최저점이 -18 이라 범위가 넓어서, 비만 안 와도 26점 상승으로 잡히기 때문입니다.
     * 그래서 "비가 오지 않아요" 같은 하나 마나 한 문장이 헤드라인에 올라왔습니다.
     *
     * 대신 **만점 대비 달성률 × 배점 크기**로 봅니다.
     * 배점이 크면서 실제로 만점에 가까운 항목이 그 장소의 강점이기 때문입니다.
     * 카테고리 기본 매력도는 어느 장소나 비슷해서 설명력이 없으니 제외합니다.
     */
    val topFactor: ScoreFactor?
        get() = factors
            .filter { it.kind != FactorKind.BASE_ATTRACTION }
            .maxByOrNull { it.ratio * it.max }

    /** 점수를 깎은 항목(있으면). */
    val penalty: ScoreFactor?
        get() = factors.filter { it.earned < 0 }.minByOrNull { it.earned }

    companion object {
        val EMPTY = PhotoScore(0, emptyList())
    }
}
