package com.youngs.picview.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.youngs.picview.R
import kotlin.math.sin

/**
 * 로딩 표시.
 *
 * 기본 원형 스피너 대신 점 세 개가 차례로 부풀었다 줄어듭니다. 점 색은
 * 새벽·일출·일몰의 빛 색이라, 기다리는 동안에도 이 앱이 무엇을 다루는지가
 * 드러납니다.
 *
 * 라이브러리를 쓰지 않은 이유: 참고한 구현(thinking-orbs, canvas-ui)은
 * 전부 React·웹용이라 안드로이드에 얹을 수 없습니다. 애니메이션 자체는
 * 사인파 하나로 끝나는 분량이라 직접 그리는 편이 의존성을 늘리는 것보다
 * 낫습니다.
 *
 * 접근성: 시스템에서 애니메이션을 끈 경우([android.provider.Settings.Global.ANIMATOR_DURATION_SCALE]
 * 가 0) 프레임워크가 애니메이터를 즉시 끝내므로, 점은 기본 크기로 멈춰
 * 표시만 남습니다.
 */
class LightLoaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 새벽 → 일출 → 일몰. 하루의 빛 순서를 그대로 씁니다. */
    private val dotColors = intArrayOf(
        ContextCompat.getColor(context, R.color.light_night),
        ContextCompat.getColor(context, R.color.light_sunrise),
        ContextCompat.getColor(context, R.color.light_sunset)
    )

    private var phase = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = CYCLE_MS
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (DOT_MAX_DP * density).toInt()
        setMeasuredDimension(
            resolveSize((size * 5.2f).toInt(), widthMeasureSpec),
            resolveSize(size * 2, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f
        val maxR = DOT_MAX_DP * density / 2f
        val minR = DOT_MIN_DP * density / 2f
        val gap = maxR * 3.2f
        val startX = (width - gap * (DOT_COUNT - 1)) / 2f

        for (i in 0 until DOT_COUNT) {
            // 점마다 위상을 어긋나게 해 물결처럼 이어 보이게 합니다.
            val offset = i / DOT_COUNT.toFloat()
            val wave = sin(((phase + offset) % 1f) * 2f * Math.PI).toFloat()
            val t = (wave + 1f) / 2f

            paint.color = dotColors[i % dotColors.size]
            paint.alpha = (120 + 135 * t).toInt().coerceIn(0, 255)
            canvas.drawCircle(startX + gap * i, cy, minR + (maxR - minR) * t, paint)
        }
    }

    // 화면에서 사라지면 애니메이션을 멈춥니다. 보이지 않는 뷰가 매 프레임
    // 다시 그리면 배터리만 씁니다.
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) start() else stop()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isVisible) start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun start() {
        if (!animator.isRunning) animator.start()
    }

    private fun stop() {
        if (animator.isRunning) animator.cancel()
    }

    private val isVisible: Boolean get() = visibility == VISIBLE

    private val density: Float get() = resources.displayMetrics.density

    companion object {
        private const val DOT_COUNT = 3
        private const val DOT_MAX_DP = 12f
        private const val DOT_MIN_DP = 6f
        private const val CYCLE_MS = 1100L
    }
}
