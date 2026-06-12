package com.youngs.picview.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // 통신 내용을 상세히 로그로 보여줌
    }
    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()
    private const val TOUR_BASE_URL = "https://apis.data.go.kr/B551011/"
    val tourApiService: TourApiService by lazy {
        Retrofit.Builder()
            .baseUrl(TOUR_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TourApiService::class.java)
    }

    private const val WEATHER_BASE_URL = "https://apis.data.go.kr/"
    val weatherApiService: WeatherApiService by lazy {
        Retrofit.Builder()
            .baseUrl(WEATHER_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)
    }
}