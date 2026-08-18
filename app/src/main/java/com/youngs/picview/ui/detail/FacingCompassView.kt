package com.youngs.picview.ui.detail

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.youngs.picview.R
import com.youngs.picview.domain.spot.Facing
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 촬영 방향을 가리키는 작은 나침반.
 *
 * "서향"이라는 글자만으로는 현장에서 몸을 어디로 돌릴지 한 번 더 생각해야
 * 합니다. 원 위의 네 방위 중 하나가 켜져 있으면 글자를 해석하는 단계가
 * 사라집니다. 시안(상세 개편안)의 방향 카드에 있는 그 그림입니다.
 *
 * 실내·무관인 장소는 방향이 없으므로 이 뷰 자체를 숨기는 쪽이 맞습니다.
 */
class FacingCompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var facing: Facing = Facing.WEST
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = ContextCompat.getColor(context, R.color.maple_300)
    }
    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(11f)
        color = ContextCompat.getColor(context, R.color.text_tertiary)
    }
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(12f)
        color = ContextCompat.getColor(context, R.color.maple_600)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.maple_500)
    }

    init {
        val font = ResourcesCompat.getFont(context, R.font.pretendard)
        letterPaint.typeface = font
        activePaint.typeface = Typeface.create(font, Typeface.BOLD)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - dp(12f)

        canvas.drawCircle(cx, cy, radius, ringPaint)

        // 방위 글자는 원 바깥이 아니라 원둘레 위에 앉힙니다. 바깥에 두면
        // 뷰가 커지고, 안쪽에 두면 원이 글자 상자처럼 보입니다.
        LETTERS.forEach { (letter, bearing) ->
            val angle = Math.toRadians(bearing.toDouble() - 90)
            val x = cx + radius * cos(angle).toFloat()
            val y = cy + radius * sin(angle).toFloat()

            val active = facing.bearing == bearing
            val paint = if (active) activePaint else letterPaint

            if (active) {
                // 글자 뒤에 옅은 점을 깔아 "여기"를 만듭니다.
                dotPaint.alpha = 40
                canvas.drawCircle(x, y, dp(11f), dotPaint)
                dotPaint.alpha = 255
            }
            canvas.drawText(letter, x, y - (paint.ascent() + paint.descent()) / 2f, paint)
        }

        // 가운데 점 — 카메라를 든 사람의 자리.
        canvas.drawCircle(cx, cy, dp(2.5f), dotPaint)
    }

    private companion object {
        val LETTERS = listOf("N" to 0, "E" to 90, "S" to 180, "W" to 270)
    }
}
