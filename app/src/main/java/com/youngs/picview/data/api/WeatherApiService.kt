package com.youngs.picview.data.api

import com.youngs.picview.data.model.AstroResponse
import com.youngs.picview.data.model.WeatherResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {
    /**
     * 초단기실황. base_time 은 정시("HH00")를 넘기는 것이 원칙이지만
     * 분이 섞여 있어도 기상청이 정시로 내림 처리해 줍니다.
     * 다만 base_date 와 기준 시각이 어긋나면 NO_DATA(03) 가 오므로
     * 호출부에서 두 값을 같은 시각에서 뽑아야 합니다.
     *
     * serviceKey 는 세 API 모두 encoded 옵션 없이 통일합니다.
     * (디코딩 키를 쓰고 Retrofit 이 인코딩하도록 맡기는 방식)
     */
    @GET("1360000/VilageFcstInfoService_2.0/getUltraSrtNcst")
    suspend fun getUltraSrtNcst(
        @Query("serviceKey") serviceKey: String,
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