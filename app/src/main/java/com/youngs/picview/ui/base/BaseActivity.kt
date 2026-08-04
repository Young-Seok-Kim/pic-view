package com.youngs.picview.ui.base

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import com.youngs.picview.R
import com.youngs.picview.util.AppPrefs

/**
 * 시니어 모드와 글씨 크기를 화면 생성 전에 반영하는 공통 Activity.
 *
 * 두 가지를 다른 방식으로 처리합니다.
 *  - 색·터치타깃·라운드 → 테마 교체 (Theme.PicView.Senior)
 *  - 글자 크기          → Configuration.fontScale 배율
 *
 * 글자를 테마로 처리하지 않는 이유: 스펙이 요구하는 "보통/크게/매우 크게"
 * 3단계 조절과 시니어 모드가 서로 곱해져야 하는데, 테마에 textSize 를 박으면
 * 두 축이 충돌합니다. fontScale 은 레이아웃을 안 고치고도 전체에 적용됩니다.
 */
abstract class BaseActivity : AppCompatActivity() {

    /** 시니어 모드에서 이 화면이 큰 글씨 테마를 쓸지. 카메라 화면 등은 끕니다. */
    protected open val appliesSeniorTheme: Boolean = true

    override fun attachBaseContext(newBase: Context) {
        val step = AppPrefs.fontStep(newBase)
        val systemScale = newBase.resources.configuration.fontScale

        // 사용자가 시스템 설정에서 이미 글씨를 키웠다면 그걸 줄이지 않습니다.
        // (접근성 설정을 앱이 되돌리면 안 됩니다)
        val scale = maxOf(systemScale, step.scale)

        val config = Configuration(newBase.resources.configuration).apply {
            fontScale = scale
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        if (appliesSeniorTheme && AppPrefs.isSeniorMode(this)) {
            setTheme(R.style.Theme_PicView_Senior)
        }
        super.onCreate(savedInstanceState)
    }

    /**
     * 모드/글씨 크기를 바꾼 뒤 호출합니다.
     * 테마와 fontScale 둘 다 Activity 생성 시점에만 반영되므로 재생성이 필요합니다.
     */
    protected fun applyDisplaySettingsAndRestart() {
        recreate()
    }
}
