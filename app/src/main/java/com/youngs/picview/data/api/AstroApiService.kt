package com.youngs.picview.data.api

import com.youngs.picview.data.model.AstroResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface AstroApiService {
    @GET("B090041/openapi/service/RiseSetInfoService/getAreaRiseSetInfo")
    suspend fun getRiseSetInfo(
        @Query("serviceKey") serviceKey: String,
        @Query("locdate") locdate: String,
        @Query("location") location: String,
        @Query("_type") type: String = "json"
    ): AstroResponse
}