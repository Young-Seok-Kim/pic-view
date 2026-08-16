package com.youngs.picview.ui.palette

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.youngs.picview.R

/**
 * 원본과 적용본을 한 화면에서 좌우로 갈라 보여 줍니다.
 *
 * 전후를 따로 두 장 놓으면 눈이 두 번 움직이는 사이에 차이를 잊습니다.
 * 같은 자리에서 경계선만 밀면 **같은 나뭇잎이 색만 바뀌는 것**이 보입니다.
 * 필터의 값어치는 결국 그 차이라서, 차이를 보여 주는 것이 이 화면의 일입니다.
 *
 * 손잡이는 화면 한가운데가 아니라 사진 위에 둡니다. 사진 밖으로 나가면
 * 무엇을 미는 것인지 헷갈립니다.
 */
class BeforeAfterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** 원본. 왼쪽에 보입니다. */
    var original: Bitmap? = null
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    /** 팔레트를 입힌 것. 오른쪽에 보입니다. */
    var graded: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    /** 경계선 위치(0~1). */
    private var split = 0.5f

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        strokeWidth = dp(2f)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(11f)
        color = 0xFFFFFFFF.toInt()
    }

    private val source = Rect()
    private val dest = Rect()
    private val pill = RectF()

    init {
        labelPaint.typeface = Typeface.create(
            ResourcesCompat.getFont(context, R.font.pretendard), Typeface.BOLD
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val bitmap = original
        // 사진 비율을 그대로 따릅니다. 정사각으로 고정하면 세로 사진의
        // 위아래가 잘려서 "이렇게 저장되나" 하고 오해합니다.
        val height = if (bitmap == null) width else {
            (width * bitmap.height.toFloat() / bitmap.width).toInt()
        }
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val before = original ?: return
        source.set(0, 0, before.width, before.height)
        dest.set(0, 0, width, height)

        canvas.drawBitmap(before, source, dest, bitmapPaint)

        val splitX = width * split
        graded?.let { after ->
            canvas.save()
            canvas.clipRect(splitX, 0f, width.toFloat(), height.toFloat())
            canvas.drawBitmap(after, source, dest, bitmapPaint)
            canvas.restore()
        }

        canvas.drawLine(splitX, 0f, splitX, height.toFloat(), linePaint)

        handlePaint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(splitX, height / 2f, dp(14f), handlePaint)
        handlePaint.color = ContextCompat.getColor(context, R.color.maple_500)
        canvas.drawCircle(splitX, height / 2f, dp(5f), handlePaint)

        drawLabel(canvas, context.getString(R.string.palette_before), atStart = true)
        drawLabel(canvas, context.getString(R.string.palette_after), atStart = false)
    }

    /** 어느 쪽이 원본인지 알려 주는 꼬리표. 없으면 좌우를 거꾸로 읽습니다. */
    private fun drawLabel(canvas: Canvas, text: String, atStart: Boolean) {
        val textWidth = labelPaint.measureText(text)
        val padding = dp(10f)
        val margin = dp(12f)
        val top = height - dp(38f)

        val left = if (atStart) margin else width - margin - textWidth - padding * 2
        pill.set(left, top, left + textWidth + padding * 2, top + dp(26f))

        pillPaint.color = if (atStart) {
            ContextCompat.getColor(context, R.color.scrim_strong)
        } else {
            ContextCompat.getColor(context, R.color.maple_500)
        }
        canvas.drawRoundRect(pill, dp(13f), dp(13f), pillPaint)
        canvas.drawText(text, left + padding, top + dp(17.5f), labelPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // 부모가 스크롤 뷰라 가로 드래그를 세로 스크롤로 가로채 갑니다.
                parent?.requestDisallowInterceptTouchEvent(true)
                split = (event.x / width).coerceIn(0f, 1f)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
