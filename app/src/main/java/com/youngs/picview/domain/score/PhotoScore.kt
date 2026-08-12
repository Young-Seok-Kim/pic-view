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
    SUBJECT(R.string.factor_subject),
    LIGHT(R.string.factor_light),
    FACING(R.string.factor_facing),
    SEASON(R.string.factor_season),
    WEATHER(R.string.factor_weather),
    COMFORT(R.string.factor_comfort),
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
     * 소재 폭은 유형별 고정값이라 설명력이 없으니 제외합니다.
     */
    val topFactor: ScoreFactor?
        get() = factors
            .filter { it.kind != FactorKind.SUBJECT }
            .maxByOrNull { it.ratio * it.max }

    /** 점수를 깎은 항목(있으면). */
    val penalty: ScoreFactor?
        get() = factors.filter { it.earned < 0 }.minByOrNull { it.earned }

    /**
     * 점수를 설명하는 문장.
     *
     * 예전에는 항목 하나의 이유만 보여 줬습니다. 그래서 "비가 오지 않아요"
     * 한 줄로 끝나 버려, 일곱 항목으로 계산해 놓고 정작 왜 이 점수인지는
     * 알 수 없었습니다.
     *
     * 이제 **강점 두 개와 발목을 잡은 것 하나**를 엮습니다. 사람이 남에게
     * 장소를 설명하는 방식("빛이 좋고 사진도 많은데, 다만 비가 와요")과
     * 같습니다. 깎인 항목이 없으면 강점만 씁니다.
     */
    val summary: String
        get() {
            if (factors.isEmpty()) return ""

            // 배점이 크면서 실제로 만점에 가까운 순.
            // 소재 폭은 유형별 고정값이라 설명력이 없으므로 뺍니다.
            val strengths = factors
                .filter { it.kind != FactorKind.SUBJECT && it.earned > 0 }
                .sortedByDescending { it.ratio * it.max }
                .take(2)
                .map { it.reason }

            val drag = penalty?.reason

            return when {
                strengths.isEmpty() -> drag.orEmpty()
                drag == null -> strengths.joinToString(" ")
                else -> "${strengths.joinToString(" ")} 다만 $drag"
            }
        }

    companion object {
        val EMPTY = PhotoScore(0, emptyList())
    }
}
