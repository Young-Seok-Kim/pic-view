package com.youngs.picview.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.youngs.picview.R
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.light.SunTimes
import java.time.LocalTime

/**
 * 하루의 빛을 가로 막대 하나로 보여 줍니다.
 *
 * 왼쪽이 새벽, 오른쪽이 밤. 구간마다 색이 다르고 지금 위치에 표식이 섭니다.
 * "오늘 언제 나가야 하는가"를 숫자를 읽지 않고 한눈에 파악하게 하는 게 목적입니다.
 *
 * 표시 범위는 [START_HOUR]~[END_HOUR]. 새벽 4시 이전과 밤 10시 이후는
 * 어차피 촬영 계획에 거의 안 쓰여서 잘라냅니다.
 */
class DayLightBarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.text_primary)
    }

    private val markerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        color = ContextCompat.getColor(context, R.color.bg_card)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_tertiary)
        textSize = dp(10f)
        textAlign = Paint.Align.CENTER
    }

    private val rect = RectF()

    var sunTimes: SunTimes = SunTimes.EMPTY
        set(value) {
            field = value
            invalidate()
        }

    /** 표식 위치. 기본은 현재 시각. */
    var markerTime: LocalTime = LocalTime.now()
        set(value) {
            field = value
            invalidate()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, dp(BAR_HEIGHT_DP + LABEL_AREA_DP).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val barTop = 0f
        val barBottom = dp(BAR_HEIGHT_DP)
        val radius = dp(BAR_HEIGHT_DP) / 2f
        val w = width.toFloat()

        // 구간을 1분 단위로 훑으면서 색이 바뀌는 지점마다 조각을 그립니다.
        // (구간 경계를 직접 계산하는 것보다 SunTimes 규칙과 어긋날 일이 없습니다)
        var segStartMin = START_MINUTE
        var segPhase = phaseAtMinute(segStartMin)

        canvas.save()
        // 막대 전체를 라운드로 클립해 양 끝을 둥글게 만듭니다.
        rect.set(0f, barTop, w, barBottom)
        val clipPath = android.graphics.Path().apply {
            addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW)
        }
        canvas.clipPath(clipPath)

        var minute = START_MINUTE
        while (minute <= END_MINUTE) {
            val phase = phaseAtMinute(minute)
            if (phase != segPhase || minute == END_MINUTE) {
                drawSegment(canvas, segStartMin, minute, segPhase, barTop, barBottom, w)
                segStartMin = minute
                segPhase = phase
            }
            minute += STEP_MINUTES
        }
        canvas.restore()

        drawMarker(canvas, w, barTop, barBottom)
        drawLabels(canvas, w, barBottom)
    }

    private fun drawSegment(
        canvas: Canvas,
        fromMin: Int,
        toMin: Int,
        phase: LightPhase,
        top: Float,
        bottom: Float,
        width: Float
    ) {
        segmentPaint.color = ContextCompat.getColor(context, phase.colorRes)
        rect.set(xFor(fromMin, width), top, xFor(toMin, width), bottom)
        canvas.drawRect(rect, segmentPaint)
    }

    private fun drawMarker(canvas: Canvas, width: Float, top: Float, bottom: Float) {
        val minute = markerTime.toSecondOfDay() / 60
        if (minute < START_MINUTE || minute > END_MINUTE) return

        val x = xFor(minute, width)
        val cy = (top + bottom) / 2f
        val r = dp(6f)
        canvas.drawCircle(x, cy, r, markerRingPaint)
        canvas.drawCircle(x, cy, r - dp(1.5f), markerPaint)
    }

    /** 일출·일몰 시각만 눈금으로 답니다. 숫자가 많으면 오히려 안 읽힙니다. */
    private fun drawLabels(canvas: Canvas, width: Float, barBottom: Float) {
        val y = barBottom + dp(14f)
        val sunrise = sunTimes.sunrise
        val sunset = sunTimes.sunset

        if (sunrise != null) {
            val minute = sunrise.toSecondOfDay() / 60
            if (minute in START_MINUTE..END_MINUTE) {
                canvas.drawText("일출 ${fmt(sunrise)}", xFor(minute, width), y, labelPaint)
            }
        }
        if (sunset != null) {
            val minute = sunset.toSecondOfDay() / 60
            if (minute in START_MINUTE..END_MINUTE) {
                canvas.drawText("일몰 ${fmt(sunset)}", xFor(minute, width), y, labelPaint)
            }
        }
    }

    private fun fmt(time: LocalTime) =
        "%02d:%02d".format(time.hour, time.minute)

    private fun phaseAtMinute(minute: Int): LightPhase =
        sunTimes.phaseAt(LocalTime.ofSecondOfDay((minute * 60).toLong()))

    private fun xFor(minute: Int, width: Float): Float {
        val ratio = (minute - START_MINUTE).toFloat() / (END_MINUTE - START_MINUTE)
        return ratio.coerceIn(0f, 1f) * width
    }

    companion object {
        private const val START_HOUR = 4
        private const val END_HOUR = 22
        private const val START_MINUTE = START_HOUR * 60
        private const val END_MINUTE = END_HOUR * 60

        /** 색 경계를 찾는 해상도. 5분이면 눈으로 구분 안 되는 수준입니다. */
        private const val STEP_MINUTES = 5

        private const val BAR_HEIGHT_DP = 16f
        private const val LABEL_AREA_DP = 20f
    }
}
