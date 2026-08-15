package com.youngs.picview.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.youngs.picview.R
import com.youngs.picview.domain.weather.HourlyTemp
import java.time.LocalTime

/**
 * 오늘의 시간대별 기온 곡선.
 *
 * 실황은 지금 한 시점만 줍니다. 24℃ 라는 숫자만으로는 "지금 나가도
 * 되나"까지는 답해도 **"몇 시에 나가면 좋나"** 에는 답하지 못합니다.
 * 출사는 시각을 고르는 일이라 그쪽이 더 자주 필요합니다.
 *
 * 그래서 단기예보(getVilageFcst)의 시간대별 기온을 곡선으로 깝니다.
 * 곡선이면 최고·최저가 몇 시인지가 숫자를 읽지 않고도 보입니다.
 *
 * 값이 없으면 아무것도 그리지 않고 높이도 0 이 됩니다. 예보를 못 받았을 때
 * 빈 상자가 남아 있는 것보다 아예 사라지는 편이 낫습니다.
 */
class TempCurveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var temps: List<HourlyTemp> = emptyList()
        set(value) {
            field = value.sortedBy { it.hour }
            requestLayout()
            invalidate()
        }

    var nowHour: Int = LocalTime.now().hour
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = dp(2f)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(10f)
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(11f)
        textAlign = Paint.Align.CENTER
    }

    private val curvePath = Path()
    private val fillPath = Path()

    private val lineColor = ContextCompat.getColor(context, R.color.maple_500)
    private val textTertiary = ContextCompat.getColor(context, R.color.text_tertiary)
    private val cardColor = ContextCompat.getColor(context, R.color.bg_card)

    init {
        val font = ResourcesCompat.getFont(context, R.font.pretendard)
        labelPaint.typeface = font
        valuePaint.typeface = Typeface.create(font, Typeface.BOLD)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        // 곡선 두 점 이하로는 곡선이 아닙니다.
        val height = if (temps.size < 3) 0 else dp(92f).toInt()
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        if (temps.size < 3) return

        val padH = dp(10f)
        val top = dp(20f)          // 값 글자가 올라앉을 자리
        // 최저값과 시각 글자가 아래위로 나란히 놓입니다. 둘 다 들어갈 만큼
        // 띄워야 합니다. 좁게 잡았더니 "23℃" 와 "06시" 가 겹쳤습니다.
        val bottom = height - dp(34f)
        val usable = width - padH * 2

        val min = temps.minOf { it.tempC }
        val max = temps.maxOf { it.tempC }
        // 하루 종일 기온이 같으면 0 으로 나눕니다. 실제로 겨울에 나옵니다.
        val span = (max - min).coerceAtLeast(1.0)

        fun xOf(hour: Int) = padH + usable * (hour - temps.first().hour) /
            (temps.last().hour - temps.first().hour).coerceAtLeast(1).toFloat()

        fun yOf(temp: Double) = bottom - ((temp - min) / span * (bottom - top)).toFloat()

        buildCurve(::xOf, ::yOf)

        // 곡선 아래를 옅게 채워야 선이 허공에 뜨지 않습니다.
        fillPaint.color = lineColor
        fillPaint.alpha = 26
        canvas.drawPath(fillPath, fillPaint)
        fillPaint.alpha = 255

        linePaint.color = lineColor
        canvas.drawPath(curvePath, linePaint)

        drawExtremes(canvas, min, max, ::xOf, ::yOf)
        drawHourLabels(canvas, ::xOf, bottom)
        drawNow(canvas, ::xOf, ::yOf)
    }

    /**
     * 점을 부드럽게 잇습니다.
     *
     * 직선으로 이으면 세 시간 간격의 꺾임이 그대로 드러나 기온이 계단처럼
     * 보입니다. 이웃한 두 점의 중간을 지나는 2차 곡선으로 이으면 실제
     * 기온 변화에 가까운 모양이 됩니다.
     */
    private fun buildCurve(xOf: (Int) -> Float, yOf: (Double) -> Float) {
        curvePath.reset()
        fillPath.reset()

        var previousX = xOf(temps.first().hour)
        var previousY = yOf(temps.first().tempC)
        curvePath.moveTo(previousX, previousY)
        fillPath.moveTo(previousX, height - dp(34f))
        fillPath.lineTo(previousX, previousY)

        for (i in 1 until temps.size) {
            val x = xOf(temps[i].hour)
            val y = yOf(temps[i].tempC)
            val midX = (previousX + x) / 2f
            val midY = (previousY + y) / 2f
            curvePath.quadTo(previousX, previousY, midX, midY)
            fillPath.quadTo(previousX, previousY, midX, midY)
            previousX = x
            previousY = y
        }
        curvePath.lineTo(previousX, previousY)
        fillPath.lineTo(previousX, previousY)
        fillPath.lineTo(previousX, height - dp(34f))
        fillPath.close()
    }

    /**
     * 최고·최저만 값을 적습니다.
     *
     * 스물네 시각에 전부 숫자를 붙이면 곡선이 안 보입니다. 곡선에서 눈이
     * 찾는 건 "언제 가장 덥고 언제 가장 선선한가" 두 곳뿐입니다.
     */
    private fun drawExtremes(
        canvas: Canvas, min: Double, max: Double,
        xOf: (Int) -> Float, yOf: (Double) -> Float
    ) {
        val hottest = temps.maxByOrNull { it.tempC } ?: return
        val coolest = temps.minByOrNull { it.tempC } ?: return

        dotPaint.color = lineColor
        valuePaint.color = lineColor
        markPoint(canvas, xOf(hottest.hour), yOf(max), "${max.toInt()}℃", above = true)

        dotPaint.color = textTertiary
        valuePaint.color = textTertiary
        markPoint(canvas, xOf(coolest.hour), yOf(min), "${min.toInt()}℃", above = false)
    }

    private fun markPoint(canvas: Canvas, x: Float, y: Float, text: String, above: Boolean) {
        canvas.drawCircle(x, y, dp(3f), dotPaint)
        val clampedX = x.coerceIn(dp(16f), width - dp(16f))
        canvas.drawText(text, clampedX, if (above) y - dp(8f) else y + dp(14f), valuePaint)
    }

    /** 여섯 시간마다 시각. 촘촘히 넣으면 곡선보다 눈금이 먼저 보입니다. */
    private fun drawHourLabels(canvas: Canvas, xOf: (Int) -> Float, bottom: Float) {
        labelPaint.color = textTertiary
        for (hour in temps.map { it.hour }) {
            if (hour % 6 != 0) continue
            canvas.drawText("%02d시".format(hour), xOf(hour), bottom + dp(28f), labelPaint)
        }
    }

    /** 지금 시각. 곡선 위 어디쯤인지가 보여야 예보가 내 얘기가 됩니다. */
    private fun drawNow(canvas: Canvas, xOf: (Int) -> Float, yOf: (Double) -> Float) {
        val now = temps.firstOrNull { it.hour == nowHour } ?: return
        val x = xOf(now.hour)
        val y = yOf(now.tempC)

        // 카드색 테두리를 둘러 곡선 위에서도 점이 또렷하게 보이게 합니다.
        dotPaint.color = cardColor
        canvas.drawCircle(x, y, dp(6.5f), dotPaint)
        dotPaint.color = lineColor
        canvas.drawCircle(x, y, dp(4.5f), dotPaint)
    }
}
