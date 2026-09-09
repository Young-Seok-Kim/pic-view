package com.youngs.picview.ui.guide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.annotation.DrawableRes
import com.youngs.picview.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 카메라 미리보기 위에 구도 가이드를 그립니다.
 *
 * 격자만 긋는 것이 아니라 **어디에 서고 무엇을 어디에 둘지**를 그립니다 —
 * 사람 자리, 수면선, 소실점, 비워 둘 자리. 포즈 예시("?")가 그림으로
 * 보여 주던 것을 화면 위에 직접 얹은 셈입니다. 카메라 격자처럼 상단
 * 버튼으로 켜고 끕니다. 구도 자체는 아래에서 고른 피사체·포즈가 정합니다.
 *
 * 그리는 법의 원칙 셋.
 *  - 선은 가늘고 흰색, 자리는 따뜻한 색. 흰 것은 "기준", 따뜻한 것은 "여기".
 *  - 글은 전부 어두운 알약 안에. 사진 위에 맨 글자를 얹으면 밝은 하늘에서
 *    사라지고 어두운 숲에서는 떠 보입니다.
 *  - 비울 자리는 옅게 채웁니다. 점선 상자만으로는 "여기를 비워라"가 아니라
 *    "여기에 넣어라"로 읽힙니다.
 */
class GuideOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /**
     * 구도 종류.
     *
     * @param id 시선 가이드 화면의 구도 식별자와 같습니다.
     * @param label 화면 왼쪽 위에 보이는 이름.
     */
    enum class GuideType(val id: String, val label: String) {
        /** 삼분할. 풍경·자연에 쓰며 교차점을 표시합니다. */
        THIRDS("thirds", "삼분할"),

        /** 좌우 대칭. 건축물·문화재에 씁니다. */
        SYMMETRY("symmetry", "대칭"),

        /** 중앙 원. 음식이나 근접 촬영에 씁니다. */
        CENTER("center", "중앙"),

        SILHOUETTE("silhouette", "실루엣"),
        REFLECTION("reflection", "반사"),
        LEADING("leading", "리딩라인"),
        FRAME("frame", "프레임 인 프레임"),
        TOP("top", "탑뷰"),
        SPACE("space", "여백"),
        MOTION("motion", "움직임"),
        LAYER("layer", "레이어드"),
        PATTERN("pattern", "반복 패턴");

        companion object {
            fun byId(id: String?): GuideType? = entries.firstOrNull { it.id == id }
        }
    }

    /**
     * 자리에 놓는 사람 그림. 포즈에 맞는 실루엣이라 "여기에 이렇게 서라"가
     * 한 번에 읽힙니다. 그림은 따로 그려 받은 단색 PNG 입니다.
     */
    enum class Figure(@DrawableRes val res: Int) {
        WALK(R.drawable.guide_fig_walk),
        SIT(R.drawable.guide_fig_sit),
        ARMS_UP(R.drawable.guide_fig_arms_up),
        JUMP(R.drawable.guide_fig_jump),
        FRAME(R.drawable.guide_fig_frame),
        TOP(R.drawable.guide_fig_top)
    }

    var guideType: GuideType = GuideType.THIRDS
        set(value) {
            field = value
            invalidate()
        }

    /** 고른 포즈의 그림. null 이면 구도마다 어울리는 기본 그림을 씁니다. */
    var figure: Figure? = null
        set(value) {
            field = value
            invalidate()
        }

    /**
     * 위아래로 가려지는 만큼. 상단 바와 하단 컨트롤이 미리보기를 덮고
     * 있어서, 그 아래에 그린 안내는 보이지 않습니다. 그림은 이 안쪽에
     * 맞춥니다 — 사람이 실제로 보며 구도를 잡는 영역이 거기입니다.
     */
    fun setInsets(top: Int, bottom: Int) {
        insetTop = top
        insetBottom = bottom
        invalidate()
    }

    private var insetTop = 0
    private var insetBottom = 0

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    // ───────────────────────── 색과 붓 ─────────────────────────

    /** 밝은 배경에서도 선이 보이도록 아래에 깔아 주는 어두운 선. */
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(60, 0, 0, 0)
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(185, 255, 255, 255)
    }

    private val warmLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.argb(235, 255, 205, 112)
    }

    private val warmFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(205, 255, 205, 112)
    }

    private val whiteFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(240, 255, 255, 255)
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val zonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(28, 255, 255, 255)
    }

    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(150, 20, 14, 10)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(11.5f)
        isFakeBoldText = true
    }

    private val dash = DashPathEffect(floatArrayOf(dp(7f), dp(6f)), 0f)
    private val path = Path()
    private val rectF = RectF()

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bitmaps = mutableMapOf<Int, Bitmap>()

    private fun bitmap(@DrawableRes res: Int): Bitmap =
        bitmaps.getOrPut(res) { BitmapFactory.decodeResource(resources, res) }

    /**
     * 그림 한 장을 (cx, cy) 가운데에 [height] 높이로. 가로는 비율대로.
     * 뒤에 옅은 그림자를 한 번 깔아 밝은 하늘에서도 윤곽이 남게 합니다.
     */
    private fun icon(canvas: Canvas, @DrawableRes res: Int, cx: Float, cy: Float, height: Float, alpha: Int = 235) {
        val bmp = bitmap(res)
        val width = height * bmp.width / bmp.height
        rectF.set(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f)
        bitmapPaint.alpha = alpha
        canvas.drawBitmap(bmp, null, rectF, bitmapPaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = (height - insetTop - insetBottom).toFloat()
        if (h <= 0f) return
        canvas.save()
        canvas.translate(0f, insetTop.toFloat())

        when (guideType) {
            GuideType.THIRDS -> drawThirds(canvas, w, h)
            GuideType.SYMMETRY -> drawSymmetry(canvas, w, h)
            GuideType.CENTER -> drawCenter(canvas, w, h)
            GuideType.SILHOUETTE -> drawSilhouette(canvas, w, h)
            GuideType.REFLECTION -> drawReflection(canvas, w, h)
            GuideType.LEADING -> drawLeading(canvas, w, h)
            GuideType.FRAME -> drawFrame(canvas, w, h)
            GuideType.TOP -> drawTop(canvas, w, h)
            GuideType.SPACE -> drawSpace(canvas, w, h)
            GuideType.MOTION -> drawMotion(canvas, w, h)
            GuideType.LAYER -> drawLayer(canvas, w, h)
            GuideType.PATTERN -> drawPattern(canvas, w, h)
        }

        // 왼쪽 위에 구도 이름. 어떤 구도가 그려진 건지 격자만 보고는 모릅니다.
        pill(canvas, "구도 · ${guideType.label}", dp(12f), dp(10f), Align.LEFT, warmDot = true)
        canvas.restore()
    }

    // ───────────────────────── 그리기 부품 ─────────────────────────

    private enum class Align { LEFT, CENTER, RIGHT }

    /** 선 하나를 어두운 선 + 흰 선 두 번 그립니다. */
    private fun line(
        canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float,
        width: Float = dp(1f), dashed: Boolean = false, warmColor: Boolean = false
    ) {
        val top = if (warmColor) warmLinePaint else linePaint
        shadowPaint.pathEffect = if (dashed) dash else null
        top.pathEffect = if (dashed) dash else null
        shadowPaint.strokeWidth = width + dp(1.5f)
        canvas.drawLine(x1, y1, x2, y2, shadowPaint)
        top.strokeWidth = width
        canvas.drawLine(x1, y1, x2, y2, top)
        shadowPaint.pathEffect = null
        top.pathEffect = null
    }

    /** 둥근 모서리 사각형. 비울 자리·틀·배경 상자에 씁니다. */
    private fun box(
        canvas: Canvas, l: Float, t: Float, r: Float, b: Float,
        width: Float = dp(1.2f), dashed: Boolean = false, warmColor: Boolean = false, fill: Boolean = false
    ) {
        rectF.set(l, t, r, b)
        val radius = dp(10f)
        if (fill) canvas.drawRoundRect(rectF, radius, radius, zonePaint)
        val top = if (warmColor) warmLinePaint else linePaint
        shadowPaint.pathEffect = if (dashed) dash else null
        top.pathEffect = if (dashed) dash else null
        shadowPaint.strokeWidth = width + dp(1.5f)
        canvas.drawRoundRect(rectF, radius, radius, shadowPaint)
        top.strokeWidth = width
        canvas.drawRoundRect(rectF, radius, radius, top)
        shadowPaint.pathEffect = null
        top.pathEffect = null
    }

    private fun circle(canvas: Canvas, cx: Float, cy: Float, r: Float, width: Float, warmColor: Boolean = false) {
        val top = if (warmColor) warmLinePaint else linePaint
        shadowPaint.strokeWidth = width + dp(1.5f)
        canvas.drawCircle(cx, cy, r, shadowPaint)
        top.strokeWidth = width
        canvas.drawCircle(cx, cy, r, top)
    }

    /** 부드러운 빛무리. 자리 표시 뒤에 깔아 "여기"가 은은히 떠오르게 합니다. */
    private fun glow(canvas: Canvas, cx: Float, cy: Float, r: Float, alpha: Int = 90) {
        glowPaint.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(Color.argb(alpha, 255, 205, 112), Color.argb(0, 255, 205, 112)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, glowPaint)
        glowPaint.shader = null
    }

    /** 삼분할 교차점 같은 기준점. 흰 점에 옅은 테. */
    private fun dot(canvas: Canvas, cx: Float, cy: Float, warmColor: Boolean = false) {
        val r = dp(3.5f)
        shadowPaint.strokeWidth = dp(2f)
        canvas.drawCircle(cx, cy, r, shadowPaint)
        canvas.drawCircle(cx, cy, r, if (warmColor) warmFillPaint else whiteFillPaint)
    }

    /**
     * 글은 어두운 알약 안에. (x, y) 는 알약의 위쪽 모서리 기준이고,
     * [align] 에 따라 x 가 왼쪽·가운데·오른쪽이 됩니다.
     */
    private fun pill(
        canvas: Canvas, text: String, x: Float, y: Float,
        align: Align = Align.CENTER, warmDot: Boolean = false
    ) {
        val padX = dp(9f)
        val padY = dp(5f)
        val dotSpace = if (warmDot) dp(11f) else 0f
        val textW = textPaint.measureText(text)
        val w = textW + padX * 2 + dotSpace
        val h = textPaint.textSize + padY * 2
        val left = when (align) {
            Align.LEFT -> x
            Align.CENTER -> x - w / 2f
            Align.RIGHT -> x - w
        }
        rectF.set(left, y, left + w, y + h)
        canvas.drawRoundRect(rectF, h / 2f, h / 2f, pillPaint)
        if (warmDot) {
            canvas.drawCircle(left + padX + dp(2.5f), y + h / 2f, dp(3f), warmFillPaint)
        }
        textPaint.textAlign = Paint.Align.LEFT
        val baseline = y + padY + textPaint.textSize - textPaint.descent() * 0.6f
        canvas.drawText(text, left + padX + dotSpace, baseline, textPaint)
    }

    /**
     * 사람이 설 자리. 발끝이 (x, footY) 에 오도록 포즈 그림을 놓습니다.
     * 고른 포즈가 있으면 그 그림, 없으면 구도가 어울리는 [fallback].
     * 뒤에 빛무리를 깔아 사진 위에서 떠 보이게 합니다.
     */
    private fun person(canvas: Canvas, x: Float, footY: Float, tall: Float = dp(52f), fallback: Figure = Figure.WALK) {
        val fig = figure ?: fallback
        glow(canvas, x, footY - tall * 0.45f, tall * 0.8f)
        icon(canvas, fig.res, x, footY - tall / 2f, tall)
    }

    /** 흐름의 방향. 둥근 선과 채운 화살촉. */
    private fun arrow(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        line(canvas, x1, y1, x2, y2, dp(2f), warmColor = true)
        val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val head = dp(11f)
        val a1 = angle + Math.PI * 0.82
        val a2 = angle - Math.PI * 0.82
        path.reset()
        path.moveTo(x2, y2)
        path.lineTo(x2 + (head * cos(a1)).toFloat(), y2 + (head * sin(a1)).toFloat())
        path.lineTo(x2 + (head * cos(a2)).toFloat(), y2 + (head * sin(a2)).toFloat())
        path.close()
        canvas.drawPath(path, warmFillPaint)
    }

    /** 삼분할 선을 옅게. 다른 구도의 바탕으로 깝니다. */
    private fun faintThirds(canvas: Canvas, w: Float, h: Float) {
        val stroke = dp(0.8f)
        line(canvas, w / 3f, 0f, w / 3f, h, stroke, dashed = true)
        line(canvas, w * 2f / 3f, 0f, w * 2f / 3f, h, stroke, dashed = true)
        line(canvas, 0f, h / 3f, w, h / 3f, stroke, dashed = true)
        line(canvas, 0f, h * 2f / 3f, w, h * 2f / 3f, stroke, dashed = true)
    }

    // ───────────────────────── 구도별 ─────────────────────────

    /** 삼분할 격자 + 네 교차점. 피사체는 오른쪽 아래 교차점에. */
    private fun drawThirds(canvas: Canvas, w: Float, h: Float) {
        val x1 = w / 3f
        val x2 = w * 2f / 3f
        val y1 = h / 3f
        val y2 = h * 2f / 3f

        line(canvas, x1, 0f, x1, h)
        line(canvas, x2, 0f, x2, h)
        line(canvas, 0f, y1, w, y1)
        line(canvas, 0f, y2, w, y2)

        dot(canvas, x1, y1)
        dot(canvas, x2, y1)
        dot(canvas, x1, y2)

        person(canvas, x2, y2 + dp(4f), fallback = Figure.SIT)
        dot(canvas, x2, y2, warmColor = true)
        pill(canvas, "교차점에 피사체", x2, y2 + dp(12f))
    }

    /** 중앙 수직선 + 좌우 여백 기준선. 건물의 대칭을 맞추는 데 씁니다. */
    private fun drawSymmetry(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val offset = w * 0.25f
        line(canvas, cx - offset, 0f, cx - offset, h, dashed = true)
        line(canvas, cx + offset, 0f, cx + offset, h, dashed = true)
        line(canvas, 0f, h / 2f, w, h / 2f)
        glow(canvas, cx, h / 2f, dp(28f), 70)
        line(canvas, cx, 0f, cx, h, dp(1.6f), warmColor = true)
        dot(canvas, cx, h / 2f, warmColor = true)
        pill(canvas, "중심축을 건물 가운데에", cx, h * 0.5f + dp(12f))
    }

    /** 중앙 원. 원 안에 피사체를 채우면 시선이 가운데로 모입니다. */
    private fun drawCenter(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) * 0.32f
        glow(canvas, cx, cy, r * 1.1f, 45)
        circle(canvas, cx, cy, r, dp(1.4f))
        val tick = dp(10f)
        line(canvas, cx - tick, cy, cx + tick, cy)
        line(canvas, cx, cy - tick, cx, cy + tick)
        pill(canvas, "원 안을 피사체로 채우기", cx, cy + r + dp(8f))
    }

    /** 낮은 지평선, 위쪽의 해, 그 앞에 서는 사람. 해를 등지고 형태만 남깁니다. */
    private fun drawSilhouette(canvas: Canvas, w: Float, h: Float) {
        val horizon = h * 0.68f
        line(canvas, 0f, horizon, w, horizon, dp(1.2f))
        pill(canvas, "지평선은 아래 1/3", dp(12f), horizon - dp(30f), Align.LEFT)

        val sunX = w * 0.6f
        val sunY = h * 0.4f
        val r = w * 0.09f
        glow(canvas, sunX, sunY, r * 2.4f, 120)
        icon(canvas, R.drawable.guide_icon_sun, sunX, sunY, r * 2f)
        pill(canvas, "해는 사람 뒤에", sunX, sunY + r + dp(8f))

        val px = w * 0.38f
        person(canvas, px, horizon, dp(64f), fallback = Figure.ARMS_UP)
        pill(canvas, "사람은 해 앞, 지평선 위", px, horizon + dp(8f))
    }

    /** 수면선을 화면 절반 아래 두고, 위의 피사체가 아래에 뒤집혀 비칩니다. */
    private fun drawReflection(canvas: Canvas, w: Float, h: Float) {
        val water = h * 0.56f
        val l = w * 0.3f
        val r = w * 0.7f
        val top = h * 0.3f
        val mirrored = water + (water - top)

        box(canvas, l, top, r, water, dp(1.2f))
        box(canvas, l, water, r, mirrored, dp(1.2f), dashed = true, fill = true)
        glow(canvas, w / 2f, water, w * 0.36f, 50)
        line(canvas, 0f, water, w, water, dp(1.6f), warmColor = true)
        icon(canvas, R.drawable.guide_icon_waves, w * 0.85f, water + dp(26f), dp(30f), 170)

        pill(canvas, "피사체", (l + r) / 2f, top + dp(8f))
        pill(canvas, "수면선은 절반보다 조금 아래", dp(12f), water - dp(30f), Align.LEFT)
        pill(canvas, "비친 상은 여기까지", (l + r) / 2f, mirrored - dp(30f))
    }

    /** 아래 양쪽에서 시작한 선이 한 점으로 모입니다. 그 끝에 사람이나 빛을 둡니다. */
    private fun drawLeading(canvas: Canvas, w: Float, h: Float) {
        faintThirds(canvas, w, h)
        val vx = w * 0.5f
        val vy = h * 0.36f
        line(canvas, w * 0.1f, h, vx, vy, dp(1.4f))
        line(canvas, w * 0.9f, h, vx, vy, dp(1.4f))
        line(canvas, w * 0.3f, h, vx, vy, dashed = true)
        line(canvas, w * 0.7f, h, vx, vy, dashed = true)

        glow(canvas, vx, vy, dp(40f), 110)
        dot(canvas, vx, vy, warmColor = true)
        pill(canvas, "소실점 — 선이 모이는 자리에 사람", vx, vy - dp(34f))
        person(canvas, vx, vy + dp(38f), dp(40f), fallback = Figure.WALK)
        pill(canvas, "선의 시작은 화면 아래에서", dp(12f), h - dp(34f), Align.LEFT)
    }

    /** 문·창·아치가 화면 가장자리까지 오도록 안쪽 사각형을 둡니다. */
    private fun drawFrame(canvas: Canvas, w: Float, h: Float) {
        val l = w * 0.14f
        val t = h * 0.14f
        val r = w * 0.86f
        val b = h * 0.86f
        box(canvas, l, t, r, b, dp(1.2f))
        // 모서리 — 테두리가 여기까지 차야 합니다
        val c = dp(18f)
        for ((cx, cy, sx, sy) in listOf(
            Corner(l, t, 1f, 1f), Corner(r, t, -1f, 1f), Corner(l, b, 1f, -1f), Corner(r, b, -1f, -1f)
        )) {
            line(canvas, cx, cy, cx + c * sx, cy, dp(2.6f), warmColor = true)
            line(canvas, cx, cy, cx, cy + c * sy, dp(2.6f), warmColor = true)
        }
        pill(canvas, "문·창틀을 이 선까지 채우기", w / 2f, t + dp(8f))
        person(canvas, w / 2f, h * 0.66f, dp(56f), fallback = Figure.FRAME)
        pill(canvas, "안쪽 가운데에 피사체", w / 2f, h * 0.66f + dp(8f))
    }

    private data class Corner(val x: Float, val y: Float, val sx: Float, val sy: Float)

    /** 바닥 패턴 점과 그 위의 사람. 그림자 방향을 사선으로 표시합니다. */
    private fun drawTop(canvas: Canvas, w: Float, h: Float) {
        val cols = 4
        val rows = 5
        for (i in 1 until cols) for (j in 1 until rows) {
            dot(canvas, w * i / cols, h * j / rows)
        }
        line(canvas, w * 0.15f, h * 0.85f, w * 0.85f, h * 0.15f, dashed = true)
        pill(canvas, "그림자 방향", w * 0.72f, h * 0.24f)

        val px = w * 0.5f
        val py = h * 0.6f
        glow(canvas, px, py, dp(38f), 110)
        icon(canvas, R.drawable.guide_fig_top, px, py, dp(34f))
        pill(canvas, "위에서 본 사람 — 패턴 한가운데", px, py + dp(24f))
    }

    /** 사람은 한쪽 1/3 교차점에, 시선이 향하는 쪽은 비웁니다. */
    private fun drawSpace(canvas: Canvas, w: Float, h: Float) {
        faintThirds(canvas, w, h)
        val px = w / 3f
        val py = h * 2f / 3f

        box(canvas, w * 0.42f, h * 0.12f, w * 0.92f, h * 0.6f, dashed = true, warmColor = true, fill = true)
        pill(canvas, "이쪽은 비워 두기", w * 0.67f, h * 0.34f)

        person(canvas, px, py + dp(4f), fallback = Figure.ARMS_UP)
        pill(canvas, "여기에 서기", px, py + dp(12f))
        arrow(canvas, px + dp(28f), py - dp(26f), w * 0.5f, h * 0.5f)
    }

    /** 고정된 배경을 한쪽에 남기고, 흐름의 방향을 화살표로 보입니다. */
    private fun drawMotion(canvas: Canvas, w: Float, h: Float) {
        box(canvas, w * 0.06f, h * 0.2f, w * 0.36f, h * 0.78f, dashed = true, fill = true)
        pill(canvas, "고정된 배경", w * 0.21f, h * 0.2f + dp(8f))

        line(canvas, 0f, h * 0.55f, w, h * 0.55f, dp(0.8f), dashed = true)
        icon(canvas, R.drawable.guide_icon_waves, w * 0.66f, h * 0.55f - dp(26f), dp(30f), 170)
        arrow(canvas, w * 0.42f, h * 0.55f, w * 0.9f, h * 0.55f)
        pill(canvas, "사람·물결이 흐르는 방향", w * 0.66f, h * 0.55f + dp(12f))
    }

    /** 앞·중간·뒤 세 겹. 앞은 걸치고, 중간에 사람, 뒤는 장소가 보이게. */
    private fun drawLayer(canvas: Canvas, w: Float, h: Float) {
        val y1 = h * 0.36f
        val y2 = h * 0.72f
        line(canvas, 0f, y1, w, y1, dashed = true)
        line(canvas, 0f, y2, w, y2, dashed = true)
        pill(canvas, "뒤 — 장소가 보이게", dp(12f), h * 0.16f, Align.LEFT)
        pill(canvas, "중간 — 사람·빛", dp(12f), (y1 + y2) / 2f - dp(32f), Align.LEFT)

        person(canvas, w * 0.55f, y2 - dp(6f), dp(48f), fallback = Figure.WALK)
        // 앞쪽에 걸치는 잎·난간 자리
        box(canvas, w * 0.62f, y2 + dp(6f), w + dp(20f), h + dp(20f), dp(1.4f), warmColor = true, fill = true)
        pill(canvas, "앞 — 잎·난간을 한쪽에 걸치기", dp(12f), h - dp(34f), Align.LEFT)
    }

    /** 같은 형태가 이어지다 한 번 깨지는 자리. 그 자리에 사람이나 색 하나. */
    private fun drawPattern(canvas: Canvas, w: Float, h: Float) {
        val cols = 5
        for (i in 1 until cols) {
            line(canvas, w * i / cols, h * 0.15f, w * i / cols, h * 0.85f, dashed = true)
        }
        pill(canvas, "같은 것이 세 번 이상 이어지게", dp(12f), h * 0.08f, Align.LEFT)

        val bx = w * 3f / cols
        val by = h * 0.5f
        glow(canvas, bx, by, dp(44f), 110)
        circle(canvas, bx, by, dp(22f), dp(1.6f), warmColor = true)
        person(canvas, bx, by + dp(18f), dp(36f), fallback = Figure.WALK)
        pill(canvas, "하나만 다르게 — 사람이나 색", bx, by + dp(28f))
    }
}
