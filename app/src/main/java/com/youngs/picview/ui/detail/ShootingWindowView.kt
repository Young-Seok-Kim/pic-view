package com.youngs.picview.ui.detail

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
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
 *
 * 시안(상세 개편안)에 맞춰 라벨을 정리했습니다.
 *  - 양 끝은 시각(굵게) 위에 이름(일출·일몰)을 아래로 — 두 줄.
 *  - 호 꼭대기에 해 아이콘과 "한낮". 호가 무엇의 궤적인지 말해 줍니다.
 *  - 지금 점에서 바닥까지 점선을 내리고 그 아래 "현재 hh:mm".
 *    점선이 있으면 라벨이 점에서 떨어져 있어도 누구의 것인지 잃지 않습니다.
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
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(12f)
        textAlign = Paint.Align.CENTER
    }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(10f)
        textAlign = Paint.Align.CENTER
    }
    private val nowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(12f)
        textAlign = Paint.Align.CENTER
    }
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
        pathEffect = DashPathEffect(floatArrayOf(dp(3f), dp(3f)), 0f)
    }
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.3f)
    }
    private val arcRect = RectF()

    private val textTertiary = ContextCompat.getColor(context, R.color.text_tertiary)
    private val nowColor = ContextCompat.getColor(context, R.color.maple_600)
    private val cardColor = ContextCompat.getColor(context, R.color.maple_50)
    private val middayColor = ContextCompat.getColor(context, R.color.light_midday)

    init {
        val font = ResourcesCompat.getFont(context, R.font.pretendard)
        val bold = Typeface.create(font, Typeface.BOLD)
        timePaint.typeface = bold
        nowPaint.typeface = bold
        capPaint.typeface = font
        dashPaint.color = nowColor
        sunPaint.color = middayColor
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width * ARC_RISE_RATIO + dp(PAD_TOP + PAD_BOTTOM)).toInt()
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val span = daylightSpan() ?: return

        val w = width.toFloat()
        val padH = dp(30f)               // 양 끝 시각이 잘리지 않을 만큼
        val baseY = height - dp(PAD_BOTTOM)
        val halfWidth = (w - padH * 2) / 2f
        val rise = w * ARC_RISE_RATIO
        val cx = w / 2f

        arcRect.set(cx - halfWidth, baseY - rise, cx + halfWidth, baseY + rise)

        drawArc(canvas, span)
        drawMidday(canvas, baseY, cx, rise)
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

    /**
     * 호 꼭대기의 해와 "한낮".
     *
     * 호가 태양의 궤적이라는 것을 그림 스스로 말하게 합니다. 꼭대기가
     * 남중(한낮)이라는 것을 알고 나면 좌우 어디쯤이 몇 시인지 감이 잡힙니다.
     */
    private fun drawMidday(canvas: Canvas, baseY: Float, cx: Float, rise: Float) {
        val top = baseY - rise
        val sunCy = top - dp(25f)
        val r = dp(4f)

        sunPaint.style = Paint.Style.STROKE
        canvas.drawCircle(cx, sunCy, r, sunPaint)
        for (i in 0 until 8) {
            val angle = i * PI / 4
            val sx = cx + (r + dp(2f)) * cos(angle).toFloat()
            val sy = sunCy + (r + dp(2f)) * sin(angle).toFloat()
            val ex = cx + (r + dp(4.5f)) * cos(angle).toFloat()
            val ey = sunCy + (r + dp(4.5f)) * sin(angle).toFloat()
            canvas.drawLine(sx, sy, ex, ey, sunPaint)
        }

        capPaint.color = textTertiary
        canvas.drawText(
            context.getString(R.string.detail_arc_midday),
            cx, top - dp(4f), capPaint
        )
    }

    /**
     * 양 끝의 일출·일몰 — 시각을 굵게, 이름을 그 아래에.
     *
     * 시각이 먼저입니다. 이 그림에서 구하는 답은 "일출이 있다"가 아니라
     * "몇 시인가"라서, 큰 글자를 숫자에 줍니다.
     */
    private fun drawEnds(canvas: Canvas, baseY: Float, cx: Float, halfWidth: Float) {
        dotPaint.color = ContextCompat.getColor(context, R.color.light_sunrise)
        canvas.drawCircle(cx - halfWidth, baseY, dp(3.5f), dotPaint)
        dotPaint.color = ContextCompat.getColor(context, R.color.light_night)
        canvas.drawCircle(cx + halfWidth, baseY, dp(3.5f), dotPaint)

        timePaint.color = textTertiary
        capPaint.color = textTertiary
        val timeY = baseY + dp(16f)
        val nameY = baseY + dp(29f)

        canvas.drawText(sunTimes.sunrise?.formatted() ?: "—", cx - halfWidth, timeY, timePaint)
        canvas.drawText(
            context.getString(R.string.detail_arc_sunrise), cx - halfWidth, nameY, capPaint
        )
        canvas.drawText(sunTimes.sunset?.formatted() ?: "—", cx + halfWidth, timeY, timePaint)
        canvas.drawText(
            context.getString(R.string.detail_arc_sunset), cx + halfWidth, nameY, capPaint
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

        // 점에서 바닥까지 점선. 라벨이 점 바로 아래가 아니어도 이 선이 잇습니다.
        canvas.drawLine(x, y + dp(9f), x, baseY + dp(4f), dashPaint)

        // 카드색 테두리를 둘러 호 위에서도 점이 또렷하게 보이게 합니다.
        dotPaint.color = cardColor
        canvas.drawCircle(x, y, dp(8f), dotPaint)
        dotPaint.color = nowColor
        canvas.drawCircle(x, y, dp(5.5f), dotPaint)

        // 같은 줄의 일출·일몰 시각과 겹치지 않게 가둡니다. 이른 아침이나
        // 해질 무렵에는 지금 점이 끝에 붙는데, 화면 폭만 기준으로 하면
        // "05:54" 위에 "현재 08:34"가 포개져 둘 다 못 읽게 됩니다.
        val text = context.getString(R.string.detail_now_at, nowTime.formatted())
        val halfText = nowPaint.measureText(text) / 2f
        val endHalf = timePaint.measureText("00:00") / 2f
        val gap = dp(8f)
        val leftLimit = cx - halfWidth + endHalf + halfText + gap
        val rightLimit = cx + halfWidth - endHalf - halfText - gap
        val clampedX = x.coerceIn(
            leftLimit.coerceAtLeast(halfText + dp(4f)),
            rightLimit.coerceAtMost(width - halfText - dp(4f))
        )

        nowPaint.color = nowColor
        canvas.drawText(text, clampedX, baseY + dp(16f), nowPaint)
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

        /** 호 위 여백 — 해 아이콘과 "한낮"이 앉는 자리. */
        const val PAD_TOP = 36f

        /** 호 아래 여백 — 시각 한 줄 + 이름 한 줄. */
        const val PAD_BOTTOM = 36f
    }
}
