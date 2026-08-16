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
 * 미션 헤더의 선화 — 해 · 구름 · 무지개다리 · 정자 · 물결.
 *
 * "장면 0 / 19" 옆의 빈 자리를 채웁니다. 아무 그림이나 넣으면 장식이
 * 되므로 **이 앱이 세는 장면이 무엇인지**를 그립니다. 해가 뜨고, 오래된
 * 지붕이 있고, 그 아래 물이 흐르는 것 — 정읍에서 모으게 되는 장면입니다.
 *
 * 시안은 PNG 였지만 Canvas 로 옮겨 그렸습니다. 그림 색이 카드 바탕색에
 * 묶여 있어서, PNG 로 두면 카드 색을 바꿀 때마다 다시 뽑아야 합니다.
 * 선 색을 흰색 알파로 두면 바탕이 무슨 색이든 같은 톤으로 얹힙니다.
 *
 * 좌표는 0~1 로 적고 그릴 때 폭·높이를 곱합니다. 카드 크기가 달라져도
 * 비율이 유지되고, 숫자를 읽을 때 "가로 3분의 1 지점" 처럼 읽힙니다.
 */
class MissionSceneView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
        alpha = 92
    }
    private val thin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
        alpha = 62
    }

    private val path = Path()
    private val rect = RectF()

    private var w = 0f
    private var h = 0f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 카드 안 장식이라 스스로 크기를 정하지 않고 놓인 자리에 맞춥니다.
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, resolveSize((width * 0.74f).toInt(), heightMeasureSpec))
    }

    /** 0~1 좌표를 실제 픽셀로. */
    private fun x(v: Float) = w * v
    private fun y(v: Float) = h * v

    override fun onDraw(canvas: Canvas) {
        w = width.toFloat()
        h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        line.strokeWidth = w * 0.011f
        thin.strokeWidth = w * 0.008f

        drawSun(canvas)
        drawCloud(canvas, 0.74f, 0.20f, 0.30f)
        drawCloud(canvas, 0.60f, 0.36f, 0.24f)
        drawCloud(canvas, 0.22f, 0.44f, 0.17f)
        drawBridge(canvas)
        drawPavilion(canvas)
        drawWater(canvas)
    }

    // ─────────────────────── 하늘 ───────────────────────

    /** 해. 동그라미 하나에 빛살 열둘. */
    private fun drawSun(canvas: Canvas) {
        val cx = x(0.33f)
        val cy = y(0.24f)
        val r = w * 0.075f

        canvas.drawCircle(cx, cy, r, line)
        for (i in 0 until 12) {
            val a = i * PI / 6
            canvas.drawLine(
                cx + (r * 1.4f) * cos(a).toFloat(), cy + (r * 1.4f) * sin(a).toFloat(),
                cx + (r * 1.95f) * cos(a).toFloat(), cy + (r * 1.95f) * sin(a).toFloat(),
                line
            )
        }
    }

    /**
     * 구름.
     *
     * 아래가 평평하고 위가 뭉게뭉게한 모양입니다. 밑변을 옆으로 길게 빼면
     * 구름이 멀리 떠 있는 것처럼 보여서 하늘에 깊이가 생깁니다.
     */
    private fun drawCloud(canvas: Canvas, cxRatio: Float, cyRatio: Float, widthRatio: Float) {
        val cx = x(cxRatio)
        val cy = y(cyRatio)
        val cw = w * widthRatio
        val ch = cw * 0.42f

        path.reset()
        path.moveTo(cx - cw * 0.42f, cy)
        path.cubicTo(
            cx - cw * 0.52f, cy - ch * 0.35f,
            cx - cw * 0.30f, cy - ch * 0.72f,
            cx - cw * 0.12f, cy - ch * 0.52f
        )
        path.cubicTo(
            cx - cw * 0.04f, cy - ch * 1.05f,
            cx + cw * 0.22f, cy - ch * 1.02f,
            cx + cw * 0.24f, cy - ch * 0.48f
        )
        path.cubicTo(
            cx + cw * 0.40f, cy - ch * 0.70f,
            cx + cw * 0.52f, cy - ch * 0.28f,
            cx + cw * 0.44f, cy
        )
        canvas.drawPath(path, line)

        // 밑변 — 구름보다 길게 빼서 수평선처럼 보이게 합니다.
        canvas.drawLine(cx - cw * 0.62f, cy, cx + cw * 0.62f, cy, line)
    }

    // ─────────────────────── 무지개다리 ───────────────────────

    /**
     * 무지개다리.
     *
     * 아치 · 상판 · 난간 세 겹입니다. 아치만 그리면 굴다리처럼 보이고,
     * 난간의 작은 기둥이 있어야 사람이 건너는 다리로 읽힙니다.
     */
    private fun drawBridge(canvas: Canvas) {
        val left = x(0.07f)
        val right = x(0.60f)
        val baseY = y(0.66f)
        val deckRise = h * 0.20f
        val cx = (left + right) / 2f

        // 1) 아치 안쪽(물에 닿는 반원)
        val archHalf = (right - left) * 0.36f
        rect.set(cx - archHalf, baseY - archHalf * 0.98f, cx + archHalf, baseY + archHalf * 0.98f)
        canvas.drawArc(rect, 180f, 180f, false, line)

        // 2) 상판 — 아치보다 완만하게
        path.reset()
        path.moveTo(left, baseY)
        path.quadTo(cx, baseY - deckRise * 1.5f, right, baseY)
        canvas.drawPath(path, line)

        // 3) 난간 — 상판을 따라 위로
        val railGap = h * 0.075f
        path.reset()
        path.moveTo(left, baseY - railGap)
        path.quadTo(cx, baseY - deckRise * 1.5f - railGap, right, baseY - railGap)
        canvas.drawPath(path, line)

        // 4) 난간 기둥. 위쪽 끝에 작은 구슬을 얹습니다.
        for (t in listOf(0.06f, 0.28f, 0.5f, 0.72f, 0.94f)) {
            val px = left + (right - left) * t
            val deckY = quad(baseY, baseY - deckRise * 1.5f, baseY, t)
            val railY = deckY - railGap
            canvas.drawLine(px, deckY, px, railY, line)
            canvas.drawCircle(px, railY - w * 0.012f, w * 0.012f, line)
        }

        // 5) 돌 이음매 — 아치를 따라 방사로 몇 줄만.
        for (t in listOf(0.2f, 0.35f, 0.5f, 0.65f, 0.8f)) {
            val a = PI * (1f + t)
            val ix = cx + archHalf * cos(a).toFloat()
            val iy = baseY + archHalf * 0.98f * sin(a).toFloat()
            val deckY = quad(baseY, baseY - deckRise * 1.5f, baseY, t)
            canvas.drawLine(ix, iy, cx + (right - left) * (t - 0.5f), deckY, thin)
        }

        // 6) 왼쪽 층계
        canvas.drawLine(left, baseY, left - w * 0.03f, baseY, line)
        canvas.drawLine(left - w * 0.03f, baseY, left - w * 0.03f, baseY - h * 0.05f, line)
    }

    /** 2차 베지어의 t 지점 값. 난간 기둥을 상판 위에 정확히 세우는 데 씁니다. */
    private fun quad(p0: Float, p1: Float, p2: Float, t: Float): Float {
        val u = 1 - t
        return u * u * p0 + 2 * u * t * p1 + t * t * p2
    }

    // ─────────────────────── 정자 ───────────────────────

    /**
     * 정자.
     *
     * 처마가 위로 들린 팔작지붕, 꼭대기의 절병통, 기둥과 난간, 그리고
     * 돌 기단입니다. 기와를 한 장씩 그리면 이 크기에서 뭉치므로 용마루에서
     * 처마로 내려오는 선 몇 개로 대신합니다.
     */
    private fun drawPavilion(canvas: Canvas) {
        val cx = x(0.79f)
        val baseY = y(0.66f)
        val half = w * 0.19f

        // 1) 절병통(지붕 꼭대기 장식)
        val topY = y(0.30f)
        canvas.drawCircle(cx, topY - h * 0.035f, w * 0.016f, line)
        canvas.drawLine(cx, topY - h * 0.02f, cx, topY, line)

        // 2) 지붕 — 가운데가 솟고 양 끝이 위로 들립니다.
        path.reset()
        path.moveTo(cx - half * 1.15f, topY + h * 0.115f)
        path.quadTo(cx - half * 0.5f, topY - h * 0.015f, cx, topY)
        path.quadTo(cx + half * 0.5f, topY - h * 0.015f, cx + half * 1.15f, topY + h * 0.115f)
        canvas.drawPath(path, line)

        // 처마 끝이 위로 말리는 곡선
        path.reset()
        path.moveTo(cx - half * 1.15f, topY + h * 0.115f)
        path.quadTo(cx - half * 1.32f, topY + h * 0.10f, cx - half * 1.30f, topY + h * 0.055f)
        canvas.drawPath(path, line)
        path.reset()
        path.moveTo(cx + half * 1.15f, topY + h * 0.115f)
        path.quadTo(cx + half * 1.32f, topY + h * 0.10f, cx + half * 1.30f, topY + h * 0.055f)
        canvas.drawPath(path, line)

        // 지붕 아래 처마선
        path.reset()
        path.moveTo(cx - half * 1.15f, topY + h * 0.13f)
        path.quadTo(cx, topY + h * 0.075f, cx + half * 1.15f, topY + h * 0.13f)
        canvas.drawPath(path, line)

        // 3) 기와 결 — 용마루에서 처마로 내려오는 선
        for (i in -3..3) {
            val t = i / 3f
            val sx = cx + half * 0.16f * i
            val ex = cx + half * 1.05f * t
            canvas.drawLine(sx, topY + h * 0.012f, ex, topY + h * 0.115f, thin)
        }

        // 4) 몸체 — 기둥 넷과 창방
        val bodyTop = topY + h * 0.145f
        val bodyBottom = baseY - h * 0.055f
        for (offset in listOf(-0.92f, -0.32f, 0.32f, 0.92f)) {
            val px = cx + half * offset
            canvas.drawLine(px, bodyTop, px, bodyBottom, line)
        }
        canvas.drawLine(cx - half, bodyTop + h * 0.02f, cx + half, bodyTop + h * 0.02f, thin)
        canvas.drawLine(cx - half, bodyBottom - h * 0.03f, cx + half, bodyBottom - h * 0.03f, thin)

        // 5) 돌 기단 — 두 단
        canvas.drawLine(cx - half * 1.2f, bodyBottom, cx + half * 1.35f, bodyBottom, line)
        canvas.drawLine(cx - half * 1.2f, baseY, cx + half * 1.35f, baseY, line)
        canvas.drawLine(cx - half * 1.2f, bodyBottom, cx - half * 1.2f, baseY, line)
        canvas.drawLine(cx + half * 1.35f, bodyBottom, cx + half * 1.35f, baseY, line)
        for (i in 1..4) {
            val px = cx - half * 1.2f + (half * 2.55f) * i / 5f
            canvas.drawLine(px, bodyBottom, px, baseY, thin)
        }
    }

    // ─────────────────────── 물 ───────────────────────

    /** 물결. 짧은 선 몇 개면 물로 읽힙니다. 흩어 놓아야 잔물결로 보입니다. */
    private fun drawWater(canvas: Canvas) {
        val dashes = listOf(
            Triple(0.36f, 0.47f, 0.71f),
            Triple(0.42f, 0.52f, 0.77f),
            Triple(0.10f, 0.22f, 0.82f),
            Triple(0.28f, 0.37f, 0.85f),
            Triple(0.50f, 0.61f, 0.83f),
            Triple(0.70f, 0.82f, 0.80f),
            Triple(0.19f, 0.29f, 0.90f),
            Triple(0.58f, 0.70f, 0.91f),
            Triple(0.34f, 0.45f, 0.95f)
        )
        dashes.forEach { (from, to, at) ->
            canvas.drawLine(x(from), y(at), x(to), y(at), line)
        }
    }
}
