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
        // BASIC = URL, 응답코드, 소요시간만. 촬영지 목록 응답이 약 60KB 라
        // BODY 로 두면 logcat 출력만으로 로딩이 눈에 띄게 느려집니다.
        // 응답 본문을 봐야 할 때만 일시적으로 BODY 로 바꿔 쓰세요.
        // 릴리즈 빌드에서는 serviceKey 노출을 막기 위해 아무것도 남기지 않습니다.
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
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
