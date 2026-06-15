package com.youngs.picview.ui.guide

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class GuideOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
        alpha = 180
        isAntiAlias = true
    }

    var guideType: GuideType = GuideType.DEFAULT
        set(value) { field = value; invalidate() }

    enum class GuideType { DEFAULT, THIRDS, CENTER }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        when (guideType) {
            GuideType.THIRDS -> drawThirds(canvas, w, h)
            GuideType.CENTER -> drawCenter(canvas, w, h)
            GuideType.DEFAULT -> drawDefault(canvas, w, h)
        }
    }

    private fun drawThirds(canvas: Canvas, w: Float, h: Float) {
        canvas.drawLine(w / 3, 0f, w / 3, h, paint)
        canvas.drawLine(w * 2 / 3, 0f, w * 2 / 3, h, paint)
        canvas.drawLine(0f, h / 3, w, h / 3, paint)
        canvas.drawLine(0f, h * 2 / 3, w, h * 2 / 3, paint)
    }

    private fun drawCenter(canvas: Canvas, w: Float, h: Float) {
        val size = 250f
        canvas.drawRect(w / 2 - size, h / 2 - size, w / 2 + size, h / 2 + size, paint)
    }

    private fun drawDefault(canvas: Canvas, w: Float, h: Float) {
        val centerX = w / 2f
        val centerY = h / 2f
        val lineLen = 60f
        canvas.drawLine(centerX - lineLen, centerY, centerX + lineLen, centerY, paint)
        canvas.drawLine(centerX, centerY - lineLen, centerX, centerY + lineLen, paint)
    }
}