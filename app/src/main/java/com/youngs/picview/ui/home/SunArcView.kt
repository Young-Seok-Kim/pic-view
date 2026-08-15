package com.youngs.picview.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
 * 하루의 빛을 해가 지나는 호(arc)로 그립니다.
 *
 * 가로 막대였던 것을 아치로 바꿨습니다. 막대는 "얼마나 남았나"는 읽히지만
 * "해가 어디쯤 떠 있나"는 안 보입니다. 아치는 해의 높이가 그대로 보여서
 * 한낮에 왜 빛이 강한지, 골든아워가 왜 짧은지가 그림으로 설명됩니다.
 *
 * 모든 값이 실데이터입니다. 일출·일몰 시각으로 호의 양 끝을 잡고, 지금
 * 시각으로 해의 위치를 정하고, 빛 구간 경계마다 점을 찍습니다. 그림을
 * 이미지로 깔면 이 값들이 고정되므로 전부 코드로 그립니다.
 */
class SunArcView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var sunTimes: SunTimes = SunTimes(sunrise = null, sunset = null)
        set(value) {
            field = value
            invalidate()
        }

    /** 지금 시각. 미리보기·테스트에서 바꿔 끼울 수 있게 열어 둡니다. */
    var nowTime: LocalTime = LocalTime.now()
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(5f)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(2f)
    }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(12f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(10f)
        textAlign = Paint.Align.CENTER
    }

    private val arcRect = RectF()
    private val hillPath = Path()

    private val textSecondary = ContextCompat.getColor(context, R.color.text_secondary)
    private val textTertiary = ContextCompat.getColor(context, R.color.text_tertiary)

    /** 호에 색을 입힐 구간. 순서가 곧 하루의 흐름입니다. */
    private val phases = listOf(
        LightPhase.BLUE_DAWN, LightPhase.SUNRISE, LightPhase.MORNING,
        LightPhase.MIDDAY, LightPhase.AFTERNOON, LightPhase.SUNSET,
        LightPhase.BLUE_DUSK, LightPhase.NIGHT
    )

    init {
        val font = ResourcesCompat.getFont(context, R.font.pretendard)
        timePaint.typeface = Typeface.create(font, Typeface.BOLD)
        labelPaint.typeface = font
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        // 반원 + 시각 표기 자리. 폭의 절반이면 반원이 정확히 들어갑니다.
        val height = (width * 0.52f + dp(34f)).toInt()
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val padH = dp(30f)          // 좌우 여백. 끝점 원이 잘리지 않게
        val baseY = height - dp(30f) // 지평선
        val radius = (w - padH * 2) / 2f
        val cx = w / 2f

        arcRect.set(cx - radius, baseY - radius, cx + radius, baseY + radius)

        drawHills(canvas, baseY, w)
        drawArc(canvas)
        drawPhaseDots(canvas, cx, baseY, radius)
        drawSun(canvas, cx, baseY, radius)
        drawTimes(canvas, baseY, padH, w)
    }

    /** 지평선 아래 언덕. 호가 어디서 시작하고 끝나는지 바닥을 만들어 줍니다. */
    private fun drawHills(canvas: Canvas, baseY: Float, w: Float) {
        hillPaint.color = ContextCompat.getColor(context, R.color.hill_far)
        hillPath.reset()
        hillPath.moveTo(0f, baseY)
        hillPath.quadTo(w * 0.25f, baseY - dp(16f), w * 0.5f, baseY - dp(4f))
        hillPath.quadTo(w * 0.75f, baseY + dp(6f), w, baseY - dp(10f))
        hillPath.lineTo(w, baseY + dp(30f))
        hillPath.lineTo(0f, baseY + dp(30f))
        hillPath.close()
        canvas.drawPath(hillPath, hillPaint)

        hillPaint.color = ContextCompat.getColor(context, R.color.hill_near)
        hillPath.reset()
        hillPath.moveTo(0f, baseY + dp(6f))
        hillPath.quadTo(w * 0.3f, baseY - dp(4f), w * 0.62f, baseY + dp(8f))
        hillPath.quadTo(w * 0.85f, baseY + dp(16f), w, baseY + dp(4f))
        hillPath.lineTo(w, baseY + dp(30f))
        hillPath.lineTo(0f, baseY + dp(30f))
        hillPath.close()
        canvas.drawPath(hillPath, hillPaint)
    }

    /**
     * 빛 구간별로 색을 나눠 호를 그립니다.
     *
     * 호는 왼쪽(일출)에서 오른쪽(일몰)으로 갑니다. 하루 전체(0~24시)가 아니라
     * **자정에서 자정까지**를 반원에 펴서, 해가 떠 있는 동안이 위쪽 절반을
     * 차지하게 합니다.
     */
    private fun drawArc(canvas: Canvas) {
        var startMinute = 0
        while (startMinute < MINUTES_PER_DAY) {
            val phase = phaseAt(startMinute)
            var end = startMinute
            while (end < MINUTES_PER_DAY && phaseAt(end) == phase) end += STEP

            arcPaint.color = ContextCompat.getColor(context, phase.colorRes)
            val startAngle = 180f + startMinute / MINUTES_PER_DAY.toFloat() * 180f
            val sweep = (end - startMinute) / MINUTES_PER_DAY.toFloat() * 180f
            canvas.drawArc(arcRect, startAngle, sweep, false, arcPaint)

            startMinute = end
        }
    }

    /**
     * 구간이 바뀌는 지점에 점을 찍고 이름을 답니다.
     *
     * 이름은 전부 달지 않습니다. 골든아워·블루아워는 20~30분짜리라 경계가
     * 붙어 있어서, 다 적으면 글자가 겹쳐 아무것도 안 읽힙니다. 대표 구간만
     * 적고 나머지는 점으로만 표시합니다.
     */
    private fun drawPhaseDots(canvas: Canvas, cx: Float, baseY: Float, radius: Float) {
        var previous: LightPhase? = null
        var lastLabelX = -Float.MAX_VALUE
        var minute = 0

        while (minute < MINUTES_PER_DAY) {
            val phase = phaseAt(minute)
            if (phase != previous && previous != null) {
                val angle = PI * (1f + minute / MINUTES_PER_DAY.toDouble())
                val x = cx + radius * cos(angle).toFloat()
                val y = baseY + radius * sin(angle).toFloat()

                dotPaint.color = ContextCompat.getColor(context, phase.colorRes)
                canvas.drawCircle(x, y, dp(3.5f), dotPaint)

                // 이름은 호 바깥쪽에 답니다. 안쪽에 두면 해와 겹칩니다.
                // 앞 이름과 너무 가까우면 건너뜁니다.
                val lx = cx + (radius + dp(16f)) * cos(angle).toFloat()
                if (phase in LABELED && lx - lastLabelX > dp(46f)) {
                    labelPaint.color = textTertiary
                    val ly = baseY + (radius + dp(16f)) * sin(angle).toFloat() + dp(3f)
                    canvas.drawText(phase.shortLabel, lx, ly, labelPaint)
                    lastLabelX = lx
                }
            }
            previous = phase
            minute += STEP
        }
    }

    /** 지금 해의 위치. 빛 구간 색으로 칠하고 짧은 광선을 답니다. */
    private fun drawSun(canvas: Canvas, cx: Float, baseY: Float, radius: Float) {
        val minute = nowTime.toSecondOfDay() / 60
        val angle = PI * (1f + minute / MINUTES_PER_DAY.toDouble())
        val x = cx + radius * cos(angle).toFloat()
        val y = baseY + radius * sin(angle).toFloat()

        val phase = phaseAt(minute)
        val color = ContextCompat.getColor(context, phase.colorRes)

        rayPaint.color = color
        rayPaint.alpha = 120
        for (i in 0 until 8) {
            val a = i * PI / 4
            val inner = dp(11f)
            val outer = dp(16f)
            canvas.drawLine(
                x + inner * cos(a).toFloat(), y + inner * sin(a).toFloat(),
                x + outer * cos(a).toFloat(), y + outer * sin(a).toFloat(),
                rayPaint
            )
        }

        // 흰 테두리를 둘러 호 위에서도 해가 또렷하게 보이게 합니다.
        sunPaint.color = ContextCompat.getColor(context, R.color.bg_card)
        canvas.drawCircle(x, y, dp(9.5f), sunPaint)
        sunPaint.color = color
        canvas.drawCircle(x, y, dp(7.5f), sunPaint)
    }

    /** 일출·일몰 시각과 낮 길이. */
    private fun drawTimes(canvas: Canvas, baseY: Float, padH: Float, w: Float) {
        val sunrise = sunTimes.sunrise
        val sunset = sunTimes.sunset
        val y = baseY + dp(22f)

        timePaint.color = ContextCompat.getColor(context, R.color.light_sunrise)
        timePaint.textAlign = Paint.Align.LEFT
        canvas.drawText(
            context.getString(R.string.sun_arc_sunrise, sunrise?.formatted() ?: "—"),
            padH - dp(18f), y, timePaint
        )

        timePaint.color = ContextCompat.getColor(context, R.color.light_night)
        timePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            context.getString(R.string.sun_arc_sunset, sunset?.formatted() ?: "—"),
            w - padH + dp(18f), y, timePaint
        )

        // 낮 길이는 두 시각이 다 있을 때만.
        if (sunrise == null || sunset == null) return
        val minutes = (sunset.toSecondOfDay() - sunrise.toSecondOfDay()) / 60
        if (minutes <= 0) return

        labelPaint.color = textSecondary
        canvas.drawText(
            context.getString(R.string.sun_arc_daylight, minutes / 60, minutes % 60),
            w / 2f, y, labelPaint
        )
    }

    private fun LocalTime.formatted() = "%02d:%02d".format(hour, minute)

    /** 자정 기준 분 → 그때의 빛 구간. */
    private fun phaseAt(minute: Int): LightPhase =
        sunTimes.phaseAt(LocalTime.of(minute / 60 % 24, minute % 60))

    companion object {
        private const val MINUTES_PER_DAY = 24 * 60

        /** 호를 훑는 간격(분). 작을수록 경계가 정확하지만 그리는 횟수가 늡니다. */
        private const val STEP = 5

        /**
         * 이름을 다는 구간.
         *
         * 골든아워·블루아워는 20~30분짜리라 경계가 붙어 있어 이름까지 달면
         * 글자가 겹칩니다. 하루의 큰 흐름을 보여 주는 구간만 답니다.
         */
        private val LABELED = setOf(
            LightPhase.SUNRISE, LightPhase.MIDDAY, LightPhase.SUNSET, LightPhase.NIGHT
        )
    }
}
