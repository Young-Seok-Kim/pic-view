package com.youngs.picview.data.model

/**
 * 기상청 초단기실황(getUltraSrtNcst) 응답.
 *
 * 주의: 실황 응답에는 fcstDate/fcstTime/fcstValue 가 없습니다(단기예보 전용 필드).
 * 예전 모델은 이들을 non-null String 으로 선언해 뒀는데, Gson 이 null 을 그대로
 * 넣기 때문에 접근하는 순간 NPE 가 나는 구조였습니다.
 * 실황에서 실제로 쓰는 값은 category 와 obsrValue 둘뿐입니다.
 */
data class WeatherResponse(
    val response: WeatherBodyResponse? = null
)

data class WeatherBodyResponse(
    val header: WeatherHeader? = null,
    val body: WeatherBody? = null
)

data class WeatherHeader(
    val resultCode: String? = null,
    val resultMsg: String? = null
)

data class WeatherBody(
    val items: WeatherItems? = null
)

data class WeatherItems(
    val item: List<WeatherItem>? = null
)

data class WeatherItem(
    val baseDate: String? = null,
    val baseTime: String? = null,
    /** T1H(기온), PTY(강수형태), SKY(하늘상태), REH(습도) 등 */
    val category: String? = null,
    /** 실황 관측값. 실황 응답에서 값이 담기는 유일한 필드입니다. */
    val obsrValue: String? = null,
    val nx: Int? = null,
    val ny: Int? = null
)
