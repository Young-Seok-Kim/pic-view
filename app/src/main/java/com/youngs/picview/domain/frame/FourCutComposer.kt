package com.youngs.picview.domain.frame

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

/**
 * 네컷 사진 한 장을 그립니다 (인생네컷 세로 스트립).
 *
 * 시안("JEONGEUP · AUTUMN LIGHT")의 구조를 따릅니다 — 종이 위에
 * 사진 네 칸이 세로로 놓이고, 아래에 영문 표제와 우리말 한 줄이 붙습니다.
 * 장식(모서리 잎 · 구름 · 수묵 산세)은 폴라로이드와 같은 붓([FrameDecor])입니다.
 *
 * 사진이 네 장보다 적으면 남는 칸은 빈 인화지로 둡니다. 잘라내지 않아야
 * "여기에 더 채울 수 있다"가 보입니다.
 */
object FourCutComposer {

    const val WIDTH = 1080
    const val HEIGHT = 1800

    /** 사진 칸 좌표. 폭 760, 높이 322, 사이 26. */
    private const val SLOT_LEFT = 160f
    private const val SLOT_RIGHT = WIDTH - 160f
    private const val SLOT_TOP = 138f
    private const val SLOT_HEIGHT = 322f
    private const val SLOT_GAP = 26f

    /** 손그림 아트워크가 없을 때 [index] 번째 칸. 조정 화면이 이 비율을 씁니다. */
    fun slotWindow(index: Int): RectF {
        val top = SLOT_TOP + index * (SLOT_HEIGHT + SLOT_GAP)
        return RectF(SLOT_LEFT, top, SLOT_RIGHT, top + SLOT_HEIGHT)
    }

    /**
     * @param crops 사진마다 보일 부분(0~1 비율 좌표). 짧거나 null 이면 가운데.
     */
    fun compose(
        photos: List<Bitmap>,
        theme: FrameTheme,
        titleTypeface: Typeface? = null,
        bodyTypeface: Typeface? = null,
        artwork: FrameArtwork? = null,
        crops: List<RectF?> = emptyList()
    ): Bitmap {
        if (artwork != null) return composeWithArtwork(photos, theme, artwork, crops)

        val out = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(theme.paperColor)

        FrameDecor.drawDoubleBorder(
            canvas,
            RectF(30f, 30f, WIDTH - 30f, HEIGHT - 30f),
            inset = 14f,
            color = theme.accentColor
        )

        // 위 — 앱 이름과 구름 문양. 스트립이 어디서 나왔는지 스스로 말합니다.
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accentColor
            textSize = 42f
            typeface = titleTypeface ?: Typeface.DEFAULT_BOLD
        }
        canvas.drawText("정읍시선", 72f, 100f, brandPaint)
        FrameDecor.drawCloud(canvas, WIDTH - 160f, 78f, 110f, theme.accentColor)
        FrameDecor.drawMotif(canvas, theme.motif, WIDTH - 300f, 84f, 40f, rotation = -14f, alpha = 190)

        // ── 사진 네 칸 ─────────────────────────────
        repeat(4) { index ->
            val top = SLOT_TOP + index * (SLOT_HEIGHT + SLOT_GAP)
            val dest = RectF(SLOT_LEFT, top, SLOT_RIGHT, top + SLOT_HEIGHT)
            val photo = photos.getOrNull(index)

            if (photo != null) {
                drawPhoto(canvas, photo, dest, crops.getOrNull(index))
            } else {
                drawEmptySlot(canvas, dest, theme, index)
            }

            canvas.drawRoundRect(dest, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                color = theme.accentColor
                alpha = 80
            })
        }

        // 칸 옆 여백에 흩어진 잎. 왼쪽 아래·오른쪽 위가 시안의 자리입니다.
        FrameDecor.drawMotif(canvas, theme.motif, 92f, 300f, 40f, rotation = -20f, alpha = 170)
        FrameDecor.drawMotif(canvas, theme.motif, WIDTH - 92f, 700f, 46f, rotation = 18f, alpha = 190)
        FrameDecor.drawMotif(canvas, theme.motif, 90f, 1120f, 34f, rotation = 30f, alpha = 140)

        // ── 아랫단 표제 ────────────────────────────
        val slotsBottom = SLOT_TOP + 4 * SLOT_HEIGHT + 3 * SLOT_GAP

        FrameDecor.drawMountains(
            canvas,
            right = WIDTH - 96f,
            base = HEIGHT - 96f,
            width = 340f,
            color = theme.accentColor
        )
        FrameDecor.drawMotif(canvas, theme.motif, 120f, HEIGHT - 130f, 44f, rotation = -16f, alpha = 200)

        val centerX = WIDTH / 2f

        val enPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.titleColor
            textSize = 46f
            typeface = bodyTypeface ?: Typeface.DEFAULT_BOLD
            isFakeBoldText = true
            letterSpacing = 0.16f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(theme.fourCutTitle, centerX, slotsBottom + 92f, enPaint)

        FrameDecor.drawMotif(canvas, theme.motif, centerX, slotsBottom + 132f, 30f)

        val koPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.captionColor
            textSize = 40f
            typeface = titleTypeface ?: Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(theme.fourCutTagline, centerX, slotsBottom + 206f, koPaint)

        return out
    }

    /**
     * 아트워크 합성 — 사진을 창마다 먼저 깔고 아트워크를 위에 덮습니다.
     * 빈 칸은 흰 인화지에 옅은 순번만 남깁니다(기존과 같은 약속 —
     * "더 채울 수 있다"가 보여야 합니다).
     */
    private fun composeWithArtwork(
        photos: List<Bitmap>, theme: FrameTheme, artwork: FrameArtwork, crops: List<RectF?>
    ): Bitmap {
        val out = Bitmap.createBitmap(artwork.bitmap.width, artwork.bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(0xFFFFFFFF.toInt())

        artwork.windows.forEachIndexed { index, dest ->
            val photo = photos.getOrNull(index)
            if (photo != null) {
                PolaroidComposer.drawPhotoInWindow(canvas, photo, dest, crops.getOrNull(index))
            } else {
                val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = theme.captionColor
                    alpha = 90
                    textSize = 44f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("${index + 1}", dest.centerX(), dest.centerY() + 16f, hint)
            }
        }

        canvas.drawBitmap(artwork.bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun drawPhoto(canvas: Canvas, photo: Bitmap, dest: RectF, crop: RectF?) {
        val src = PhotoCrop.srcRect(photo, dest, crop)

        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(dest, 10f, 10f, Path.Direction.CW) })
        canvas.drawBitmap(photo, src, dest, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }

    /** 빈 칸 — 흰 인화지에 옅은 순번만 둡니다. */
    private fun drawEmptySlot(canvas: Canvas, dest: RectF, theme: FrameTheme, index: Int) {
        canvas.drawRoundRect(dest, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
        })

        val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.captionColor
            alpha = 90
            textSize = 44f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "${index + 1}",
            dest.centerX(), dest.centerY() + 16f, hint
        )
    }
}
