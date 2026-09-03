package com.youngs.picview.ui.splash

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.youngs.picview.R
import kotlin.math.sin
import kotlin.random.Random

/**
 * 스플래시 배경에서 천천히 떨어지는 단풍잎.
 *
 * 잎마다 속도·흔들림·회전이 다르게 정해져 있어 같은 화면이 반복되지
 * 않습니다. 창에 붙으면 돌기 시작하고 떨어지면 멈추므로 따로 관리할 것이
 * 없습니다.
 */
class SplashLeavesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private class Leaf(
        /** 가로 위치(0~1, 폭 대비). */
        val x: Float,
        /** 낙하 속도(px/s). */
        val speed: Float,
        /** 시작 높이 오프셋(px). 처음부터 화면 곳곳에 흩어져 있게 합니다. */
        val offset: Float,
        /** 좌우 흔들림 폭(px)과 빠르기(rad/s). */
        val sway: Float,
        val swayFreq: Float,
        /** 회전 속도(deg/s)와 초기 각도. */
        val spin: Float,
        val phase: Float,
        /** 한 변 길이(px). */
        val size: Float,
        val drawable: Drawable,
    )

    private val density = resources.displayMetrics.density
    private val random = Random(SEED)

    private val palette = intArrayOf(
        R.color.maple_300, R.color.maple_400, R.color.maple_200, R.color.golden_300,
    ).map { colorRes ->
        ContextCompat.getDrawable(context, R.drawable.ic_leaf)!!.mutate().apply {
            setTint(ContextCompat.getColor(context, colorRes))
        }
    }

    private var leaves: List<Leaf> = emptyList()
    private val startedAt = SystemClock.uptimeMillis()

    private val ticker = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { invalidate() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return
        leaves = List(LEAF_COUNT) {
            val size = dp(random.nextInt(14, 26))
            Leaf(
                x = random.nextFloat(),
                speed = dp(random.nextInt(28, 60)),
                offset = random.nextFloat() * (h + size * 2),
                sway = dp(random.nextInt(10, 26)),
                swayFreq = 0.6f + random.nextFloat() * 0.9f,
                spin = random.nextInt(25, 70) * (if (random.nextBoolean()) 1f else -1f),
                phase = random.nextFloat() * 360f,
                size = size,
                drawable = palette[random.nextInt(palette.size)],
            )
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ticker.start()
    }

    override fun onDetachedFromWindow() {
        ticker.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return
        val t = (SystemClock.uptimeMillis() - startedAt) / 1000f

        for (leaf in leaves) {
            val span = h + leaf.size * 2
            val y = (t * leaf.speed + leaf.offset) % span - leaf.size
            val x = leaf.x * w + sin(t * leaf.swayFreq + leaf.phase) * leaf.sway
            // 위에서 막 나타날 때와 아래로 사라질 때는 옅게.
            val edge = leaf.size * 3
            val fade = minOf((y + leaf.size) / edge, (h - y) / edge, 1f).coerceIn(0f, 1f)

            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(leaf.phase + t * leaf.spin, leaf.size / 2, leaf.size / 2)
            leaf.drawable.alpha = (fade * LEAF_ALPHA).toInt()
            leaf.drawable.setBounds(0, 0, leaf.size.toInt(), leaf.size.toInt())
            leaf.drawable.draw(canvas)
            canvas.restore()
        }
    }

    private fun dp(v: Int) = v * density

    private companion object {
        const val LEAF_COUNT = 12
        const val LEAF_ALPHA = 210

        /** 고정 시드. 매번 같은 배치라 디자인 검수가 쉽습니다. */
        const val SEED = 20260903
    }
}
