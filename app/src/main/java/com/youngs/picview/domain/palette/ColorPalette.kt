package com.youngs.picview.domain.palette

import androidx.annotation.ColorInt

/**
 * 정읍 색감 팔레트 (시안 13:2).
 *
 * 흔한 필터는 "빈티지 3", "필름 A" 처럼 이름이 색을 설명하지 못합니다.
 * 여기서는 **정읍에서 실제로 찍히는 것**에서 색을 가져옵니다. 필터를 고르는
 * 행위가 곧 "어디의 색으로 담을까"를 고르는 일이 되고, 사진이 남에게
 * 보여질 때 정읍이 함께 따라갑니다. [com.youngs.picview.domain.frame.FrameTheme]
 * 과 같은 네 가지를 씁니다. 필터와 프레임을 같은 이름으로 맞춰야 한 장에
 * 겹쳐 썼을 때 따로 놀지 않습니다.
 *
 * 색은 세 단씩입니다. 그림자·중간·밝은 곳에 각각 어떤 색을 얹을지가
 * 색감의 전부라서입니다. [PaletteGrader] 가 이 셋으로 밝기 사다리를
 * 만들어 사진에 입힙니다.
 */
enum class ColorPalette(
    val label: String,
    /** 목록에 붙는 한 줄. 무엇을 찍을 때 어울리는지 */
    val note: String,
    @ColorInt val shadow: Int,
    @ColorInt val mid: Int,
    @ColorInt val highlight: Int
) {
    MAPLE(
        "내장산 단풍", "붉게 물든 가을",
        shadow = 0xFF33150E.toInt(),
        mid = 0xFFC4441F.toInt(),
        highlight = 0xFFF6CD92.toInt()
    ),

    GUJEOLCHO(
        "구절초 화이트", "맑고 서늘한 들꽃",
        shadow = 0xFF2E2536.toInt(),
        mid = 0xFFA98BB5.toInt(),
        highlight = 0xFFFBF6F2.toInt()
    ),

    SSANGHWA(
        "쌍화차 브라운", "따뜻한 찻집의 톤",
        shadow = 0xFF241708.toInt(),
        mid = 0xFF8B5A2B.toInt(),
        highlight = 0xFFEBD3AC.toInt()
    ),

    SEOWON(
        "무성서원 그린", "이끼와 기와의 초록",
        shadow = 0xFF16241C.toInt(),
        mid = 0xFF3F7A55.toInt(),
        highlight = 0xFFDDE8C6.toInt()
    );

    /** 목록 카드에 늘어놓을 세 칸. */
    val swatches: List<Int> get() = listOf(shadow, mid, highlight)
}
