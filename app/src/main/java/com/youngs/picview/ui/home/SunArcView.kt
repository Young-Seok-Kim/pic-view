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
 * 하루의 빛을, 해가 정읍의 지형 위를 지나는 그림으로 그립니다.
 *
 * 몇 번 갈아엎었습니다. 반원은 세로로 높아 카드가 화면의 3분의 1을 먹었고,
 * 납작한 호는 짧지만 밋밋했습니다. 지금은 호를 다시 세우되 **호 아래에 땅을
 * 깔았습니다**. 그림이 되니 높이가 아깝지 않습니다.
 *
 * 땅은 장식이 아니라 방향입니다. 왼쪽은 해가 뜨는 붉은 흙과 떠오르는 해,
 * 오른쪽은 해가 지는 쪽의 푸른 산과 물, 달, 별입니다. 호의 색이 주황에서
 * 남색으로 넘어가는 것과 땅이 같은 방향을 가리켜서, 그림 어느 쪽을 봐도
 * 하루가 어디로 흐르는지 읽힙니다.
 *
 * 그림이지만 값은 전부 실데이터입니다. 일출·일몰 시각이 구간 경계를 정하고,
 * 지금 시각이 해의 위치를 정합니다. 이미지를 깔면 이 값들이 고정되므로
 * 전부 Canvas 로 그립니다.
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
    private val scenePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(1.6f)
    }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(14f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(10f)
        textAlign = Paint.Align.LEFT
    }

    private val arcRect = RectF()
    private val pillRect = RectF()
    private val scenePath = Path()

    private val textTertiary = ContextCompat.getColor(context, R.color.text_tertiary)
    private val cardColor = ContextCompat.getColor(context, R.color.bg_card)

    private val bodyFont: Typeface?

    init {
        val font = ResourcesCompat.getFont(context, R.font.pretendard)
        bodyFont = font
        timePaint.typeface = Typeface.create(font, Typeface.BOLD)
        labelPaint.typeface = font
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        // 호가 솟는 높이 + 시각 한 줄 + 격자 두 줄.
        val height = (width * ARC_RISE_RATIO + dp(88f)).toInt()
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val padH = dp(18f)
        val horizon = height - dp(76f)   // 땅이 끝나는 선. 호의 양 끝도 여기에 닿습니다.
        val halfWidth = (w - padH * 2) / 2f
        val rise = w * ARC_RISE_RATIO
        val cx = w / 2f

        arcRect.set(cx - halfWidth, horizon - rise, cx + halfWidth, horizon + rise)

        drawSky(canvas, horizon, cx, halfWidth, rise)
        drawGround(canvas, w, horizon)
        drawArc(canvas)
        drawEndCaps(canvas, horizon, cx, halfWidth)
        drawSunMarker(canvas, cx, horizon, halfWidth, rise)
        drawEndTimes(canvas, horizon, padH, w)
        drawPhaseLegend(canvas, horizon, w, padH)
    }

    // ─────────────────────── 하늘 ───────────────────────

    /** 구름 · 달 · 별. 호 뒤에 옅게 깔려 여백을 채웁니다. */
    private fun drawSky(canvas: Canvas, horizon: Float, cx: Float, halfWidth: Float, rise: Float) {
        scenePaint.color = ContextCompat.getColor(context, R.color.scene_cloud)
        cloud(canvas, cx - halfWidth * 0.14f, horizon - rise * 0.46f, dp(30f))
        cloud(canvas, cx + halfWidth * 0.40f, horizon - rise * 0.66f, dp(22f))

        // 달과 별은 해가 지는 오른쪽에 몰아 둡니다. 양쪽에 다 두면 방향이 사라집니다.
        drawMoon(canvas, cx + halfWidth * 0.80f, horizon - rise * 0.30f, dp(7f))
        star(canvas, cx + halfWidth * 0.63f, horizon - rise * 0.14f, dp(4f))
        star(canvas, cx + halfWidth * 0.93f, horizon - rise * 0.50f, dp(3f))
        star(canvas, cx + halfWidth * 0.71f, horizon - rise * 0.40f, dp(2.5f))
        star(canvas, cx - halfWidth * 0.88f, horizon - rise * 0.14f, dp(3.5f))
        star(canvas, cx - halfWidth * 0.76f, horizon - rise * 0.30f, dp(2.5f))
    }

    private fun cloud(canvas: Canvas, x: Float, y: Float, r: Float) {
        canvas.drawCircle(x, y, r * 0.60f, scenePaint)
        canvas.drawCircle(x + r * 0.55f, y + r * 0.12f, r * 0.45f, scenePaint)
        canvas.drawCircle(x - r * 0.55f, y + r * 0.16f, r * 0.38f, scenePaint)
        canvas.drawRect(x - r * 0.6f, y + r * 0.10f, x + r * 0.6f, y + r * 0.32f, scenePaint)
    }

    /**
     * 초승달.
     *
     * 원 하나를 그리고 카드 배경색 원으로 한쪽을 깎아 냅니다. 달이 하늘(카드
     * 배경) 위에만 놓이므로 이 방법으로 충분하고, 레이어를 따로 안 씁니다.
     */
    private fun drawMoon(canvas: Canvas, x: Float, y: Float, r: Float) {
        scenePaint.color = ContextCompat.getColor(context, R.color.scene_slate)
        canvas.drawCircle(x, y, r, scenePaint)
        scenePaint.color = cardColor
        canvas.drawCircle(x + r * 0.62f, y - r * 0.30f, r * 0.92f, scenePaint)
    }

    /** 네 갈래 별. 가운데가 잘록한 마름모라 뾰족하게 보입니다. */
    private fun star(canvas: Canvas, x: Float, y: Float, r: Float) {
        scenePaint.color = ContextCompat.getColor(context, R.color.scene_slate)
        scenePath.reset()
        scenePath.moveTo(x, y - r)
        scenePath.quadTo(x + r * 0.18f, y - r * 0.18f, x + r, y)
        scenePath.quadTo(x + r * 0.18f, y + r * 0.18f, x, y + r)
        scenePath.quadTo(x - r * 0.18f, y + r * 0.18f, x - r, y)
        scenePath.quadTo(x - r * 0.18f, y - r * 0.18f, x, y - r)
        scenePath.close()
        canvas.drawPath(scenePath, scenePaint)
    }

    // ─────────────────────── 땅 ───────────────────────

    /**
     * 언덕 · 물 · 나무.
     *
     * 뒤에서 앞으로 여러 겹입니다. 겹칠수록 앞이 진해져 거리가 생깁니다.
     * 왼쪽은 붉은 흙(해 뜨는 쪽), 오른쪽은 푸른 산과 물(해 지는 쪽)입니다.
     */
    private fun drawGround(canvas: Canvas, w: Float, horizon: Float) {
        val depth = dp(42f)

        // 1) 가장 뒤 — 옅은 모래빛 능선
        scenePaint.color = ContextCompat.getColor(context, R.color.scene_sand)
        scenePath.reset()
        scenePath.moveTo(0f, horizon)
        scenePath.lineTo(0f, horizon - depth * 0.62f)
        scenePath.quadTo(w * 0.18f, horizon - depth * 0.20f, w * 0.36f, horizon - depth * 0.52f)
        scenePath.quadTo(w * 0.54f, horizon - depth * 0.86f, w * 0.72f, horizon - depth * 0.30f)
        scenePath.quadTo(w * 0.86f, horizon - depth * 0.02f, w, horizon - depth * 0.40f)
        scenePath.lineTo(w, horizon)
        scenePath.close()
        canvas.drawPath(scenePath, scenePaint)

        // 2) 왼쪽 붉은 흙
        scenePaint.color = ContextCompat.getColor(context, R.color.scene_coral)
        scenePath.reset()
        scenePath.moveTo(0f, horizon)
        scenePath.lineTo(0f, horizon - depth * 0.50f)
        scenePath.quadTo(w * 0.10f, horizon - depth * 0.96f, w * 0.24f, horizon - depth * 0.32f)
        scenePath.quadTo(w * 0.30f, horizon - depth * 0.06f, w * 0.36f, horizon)
        scenePath.close()
        canvas.drawPath(scenePath, scenePaint)

        // 3) 가운데 들 — 옅은 카키에서 진한 올리브로
        scenePaint.color = ContextCompat.getColor(context, R.color.scene_khaki)
        scenePath.reset()
        scenePath.moveTo(w * 0.22f, horizon)
        scenePath.quadTo(w * 0.38f, horizon - depth * 0.70f, w * 0.56f, horizon - depth * 0.28f)
        scenePath.quadTo(w * 0.64f, horizon - depth * 0.10f, w * 0.70f, horizon)
        scenePath.close()
        canvas.drawPath(scenePath, scenePaint)

        scenePaint.color = ContextCompat.getColor(context, R.color.scene_olive)
        scenePath.reset()
        scenePath.moveTo(w * 0.40f, horizon)
        scenePath.quadTo(w * 0.52f, horizon - depth * 0.38f, w * 0.66f, horizon - depth * 0.16f)
        scenePath.quadTo(w * 0.72f, horizon - depth * 0.06f, w * 0.78f, horizon)
        scenePath.close()
        canvas.drawPath(scenePath, scenePaint)

        // 4) 오른쪽 푸른 산과 물
        scenePaint.color = ContextCompat.getColor(context, R.color.scene_slate)
        scenePath.reset()
        scenePath.moveTo(w * 0.62f, horizon)
        scenePath.quadTo(w * 0.78f, horizon - depth * 0.90f, w * 0.90f, horizon - depth * 0.38f)
        scenePath.quadTo(w * 0.96f, horizon - depth * 0.16f, w, horizon - depth * 0.24f)
        scenePath.lineTo(w, horizon)
        scenePath.close()
        canvas.drawPath(scenePath, scenePaint)

        scenePaint.color = ContextCompat.getColor(context, R.color.scene_water)
        scenePath.reset()
        scenePath.moveTo(w * 0.72f, horizon)
        scenePath.quadTo(w * 0.84f, horizon - depth * 0.34f, w, horizon - depth * 0.26f)
        scenePath.lineTo(w, horizon)
        scenePath.close()
        canvas.drawPath(scenePath, scenePaint)

        // 잔물결. 두 줄이면 물이라는 것이 읽히고, 더 넣으면 어수선해집니다.
        strokePaint.color = ContextCompat.getColor(context, R.color.scene_slate)
        strokePaint.strokeWidth = dp(1.2f)
        strokePaint.alpha = 170
        canvas.drawLine(w * 0.84f, horizon - depth * 0.16f, w * 0.92f, horizon - depth * 0.16f, strokePaint)
        canvas.drawLine(w * 0.80f, horizon - depth * 0.07f, w * 0.86f, horizon - depth * 0.07f, strokePaint)
        strokePaint.alpha = 255

        drawTree(canvas, w * 0.47f, horizon - depth * 0.28f, dp(12f))
        drawTree(canvas, w * 0.535f, horizon - depth * 0.22f, dp(9f))
        drawTree(canvas, w * 0.58f, horizon - depth * 0.16f, dp(7f))
        drawBush(canvas, w * 0.30f, horizon - depth * 0.02f, dp(10f))
        drawRisingSun(canvas, w * 0.12f, horizon - depth * 0.50f, dp(12f))
    }

    private fun drawTree(canvas: Canvas, x: Float, groundY: Float, size: Float) {
        scenePaint.color = ContextCompat.getColor(context, R.color.scene_tree)
        scenePath.reset()
        scenePath.moveTo(x, groundY - size * 1.7f)
        scenePath.quadTo(x + size * 0.62f, groundY - size * 0.75f, x, groundY - size * 0.22f)
        scenePath.quadTo(x - size * 0.62f, groundY - size * 0.75f, x, groundY - size * 1.7f)
        scenePath.close()
        canvas.drawPath(scenePath, scenePaint)

        strokePaint.color = ContextCompat.getColor(context, R.color.scene_tree)
        strokePaint.strokeWidth = dp(1.4f)
        canvas.drawLine(x, groundY - size * 0.5f, x, groundY, strokePaint)
    }

    private fun drawBush(canvas: Canvas, x: Float, groundY: Float, size: Float) {
        scenePaint.color = ContextCompat.getColor(context, R.color.scene_coral_deep)
        canvas.drawCircle(x, groundY - size * 0.55f, size * 0.62f, scenePaint)
        canvas.drawCircle(x + size * 0.70f, groundY - size * 0.36f, size * 0.46f, scenePaint)
        canvas.drawCircle(x - size * 0.70f, groundY - size * 0.32f, size * 0.42f, scenePaint)
        canvas.drawRect(x - size * 0.75f, groundY - size * 0.55f, x + size * 0.75f, groundY, scenePaint)
    }

    /**
     * 왼쪽 언덕 위로 떠오르는 해.
     *
     * 지금 해([drawSunMarker])와는 다른 것입니다. 이건 "여기가 해가 뜨는
     * 쪽"이라는 그림의 방향 표시라 언제나 왼쪽에 고정입니다.
     */
    private fun drawRisingSun(canvas: Canvas, x: Float, groundY: Float, r: Float) {
        val color = ContextCompat.getColor(context, R.color.light_sunrise)
        strokePaint.color = color
        strokePaint.strokeWidth = dp(1.6f)
        for (i in 0..4) {
            val a = PI + i * PI / 4
            canvas.drawLine(
                x + (r + dp(3f)) * cos(a).toFloat(), groundY + (r + dp(3f)) * sin(a).toFloat(),
                x + (r + dp(7f)) * cos(a).toFloat(), groundY + (r + dp(7f)) * sin(a).toFloat(),
                strokePaint
            )
        }
        scenePaint.color = color
        canvas.drawArc(x - r, groundY - r, x + r, groundY + r, 180f, 180f, true, scenePaint)
    }

    // ─────────────────────── 호 ───────────────────────

    /**
     * 빛 구간별로 색을 나눠 호를 그립니다.
     *
     * 왼쪽 끝이 자정, 오른쪽 끝이 다음 자정입니다. 5분 간격으로 훑으며 구간이
     * 바뀌는 곳에서 색을 갈아 끼웁니다. 골든아워·블루아워는 20~30분짜리라
     * 아주 얇은 띠로 나오는데, 그 얇음 자체가 "놓치면 끝"을 말해 주므로
     * 억지로 넓히지 않습니다.
     *
     * 이어지는 구간이 실선으로 붙어야 해서 끝을 둥글리지 않습니다. ROUND 로
     * 두면 얇은 구간이 옆 구간 위로 번져 색이 섞입니다.
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

    /** 호의 양 끝을 점으로 맺습니다. 선이 땅에 그냥 박히면 잘린 것처럼 보입니다. */
    private fun drawEndCaps(canvas: Canvas, horizon: Float, cx: Float, halfWidth: Float) {
        scenePaint.color = ContextCompat.getColor(context, R.color.light_night)
        canvas.drawCircle(cx - halfWidth, horizon, dp(4f), scenePaint)
        canvas.drawCircle(cx + halfWidth, horizon, dp(4f), scenePaint)
    }

    /** 지금 해의 위치. 빛 구간 색으로 칠하고 짧은 광선을 답니다. */
    private fun drawSunMarker(canvas: Canvas, cx: Float, horizon: Float, halfWidth: Float, rise: Float) {
        val minute = nowTime.toSecondOfDay() / 60
        val angle = PI * (1f + minute / MINUTES_PER_DAY.toDouble())
        val x = cx + halfWidth * cos(angle).toFloat()
        val y = horizon + rise * sin(angle).toFloat()

        val color = ContextCompat.getColor(context, phaseAt(minute).colorRes)

        strokePaint.color = color
        strokePaint.strokeWidth = dp(1.6f)
        strokePaint.alpha = 150
        for (i in 0 until 8) {
            val a = i * PI / 4
            canvas.drawLine(
                x + dp(9f) * cos(a).toFloat(), y + dp(9f) * sin(a).toFloat(),
                x + dp(13f) * cos(a).toFloat(), y + dp(13f) * sin(a).toFloat(),
                strokePaint
            )
        }
        strokePaint.alpha = 255

        // 흰 테두리를 둘러 호·언덕 위에서도 해가 또렷하게 보이게 합니다.
        scenePaint.color = cardColor
        canvas.drawCircle(x, y, dp(8f), scenePaint)
        scenePaint.color = color
        canvas.drawCircle(x, y, dp(6f), scenePaint)
    }

    // ─────────────────────── 글자 ───────────────────────

    /** 땅 바로 아래 양 끝에 일출·일몰 시각. */
    private fun drawEndTimes(canvas: Canvas, horizon: Float, padH: Float, w: Float) {
        val y = horizon + dp(20f)

        timePaint.color = ContextCompat.getColor(context, R.color.light_sunrise)
        timePaint.textAlign = Paint.Align.LEFT
        canvas.drawText(
            context.getString(R.string.sun_arc_sunrise, sunTimes.sunrise?.formatted() ?: "—"),
            padH, y, timePaint
        )

        timePaint.color = ContextCompat.getColor(context, R.color.light_night)
        timePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            context.getString(R.string.sun_arc_sunset, sunTimes.sunset?.formatted() ?: "—"),
            w - padH, y, timePaint
        )
    }

    /**
     * 구간 이름과 시각을 4열 2행 격자로 답니다.
     *
     * 곡선을 따라 각자 자기 띠 아래에 놓아 봤는데, 골든아워·블루아워가
     * 30분짜리라 왼쪽 끝에 넷이 몰려 어긋나고 자정 쪽 이름이 잘렸습니다.
     * 격자는 날짜가 바뀌어 구간 길이가 달라져도 자리가 흔들리지 않습니다.
     * 위치로 잇던 이름과 띠는 **색 점**으로 잇습니다.
     *
     * 지금 구간에는 알약을 깔아 둡니다. 여덟 개를 훑어 내려가며 "지금 어디"를
     * 찾게 하지 않고 눈에 먼저 걸리게 하려는 것입니다.
     */
    private fun drawPhaseLegend(canvas: Canvas, horizon: Float, w: Float, padH: Float) {
        val starts = phaseStarts()
        val cellWidth = (w - padH * 2) / COLUMNS
        val current = phaseAt(nowTime.toSecondOfDay() / 60)

        LEGEND_ORDER.forEachIndexed { index, phase ->
            val left = padH + (index % COLUMNS) * cellWidth
            val y = horizon + dp(46f) + (index / COLUMNS) * dp(18f)
            val time = timeTextOf(starts[phase] ?: 0, phase)
            val isNow = phase == current

            labelPaint.typeface = if (isNow) Typeface.create(bodyFont, Typeface.BOLD) else bodyFont

            if (isNow) {
                val end = left + dp(16f) + labelPaint.measureText(phase.shortLabel) +
                    labelPaint.measureText(time)
                pillRect.set(left - dp(6f), y - dp(11f), end, y + dp(5f))
                // 알약은 그 구간의 색입니다. 대표색으로 고정하면 야간인데
                // 알약만 붉어서 히어로 카드와 어긋납니다.
                scenePaint.color = ContextCompat.getColor(context, phase.heroColorRes)
                canvas.drawRoundRect(pillRect, dp(8f), dp(8f), scenePaint)
            }

            scenePaint.color = ContextCompat.getColor(context, phase.colorRes)
            canvas.drawCircle(left + dp(3f), y - dp(3f), dp(3f), scenePaint)

            labelPaint.color = if (isNow) {
                ContextCompat.getColor(context, R.color.white)
            } else textTertiary
            canvas.drawText(phase.shortLabel, left + dp(11f), y, labelPaint)

            // 시각은 그 구간 색으로. 이름·시각·띠가 한 덩어리로 읽힙니다.
            // 알약 위에서는 색 대비가 무너지므로 알약 글자색을 그대로 씁니다.
            if (!isNow) labelPaint.color = ContextCompat.getColor(context, phase.colorRes)
            canvas.drawText(
                time,
                left + dp(11f) + labelPaint.measureText(phase.shortLabel) + dp(5f),
                y, labelPaint
            )
        }
        labelPaint.typeface = bodyFont
    }

    /**
     * 구간 이름 옆에 붙는 시각.
     *
     * 대부분은 그 구간이 시작하는 시각입니다. 알고 싶은 것이 "몇 시부터 이
     * 빛인가" 라서입니다.
     *
     * 골든아워만 다릅니다. 일출 골든아워는 일출 30분 전에 시작하므로 시작
     * 시각을 적으면 05:21 이 되는데, "일출"이라는 낱말에서 기대하는 숫자는
     * 해가 뜨는 05:51 입니다. 두 값이 어긋나 보이면 그림 전체를 못 믿게
     * 되므로 이 둘만 실제 일출·일몰 시각을 적습니다.
     */
    private fun timeTextOf(startMinute: Int, phase: LightPhase): String = when (phase) {
        LightPhase.SUNRISE -> sunTimes.sunrise?.formatted()
        LightPhase.SUNSET -> sunTimes.sunset?.formatted()
        else -> null
    } ?: "%02d:%02d".format(startMinute / 60 % 24, startMinute % 60)

    /**
     * 구간별 시작 분.
     *
     * 야간만 자정에 잘려 조각이 둘입니다. 저녁 뒤에 오는 조각을 씁니다.
     * 00:00 을 적으면 "오늘 밤 몇 시부터 야간인가"에 답하지 못합니다.
     */
    private fun phaseStarts(): Map<LightPhase, Int> {
        val segments = segments()
        return LEGEND_ORDER.mapNotNull { phase ->
            val candidates = segments.filter { it.third == phase }
            val chosen = if (phase == LightPhase.NIGHT) candidates.maxByOrNull { it.first }
            else candidates.minByOrNull { it.first }
            chosen?.let { phase to it.first }
        }.toMap()
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

    private fun LocalTime.formatted() = "%02d:%02d".format(hour, minute)

    /** 자정 기준 분 → 그때의 빛 구간. */
    private fun phaseAt(minute: Int): LightPhase =
        sunTimes.phaseAt(LocalTime.of(minute / 60 % 24, minute % 60))

    companion object {
        private const val MINUTES_PER_DAY = 24 * 60

        /** 호를 훑는 간격(분). 작을수록 경계가 정확하지만 그리는 횟수가 늡니다. */
        private const val STEP = 5

        /**
         * 호가 솟는 높이 ÷ 카드 폭.
         *
         * 반원(0.5)이면 카드가 너무 커지고, 아주 납작하면(0.17) 아래에 땅을
         * 깔 여유가 없습니다. 그 사이입니다.
         */
        private const val ARC_RISE_RATIO = 0.30f

        /** 격자 열 수. 여덟 구간이 4×2 로 딱 떨어집니다. */
        private const val COLUMNS = 4

        /** 격자에 놓는 순서. 하루가 흐르는 순서 그대로입니다. */
        private val LEGEND_ORDER = listOf(
            LightPhase.BLUE_DAWN, LightPhase.SUNRISE, LightPhase.MORNING, LightPhase.MIDDAY,
            LightPhase.AFTERNOON, LightPhase.SUNSET, LightPhase.BLUE_DUSK, LightPhase.NIGHT
        )
    }
}
