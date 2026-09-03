package com.youngs.picview.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.doOnLayout
import com.youngs.picview.MainActivity
import com.youngs.picview.R
import com.youngs.picview.databinding.ActivitySplashBinding
import com.youngs.picview.ui.base.BaseActivity
import com.youngs.picview.ui.onboarding.OnboardingActivity
import com.youngs.picview.util.AppPrefs

/**
 * 앱의 진입점. 브랜드 스플래시를 끝까지 보여 준 뒤 온보딩 또는 메인으로 넘깁니다.
 *
 * 시스템 스플래시는 같은 노을색 단색만 잠깐 띄우고, 이 화면의 첫 프레임부터
 * BrandSplash 가 움직입니다. 화면이 가벼워 첫 프레임이 빨리 나오므로 단색이
 * 홀로 머무는 시간이 짧습니다.
 */
class SplashActivity : BaseActivity() {

    override val appliesSeniorTheme: Boolean = false

    private lateinit var binding: ActivitySplashBinding
    private lateinit var brand: BrandSplash

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        brand = BrandSplash(binding) { goNext() }

        // 첫 레이아웃부터 바로 움직입니다. 시스템 스플래시는 첫 프레임이
        // 그려지는 순간 걷히므로, 그 위를 덮고 있던 단색이 곧장 움직이는
        // 화면으로 바뀝니다.
        binding.root.doOnLayout { brand.start() }
        splashScreen.setOnExitAnimationListener { provider -> provider.remove() }
    }

    override fun onDestroy() {
        brand.cancel()
        super.onDestroy()
    }

    private fun goNext() {
        if (isFinishing || isDestroyed) return
        val next = if (AppPrefs.isOnboarded(this)) {
            Intent(this, MainActivity::class.java)
        } else {
            OnboardingActivity.intent(this)
        }
        startActivity(next)
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }
}
