package com.youngs.picview.domain.frame

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.min

/**
 * 폴라로이드 한 장을 그립니다 (정읍의 순간을 담다 시안).
 *
 * 화면에 미리보기를 띄우는 것과 공유할 파일을 만드는 것을 같은 코드로
 * 처리합니다. 미리보기와 저장본이 다르게 나오면 "본 것과 다른 게 저장되는"
 * 문제가 생기는데, 그건 사용자가 알아채기 어려운 종류의 배신입니다.
 *
 * 출력은 정사각형입니다. 인스타그램·카카오톡 어디에 올려도 잘리지 않습니다.
 * 테마 장식(모서리 잎 · 구름 문양 · 수묵 산세)은 [FrameDecor] 가 그립니다.
 */
object PolaroidComposer {

    /** 출력 한 변(px). 공유용으로 충분하면서 메모리를 과하게 쓰지 않는 크기입니다. */
    const val SIZE = 1080

    private const val MARGIN = 64f          // 종이 바깥 여백
    private const val PHOTO_TOP = 88f       // 사진 위쪽 여백
    private const val CAPTION_HEIGHT = 252f // 아랫단(글씨 들어가는 넓은 부분)

    /**
     * @param photo 원본 사진
     * @param theme 프레임 테마
     * @param place 장소 이름
     * @param dateText "2026.08.12" 형태
     * @param artwork 손그림 프레임 아트워크. 있으면 시안 원본에 사진만 끼웁니다.
     */
    fun compose(
        photo: Bitmap,
        theme: FrameTheme,
        place: String,
        dateText: String,
        titleTypeface: Typeface? = null,
        bodyTypeface: Typeface? = null,
        artwork: FrameArtwork? = null
    ): Bitmap {
        if (artwork != null) return composeWithArtwork(photo, artwork)

        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(theme.paperColor)

        // 종이 가장자리의 이중 테두리(시안의 얇은 금선).
        FrameDecor.drawDoubleBorder(
            canvas,
            RectF(26f, 26f, SIZE - 26f, SIZE - 26f),
            inset = 12f,
            color = theme.accentColor
        )

        // 구름 문양 — 오른쪽 위 여백.
        FrameDecor.drawCloud(canvas, SIZE - 170f, 58f, 110f, theme.accentColor)

        // ── 사진 영역 ─────────────────────────────
        val dest = RectF(MARGIN, PHOTO_TOP, SIZE - MARGIN, SIZE - CAPTION_HEIGHT)

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

        // 모서리를 살짝 둥글려 종이 위에 얹힌 인화지처럼 보이게 합니다.
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(dest, 16f, 16f, Path.Direction.CW) })
        canvas.drawBitmap(photo, src, dest, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()

        // 사진 가장자리에 얇은 테마색 선. 종이와 사진의 경계를 만들어 줍니다.
        canvas.drawRoundRect(dest, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = theme.accentColor
            alpha = 90
        })

        // 왼쪽 위 모서리에 흩어진 잎 — 사진 귀퉁이에 살짝 겹칩니다(시안 표현).
        FrameDecor.drawMotif(canvas, theme.motif, 96f, 92f, 54f, rotation = -18f, alpha = 235)
        FrameDecor.drawMotif(canvas, theme.motif, 162f, 130f, 38f, rotation = 24f, alpha = 150)

        // ── 아랫단 ───────────────────────────────
        val captionTop = dest.bottom

        // 오른쪽 아래 — 수묵 산세와 잎 하나.
        FrameDecor.drawMountains(
            canvas,
            right = SIZE - MARGIN - 10f,
            base = SIZE - 84f,
            width = 320f,
            color = theme.accentColor
        )
        FrameDecor.drawMotif(
            canvas, theme.motif,
            SIZE - MARGIN - 60f, captionTop + 52f, 40f, rotation = 14f, alpha = 210
        )

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.titleColor
            textSize = 58f
            typeface = titleTypeface ?: Typeface.DEFAULT_BOLD
        }
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accentColor
            textSize = 36f
            typeface = bodyTypeface ?: Typeface.DEFAULT
            isFakeBoldText = true
        }
        val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.captionColor
            textSize = 31f
            typeface = bodyTypeface ?: Typeface.DEFAULT
        }

        // 장소명 + 작은 잎(시안: "내장산국립공원 🍁").
        val textLeft = MARGIN + 10f
        val title = ellipsize(place, titlePaint, SIZE - MARGIN * 2 - 220f)
        canvas.drawText(title, textLeft, captionTop + 78f, titlePaint)
        FrameDecor.drawMotif(
            canvas, theme.motif,
            textLeft + titlePaint.measureText(title) + 34f, captionTop + 58f,
            34f, rotation = 10f
        )

        canvas.drawText(dateText, textLeft, captionTop + 132f, datePaint)
        canvas.drawText(
            "전라북도 정읍시 · ${theme.tagline}",
            textLeft, captionTop + 178f, captionPaint
        )

        return out
    }

    /**
     * 아트워크 합성 — 사진을 창에 먼저 깔고 아트워크를 위에 덮습니다.
     * 장소명·날짜는 그리지 않습니다. 아트워크가 이미 제 표제를 갖고 있어
     * 글씨를 더 얹으면 시안의 균형이 무너집니다.
     */
    private fun composeWithArtwork(photo: Bitmap, artwork: FrameArtwork): Bitmap {
        val out = Bitmap.createBitmap(artwork.bitmap.width, artwork.bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(0xFFFFFFFF.toInt())
        drawPhotoInWindow(canvas, photo, artwork.windows.first())
        canvas.drawBitmap(artwork.bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** 창 좌표에 센터 크롭으로 채웁니다. 늘려 채우면 얼굴이 일그러집니다. */
    internal fun drawPhotoInWindow(canvas: Canvas, photo: Bitmap, dest: RectF) {
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
    }

    /** 장소 이름이 길면 말줄임. 두 줄로 흘리면 아랫단 균형이 무너집니다. */
    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.take(end) + "…") > maxWidth) end--
        return text.take(min(end, text.length)) + "…"
    }
}
