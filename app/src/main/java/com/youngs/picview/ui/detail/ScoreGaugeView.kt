package com.youngs.picview.ui.detail

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.youngs.picview.R

/**
 * 출사 적합도 원형 게이지.
 *
 * 예전에는 "오늘의 출사 적합도 58점"이 회색 글씨 한 줄이었습니다. 숫자만
 * 있으면 58이 높은 것인지 낮은 것인지 견줄 데가 없어, 읽어도 판단이
 * 서지 않습니다. 원의 채움 정도가 그 견줌을 대신합니다 — 절반쯤 찬 원은
 * 글자를 읽기 전에 이미 "그저 그렇다"를 말합니다.
 *
 * 색도 점수를 따라갑니다. 색만으로 판정하지는 않지만(숫자를 가운데 그대로
 * 둡니다), 목록을 훑을 때 초록·노랑·빨강이 먼저 눈에 들어옵니다.
 */
class ScoreGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** 0~100. 값을 넣으면 다시 그립니다. */
    var score: Int = 0
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val strokeWidth = 7f * density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@ScoreGaugeView.strokeWidth
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.divider)
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@ScoreGaugeView.strokeWidth
        strokeCap = Paint.Cap.ROUND
    }

    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 20f * density
        color = ContextCompat.getColor(context, R.color.text_primary)
        isFakeBoldText = true
    }

    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 10f * density
        color = ContextCompat.getColor(context, R.color.text_secondary)
    }

    private val bounds = RectF()

    /**
     * 점수 구간의 색.
     *
     * 경계를 70·45 로 둔 것은 [com.youngs.picview.domain.score.PhotoScoreEngine]
     * 이 만점 100 을 빛·날씨·시간대·혼잡도에 나눠 주기 때문입니다. 70이면
     * 대부분의 항목이 맞은 것이고, 45 아래면 두 축 이상이 어긋난 상태입니다.
     */
    private fun arcColorRes(): Int = when {
        score >= 70 -> R.color.theme_nature
        score >= 45 -> R.color.golden_600
        else -> R.color.maple_500
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (84 * density).toInt()
        setMeasuredDimension(
            resolveSize(size, widthMeasureSpec),
            resolveSize(size, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val inset = strokeWidth / 2f
        bounds.set(inset, inset, width - inset, height - inset)

        // 12시 방향에서 시계 방향으로. 시계를 읽는 방향과 같습니다.
        canvas.drawArc(bounds, -90f, 360f, false, trackPaint)

        arcPaint.color = ContextCompat.getColor(context, arcColorRes())
        canvas.drawArc(bounds, -90f, 360f * score / 100f, false, arcPaint)

        val centerX = width / 2f
        val centerY = height / 2f
        canvas.drawText(score.toString(), centerX, centerY + 4f * density, scorePaint)
        canvas.drawText(
            context.getString(R.string.detail_score_unit),
            centerX,
            centerY + 18f * density,
            unitPaint
        )
    }
}
