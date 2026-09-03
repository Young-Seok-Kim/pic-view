package com.youngs.picview.util

import android.content.Context
import com.naver.maps.map.NaverMapSdk
import com.youngs.picview.BuildConfig

/**
 * 네이버 지도 SDK 초기화를 지도를 처음 쓰는 순간까지 미룹니다.
 *
 * 예전에는 Application.onCreate 에서 했는데, 그러면 앱을 켤 때마다 스플래시
 * 첫 프레임 앞에 초기화 시간이 끼어 OS 의 단색 시작 창이 그만큼 오래
 * 보입니다. 지도는 탐색 탭에서만 쓰므로 거기서 처음 필요할 때 합니다.
 */
object NaverMapSdkInit {

    @Volatile
    private var done = false

    /** 지도 화면을 만들기 전에 한 번 부릅니다. 여러 번 불러도 한 번만 합니다. */
    fun ensure(context: Context) {
        if (done) return
        synchronized(this) {
            if (done) return
            NaverMapSdk.getInstance(context.applicationContext).client =
                NaverMapSdk.NcpKeyClient(BuildConfig.NAVER_CLIENT_ID)
            done = true
        }
    }
}
