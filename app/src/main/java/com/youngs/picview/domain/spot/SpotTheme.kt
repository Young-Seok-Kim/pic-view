package com.youngs.picview.domain.spot

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.youngs.picview.R

/**
 * 촬영지 주제.
 *
 * 지도 마커·목록 카드·탐색 필터가 각자 다른 기준으로 색을 쓰면 같은 장소가
 * 화면마다 다른 색으로 보입니다. 판별과 색을 한 곳에 모아 "초록은 자연,
 * 노랑은 맛집" 이 앱 전체에서 같은 뜻이 되게 합니다.
 *
 * 판별 기준은 관광공사 contentTypeId 입니다. 이름으로 추측하면 "정읍사예술회관"
 * 처럼 이름에 단서가 없는 곳이 엉뚱한 주제로 빠집니다.
 */
enum class SpotTheme(
    /** 알약에 들어가는 짧은 이름. 카드 폭이 좁아 두 낱말을 넘기면 안 됩니다. */
    val label: String,
    @ColorRes val colorRes: Int,
    @ColorRes val containerRes: Int,
    @DrawableRes val markerRes: Int
) {
    NATURE("자연", R.color.theme_nature, R.color.theme_nature_container, R.drawable.marker_nature),
    CULTURE("문화", R.color.theme_culture, R.color.theme_culture_container, R.drawable.marker_culture),
    LEPORTS("레포츠", R.color.theme_leports, R.color.theme_leports_container, R.drawable.marker_leports),
    FOOD("맛집", R.color.theme_food, R.color.theme_food_container, R.drawable.marker_food),
    ETC("기타", R.color.theme_etc, R.color.theme_etc_container, R.drawable.marker_etc);

    companion object {
        fun of(contentTypeId: String?): SpotTheme = when (contentTypeId) {
            "12", "25" -> NATURE   // 관광지 · 여행코스
            "14" -> CULTURE        // 문화시설
            "28" -> LEPORTS        // 레포츠
            "39" -> FOOD           // 음식점
            else -> ETC
        }
    }
}
