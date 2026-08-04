package com.youngs.picview.domain.light

import androidx.annotation.ColorRes
import com.youngs.picview.R

/**
 * 하루를 촬영 관점에서 나눈 빛 구간.
 *
 * 일반 여행 앱은 "오전/오후"로만 나누지만, 출사에서는 같은 오전이라도
 * 일출 직후와 남중 직전의 빛이 완전히 다릅니다.
 * 코스 정렬과 스팟 배치가 전부 이 구간을 기준으로 이뤄집니다.
 */
enum class LightPhase(
    val label: String,
    /** 화면에 붙는 한 줄 설명 */
    val hint: String,
    @ColorRes val colorRes: Int
) {
    /** 일출 전 파란 빛. 도시 야경과 하늘이 함께 나오는 짧은 구간. */
    BLUE_DAWN("새벽 블루아워", "하늘이 푸르게 깔리는 20분", R.color.light_night),

    /** 일출 전후. 가장 좋은 빛. */
    SUNRISE("일출 골든아워", "가장 부드러운 빛", R.color.light_sunrise),

    /** 일출 후 ~ 남중 전. 측광이 살아있는 안정적인 시간. */
    MORNING("오전", "측광이 좋은 시간", R.color.light_morning),

    /** 남중 전후. 그림자가 짧고 대비가 강해 야외 인물·풍경에 불리합니다. */
    MIDDAY("한낮", "빛이 강해 실내가 유리", R.color.light_midday),

    /** 남중 후 ~ 일몰 전. */
    AFTERNOON("오후", "그림자가 길어지기 시작", R.color.light_morning),

    /** 일몰 전후. 서향 스팟의 최적 시간. */
    SUNSET("일몰 골든아워", "하루 중 가장 붉은 빛", R.color.light_sunset),

    /** 일몰 후 파란 빛. */
    BLUE_DUSK("저녁 블루아워", "가로등과 하늘이 함께 담기는 20분", R.color.light_night),

    /** 그 외. */
    NIGHT("야간", "삼각대가 필요해요", R.color.light_night);

    /**
     * 좁은 칸(홈의 현황 카드 등)에 넣는 짧은 이름.
     * "일출 골든아워" 는 3분할 카드에서 잘리므로 앞머리만 씁니다.
     */
    val shortLabel: String
        get() = when (this) {
            BLUE_DAWN -> "새벽"
            SUNRISE -> "일출"
            SUNSET -> "일몰"
            BLUE_DUSK -> "저녁"
            else -> label
        }

    /** 야외 촬영에 유리한 구간인지. 코스 배치에 씁니다. */
    val isOutdoorFriendly: Boolean
        get() = this != MIDDAY && this != NIGHT

    /** 골든아워인지. 포토스코어 가산에 씁니다. */
    val isGolden: Boolean
        get() = this == SUNRISE || this == SUNSET
}
