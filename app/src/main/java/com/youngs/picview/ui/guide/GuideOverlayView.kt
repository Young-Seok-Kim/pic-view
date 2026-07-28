package com.youngs.picview.ui.guide

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * 카메라 미리보기 위에 구도 가이드를 그립니다.
 *
 * 밝은 하늘 위에서 흰 선이 사라지지 않도록, 모든 선은
 * 어두운 선을 먼저 깔고 그 위에 흰 선을 겹쳐 그립니다.
 * 크기는 전부 dp 또는 화면 비율 기준이라 해상도가 달라도 같은 비율로 보입니다.
 */
class GuideOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class GuideType {
        /** 삼분할. 풍경·자연에 쓰며 교차점을 표시합니다. */
        THIRDS,

        /** 좌우 대칭. 건축물·문화재에 씁니다. */
        SYMMETRY,

        /** 중앙 원. 음식이나 근접 촬영에 씁니다. */
        CENTER
    }

    var guideType: GuideType = GuideType.THIRDS
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density

    private fun dp(value: Float) = value * density

    /** 밝은 배경에서도 선이 보이도록 아래에 깔아 주는 어두운 선. */
    private val shadowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = Color.argb(70, 0, 0, 0)
    }

    private val linePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = Color.argb(200, 255, 255, 255)
    }

    private val dotPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.argb(235, 255, 255, 255)
    }

    private val dotShadowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.argb(60, 0, 0, 0)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        when (guideType) {
            GuideType.THIRDS -> drawThirds(canvas, w, h)
            GuideType.SYMMETRY -> drawSymmetry(canvas, w, h)
            GuideType.CENTER -> drawCenter(canvas, w, h)
        }
    }

    /** 선 하나를 어두운 선 + 흰 선 두 번 그립니다. */
    private fun line(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, width: Float) {
        shadowPaint.strokeWidth = width + dp(1.5f)
        canvas.drawLine(x1, y1, x2, y2, shadowPaint)
        linePaint.strokeWidth = width
        canvas.drawLine(x1, y1, x2, y2, linePaint)
    }

    private fun circle(canvas: Canvas, cx: Float, cy: Float, r: Float, width: Float) {
        shadowPaint.strokeWidth = width + dp(1.5f)
        canvas.drawCircle(cx, cy, r, shadowPaint)
        linePaint.strokeWidth = width
        canvas.drawCircle(cx, cy, r, linePaint)
    }

    private fun dot(canvas: Canvas, cx: Float, cy: Float) {
        val r = dp(3.5f)
        canvas.drawCircle(cx, cy, r + dp(1f), dotShadowPaint)
        canvas.drawCircle(cx, cy, r, dotPaint)
    }

    /** 삼분할 격자 + 네 교차점. 피사체를 교차점에 두면 안정적인 구도가 됩니다. */
    private fun drawThirds(canvas: Canvas, w: Float, h: Float) {
        val stroke = dp(1f)
        val x1 = w / 3f
        val x2 = w * 2f / 3f
        val y1 = h / 3f
        val y2 = h * 2f / 3f

        line(canvas, x1, 0f, x1, h, stroke)
        line(canvas, x2, 0f, x2, h, stroke)
        line(canvas, 0f, y1, w, y1, stroke)
        line(canvas, 0f, y2, w, y2, stroke)

        // 교차점 강조 — 안내 문구가 가리키는 지점
        dot(canvas, x1, y1)
        dot(canvas, x2, y1)
        dot(canvas, x1, y2)
        dot(canvas, x2, y2)
    }

    /** 중앙 수직선 + 좌우 여백 기준선. 건물의 대칭을 맞추는 데 씁니다. */
    private fun drawSymmetry(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val stroke = dp(1f)

        // 중앙 축
        line(canvas, cx, 0f, cx, h, dp(1.4f))

        // 좌우 대칭 확인용 보조선
        val offset = w * 0.25f
        line(canvas, cx - offset, 0f, cx - offset, h, stroke)
        line(canvas, cx + offset, 0f, cx + offset, h, stroke)

        // 수평 기준선 (건물이 기울지 않았는지)
        line(canvas, 0f, h / 2f, w, h / 2f, stroke)

        dot(canvas, cx, h / 2f)
    }

    /** 중앙 원. 원 안에 피사체를 채우면 시선이 가운데로 모입니다. */
    private fun drawCenter(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) * 0.32f

        circle(canvas, cx, cy, r, dp(1.4f))

        // 가운데를 잡아 주는 짧은 십자선
        val tick = dp(14f)
        line(canvas, cx - tick, cy, cx + tick, cy, dp(1f))
        line(canvas, cx, cy - tick, cx, cy + tick, dp(1f))
    }
}
