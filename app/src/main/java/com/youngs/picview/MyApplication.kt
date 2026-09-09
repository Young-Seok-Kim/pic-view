package com.youngs.picview

import android.app.Application

/**
 * 앱 전역 초기화 자리.
 *
 * 지금은 비어 있습니다. 네이버 지도 SDK 초기화는 스플래시 첫 프레임을
 * 늦추지 않도록 지도를 처음 쓰는 곳(NaverMapSdkInit)으로 옮겼습니다.
 * 여기에 무언가를 넣으면 앱을 켤 때마다 OS 의 단색 시작 창이 그만큼
 * 오래 보인다는 점을 기억해 주세요.
 */
class MyApplication : Application()
