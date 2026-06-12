package com.youngs.picview.data.model

data class WeatherResponse(
    val response: WeatherBodyResponse
)

data class WeatherBodyResponse(
    val header: WeatherHeader,
    val body: WeatherBody
)

data class WeatherHeader(
    val resultCode: String,
    val resultMsg: String
)

data class WeatherBody(
    val items: WeatherItems
)

data class WeatherItems(
    val item: List<WeatherItem>
)

data class WeatherItem(
    val baseDate: String,
    val baseTime: String,
    val category: String, // T1H(기온), PTY(강수형태), SKY(하늘상태) 등
    val fcstDate: String,
    val fcstTime: String,
    val fcstValue: String,
    val obsrValue: String?,
    val nx: Int,
    val ny: Int
)