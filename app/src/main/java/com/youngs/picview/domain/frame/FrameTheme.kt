package com.youngs.picview.domain.frame

import androidx.annotation.ColorInt

/**
 * 포토 프레임 테마.
 *
 * 정읍의 대표 피사체에서 색을 가져왔습니다. 프레임을 고르는 행위가 곧
 * "어디서 찍었는지"를 고르는 것이 되도록, 색 이름 대신 장소 이름을 씁니다.
 * "주황색 프레임"보다 "내장산 단풍"이 사진에 붙을 이름으로 어울립니다.
 */
enum class FrameTheme(
    val label: String,
    /** 사진 아래 여백(폴라로이드의 넓은 아랫단) 색. */
    @ColorInt val paperColor: Int,
    /** 장소명 글자색. */
    @ColorInt val titleColor: Int,
    /** 날짜·출처 글자색. */
    @ColorInt val captionColor: Int,
    /** 사진 테두리에 얇게 두르는 색. */
    @ColorInt val accentColor: Int
) {
    MAPLE(
        "내장산 단풍",
        paperColor = 0xFFFDF6EF.toInt(),
        titleColor = 0xFF2A211C.toInt(),
        captionColor = 0xFF9B8878.toInt(),
        accentColor = 0xFFE8622C.toInt()
    ),
    SEOWON(
        "무성서원",
        paperColor = 0xFFF4EFE7.toInt(),
        titleColor = 0xFF2A211C.toInt(),
        captionColor = 0xFF8B8073.toInt(),
        accentColor = 0xFF6B5B4A.toInt()
    ),
    GUJEOLCHO(
        "구절초",
        paperColor = 0xFFF7F2F6.toInt(),
        titleColor = 0xFF2A1C28.toInt(),
        captionColor = 0xFF8E7C8A.toInt(),
        accentColor = 0xFF8A5A86.toInt()
    ),
    SSANGHWA(
        "쌍화차 거리",
        paperColor = 0xFFF6EEE6.toInt(),
        titleColor = 0xFF2A211C.toInt(),
        captionColor = 0xFF938271.toInt(),
        accentColor = 0xFF8B4A2B.toInt()
    )
}
