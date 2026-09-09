package com.youngs.picview.domain.frame

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF

/**
 * 사진에서 프레임 창에 넣을 부분을 정합니다.
 *
 * 창과 사진의 비율이 다르면 어딘가는 잘립니다. 전에는 무조건 가운데를
 * 잘랐는데, 사람이 한쪽에 서 있는 사진은 사람이 잘려 나갔습니다. 이제는
 * 사용자가 고른 부분([crop], 0~1 비율 좌표)이 있으면 그 안에서 창 비율에
 * 맞는 가장 큰 영역을 씁니다. 없으면 예전처럼 가운데입니다.
 */
object PhotoCrop {

    /**
     * 사진에서 잘라 쓸 픽셀 영역.
     *
     * @param crop 사용자가 고른 부분. 사진 전체를 0~1 로 본 비율 좌표. null 이면 가운데.
     * @param dest 그려 넣을 창. 비율만 씁니다.
     */
    fun srcRect(photo: Bitmap, dest: RectF, crop: RectF?): Rect {
        val region = if (crop == null) {
            Rect(0, 0, photo.width, photo.height)
        } else {
            Rect(
                (crop.left * photo.width).toInt().coerceIn(0, photo.width - 1),
                (crop.top * photo.height).toInt().coerceIn(0, photo.height - 1),
                (crop.right * photo.width).toInt().coerceIn(1, photo.width),
                (crop.bottom * photo.height).toInt().coerceIn(1, photo.height)
            )
        }
        // 고른 영역 안에서 창 비율에 맞는 가장 큰 사각형을 가운데에 둡니다.
        val scale = maxOf(dest.width() / region.width(), dest.height() / region.height())
        val w = (dest.width() / scale).toInt().coerceIn(1, region.width())
        val h = (dest.height() / scale).toInt().coerceIn(1, region.height())
        val left = region.left + (region.width() - w) / 2
        val top = region.top + (region.height() - h) / 2
        return Rect(left, top, left + w, top + h)
    }

    /**
     * 창 비율로 사진을 가운데 잘랐을 때의 기본 영역(0~1). 조정 화면이
     * "원래대로"의 기준과 확대 1배의 기준으로 씁니다.
     */
    fun centered(photoWidth: Int, photoHeight: Int, aspect: Float): RectF {
        val photoAspect = photoWidth.toFloat() / photoHeight
        return if (photoAspect > aspect) {
            val w = aspect / photoAspect
            RectF((1f - w) / 2f, 0f, (1f + w) / 2f, 1f)
        } else {
            val h = photoAspect / aspect
            RectF(0f, (1f - h) / 2f, 1f, (1f + h) / 2f)
        }
    }
}
