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
 * 하루의 빛을 해가 지나는 완만한 호로 그립니다.
 *
 * 처음에는 자정~자정을 반원에 폈습니다. 하루 전체가 보이는 대신 세로로
 * 높아서 카드가 화면의 3분의 1을 먹었고, 정작 사람이 궁금해하는 "오늘 몇
 * 시부터 몇 시까지 빛이 있나"는 양 끝 글자로만 읽혔습니다.
 *
 * 지금은 **일출에서 일몰까지**를 낮고 넓은 호로 폅니다. 호의 양 끝이 곧
 * 일출·일몰이라 시각과 그림이 같은 것을 가리키고, 해의 위치가 낮이 얼마나
 * 남았는지를 그대로 보여 줍니다. 밤은 위쪽 히어로 카드가 이미 말하고
 * 있으므로 여기서 또 그리지 않습니다.
 *
 * 모든 값이 실데이터입니다. 일출·일몰 시각으로 호의 양 끝을 잡고, 지금
 * 시각으로 해의 위치를 정하고, 빛 구간마다 호의 색이 바뀝니다.
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
        strokeWidth = dp(4f)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(1.6f)
    }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(15f)
        textAlign = Paint.Align.CENTER
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(11f)
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
        // 호의 높이(폭의 0.17배)에 아래 시각 두 줄을 더한 높이.
        val height = (width * ARC_RISE_RATIO + dp(72f)).toInt()
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val padH = dp(34f)              // 양 끝 글자가 잘리지 않을 만큼
        val baseY = height - dp(58f)    // 호의 양 끝이 놓이는 선
        val halfWidth = (w - padH * 2) / 2f
        val rise = w * ARC_RISE_RATIO   // 호가 솟는 높이
        val cx = w / 2f

        // 납작한 호는 원이 아니라 타원입니다. 가로 반지름은 그대로 두고
        // 세로 반지름만 줄여야 폭을 유지한 채 높이만 낮아집니다.
        arcRect.set(cx - halfWidth, baseY - rise, cx + halfWidth, baseY + rise)

        drawFill(canvas, baseY, cx, halfWidth, rise)
        drawArc(canvas)
        drawSun(canvas, cx, baseY, halfWidth, rise)
        drawTimes(canvas, baseY, cx, halfWidth)
    }

    /** 호 아래를 옅게 채웁니다. 선만 있으면 허공에 떠 보입니다. */
    private fun drawFill(canvas: Canvas, baseY: Float, cx: Float, halfWidth: Float, rise: Float) {
        fillPaint.color = ContextCompat.getColor(context, R.color.sun_arc_fill)
        fillPath.reset()
        fillPath.moveTo(cx - halfWidth, baseY)
        fillPath.arcTo(arcRect, 180f, 180f, false)
        fillPath.lineTo(cx + halfWidth, baseY + dp(10f))
        fillPath.lineTo(cx - halfWidth, baseY + dp(10f))
        fillPath.close()
        canvas.drawPath(fillPath, fillPaint)
    }

    /**
     * 빛 구간별로 색을 나눠 호를 그립니다.
     *
     * 호의 왼쪽 끝이 일출, 오른쪽 끝이 일몰입니다. 그 사이를 5분 간격으로
     * 훑으며 구간이 바뀌는 곳에서 색을 갈아 끼웁니다. 골든아워가 양 끝에
     * 주황으로, 한낮이 꼭대기에 밝게 앉는 모양이 자연히 나옵니다.
     */
    private fun drawArc(canvas: Canvas) {
        val span = daylightSpan() ?: run {
            // 일출·일몰을 못 받았으면 회색 호 하나로 둡니다. 빈 화면보다 낫습니다.
            arcPaint.color = ContextCompat.getColor(context, R.color.card_stroke)
            canvas.drawArc(arcRect, 180f, 180f, false, arcPaint)
            return
        }
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
                false,
                arcPaint
            )
            offset = end
        }
    }

    /**
     * 지금 해의 위치.
     *
     * 낮이면 호 위를 지나고, 해가 뜨기 전이거나 진 뒤면 가까운 쪽 끝에
     * 붙습니다. 밤에도 해를 아예 안 그리면 그림이 미완성으로 보입니다.
     */
    private fun drawSun(canvas: Canvas, cx: Float, baseY: Float, halfWidth: Float, rise: Float) {
        val span = daylightSpan() ?: return
        val (start, total) = span

        val minute = nowTime.toSecondOfDay() / 60
        val progress = ((minute - start) / total.toFloat()).coerceIn(0f, 1f)
        val isDaylight = minute in start..(start + total)

        val angle = PI * (1f + progress)
        val x = cx + halfWidth * cos(angle).toFloat()
        val y = baseY + rise * sin(angle).toFloat()

        val color = ContextCompat.getColor(context, phaseAt(minute).colorRes)

        rayPaint.color = color
        rayPaint.alpha = if (isDaylight) 150 else 60
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
        sunPaint.alpha = if (isDaylight) 255 else 90
        canvas.drawCircle(x, y, dp(6f), sunPaint)
        sunPaint.alpha = 255
    }

    /**
     * 양 끝에 일출·일몰을 세로로 답니다.
     *
     * 이름 위에 시각을 얹지 않고 이름을 위에 둡니다. 눈이 먼저 닿는 자리에
     * "무엇"이 오고 그 아래에 "몇 시"가 오는 편이 읽는 순서와 맞습니다.
     */
    private fun drawTimes(canvas: Canvas, baseY: Float, cx: Float, halfWidth: Float) {
        // 해가 끝에 붙는 시간대(일출 직전·일몰 직후)에는 광선이 baseY 아래
        // 13dp 까지 뻗습니다. 이름을 그보다 위에 두면 글자를 덮습니다.
        val labelY = baseY + dp(28f)
        val timeY = labelY + dp(20f)

        labelPaint.color = textTertiary
        canvas.drawText(context.getString(R.string.sun_arc_sunrise_label), cx - halfWidth, labelY, labelPaint)
        canvas.drawText(context.getString(R.string.sun_arc_sunset_label), cx + halfWidth, labelY, labelPaint)

        timePaint.color = ContextCompat.getColor(context, R.color.light_sunrise)
        canvas.drawText(sunTimes.sunrise?.formatted() ?: "—", cx - halfWidth, timeY, timePaint)

        timePaint.color = ContextCompat.getColor(context, R.color.light_sunset)
        canvas.drawText(sunTimes.sunset?.formatted() ?: "—", cx + halfWidth, timeY, timePaint)
    }

    /**
     * 낮의 시작(자정 기준 분)과 길이.
     *
     * 둘 중 하나라도 없거나 순서가 뒤집혀 있으면 그릴 수 없으므로 null 입니다.
     */
    private fun daylightSpan(): Pair<Int, Int>? {
        val sunrise = sunTimes.sunrise ?: return null
        val sunset = sunTimes.sunset ?: return null
        val start = sunrise.toSecondOfDay() / 60
        val total = sunset.toSecondOfDay() / 60 - start
        return if (total > 0) start to total else null
    }

    private fun LocalTime.formatted() = "%02d:%02d".format(hour, minute)

    /** 자정 기준 분 → 그때의 빛 구간. */
    private fun phaseAt(minute: Int): LightPhase =
        sunTimes.phaseAt(LocalTime.of(minute / 60 % 24, minute % 60))

    companion object {
        /** 호를 훑는 간격(분). 작을수록 경계가 정확하지만 그리는 횟수가 늡니다. */
        private const val STEP = 5

        /** 호가 솟는 높이 ÷ 카드 폭. 반원(0.5)보다 훨씬 납작해야 카드가 안 커집니다. */
        private const val ARC_RISE_RATIO = 0.17f
    }
}
