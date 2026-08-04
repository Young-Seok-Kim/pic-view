package com.youngs.picview.data.model

/**
 * 천문연 출몰시각(getAreaRiseSetInfo) 응답.
 *
 * 값이 "0543" 같은 4자리 문자열로 오지만, 관측 불가일에는 공백이나
 * 필드 누락으로 오는 경우가 있어 전부 nullable 로 둡니다.
 */
data class AstroResponse(val response: AstroBody? = null)

data class AstroBody(val body: AstroItems? = null)

data class AstroItems(
    val items: AstroItemContainer? = null,
    val totalCount: Int? = null
)

data class AstroItemContainer(
    /** 단일 지역 조회라 객체 하나로 옵니다. */
    val item: AstroItem? = null
)

data class AstroItem(
    /** 일출 시각 "HHmm" */
    val sunrise: String? = null,
    /** 일몰 시각 "HHmm" */
    val sunset: String? = null,
    /** 남중 시각 "HHmm". 정오 강한 빛 회피 판단에 씁니다. */
    val meridian: String? = null
)
