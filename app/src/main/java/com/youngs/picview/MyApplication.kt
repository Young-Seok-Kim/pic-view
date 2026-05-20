package com.youngs.picview

import android.app.Application
import android.util.Log
import com.kakao.sdk.common.KakaoSdk
import com.naver.maps.map.NaverMapSdk

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 1. 카카오 SDK 초기화 (BuildConfig.키이름 으로 접근!)
        KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)

        // 2. 네이버 지도 SDK 초기화
        NaverMapSdk.getInstance(this).client = NaverMapSdk.NcpKeyClient(BuildConfig.NAVER_CLIENT_ID)
    }
}