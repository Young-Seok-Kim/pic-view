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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 하루의 빛을 해가 지나는 완만한 호로 그립니다.
 *
 * 두 번 갈아엎었습니다. 처음에는 자정~자정을 **반원**에 폈는데 세로로 높아
 * 카드가 화면의 3분의 1을 먹었습니다. 그다음 일출~일몰만 **납작한 호**로
 * 폈더니 카드는 짧아졌지만 새벽·저녁·야간이 그림에서 사라졌습니다.
 *
 * 지금은 둘을 합쳤습니다. 하루 24시간을 그대로 담되 호를 납작하게 눕혀
 * 높이를 줄였습니다. 여덟 구간이 전부 제 색으로 들어가고, 이름은 아래에
 * 두 줄로 번갈아 답니다.
 *
 * 모든 값이 실데이터입니다. 일출·일몰 시각이 구간 경계를 정하고, 지금
 * 시각이 해의 위치를 정합니다.
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
        strokeWidth = dp(5f)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(1.6f)
    }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(13f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(10f)
        textAlign = Paint.Align.CENTER
    }

    private val arcRect = RectF()
    private val fillPath = Path()

    private val textTertiary = ContextCompat.getColor(context, R.color.text_tertiary)

    init {
        val font = ResourcesCompat.getFont(context, R.font.pretendard)
        timePaint.typeface = Typeface.create(font, Typeface.BOLD)
        labelPaint.typeface = font
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        // 호가 솟는 높이 + 이름 두 줄 + 시각 한 줄.
        val height = (width * ARC_RISE_RATIO + dp(76f)).toInt()
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val padH = dp(14f)              // 끝 구간 색이 잘리지 않을 만큼만
        val baseY = height - dp(62f)    // 호의 양 끝이 놓이는 선
        val halfWidth = (w - padH * 2) / 2f
        val rise = w * ARC_RISE_RATIO
        val cx = w / 2f

        // 납작한 호는 원이 아니라 타원입니다. 가로 반지름을 두고 세로만
        // 줄여야 폭을 유지한 채 높이만 낮아집니다.
        arcRect.set(cx - halfWidth, baseY - rise, cx + halfWidth, baseY + rise)

        drawFill(canvas, baseY, cx, halfWidth)
        drawArc(canvas)
        drawPhaseLabels(canvas, baseY, cx, halfWidth)
        drawSun(canvas, cx, baseY, halfWidth, rise)
        drawTimes(canvas, baseY, padH, w)
    }

    /** 호 아래를 옅게 채웁니다. 선만 있으면 허공에 떠 보입니다. */
    private fun drawFill(canvas: Canvas, baseY: Float, cx: Float, halfWidth: Float) {
        fillPaint.color = ContextCompat.getColor(context, R.color.sun_arc_fill)
        fillPath.reset()
        fillPath.moveTo(cx - halfWidth, baseY)
        fillPath.arcTo(arcRect, 180f, 180f, false)
        fillPath.lineTo(cx + halfWidth, baseY + dp(8f))
        fillPath.lineTo(cx - halfWidth, baseY + dp(8f))
        fillPath.close()
        canvas.drawPath(fillPath, fillPaint)
    }

    /**
     * 빛 구간별로 색을 나눠 호를 그립니다.
     *
     * 왼쪽 끝이 자정, 오른쪽 끝이 다음 자정입니다. 5분 간격으로 훑으며
     * 구간이 바뀌는 곳에서 색을 갈아 끼웁니다. 골든아워·블루아워는
     * 20~30분짜리라 아주 얇은 띠로 나오는데, 그 얇음 자체가 "놓치면 끝"을
     * 말해 주므로 억지로 넓히지 않습니다.
     *
     * 이어지는 구간이 실선으로 붙어야 해서 끝을 둥글리지 않습니다.
     * ROUND 로 두면 얇은 구간이 옆 구간 위로 번져 색이 섞입니다.
     */
    private fun drawArc(canvas: Canvas) {
        var startMinute = 0
        while (startMinute < MINUTES_PER_DAY) {
            val phase = phaseAt(startMinute)
            var end = startMinute
            while (end < MINUTES_PER_DAY && phaseAt(end) == phase) end += STEP

            arcPaint.color = ContextCompat.getColor(context, phase.colorRes)
            canvas.drawArc(
                arcRect,
                180f + startMinute / MINUTES_PER_DAY.toFloat() * 180f,
                (end - startMinute) / MINUTES_PER_DAY.toFloat() * 180f,
                false,
                arcPaint
            )
            startMinute = end
        }
    }

    /**
     * 구간 이름을 호 아래 두 줄에 번갈아 답니다.
     *
     * 한 줄에 몰면 골든아워·블루아워가 30분짜리라 이름이 서로 포개집니다.
     * 이웃한 구간을 위아래로 갈라 두면 가로로 붙어 있어도 안 겹칩니다.
     * 그래도 같은 줄끼리 가까워지는 경우가 남아서 간격을 한 번 더 봅니다.
     */
    private fun drawPhaseLabels(canvas: Canvas, baseY: Float, cx: Float, halfWidth: Float) {
        val rowY = floatArrayOf(baseY + dp(20f), baseY + dp(34f))
        val lastX = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE)

        labelPaint.color = textTertiary
        var row = 0

        for ((from, until, phase) in segments()) {
            // 자정에 잘린 야간 조각 중 짧은 쪽에는 이름을 달지 않습니다.
            // 달면 "야간"이 한 그림에 두 번 나옵니다.
            if (phase == LightPhase.NIGHT && until - from < longestNightSpan()) continue

            val x = cx - halfWidth + (from + until) / 2f / MINUTES_PER_DAY * halfWidth * 2f
            if (abs(x - lastX[row]) < dp(38f)) continue

            canvas.drawText(phase.shortLabel, x, rowY[row], labelPaint)
            lastX[row] = x
            row = 1 - row
        }
    }

    /**
     * 지금 해의 위치. 빛 구간 색으로 칠하고 짧은 광선을 답니다.
     */
    private fun drawSun(canvas: Canvas, cx: Float, baseY: Float, halfWidth: Float, rise: Float) {
        val minute = nowTime.toSecondOfDay() / 60
        val angle = PI * (1f + minute / MINUTES_PER_DAY.toDouble())
        val x = cx + halfWidth * cos(angle).toFloat()
        val y = baseY + rise * sin(angle).toFloat()

        val color = ContextCompat.getColor(context, phaseAt(minute).colorRes)

        rayPaint.color = color
        rayPaint.alpha = 150
        for (i in 0 until 8) {
            val a = i * PI / 4
            canvas.drawLine(
                x + dp(9f) * cos(a).toFloat(), y + dp(9f) * sin(a).toFloat(),
                x + dp(13f) * cos(a).toFloat(), y + dp(13f) * sin(a).toFloat(),
                rayPaint
            )
        }

        // 흰 테두리를 둘러 호 위에서도 해가 또렷하게 보이게 합니다.
        sunPaint.color = ContextCompat.getColor(context, R.color.bg_card)
        canvas.drawCircle(x, y, dp(8f), sunPaint)
        sunPaint.color = color
        canvas.drawCircle(x, y, dp(6f), sunPaint)
    }

    /** 맨 아래 줄에 일출·일몰 시각. */
    private fun drawTimes(canvas: Canvas, baseY: Float, padH: Float, w: Float) {
        val y = baseY + dp(54f)

        timePaint.color = ContextCompat.getColor(context, R.color.light_sunrise)
        timePaint.textAlign = Paint.Align.LEFT
        canvas.drawText(
            context.getString(R.string.sun_arc_sunrise, sunTimes.sunrise?.formatted() ?: "—"),
            padH, y, timePaint
        )

        timePaint.color = ContextCompat.getColor(context, R.color.light_sunset)
        timePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            context.getString(R.string.sun_arc_sunset, sunTimes.sunset?.formatted() ?: "—"),
            w - padH, y, timePaint
        )
    }

    /** 하루를 구간별로 자른 목록. (시작 분, 끝 분, 구간) */
    private fun segments(): List<Triple<Int, Int, LightPhase>> {
        val result = mutableListOf<Triple<Int, Int, LightPhase>>()
        var start = 0
        while (start < MINUTES_PER_DAY) {
            val phase = phaseAt(start)
            var end = start
            while (end < MINUTES_PER_DAY && phaseAt(end) == phase) end += STEP
            result += Triple(start, end.coerceAtMost(MINUTES_PER_DAY), phase)
            start = end
        }
        return result
    }

    /** 야간 조각 중 가장 긴 것의 길이. 이름을 한 번만 달기 위한 기준입니다. */
    private fun longestNightSpan(): Int = segments()
        .filter { it.third == LightPhase.NIGHT }
        .maxOfOrNull { it.second - it.first } ?: 0

    private fun LocalTime.formatted() = "%02d:%02d".format(hour, minute)

    /** 자정 기준 분 → 그때의 빛 구간. */
    private fun phaseAt(minute: Int): LightPhase =
        sunTimes.phaseAt(LocalTime.of(minute / 60 % 24, minute % 60))

    companion object {
        private const val MINUTES_PER_DAY = 24 * 60

        /** 호를 훑는 간격(분). 작을수록 경계가 정확하지만 그리는 횟수가 늡니다. */
        private const val STEP = 5

        /** 호가 솟는 높이 ÷ 카드 폭. 반원(0.5)보다 훨씬 납작해야 카드가 안 커집니다. */
        private const val ARC_RISE_RATIO = 0.17f
    }
}
