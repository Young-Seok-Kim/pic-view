package com.youngs.picview.domain.frame

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * 프레임 장식 붓.
 *
 * 시안의 손그림 요소(수묵 산세 · 구름 문양 · 흩어진 단풍)를 코드로 그립니다.
 * 폴라로이드와 네컷이 같은 붓을 쓰므로 두 출력의 인상이 하나로 묶입니다.
 */
object FrameDecor {

    /**
     * 수묵풍 산세. 낮은 봉우리 둘이 겹칩니다.
     *
     * @param base 산자락이 닿는 밑변 y
     * @param width 전체 폭
     */
    fun drawMountains(canvas: Canvas, right: Float, base: Float, width: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }

        // 뒷산 — 옅고 낮게.
        val left = right - width
        paint.alpha = 46
        canvas.drawPath(Path().apply {
            moveTo(left, base)
            quadTo(left + width * 0.28f, base - width * 0.30f, left + width * 0.56f, base)
            close()
        }, paint)

        // 앞산 — 조금 진하고 크게.
        paint.alpha = 76
        canvas.drawPath(Path().apply {
            moveTo(left + width * 0.34f, base)
            quadTo(left + width * 0.64f, base - width * 0.40f, right, base)
            close()
        }, paint)

        // 물가의 잔선. 산 아래에 가로 획 세 개면 물이 읽힙니다.
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            alpha = 56
            strokeWidth = width * 0.008f
        }
        canvas.drawLine(left + width * 0.10f, base + width * 0.045f,
            left + width * 0.38f, base + width * 0.045f, line)
        canvas.drawLine(left + width * 0.50f, base + width * 0.075f,
            left + width * 0.86f, base + width * 0.075f, line)
        canvas.drawLine(left + width * 0.30f, base + width * 0.105f,
            left + width * 0.52f, base + width * 0.105f, line)
    }

    /** 구름 문양 — 시안 모서리의 소용돌이 선. 가로 획 둘과 반원 하나로 만듭니다. */
    fun drawCloud(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            alpha = 90
            style = Paint.Style.STROKE
            strokeWidth = size * 0.055f
            strokeCap = Paint.Cap.ROUND
        }

        val r = size * 0.16f
        canvas.drawArc(
            RectF(cx - r, cy - r * 2f, cx + r, cy),
            0f, 300f, false, paint
        )
        canvas.drawLine(cx - size * 0.5f, cy, cx + size * 0.34f, cy, paint)
        canvas.drawLine(cx - size * 0.30f, cy + size * 0.14f, cx + size * 0.5f, cy + size * 0.14f, paint)
    }

    /**
     * 장식 이모지 한 장.
     *
     * 벡터를 손으로 깎는 대신 이모지를 씁니다 — 단풍·들꽃·찻잔 모두
     * 그림 품질이 보장되고, 회전·크기·투명도만 얹으면 흩어진 잎이 됩니다.
     */
    fun drawMotif(
        canvas: Canvas,
        motif: String,
        cx: Float,
        cy: Float,
        size: Float,
        rotation: Float = 0f,
        alpha: Int = 255
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            textAlign = Paint.Align.CENTER
            this.alpha = alpha
        }
        canvas.save()
        canvas.rotate(rotation, cx, cy)
        // drawText 의 y 는 베이스라인이라 가운데로 보정합니다.
        canvas.drawText(motif, cx, cy + size * 0.36f, paint)
        canvas.restore()
    }

    /** 시안의 이중 테두리. 바깥은 진하게, 안쪽은 옅게 한 번 더. */
    fun drawDoubleBorder(canvas: Canvas, bounds: RectF, inset: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
        }

        paint.strokeWidth = 3f
        paint.alpha = 110
        canvas.drawRoundRect(bounds, 18f, 18f, paint)

        paint.strokeWidth = 1.5f
        paint.alpha = 70
        canvas.drawRoundRect(
            RectF(
                bounds.left + inset, bounds.top + inset,
                bounds.right - inset, bounds.bottom - inset
            ),
            12f, 12f, paint
        )
    }
}
