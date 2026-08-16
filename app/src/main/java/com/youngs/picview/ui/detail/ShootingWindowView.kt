package com.youngs.picview.ui.detail

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.youngs.picview.R
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.light.SunTimes
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 이 장소의 오늘 촬영 적기를 작은 호로 그립니다.
 *
 * 홈의 [com.youngs.picview.ui.home.SunArcView] 와 형제지만 하는 말이
 * 다릅니다. 홈은 "하루가 어떻게 흐르는가"를 보여 줘야 해서 24시간을 담고
 * 여덟 구간을 다 폅니다. 여기서는 이미 장소를 고른 사람이 보고 있으므로
 * 물음이 하나로 좁혀집니다 — **오늘 몇 시에 여기 오면 되나.**
 *
 * 그래서 해가 떠 있는 동안만 그립니다. 호의 왼쪽 끝이 일출, 오른쪽 끝이
 * 일몰이고, 그 위에 지금이 어디인지 점을 찍습니다. 밤 시간을 함께 그리면
 * 정작 답인 낮이 좁아집니다.
 */
class ShootingWindowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var sunTimes: SunTimes = SunTimes(null, null)
        set(value) {
            field = value
            invalidate()
        }

    var nowTime: LocalTime = LocalTime.now()
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(4f)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(12f)
        textAlign = Paint.Align.CENTER
    }
    private val nowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(12f)
        textAlign = Paint.Align.CENTER
    }
    private val arcRect = RectF()

    private val textTertiary = ContextCompat.getColor(context, R.color.text_tertiary)
    private val nowColor = ContextCompat.getColor(context, R.color.maple_600)
    private val cardColor = ContextCompat.getColor(context, R.color.maple_50)

    init {
        val font = ResourcesCompat.getFont(context, R.font.pretendard)
        val bold = Typeface.create(font, Typeface.BOLD)
        timePaint.typeface = bold
        nowPaint.typeface = bold
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width * ARC_RISE_RATIO + dp(54f)).toInt()
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val span = daylightSpan() ?: return

        val w = width.toFloat()
        val padH = dp(34f)               // 양 끝 시각이 잘리지 않을 만큼
        val baseY = height - dp(38f)
        val halfWidth = (w - padH * 2) / 2f
        val rise = w * ARC_RISE_RATIO
        val cx = w / 2f

        arcRect.set(cx - halfWidth, baseY - rise, cx + halfWidth, baseY + rise)

        drawArc(canvas, span)
        drawEnds(canvas, baseY, cx, halfWidth)
        drawNow(canvas, span, baseY, cx, halfWidth, rise)
    }

    /** 빛 구간별로 색을 나눠 호를 그립니다. 골든아워가 양 끝에 주황으로 앉습니다. */
    private fun drawArc(canvas: Canvas, span: Pair<Int, Int>) {
        val (start, total) = span
        var offset = 0
        while (offset < total) {
            val phase = phaseAt(start + offset)
            var end = offset
            while (end < total && phaseAt(start + end) == phase) end += STEP

            arcPaint.color = ContextCompat.getColor(context, phase.colorRes)
            canvas.drawArc(
                arcRect,
                180f + offset / total.toFloat() * 180f,
                (end - offset).coerceAtMost(total - offset) / total.toFloat() * 180f,
                false, arcPaint
            )
            offset = end
        }
    }

    /** 양 끝의 일출·일몰 시각. 이름은 달지 않습니다 — 위치가 곧 이름입니다. */
    private fun drawEnds(canvas: Canvas, baseY: Float, cx: Float, halfWidth: Float) {
        dotPaint.color = ContextCompat.getColor(context, R.color.light_sunrise)
        canvas.drawCircle(cx - halfWidth, baseY, dp(3.5f), dotPaint)
        dotPaint.color = ContextCompat.getColor(context, R.color.light_night)
        canvas.drawCircle(cx + halfWidth, baseY, dp(3.5f), dotPaint)

        // 이름을 함께 답니다. 숫자만 두면 왼쪽이 일출인지 지금인지 헷갈립니다.
        timePaint.color = textTertiary
        val y = baseY + dp(18f)
        canvas.drawText(
            context.getString(R.string.sun_arc_sunrise, sunTimes.sunrise?.formatted() ?: "—"),
            cx - halfWidth, y, timePaint
        )
        canvas.drawText(
            context.getString(R.string.sun_arc_sunset, sunTimes.sunset?.formatted() ?: "—"),
            cx + halfWidth, y, timePaint
        )
    }

    /**
     * 지금 위치.
     *
     * 해가 뜨기 전이거나 진 뒤면 가까운 끝에 붙습니다. 점을 아예 안 그리면
     * "지금은 어디쯤인가"에 답하지 않는 그림이 됩니다.
     */
    private fun drawNow(
        canvas: Canvas, span: Pair<Int, Int>,
        baseY: Float, cx: Float, halfWidth: Float, rise: Float
    ) {
        val (start, total) = span
        val minute = nowTime.toSecondOfDay() / 60
        val progress = ((minute - start) / total.toFloat()).coerceIn(0f, 1f)

        val angle = PI * (1f + progress)
        val x = cx + halfWidth * cos(angle).toFloat()
        val y = baseY + rise * sin(angle).toFloat()

        // 카드색 테두리를 둘러 호 위에서도 점이 또렷하게 보이게 합니다.
        dotPaint.color = cardColor
        canvas.drawCircle(x, y, dp(8f), dotPaint)
        dotPaint.color = nowColor
        canvas.drawCircle(x, y, dp(5.5f), dotPaint)

        // 글자가 카드 밖으로 나가지 않게 가둡니다.
        val text = context.getString(R.string.detail_now_at, nowTime.formatted())
        val clampedX = x.coerceIn(nowPaint.measureText(text) / 2f + dp(4f), width - nowPaint.measureText(text) / 2f - dp(4f))

        nowPaint.color = nowColor
        canvas.drawText(text, clampedX, baseY + dp(18f), nowPaint)
    }

    /** 낮의 시작(자정 기준 분)과 길이. 둘 중 하나라도 없으면 그릴 수 없습니다. */
    private fun daylightSpan(): Pair<Int, Int>? {
        val sunrise = sunTimes.sunrise ?: return null
        val sunset = sunTimes.sunset ?: return null
        val start = sunrise.toSecondOfDay() / 60
        val total = sunset.toSecondOfDay() / 60 - start
        return if (total > 0) start to total else null
    }

    private fun LocalTime.formatted() = "%02d:%02d".format(hour, minute)

    private fun phaseAt(minute: Int): LightPhase =
        sunTimes.phaseAt(LocalTime.of(minute / 60 % 24, minute % 60))

    private companion object {
        const val STEP = 5
        const val ARC_RISE_RATIO = 0.16f
    }
}
