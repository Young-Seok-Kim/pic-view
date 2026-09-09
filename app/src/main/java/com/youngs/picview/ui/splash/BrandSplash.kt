package com.youngs.picview.ui.splash

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.youngs.picview.R
import com.youngs.picview.databinding.ActivitySplashBinding

/**
 * 브랜드 스플래시의 움직임.
 *
 * 첫 프레임부터 움직입니다. 풍경이 천천히 당겨지고(켄 번즈), 단풍잎이
 * 떨어지며, 앱 이름 → 구분선 → 태그라인이 차례로 떠오릅니다.
 * 정해진 시간이 지나면 [onFinished] 를 부릅니다.
 */
class BrandSplash(
    private val b: ActivitySplashBinding,
    private val onFinished: () -> Unit,
) {
    private var started = false
    private var finished = false
    private val running = mutableListOf<Animator>()

    /** 움직임을 시작합니다. 두 번 불려도 한 번만 시작합니다. */
    fun start() {
        if (started) return
        started = true
        b.splashTagline.text = highlightedTagline()
        choreograph()
        b.root.postDelayed(::finish, TOTAL_MS)
    }

    /** 화면이 사라질 때. 남은 움직임과 예약을 모두 거둡니다. */
    fun cancel() {
        finished = true
        running.forEach { it.cancel() }
        b.root.removeCallbacks(::finish)
    }

    private fun finish() {
        if (finished) return
        finished = true
        running.forEach { it.cancel() }
        onFinished()
    }

    /** 태그라인의 "가장" 만 주황으로. 문구가 바뀌어 그 말이 없으면 그대로 둡니다. */
    private fun highlightedTagline(): CharSequence {
        val context = b.root.context
        val text = context.getString(R.string.app_tagline)
        val word = context.getString(R.string.splash_tagline_accent_word)
        val at = text.indexOf(word)
        if (word.isEmpty() || at < 0) return text
        return SpannableString(text).apply {
            setSpan(
                ForegroundColorSpan(ContextCompat.getColor(context, R.color.splash_accent)),
                at, at + word.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    /**
     * 움직임의 순서.
     *
     *  0ms  시작 창과 같은 단색 막이 걷히며 풍경이 드러남,
     *       풍경은 살짝 당겨진 상태에서 천천히 제자리로(켄 번즈)
     *  150  단풍잎 나타남
     *  350  앱 이름이 떠오름
     *  700  구분선이 가운데서 양쪽으로 펼쳐짐
     *  900  태그라인이 떠오름
     */
    private fun choreograph() {
        val density = b.root.resources.displayMetrics.density

        b.splashScene.scaleX = SCENE_START_SCALE
        b.splashScene.scaleY = SCENE_START_SCALE
        val kenBurns = ObjectAnimator.ofPropertyValuesHolder(
            b.splashScene,
            PropertyValuesHolder.ofFloat(View.SCALE_X, SCENE_START_SCALE, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, SCENE_START_SCALE, 1f),
        ).apply {
            duration = TOTAL_MS + 600L
            interpolator = LinearInterpolator()
        }
        running += kenBurns
        kenBurns.start()

        b.splashVeil.animate()
            .alpha(0f)
            .setDuration(VEIL_MS)
            .withEndAction { b.splashVeil.visibility = View.GONE }
            .start()

        b.splashLeaves.animate().alpha(1f).setStartDelay(150L).setDuration(800L).start()

        rise(b.splashTitle, delay = 350L, distance = TEXT_RISE_DP * density)

        b.splashDivider.scaleX = 0f
        b.splashDivider.animate()
            .setStartDelay(700L)
            .alpha(1f).scaleX(1f)
            .setDuration(600L)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .start()

        rise(b.splashTagline, delay = 900L, distance = TEXT_RISE_DP * 0.7f * density)
    }

    private fun rise(view: View, delay: Long, distance: Float) {
        view.translationY = distance
        view.animate()
            .setStartDelay(delay)
            .alpha(1f).translationY(0f)
            .setDuration(700L)
            .setInterpolator(DecelerateInterpolator(1.8f))
            .start()
    }

    private companion object {
        /** 다음 화면으로 넘기기까지의 시간. 움직임이 한 바퀴 도는 길이입니다. */
        const val TOTAL_MS = 2800L

        /** 단색 막이 걷히는 시간. OS 시작 창의 색에서 그림으로 녹아드는 길이입니다. */
        const val VEIL_MS = 450L

        /** 풍경이 처음에 당겨져 있는 배율. 1 로 돌아오며 화면이 숨을 쉽니다. */
        const val SCENE_START_SCALE = 1.08f

        /** 글자가 떠오르는 거리. */
        const val TEXT_RISE_DP = 22f
    }
}
