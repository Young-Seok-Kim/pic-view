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
    /**
     * 시민박명 시작·종료 "HHmm".
     *
     * 블루아워의 실제 경계입니다. 예전에는 골든아워 바깥 25분을 블루아워로
     * 어림잡았는데, 정읍 8월 16일 실값으로 대보니 28분이나 어긋났습니다.
     * (어림값 04:57~05:22 / 실값 05:25~05:52) 게다가 어림값은 블루아워를
     * 골든아워보다 **앞**에 두었는데, 실제로는 블루아워가 일출에 맞닿아
     * 끝나고 거기서부터 골든아워가 시작됩니다. 순서 자체가 틀렸던 셈입니다.
     */
    val civilm: String? = null,
    val civile: String? = null,

    /**
     * 남중 시각.
     *
     * 이 API 는 `meridian` 이 아니라 `suntransit` 으로 주고, 형식도
     * "HHmm" 이 아니라 HHMMSS 정수(123657 = 12:36:57)입니다. 이름만 보고
     * meridian 으로 받고 있어서 늘 null 이었고, 남중을 일출·일몰의 중간으로
     * 되짚어 쓰고 있었습니다. 다행히 그 값이 실제와 몇 분 차이라 티가 안
     * 났을 뿐입니다.
     */
    val suntransit: Long? = null,
    val meridian: String? = null
)
