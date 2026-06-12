package com.youngs.picview.data.api

import com.youngs.picview.data.model.AstroResponse
import com.youngs.picview.data.model.WeatherResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {
    // 1. 주소 끝부분을 getUltraSrtNcst로 변경
    @GET("1360000/VilageFcstInfoService_2.0/getUltraSrtNcst")
    suspend fun getUltraSrtNcst(
        @Query("serviceKey", encoded = true) serviceKey: String,
        @Query("pageNo") pageNo: Int = 1,
        @Query("numOfRows") numOfRows: Int = 100,
        @Query("dataType") dataType: String = "JSON",
        @Query("base_date") baseDate: String,
        @Query("base_time") baseTime: String,
        @Query("nx") nx: String = "57",
        @Query("ny") ny: String = "71"
    ): WeatherResponse

    @GET("B090041/openapi/service/RiseSetInfoService/getAreaRiseSetInfo")
    suspend fun getAreaRiseSetInfo(
        @Query("serviceKey") serviceKey: String, // encoded 옵션 제거
        @Query("locdate") locdate: String,
        @Query("location") location: String = "정읍", // 위치 기반 대신 지역명 사용
        @Query("_type") type: String = "json"
    ): AstroResponse

}