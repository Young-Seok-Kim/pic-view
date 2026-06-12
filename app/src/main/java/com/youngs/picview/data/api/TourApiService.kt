package com.youngs.picview.data.api

import com.youngs.picview.data.model.DetailCommonResponse
import com.youngs.picview.data.model.ImageResponse
import com.youngs.picview.data.model.TourResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface TourApiService {
    @GET("KorService2/areaBasedList2")
    suspend fun getJeongeupSpots(
        @Query("serviceKey") serviceKey: String,
        @Query("numOfRows") numOfRows: Int = 100,
        @Query("pageNo") pageNo: Int = 1,
        @Query("MobileOS") mobileOS: String = "AND",
        @Query("MobileApp") mobileApp: String = "PicView",
        @Query("_type") type: String = "json",
        @Query("areaCode") areaCode: String = "37", // 전북
        @Query("sigunguCode") sigunguCode: String = "13" // 정읍
    ): TourResponse

    @GET("KorService2/detailImage2")
    suspend fun getSpotImages(
        @Query("serviceKey") serviceKey: String,
        @Query("contentId") contentId: String, // 해당 장소의 고유 ID
        @Query("MobileOS") mobileOS: String = "AND",
        @Query("MobileApp") mobileApp: String = "PicView",
        @Query("_type") type: String = "json"
    ): ImageResponse // 별도의 응답 모델 필요

    @GET("KorService2/detailCommon2")
    suspend fun getDetailCommon(
        @Query("serviceKey") serviceKey: String,
        @Query("contentId") contentId: String,
//        @Query("contentTypeId") contentTypeId: String = "", // 혹시 있다면 추가
        @Query("MobileOS") mobileOS: String = "AND",        // 필수 (필수값 확인 요망)
        @Query("MobileApp") mobileApp: String = "PicView",  // 필수 (앱 이름 아무거나 가능)
//        @Query("defaultYN") defaultYN: String = "Y",
//        @Query("overviewYN") overviewYN: String = "Y",
        @Query("_type") type: String = "json"
    ): DetailCommonResponse
}