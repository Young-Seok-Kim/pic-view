package com.youngs.picview.ui.guide

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * 카메라 미리보기 위에 구도 가이드를 그립니다.
 *
 * 격자만 긋는 것이 아니라 **어디에 서고 무엇을 어디에 둘지**를 그립니다 —
 * 사람 표시, 수면선, 소실점, 비워 둘 자리. 포즈 예시("?")가 그림으로
 * 보여 주던 것을 화면 위에 직접 얹은 셈입니다. 카메라 격자처럼 상단
 * 버튼으로 켜고 끕니다.
 *
 * 밝은 하늘 위에서 흰 선이 사라지지 않도록, 모든 선은
 * 어두운 선을 먼저 깔고 그 위에 흰 선을 겹쳐 그립니다.
 * 크기는 전부 dp 또는 화면 비율 기준이라 해상도가 달라도 같은 비율로 보입니다.
 */
class GuideOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /**
     * 구도 종류.
     *
     * @param id 시선 가이드 화면의 구도 식별자와 같습니다. 그 화면에서
     *   넘어오면 같은 구도로 시작합니다.
     * @param label 카메라 상단 알약과 고르기 목록에 보이는 이름.
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

    var guideType: GuideType = GuideType.THIRDS
        set(value) {
            field = value
            invalidate()
        }

    /**
     * 위아래로 가려지는 만큼. 상단 바와 하단 컨트롤이 미리보기를 덮고
     * 있어서, 그 아래에 그린 안내 글은 보이지 않습니다. 그림은 이 안쪽에
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

    /** 밝은 배경에서도 선이 보이도록 아래에 깔아 주는 어두운 선. */
    private val shadowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = Color.argb(70, 0, 0, 0)
    }

    private val linePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = Color.argb(200, 255, 255, 255)
    }

    /** 선택된 자리·강조를 나타내는 따뜻한 색. 흰 선과 구분됩니다. */
    private val accentPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = Color.argb(230, 255, 196, 92)
    }

    private val dotPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.argb(235, 255, 255, 255)
    }

    private val dotShadowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.argb(60, 0, 0, 0)
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textSize = dp(11f)
        isFakeBoldText = true
        setShadowLayer(dp(2.5f), 0f, dp(1f), Color.argb(170, 0, 0, 0))
    }

    private val dash = DashPathEffect(floatArrayOf(dp(6f), dp(5f)), 0f)

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
        canvas.restore()
    }

    // ───────────────────────── 그리기 부품 ─────────────────────────

    /** 선 하나를 어두운 선 + 흰 선 두 번 그립니다. */
    private fun line(
        canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, width: Float,
        dashed: Boolean = false, accent: Boolean = false
    ) {
        val top = if (accent) accentPaint else linePaint
        shadowPaint.pathEffect = if (dashed) dash else null
        top.pathEffect = if (dashed) dash else null
        shadowPaint.strokeWidth = width + dp(1.5f)
        canvas.drawLine(x1, y1, x2, y2, shadowPaint)
        top.strokeWidth = width
        canvas.drawLine(x1, y1, x2, y2, top)
        shadowPaint.pathEffect = null
        top.pathEffect = null
    }

    private fun rect(
        canvas: Canvas, l: Float, t: Float, r: Float, b: Float, width: Float,
        dashed: Boolean = false, accent: Boolean = false
    ) {
        line(canvas, l, t, r, t, width, dashed, accent)
        line(canvas, r, t, r, b, width, dashed, accent)
        line(canvas, r, b, l, b, width, dashed, accent)
        line(canvas, l, b, l, t, width, dashed, accent)
    }

    private fun circle(canvas: Canvas, cx: Float, cy: Float, r: Float, width: Float, accent: Boolean = false) {
        val top = if (accent) accentPaint else linePaint
        shadowPaint.strokeWidth = width + dp(1.5f)
        canvas.drawCircle(cx, cy, r, shadowPaint)
        top.strokeWidth = width
        canvas.drawCircle(cx, cy, r, top)
    }

    private fun dot(canvas: Canvas, cx: Float, cy: Float) {
        val r = dp(3.5f)
        canvas.drawCircle(cx, cy, r + dp(1f), dotShadowPaint)
        canvas.drawCircle(cx, cy, r, dotPaint)
    }

    /** 짧은 안내 글. 가운데 정렬로 (cx, cy) 아래에 둡니다. */
    private fun label(canvas: Canvas, text: String, cx: Float, cy: Float) {
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, cx, cy + textPaint.textSize, textPaint)
    }

    private fun labelLeft(canvas: Canvas, text: String, x: Float, cy: Float) {
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(text, x, cy + textPaint.textSize * 0.4f, textPaint)
    }

    /**
     * 사람이 설 자리. 발끝이 (x, footY) 에 오는 작은 막대 인물입니다.
     * 따뜻한 색이라 흰 격자 사이에서 "여기"가 바로 보입니다.
     */
    private fun person(canvas: Canvas, x: Float, footY: Float, tall: Float = dp(40f)) {
        val head = tall * 0.14f
        val top = footY - tall
        val neck = top + head * 2
        val hip = footY - tall * 0.42f
        val stroke = dp(2.2f)

        circle(canvas, x, top + head, head, stroke, accent = true)
        line(canvas, x, neck, x, hip, stroke, accent = true)
        line(canvas, x - tall * 0.18f, neck + tall * 0.2f, x + tall * 0.18f, neck + tall * 0.2f, stroke, accent = true)
        line(canvas, x, hip, x - tall * 0.14f, footY, stroke, accent = true)
        line(canvas, x, hip, x + tall * 0.14f, footY, stroke, accent = true)
    }

    private fun arrow(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        val stroke = dp(1.8f)
        line(canvas, x1, y1, x2, y2, stroke, accent = true)
        val head = dp(9f)
        val angle = Math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val a1 = angle + Math.PI * 0.8
        val a2 = angle - Math.PI * 0.8
        line(canvas, x2, y2, x2 + (head * Math.cos(a1)).toFloat(), y2 + (head * Math.sin(a1)).toFloat(), stroke, accent = true)
        line(canvas, x2, y2, x2 + (head * Math.cos(a2)).toFloat(), y2 + (head * Math.sin(a2)).toFloat(), stroke, accent = true)
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

    /** 삼분할 격자 + 네 교차점. 피사체를 교차점에 두면 안정적인 구도가 됩니다. */
    private fun drawThirds(canvas: Canvas, w: Float, h: Float) {
        val stroke = dp(1f)
        val x1 = w / 3f
        val x2 = w * 2f / 3f
        val y1 = h / 3f
        val y2 = h * 2f / 3f

        line(canvas, x1, 0f, x1, h, stroke)
        line(canvas, x2, 0f, x2, h, stroke)
        line(canvas, 0f, y1, w, y1, stroke)
        line(canvas, 0f, y2, w, y2, stroke)

        // 교차점 강조 — 안내 문구가 가리키는 지점
        dot(canvas, x1, y1)
        dot(canvas, x2, y1)
        dot(canvas, x1, y2)
        dot(canvas, x2, y2)

        person(canvas, x2, y2 + dp(6f))
        label(canvas, "교차점에 피사체", x2, y2 + dp(8f))
    }

    /** 중앙 수직선 + 좌우 여백 기준선. 건물의 대칭을 맞추는 데 씁니다. */
    private fun drawSymmetry(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val stroke = dp(1f)

        // 중앙 축
        line(canvas, cx, 0f, cx, h, dp(1.4f), accent = true)

        // 좌우 대칭 확인용 보조선
        val offset = w * 0.25f
        line(canvas, cx - offset, 0f, cx - offset, h, stroke)
        line(canvas, cx + offset, 0f, cx + offset, h, stroke)

        // 수평 기준선 (건물이 기울지 않았는지)
        line(canvas, 0f, h / 2f, w, h / 2f, stroke)

        dot(canvas, cx, h / 2f)
        label(canvas, "중심축을 건물 가운데에", cx, h * 0.5f + dp(6f))
    }

    /** 중앙 원. 원 안에 피사체를 채우면 시선이 가운데로 모입니다. */
    private fun drawCenter(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) * 0.32f

        circle(canvas, cx, cy, r, dp(1.4f))

        // 가운데를 잡아 주는 짧은 십자선
        val tick = dp(14f)
        line(canvas, cx - tick, cy, cx + tick, cy, dp(1f))
        line(canvas, cx, cy - tick, cx, cy + tick, dp(1f))
        label(canvas, "원 안을 피사체로 채우기", cx, cy + r + dp(4f))
    }

    /** 낮은 지평선, 위쪽의 해, 그 앞에 서는 사람. 해를 등지고 형태만 남깁니다. */
    private fun drawSilhouette(canvas: Canvas, w: Float, h: Float) {
        val horizon = h * 0.68f
        line(canvas, 0f, horizon, w, horizon, dp(1.2f))
        labelLeft(canvas, "지평선은 아래 1/3", dp(12f), horizon - dp(12f))

        val sunX = w * 0.6f
        val sunY = h * 0.4f
        val r = w * 0.07f
        circle(canvas, sunX, sunY, r, dp(1.4f), accent = true)
        for (i in 0 until 8) {
            val a = Math.PI * 2 * i / 8
            val x1 = sunX + (Math.cos(a) * (r + dp(4f))).toFloat()
            val y1 = sunY + (Math.sin(a) * (r + dp(4f))).toFloat()
            val x2 = sunX + (Math.cos(a) * (r + dp(11f))).toFloat()
            val y2 = sunY + (Math.sin(a) * (r + dp(11f))).toFloat()
            line(canvas, x1, y1, x2, y2, dp(1.2f), accent = true)
        }
        label(canvas, "해는 사람 뒤에", sunX, sunY + r + dp(4f))

        person(canvas, w * 0.4f, horizon, dp(52f))
        label(canvas, "사람은 해 앞, 지평선 위에", w * 0.4f, horizon + dp(4f))
    }

    /** 수면선을 화면 절반 아래 두고, 위의 피사체가 아래에 뒤집혀 비칩니다. */
    private fun drawReflection(canvas: Canvas, w: Float, h: Float) {
        val water = h * 0.56f
        line(canvas, 0f, water, w, water, dp(1.4f), accent = true)
        labelLeft(canvas, "수면선은 절반보다 조금 아래", dp(12f), water - dp(12f))

        val l = w * 0.3f
        val r = w * 0.7f
        val top = h * 0.3f
        rect(canvas, l, top, r, water, dp(1.2f))
        label(canvas, "피사체", (l + r) / 2f, top + dp(4f))

        val mirrored = water + (water - top)
        rect(canvas, l, water, r, mirrored, dp(1.2f), dashed = true)
        label(canvas, "비친 상이 여기까지", (l + r) / 2f, mirrored + dp(2f))
    }

    /** 아래 양쪽에서 시작한 선이 한 점으로 모입니다. 그 끝에 사람이나 빛을 둡니다. */
    private fun drawLeading(canvas: Canvas, w: Float, h: Float) {
        faintThirds(canvas, w, h)
        val vx = w * 0.5f
        val vy = h * 0.36f
        line(canvas, w * 0.1f, h, vx, vy, dp(1.4f))
        line(canvas, w * 0.9f, h, vx, vy, dp(1.4f))
        line(canvas, w * 0.3f, h, vx, vy, dp(1f), dashed = true)
        line(canvas, w * 0.7f, h, vx, vy, dp(1f), dashed = true)

        dot(canvas, vx, vy)
        label(canvas, "소실점", vx, vy - dp(26f))
        person(canvas, vx, vy + dp(30f), dp(34f))
        label(canvas, "선이 모이는 자리에 사람", vx, vy + dp(32f))
        labelLeft(canvas, "선의 시작은 화면 아래에서", dp(12f), h - dp(28f))
    }

    /** 문·창·아치가 화면 가장자리까지 오도록 안쪽 사각형을 둡니다. */
    private fun drawFrame(canvas: Canvas, w: Float, h: Float) {
        val l = w * 0.14f
        val t = h * 0.14f
        val r = w * 0.86f
        val b = h * 0.86f
        rect(canvas, l, t, r, b, dp(1.4f))
        // 모서리 표시 — 테두리가 여기까지 차야 합니다
        val c = dp(16f)
        line(canvas, l, t, l + c, t, dp(2.4f), accent = true)
        line(canvas, l, t, l, t + c, dp(2.4f), accent = true)
        line(canvas, r, t, r - c, t, dp(2.4f), accent = true)
        line(canvas, r, t, r, t + c, dp(2.4f), accent = true)
        line(canvas, l, b, l + c, b, dp(2.4f), accent = true)
        line(canvas, l, b, l, b - c, dp(2.4f), accent = true)
        line(canvas, r, b, r - c, b, dp(2.4f), accent = true)
        line(canvas, r, b, r, b - c, dp(2.4f), accent = true)

        label(canvas, "문·창틀을 이 선까지 채우기", w / 2f, t + dp(4f))
        person(canvas, w / 2f, h * 0.66f, dp(44f))
        label(canvas, "안쪽 가운데에 피사체", w / 2f, h * 0.66f + dp(4f))
    }

    /** 바닥 패턴 점과 그 위의 사람. 그림자 방향을 사선으로 표시합니다. */
    private fun drawTop(canvas: Canvas, w: Float, h: Float) {
        val cols = 4
        val rows = 5
        for (i in 1 until cols) for (j in 1 until rows) {
            dot(canvas, w * i / cols, h * j / rows)
        }
        line(canvas, w * 0.15f, h * 0.85f, w * 0.85f, h * 0.15f, dp(1f), dashed = true)
        labelLeft(canvas, "그림자 방향", w * 0.6f, h * 0.3f)

        val px = w * 0.5f
        val py = h * 0.6f
        circle(canvas, px, py, dp(12f), dp(2f), accent = true)
        label(canvas, "위에서 본 사람 — 패턴 한가운데", px, py + dp(12f))
    }

    /** 사람은 한쪽 1/3 교차점에, 시선이 향하는 쪽은 비웁니다. */
    private fun drawSpace(canvas: Canvas, w: Float, h: Float) {
        faintThirds(canvas, w, h)
        val px = w / 3f
        val py = h * 2f / 3f
        person(canvas, px, py + dp(6f), dp(40f))
        label(canvas, "여기에 서기", px, py + dp(8f))

        rect(canvas, w * 0.42f, h * 0.12f, w * 0.92f, h * 0.6f, dp(1.2f), dashed = true, accent = true)
        label(canvas, "이쪽은 비워 두기", w * 0.67f, h * 0.34f)
        arrow(canvas, px + dp(26f), py - dp(22f), w * 0.5f, h * 0.5f)
    }

    /** 고정된 배경을 한쪽에 남기고, 흐름의 방향을 화살표로 보입니다. */
    private fun drawMotion(canvas: Canvas, w: Float, h: Float) {
        rect(canvas, w * 0.06f, h * 0.2f, w * 0.36f, h * 0.78f, dp(1.2f), dashed = true)
        label(canvas, "고정된 배경", w * 0.21f, h * 0.2f + dp(4f))

        arrow(canvas, w * 0.42f, h * 0.55f, w * 0.9f, h * 0.55f)
        label(canvas, "사람·물결이 흐르는 방향", w * 0.66f, h * 0.55f + dp(8f))
        line(canvas, 0f, h * 0.55f, w, h * 0.55f, dp(0.8f), dashed = true)
    }

    /** 앞·중간·뒤 세 겹. 앞은 걸치고, 중간에 사람, 뒤는 장소가 보이게. */
    private fun drawLayer(canvas: Canvas, w: Float, h: Float) {
        val y1 = h * 0.36f
        val y2 = h * 0.72f
        line(canvas, 0f, y1, w, y1, dp(1.2f), dashed = true)
        line(canvas, 0f, y2, w, y2, dp(1.2f), dashed = true)
        labelLeft(canvas, "뒤 — 장소가 보이게", dp(12f), h * 0.18f)
        labelLeft(canvas, "중간 — 사람·빛", dp(12f), (y1 + y2) / 2f - dp(20f))
        labelLeft(canvas, "앞 — 잎·난간을 한쪽에 걸치기", dp(12f), h * 0.86f)

        person(canvas, w * 0.55f, y2 - dp(8f), dp(40f))
        // 앞쪽에 걸치는 잎·난간 자리
        rect(canvas, w * 0.66f, y2, w, h, dp(1.2f), accent = true)
    }

    /** 같은 형태가 이어지다 한 번 깨지는 자리. 그 자리에 사람이나 색 하나. */
    private fun drawPattern(canvas: Canvas, w: Float, h: Float) {
        val cols = 5
        for (i in 1 until cols) {
            line(canvas, w * i / cols, h * 0.15f, w * i / cols, h * 0.85f, dp(1f), dashed = true)
        }
        labelLeft(canvas, "같은 것이 세 번 이상 이어지게", dp(12f), h * 0.1f)

        val bx = w * 3f / cols
        val by = h * 0.5f
        circle(canvas, bx, by, dp(16f), dp(2f), accent = true)
        person(canvas, bx, by + dp(16f), dp(30f))
        label(canvas, "하나만 다르게 — 사람이나 색", bx, by + dp(18f))
    }
}
