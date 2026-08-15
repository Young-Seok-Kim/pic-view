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

    /**
     * 단기예보. 시간대별 기온(TMP) 을 받아 오늘의 기온 곡선을 그리는 데 씁니다.
     *
     * 실황(getUltraSrtNcst)은 **지금 한 시점**만 줍니다. "몇 시에 나가면
     * 시원한가" 는 실황으로 답할 수 없어서 예보가 따로 필요합니다.
     *
     * base_time 은 02·05·08·11·14·17·20·23시만 유효합니다. 그중 02시 발표가
     * 그날 03시부터 사흘치를 담고 있어 하루 곡선을 한 번에 얻을 수 있습니다.
     *
     * numOfRows 를 크게 잡습니다. 한 시각마다 12개 항목(TMP·SKY·POP…)이
     * 붙어서, 하루치 TMP 만 챙기려 해도 그 12배를 받아야 합니다.
     */
    @GET("1360000/VilageFcstInfoService_2.0/getVilageFcst")
    suspend fun getVilageFcst(
        @Query("serviceKey") serviceKey: String,
        @Query("pageNo") pageNo: Int = 1,
        @Query("numOfRows") numOfRows: Int = 300,
        @Query("dataType") dataType: String = "JSON",
        @Query("base_date") baseDate: String,
        @Query("base_time") baseTime: String = "0200",
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