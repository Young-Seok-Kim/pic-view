package com.youngs.picview

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/**
 * 앱 전역 초기화 자리.
 *
 * 네이버 지도 SDK 초기화는 스플래시 첫 프레임을 늦추지 않도록 지도를
 * 처음 쓰는 곳(NaverMapSdkInit)으로 옮겼습니다. 여기에 무언가를 넣으면
 * 앱을 켤 때마다 OS 의 단색 시작 창이 그만큼 오래 보인다는 점을 기억해 주세요.
 */
class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 이 앱은 크림 톤 라이트 디자인 하나로 고정입니다. 시스템 다크 모드를
        // 따라가면 배경·글자색이 뒤집혀, 배경색을 직접 박아 둔 버튼 위의 글자가
        // 사라지는 식의 조합이 생깁니다(온보딩 3장에서 실제로 났습니다).
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }
}
