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
    /**
     * 이 시간에 무엇을 담게 되는지.
     *
     * [hint] 는 "삼각대가 필요해요" 처럼 **장비·조건**을 말합니다. 그것만으로는
     * 나갈 이유가 안 됩니다. 이 문장은 **찍을 것**을 말합니다.
     */
    val subject: String,
    @ColorRes val colorRes: Int,
    /**
     * 홈 히어로 카드의 바탕색.
     *
     * [colorRes] 를 그대로 쓰면 오전·한낮의 노랑·회색 위에 흰 글씨가 얹혀
     * 안 읽힙니다. 같은 계열에서 명도만 낮춘 짝입니다.
     */
    @ColorRes val heroColorRes: Int
) {
    /** 일출 전 파란 빛. 도시 야경과 하늘이 함께 나오는 짧은 구간. */
    BLUE_DAWN("새벽 블루아워", "하늘이 푸르게 깔리는 20분", "고요한 풍경과 낮은 조도의 잔상", R.color.light_blue_dawn, R.color.hero_blue_dawn),

    /** 일출 전후. 가장 좋은 빛. */
    SUNRISE("일출 골든아워", "가장 부드러운 빛", "한옥 지붕선에 닿는 따뜻한 사광", R.color.light_sunrise, R.color.hero_sunrise),

    /** 일출 후 ~ 남중 전. 측광이 살아있는 안정적인 시간. */
    MORNING("오전", "측광이 좋은 시간", "목재와 돌의 결을 담기 좋은 빛", R.color.light_morning, R.color.hero_morning),

    /** 남중 전후. 그림자가 짧고 대비가 강해 야외 인물·풍경에 불리합니다. */
    MIDDAY("한낮", "빛이 강해 실내가 유리", "넓은 시야와 지역의 색", R.color.light_midday, R.color.hero_midday),

    /** 남중 후 ~ 일몰 전. */
    AFTERNOON("오후", "그림자가 길어지기 시작", "길어진 그림자와 장소의 표정", R.color.light_afternoon, R.color.hero_afternoon),

    /** 일몰 전후. 서향 스팟의 최적 시간. */
    SUNSET("일몰 골든아워", "하루 중 가장 붉은 빛", "수면 반사와 테라코타빛 장면", R.color.light_sunset, R.color.hero_sunset),

    /** 일몰 후 파란 빛. */
    BLUE_DUSK("저녁 블루아워", "가로등과 하늘이 함께 담기는 20분", "점등 전후의 깊은 하늘", R.color.light_blue_dusk, R.color.hero_blue_dusk),

    /** 그 외. */
    NIGHT("야간", "삼각대가 필요해요", "조명과 별빛", R.color.light_night, R.color.hero_night);

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
