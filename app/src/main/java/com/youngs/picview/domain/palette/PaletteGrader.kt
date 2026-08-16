package com.youngs.picview.domain.palette

import android.graphics.Bitmap
import android.graphics.Color

/**
 * 팔레트를 사진에 입힙니다.
 *
 * 방식은 **그라디언트 맵**입니다. 픽셀의 밝기를 재서, 어두울수록 팔레트의
 * 그림자색에 가깝게 밝을수록 밝은색에 가깝게 바꿔 칠합니다. 색상환을
 * 회전시키거나 채도만 올리는 흔한 방식과 달리, 그림자·중간·밝은 곳을 각각
 * 다른 색으로 물들일 수 있어서 필름 색감에 가까운 결과가 나옵니다.
 *
 * 원본 밝기는 그대로 둡니다. 밝기까지 건드리면 얼굴이 뭉개지고 하늘이
 * 날아갑니다. 바뀌는 것은 색뿐입니다.
 *
 * 세기(0~100)로 원본과 섞습니다. 100 이면 통째로 물들고, 0 이면 원본입니다.
 * 시안의 기본값은 70 인데, 이 정도라야 "정읍 색"이 보이면서도 사진에
 * 찍힌 것이 무엇인지 알아볼 수 있습니다.
 */
object PaletteGrader {

    /**
     * @param strength 0~100. 원본과 섞는 비율.
     * @return 새 비트맵. 원본은 건드리지 않습니다(되돌리기와 비교에 필요).
     */
    fun apply(source: Bitmap, palette: ColorPalette, strength: Int): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val lut = buildLut(palette)
        val mix = strength.coerceIn(0, 100) / 100f

        for (i in pixels.indices) {
            val color = pixels[i]
            val r = color shr 16 and 0xFF
            val g = color shr 8 and 0xFF
            val b = color and 0xFF

            // 사람 눈은 초록에 가장 민감합니다. 단순 평균으로 밝기를 재면
            // 초록 잎이 실제보다 어둡게 잡혀 숲 사진이 통째로 그림자색이 됩니다.
            val luma = (r * 77 + g * 151 + b * 28) shr 8
            val mapped = lut[luma]

            pixels[i] = (color and 0xFF000000.toInt()) or
                (blend(r, mapped shr 16 and 0xFF, mix) shl 16) or
                (blend(g, mapped shr 8 and 0xFF, mix) shl 8) or
                blend(b, mapped and 0xFF, mix)
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun blend(from: Int, to: Int, mix: Float): Int =
        (from + (to - from) * mix).toInt().coerceIn(0, 255)

    /**
     * 밝기 0~255 를 색으로 바꾸는 표를 미리 만듭니다.
     *
     * 픽셀마다 계산하면 백만 번 넘게 같은 보간을 되풀이합니다. 256칸짜리
     * 표를 한 번 만들어 두면 그 뒤로는 찾아보기만 하면 됩니다.
     */
    private fun buildLut(palette: ColorPalette): IntArray = IntArray(256) { level ->
        if (level < 128) {
            lerpColor(palette.shadow, palette.mid, level / 127f)
        } else {
            lerpColor(palette.mid, palette.highlight, (level - 128) / 127f)
        }
    }

    private fun lerpColor(from: Int, to: Int, t: Float): Int = Color.rgb(
        blend(Color.red(from), Color.red(to), t),
        blend(Color.green(from), Color.green(to), t),
        blend(Color.blue(from), Color.blue(to), t)
    )
}
