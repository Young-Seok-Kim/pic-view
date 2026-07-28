package com.youngs.picview.data.api

import com.youngs.picview.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // 공공데이터포털은 응답이 느릴 때가 잦아 기본 10초로는 자주 timeout 이 납니다.
    private const val CONNECT_TIMEOUT_SEC = 15L
    private const val READ_TIMEOUT_SEC = 30L

    private val logging = HttpLoggingInterceptor().apply {
        // 릴리즈 빌드에서는 응답 본문(및 serviceKey)을 로그에 남기지 않습니다.
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .writeTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
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
