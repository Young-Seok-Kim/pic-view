package com.youngs.picview.ui.calendar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.youngs.picview.R
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.ceil

/**
 * 한 달 달력.
 *
 * 촬영 적기가 며칠에 걸쳐 있는지는 목록만으로는 감이 안 옵니다. "10월 19~21일"
 * 이라고 읽는 것과 달력에서 그 사흘에 점이 찍힌 걸 보는 것은 다릅니다.
 * 그래서 목록 위에 달력을 두고, 절정 기간에 해당하는 날짜 아래 점을 찍습니다.
 *
 * 직접 그리는 이유: 안드로이드 기본 [android.widget.CalendarView] 는 날짜별
 * 표시를 커스터마이즈할 수 없고, 외부 달력 라이브러리는 이 화면 하나를 위해
 * 넣기에는 무겁습니다. 격자 계산은 요일 오프셋 하나가 전부입니다.
 */
class MonthGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** 그리는 달. */
    var yearMonth: YearMonth = YearMonth.now()
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    /** 점을 찍을 날짜(절정 기간). */
    var markedDays: Set<Int> = emptySet()
        set(value) {
            field = value
            invalidate()
        }

    /** 절정 앞뒤의 추천 날짜. 절정과 겹치면 절정이 이깁니다. */
    var recommendedDays: Set<Int> = emptySet()
        set(value) {
            field = value
            invalidate()
        }

    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val textPrimary = ContextCompat.getColor(context, R.color.text_primary)
    private val textTertiary = ContextCompat.getColor(context, R.color.text_tertiary)
    private val maple = ContextCompat.getColor(context, R.color.maple_500)
    private val golden = ContextCompat.getColor(context, R.color.golden_500)
    private val white = ContextCompat.getColor(context, R.color.white)

    private val weekdays = listOf("일", "월", "화", "수", "목", "금", "토")

    init {
        val font = ResourcesCompat.getFont(context, R.font.pretendard)
        dayPaint.typeface = font
        headerPaint.typeface = Typeface.create(font, Typeface.BOLD)

        dayPaint.textSize = 14f * density
        headerPaint.textSize = 12f * density
        todayPaint.color = maple
        dotPaint.color = golden
    }

    private val recoDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 이 달을 그리는 데 필요한 줄 수(요일 머리 1줄 + 날짜 줄). */
    private val weekRows: Int
        get() {
            val first = yearMonth.atDay(1).dayOfWeek.value % 7 // 일요일=0
            return ceil((first + yearMonth.lengthOfMonth()) / 7.0).toInt()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (ROW_HEIGHT_DP * density * (weekRows + 1)).toInt()
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val cellW = width / 7f
        val rowH = ROW_HEIGHT_DP * density

        // 요일 머리
        weekdays.forEachIndexed { i, label ->
            headerPaint.color = when (i) {
                0 -> maple           // 일요일
                else -> textTertiary
            }
            canvas.drawText(label, cellW * (i + 0.5f), rowH * 0.65f, headerPaint)
        }

        val today = LocalDate.now()
        val isThisMonth = YearMonth.from(today) == yearMonth
        val firstOffset = yearMonth.atDay(1).dayOfWeek.value % 7

        for (day in 1..yearMonth.lengthOfMonth()) {
            val index = firstOffset + day - 1
            val col = index % 7
            val row = index / 7 + 1

            val cx = cellW * (col + 0.5f)
            val cy = rowH * (row + 0.62f)

            val isToday = isThisMonth && today.dayOfMonth == day

            // 오늘은 단풍색 원으로 채웁니다.
            if (isToday) {
                canvas.drawCircle(cx, cy - rowH * 0.16f, rowH * 0.34f, todayPaint)
            }

            dayPaint.color = when {
                isToday -> white
                col == 0 -> maple            // 일요일
                day in markedDays -> maple   // 절정 기간
                else -> textPrimary
            }
            canvas.drawText(day.toString(), cx, cy, dayPaint)

            // 절정(단풍색)·추천(황금색) 표시. 오늘 원 안에는 찍지 않습니다.
            // 절정이 추천을 이깁니다 — 같은 날 점 두 개는 소음입니다.
            if (!isToday) {
                if (day in markedDays) {
                    dotPaint.color = maple
                    canvas.drawCircle(cx, cy + rowH * 0.22f, 2.5f * density, dotPaint)
                } else if (day in recommendedDays) {
                    recoDotPaint.color = golden
                    canvas.drawCircle(cx, cy + rowH * 0.22f, 2.5f * density, recoDotPaint)
                }
            }
        }
    }

    private val density: Float get() = resources.displayMetrics.density

    companion object {
        private const val ROW_HEIGHT_DP = 38f
    }
}
