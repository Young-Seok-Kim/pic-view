package com.youngs.picview.domain.frame

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.min

/**
 * 폴라로이드 한 장을 그립니다.
 *
 * 화면에 미리보기를 띄우는 것과 공유할 파일을 만드는 것을 같은 코드로
 * 처리합니다. 미리보기와 저장본이 다르게 나오면 "본 것과 다른 게 저장되는"
 * 문제가 생기는데, 그건 사용자가 알아채기 어려운 종류의 배신입니다.
 *
 * 출력은 정사각형입니다. 인스타그램·카카오톡 어디에 올려도 잘리지 않습니다.
 */
object PolaroidComposer {

    /** 출력 한 변(px). 공유용으로 충분하면서 메모리를 과하게 쓰지 않는 크기입니다. */
    const val SIZE = 1080

    private const val MARGIN = 72f          // 종이 바깥 여백
    private const val PHOTO_TOP = 72f       // 사진 위쪽 여백
    private const val CAPTION_HEIGHT = 210f // 아랫단(글씨 들어가는 넓은 부분)

    /**
     * @param photo 원본 사진
     * @param theme 프레임 테마
     * @param place 장소 이름
     * @param dateText "2026.08.12" 형태
     * @param credit 출처 한 줄(관광 정보 출처 표기)
     */
    fun compose(
        photo: Bitmap,
        theme: FrameTheme,
        place: String,
        dateText: String,
        credit: String,
        titleTypeface: Typeface? = null,
        bodyTypeface: Typeface? = null
    ): Bitmap {
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(theme.paperColor)

        // ── 사진 영역 ─────────────────────────────
        val photoLeft = MARGIN
        val photoRight = SIZE - MARGIN
        val photoTop = PHOTO_TOP
        val photoBottom = SIZE - CAPTION_HEIGHT
        val dest = RectF(photoLeft, photoTop, photoRight, photoBottom)

        // 원본 비율을 지키며 영역을 꽉 채웁니다(센터 크롭).
        // 늘려서 채우면 사람 얼굴이 일그러집니다.
        val scale = maxOf(dest.width() / photo.width, dest.height() / photo.height)
        val cropW = (dest.width() / scale).toInt().coerceAtMost(photo.width)
        val cropH = (dest.height() / scale).toInt().coerceAtMost(photo.height)
        val src = Rect(
            (photo.width - cropW) / 2,
            (photo.height - cropH) / 2,
            (photo.width - cropW) / 2 + cropW,
            (photo.height - cropH) / 2 + cropH
        )
        canvas.drawBitmap(photo, src, dest, Paint(Paint.FILTER_BITMAP_FLAG))

        // 사진 가장자리에 얇은 테마색 선. 종이와 사진의 경계를 만들어 줍니다.
        canvas.drawRect(dest, Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = theme.accentColor
            alpha = 90
        })

        // ── 아랫단 글씨 ───────────────────────────
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.titleColor
            textSize = 52f
            typeface = titleTypeface ?: Typeface.DEFAULT_BOLD
        }
        val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.captionColor
            textSize = 34f
            typeface = bodyTypeface ?: Typeface.DEFAULT
        }
        val creditPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.captionColor
            textSize = 24f
            alpha = 150
            typeface = bodyTypeface ?: Typeface.DEFAULT
        }

        val textLeft = MARGIN + 8f
        canvas.drawText(
            ellipsize(place, titlePaint, SIZE - MARGIN * 2 - 120f),
            textLeft, photoBottom + 76f, titlePaint
        )
        canvas.drawText("$dateText · 정읍시", textLeft, photoBottom + 126f, captionPaint)
        canvas.drawText(credit, textLeft, photoBottom + 168f, creditPaint)

        // 테마색 점 두 개. 시안의 오른쪽 아래 장식입니다.
        val dotY = photoBottom + 66f
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.accentColor }
        canvas.drawCircle(SIZE - MARGIN - 56f, dotY, 17f, dotPaint.apply { alpha = 130 })
        canvas.drawCircle(SIZE - MARGIN - 22f, dotY, 17f, dotPaint.apply { alpha = 255 })

        return out
    }

    /** 장소 이름이 길면 말줄임. 두 줄로 흘리면 아랫단 균형이 무너집니다. */
    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.take(end) + "…") > maxWidth) end--
        return text.take(min(end, text.length)) + "…"
    }
}
