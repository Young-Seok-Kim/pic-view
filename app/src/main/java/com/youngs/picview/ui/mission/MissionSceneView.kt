package com.youngs.picview.ui.mission

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 미션 헤더의 장식 그림 — 해 · 정자 · 무지개다리 · 물.
 *
 * "장면 0 / 19" 옆의 빈 자리를 채웁니다. 다만 아무 그림이나 넣으면 장식이
 * 되므로, **이 앱이 세는 장면이 무엇인지**를 그립니다. 해가 뜨고, 오래된
 * 지붕이 있고, 그 아래 물이 흐르는 것 — 정읍에서 모으게 되는 장면입니다.
 *
 * 선만 그리고 채우지 않습니다. 카드 바탕이 이미 진한 단풍색이라 면을
 * 칠하면 글자와 무게가 같아져 "장면 0 / 19" 가 안 읽힙니다. 흰색을 아주
 * 옅게 깔아 배경으로 물러나 있게 합니다.
 *
 * 이미지가 아니라 코드로 그리는 이유는 카드 색이 바뀌어도 따라오게 하기
 * 위해서입니다. PNG 로 두면 배경색이 바뀔 때마다 다시 뽑아야 합니다.
 */
class MissionSceneView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
        alpha = 82
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 46
    }

    private val path = Path()
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 폭을 주면 높이는 비율로 따라옵니다. 카드 안 장식이라 스스로
        // 크기를 정하지 않고 놓인 자리에 맞춥니다.
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, resolveSize((width * 0.78f).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        linePaint.strokeWidth = w * 0.016f

        drawSun(canvas, w * 0.60f, h * 0.20f, w * 0.085f)
        drawPavilion(canvas, w * 0.74f, h * 0.62f, w * 0.30f)
        drawBridge(canvas, w * 0.34f, h * 0.66f, w * 0.42f)
        drawWater(canvas, w, h)
    }

    /** 해. 동그라미 하나에 짧은 빛살 여덟. */
    private fun drawSun(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        canvas.drawCircle(cx, cy, r, linePaint)
        for (i in 0 until 8) {
            val a = i * PI / 4
            canvas.drawLine(
                cx + (r * 1.45f) * cos(a).toFloat(), cy + (r * 1.45f) * sin(a).toFloat(),
                cx + (r * 2.1f) * cos(a).toFloat(), cy + (r * 2.1f) * sin(a).toFloat(),
                linePaint
            )
        }
    }

    /**
     * 정자.
     *
     * 처마가 위로 들린 곡선이 한옥 지붕을 한 획으로 말합니다. 기와 한 장씩
     * 그리면 이 크기에서는 뭉쳐 보이기만 합니다.
     */
    private fun drawPavilion(canvas: Canvas, cx: Float, baseY: Float, size: Float) {
        val half = size / 2f

        // 위 지붕
        path.reset()
        path.moveTo(cx - half, baseY - size * 0.62f)
        path.quadTo(cx, baseY - size * 0.95f, cx + half, baseY - size * 0.62f)
        path.quadTo(cx + half * 0.6f, baseY - size * 0.52f, cx, baseY - size * 0.55f)
        path.quadTo(cx - half * 0.6f, baseY - size * 0.52f, cx - half, baseY - size * 0.62f)
        path.close()
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, linePaint)

        // 아래 지붕 — 두 겹이라야 정자로 보입니다.
        path.reset()
        path.moveTo(cx - half * 1.25f, baseY - size * 0.18f)
        path.quadTo(cx, baseY - size * 0.48f, cx + half * 1.25f, baseY - size * 0.18f)
        path.quadTo(cx + half * 0.7f, baseY - size * 0.08f, cx, baseY - size * 0.12f)
        path.quadTo(cx - half * 0.7f, baseY - size * 0.08f, cx - half * 1.25f, baseY - size * 0.18f)
        path.close()
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, linePaint)

        // 기둥 셋
        for (offset in listOf(-0.55f, 0f, 0.55f)) {
            val x = cx + half * offset
            canvas.drawLine(x, baseY - size * 0.12f, x, baseY, linePaint)
        }
    }

    /** 무지개다리. 아치 하나와 그 위 난간. */
    private fun drawBridge(canvas: Canvas, cx: Float, baseY: Float, span: Float) {
        val half = span / 2f
        val rise = span * 0.34f

        // 아치 아래쪽(물에 닿는 반원)
        rect.set(cx - half * 0.52f, baseY - rise * 0.5f, cx + half * 0.52f, baseY + rise * 0.5f)
        canvas.drawArc(rect, 180f, 180f, false, linePaint)

        // 상판
        path.reset()
        path.moveTo(cx - half, baseY)
        path.quadTo(cx, baseY - rise, cx + half, baseY)
        canvas.drawPath(path, linePaint)

        // 난간 — 상판을 따라 조금 위로
        path.reset()
        path.moveTo(cx - half, baseY - span * 0.09f)
        path.quadTo(cx, baseY - rise - span * 0.09f, cx + half, baseY - span * 0.09f)
        canvas.drawPath(path, linePaint)
    }

    /** 물. 짧은 선 몇 개면 물결로 읽힙니다. */
    private fun drawWater(canvas: Canvas, w: Float, h: Float) {
        val lines = listOf(
            Triple(0.06f, 0.30f, 0.80f),
            Triple(0.40f, 0.72f, 0.87f),
            Triple(0.12f, 0.34f, 0.93f),
            Triple(0.52f, 0.88f, 0.96f)
        )
        lines.forEach { (from, to, y) ->
            canvas.drawLine(w * from, h * y, w * to, h * y, linePaint)
        }
    }
}
