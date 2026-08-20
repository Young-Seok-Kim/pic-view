package com.youngs.picview.domain.frame

import androidx.annotation.ColorInt

/**
 * 포토 프레임 테마.
 *
 * 정읍의 대표 피사체에서 색을 가져왔습니다. 프레임을 고르는 행위가 곧
 * "어디서 찍었는지"를 고르는 것이 되도록, 색 이름 대신 장소 이름을 씁니다.
 * "주황색 프레임"보다 "내장산 단풍"이 사진에 붙을 이름으로 어울립니다.
 *
 * 시안의 장식(단풍잎·서원 대나무·구절초·찻잔)은 [motif] 이모지와
 * [FrameDecor] 의 산·구름 문양으로 그립니다.
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
    @ColorInt val accentColor: Int,
    /** 프레임 위에 흩어 놓는 장식 이모지. */
    val motif: String,
    /** 장소명 옆에 붙는 한 줄 — "전라북도 정읍시 · ○○" 의 ○○. */
    val tagline: String,
    /** 네컷 사진 아래의 영문 표제. */
    val fourCutTitle: String,
    /** 네컷 사진 아래의 우리말 표제. */
    val fourCutTagline: String,
    /** 프레임 선택 카드의 색 견본 세 칸(진한 → 옅은). */
    val swatches: IntArray
) {
    MAPLE(
        "내장산 단풍",
        paperColor = 0xFFF6EBDC.toInt(),
        titleColor = 0xFF2A211C.toInt(),
        captionColor = 0xFF9B8878.toInt(),
        accentColor = 0xFFC0561F.toInt(),
        motif = "🍁",
        tagline = "내장산 단풍 명소",
        fourCutTitle = "JEONGEUP · AUTUMN LIGHT",
        fourCutTagline = "내장산의 빛을 네 장면으로",
        swatches = intArrayOf(0xFFB23A1B.toInt(), 0xFFD9805E.toInt(), 0xFFE8AC93.toInt())
    ),
    SEOWON(
        "무성서원",
        paperColor = 0xFFF2EFE6.toInt(),
        titleColor = 0xFF2A241C.toInt(),
        captionColor = 0xFF8B8073.toInt(),
        accentColor = 0xFF6B7A4A.toInt(),
        motif = "🌿",
        tagline = "선비의 마을",
        fourCutTitle = "JEONGEUP · SLOW WALK",
        fourCutTagline = "느린 장면을 오래 바라보기",
        swatches = intArrayOf(0xFF56663A.toInt(), 0xFF9CAE7C.toInt(), 0xFFD6DEC7.toInt())
    ),
    GUJEOLCHO(
        "구절초",
        paperColor = 0xFFF6F1EE.toInt(),
        titleColor = 0xFF2A1C28.toInt(),
        captionColor = 0xFF8E7C8A.toInt(),
        accentColor = 0xFF8A6AA0.toInt(),
        motif = "🌼",
        tagline = "가을 들꽃의 길",
        fourCutTitle = "JEONGEUP · WILDFLOWER DAYS",
        fourCutTagline = "구절초 들길을 네 장면으로",
        swatches = intArrayOf(0xFF6E4E86.toInt(), 0xFFA88CC0.toInt(), 0xFFE0D2EA.toInt())
    ),
    SSANGHWA(
        "쌍화차 거리",
        paperColor = 0xFFF4EADA.toInt(),
        titleColor = 0xFF2A211C.toInt(),
        captionColor = 0xFF938271.toInt(),
        accentColor = 0xFF8B4A2B.toInt(),
        motif = "🍵",
        tagline = "따뜻한 차 한 잔",
        fourCutTitle = "JEONGEUP · TEA TIME",
        fourCutTagline = "따뜻한 한 잔, 네 장면",
        swatches = intArrayOf(0xFF5C3A26.toInt(), 0xFF8B6A4F.toInt(), 0xFFC9B39C.toInt())
    )
}
