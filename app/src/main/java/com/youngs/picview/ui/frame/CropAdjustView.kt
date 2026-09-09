package com.youngs.picview.ui.frame

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.youngs.picview.domain.frame.PhotoCrop

/**
 * 프레임 창에 보일 부분을 고르는 화면.
 *
 * 가운데 창(프레임의 사진 칸과 같은 비율)은 고정이고, 그 뒤의 사진을
 * 끌어 옮기거나 두 손가락으로 키웁니다. 창 밖은 어둡게 덮어 "이 안이
 * 나온다"가 바로 보이게 합니다. 결과는 [crop] — 사진 전체를 0~1 로 본
 * 비율 좌표라 원본 크기와 무관하게 합성기에 그대로 넘깁니다.
 */
class CropAdjustView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var bitmap: Bitmap? = null

    /** 창의 가로/세로 비율. */
    private var aspect = 1f

    /** 확대 1배(창 비율로 가운데 자른 것)의 영역. 여기서 줌·이동으로 좁혀 갑니다. */
    private var base = RectF(0f, 0f, 1f, 1f)

    private var zoom = 1f
    private var centerX = 0.5f
    private var centerY = 0.5f

    /** 지금 고른 영역(0~1). */
    val crop: RectF
        get() {
            val w = base.width() / zoom
            val h = base.height() / zoom
            return RectF(centerX - w / 2f, centerY - h / 2f, centerX + w / 2f, centerY + h / 2f)
        }

    /** 확대 1배 그대로면 "조정 안 함"과 같아, 합성기에는 null 로 넘깁니다. */
    val cropOrNull: RectF?
        get() = if (zoom <= 1.001f && isCentered()) null else crop

    private val window = RectF()
    private val drawRect = RectF()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val dimPaint = Paint().apply { color = Color.argb(150, 0, 0, 0) }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        color = Color.WHITE
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
        color = Color.argb(110, 255, 255, 255)
    }

    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoom = (zoom * detector.scaleFactor).coerceIn(1f, MAX_ZOOM)
                clampCenter()
                invalidate()
                return true
            }
        }
    )

    /**
     * @param initial 이미 조정해 둔 영역. null 이면 창 비율로 가운데.
     */
    fun bind(bitmap: Bitmap, aspect: Float, initial: RectF?) {
        this.bitmap = bitmap
        this.aspect = aspect
        base = PhotoCrop.centered(bitmap.width, bitmap.height, aspect)
        if (initial != null) {
            // 저장된 영역을 이 창 비율에 맞춰 다시 읽습니다. 프레임을 바꿔
            // 비율이 달라졌어도 가운데는 유지됩니다.
            val zoomX = base.width() / initial.width()
            val zoomY = base.height() / initial.height()
            zoom = maxOf(zoomX, zoomY).coerceIn(1f, MAX_ZOOM)
            centerX = initial.centerX()
            centerY = initial.centerY()
        } else {
            reset()
        }
        clampCenter()
        invalidate()
    }

    fun reset() {
        zoom = 1f
        centerX = 0.5f
        centerY = 0.5f
        clampCenter()
        invalidate()
    }

    private fun isCentered(): Boolean =
        kotlin.math.abs(centerX - 0.5f) < 0.002f && kotlin.math.abs(centerY - 0.5f) < 0.002f

    private fun clampCenter() {
        val halfW = base.width() / zoom / 2f
        val halfH = base.height() / zoom / 2f
        centerX = centerX.coerceIn(halfW, 1f - halfW)
        centerY = centerY.coerceIn(halfH, 1f - halfH)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val pad = 16f * resources.displayMetrics.density
        val availW = w - pad * 2
        val availH = h - pad * 2
        val winW: Float
        val winH: Float
        if (availW / availH > aspect) {
            winH = availH
            winW = availH * aspect
        } else {
            winW = availW
            winH = availW / aspect
        }
        window.set((w - winW) / 2f, (h - winH) / 2f, (w + winW) / 2f, (h + winH) / 2f)
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap ?: return
        if (window.isEmpty) onSizeChanged(width, height, 0, 0)

        // 고른 영역이 창에 딱 맞도록 사진 전체를 배치합니다.
        val c = crop
        val scale = window.width() / (c.width() * bmp.width)
        val left = window.left - c.left * bmp.width * scale
        val top = window.top - c.top * bmp.height * scale
        drawRect.set(left, top, left + bmp.width * scale, top + bmp.height * scale)
        canvas.drawBitmap(bmp, null, drawRect, bitmapPaint)

        // 창 밖은 어둡게
        canvas.drawRect(0f, 0f, width.toFloat(), window.top, dimPaint)
        canvas.drawRect(0f, window.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, window.top, window.left, window.bottom, dimPaint)
        canvas.drawRect(window.right, window.top, width.toFloat(), window.bottom, dimPaint)

        // 삼분할 보조선과 테두리
        for (i in 1..2) {
            val x = window.left + window.width() * i / 3f
            val y = window.top + window.height() * i / 3f
            canvas.drawLine(x, window.top, x, window.bottom, gridPaint)
            canvas.drawLine(window.left, y, window.right, y, gridPaint)
        }
        canvas.drawRect(window, borderPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                dragging = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging && !scaleDetector.isInProgress && event.pointerCount == 1) {
                    val bmp = bitmap ?: return true
                    // 화면에서 끈 만큼을 사진 비율 좌표로. 사진을 오른쪽으로
                    // 끌면 보이는 영역은 왼쪽으로 갑니다.
                    val c = crop
                    val pxPerUnitX = window.width() / c.width()
                    val pxPerUnitY = window.height() / c.height()
                    centerX -= (event.x - lastX) / pxPerUnitX
                    centerY -= (event.y - lastY) / pxPerUnitY
                    clampCenter()
                    invalidate()
                }
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // 손가락 하나가 떨어진 뒤 남은 손가락으로 이어 끌 때 튀지 않게.
                val remaining = if (event.actionIndex == 0) 1 else 0
                lastX = event.getX(remaining)
                lastY = event.getY(remaining)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    companion object {
        private const val MAX_ZOOM = 4f
    }
}
