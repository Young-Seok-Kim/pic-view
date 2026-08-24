package com.youngs.picview.domain.frame

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF

/**
 * 손그림 프레임 아트워크 (시안 원본).
 *
 * 시안의 흰 사진 창을 투명하게 뚫은 WebP 를 assets/frames 에 둡니다.
 * 합성은 두 겹입니다 — 사진을 창 좌표에 먼저 깔고 아트워크를 위에
 * 덮습니다. 창 안으로 넘어온 장식(찻잔의 김 · 들꽃 · 처마)이 반투명
 * 알파를 그대로 갖고 있어 사진 위에 자연스럽게 겹칩니다.
 *
 * 창 좌표는 원본 이미지의 흰 영역을 스크립트로 측정해 박아 둔 값이라
 * 아트워크 파일을 바꾸면 좌표도 함께 갱신해야 합니다.
 *
 * 무성서원 1컷 아트워크는 아직 없습니다 — [single] 이 null 을 돌려주고
 * [PolaroidComposer] 가 기존 손그림 폴라로이드로 그립니다.
 */
class FrameArtwork private constructor(
    val bitmap: Bitmap,
    val windows: List<RectF>
) {

    private data class Spec(val asset: String, val windows: List<RectF>)

    companion object {
        /** 아트워크 원본 크기(px). 합성 결과물도 이 크기로 나갑니다. */
        const val WIDTH = 1024
        const val HEIGHT = 1536

        private val SINGLE = mapOf(
            FrameTheme.MAPLE to Spec(
                "frame_naejang_1.webp",
                listOf(RectF(266f, 290f, 751f, 1225f))
            ),
            FrameTheme.SSANGHWA to Spec(
                "frame_ssanghwa_1.webp",
                listOf(RectF(229f, 288f, 795f, 1200f))
            ),
            FrameTheme.GUJEOLCHO to Spec(
                "frame_gujeolcho_1.webp",
                listOf(RectF(205f, 323f, 819f, 1131f))
            )
        )

        private val FOUR_CUT = mapOf(
            FrameTheme.MAPLE to Spec(
                "frame_naejang_4.webp",
                listOf(
                    RectF(221f, 304f, 471f, 735f),
                    RectF(557f, 304f, 806f, 735f),
                    RectF(221f, 827f, 471f, 1227f),
                    RectF(557f, 827f, 806f, 1227f)
                )
            ),
            FrameTheme.SEOWON to Spec(
                "frame_seowon_4.webp",
                listOf(
                    RectF(321f, 221f, 703f, 440f),
                    RectF(321f, 507f, 703f, 727f),
                    RectF(321f, 795f, 703f, 1015f),
                    RectF(321f, 1083f, 703f, 1303f)
                )
            ),
            FrameTheme.GUJEOLCHO to Spec(
                "frame_gujeolcho_4.webp",
                listOf(
                    RectF(187f, 343f, 462f, 712f),
                    RectF(561f, 343f, 836f, 712f),
                    RectF(187f, 803f, 462f, 1172f),
                    RectF(561f, 803f, 836f, 1172f)
                )
            ),
            FrameTheme.SSANGHWA to Spec(
                "frame_ssanghwa_4.webp",
                listOf(
                    RectF(202f, 312f, 473f, 715f),
                    RectF(551f, 312f, 821f, 715f),
                    RectF(202f, 793f, 473f, 1190f),
                    RectF(551f, 793f, 821f, 1190f)
                )
            )
        )

        /** 1컷 아트워크. 없는 테마(무성서원)는 null. */
        fun single(context: Context, theme: FrameTheme): FrameArtwork? =
            load(context, SINGLE[theme])

        /** 4컷 아트워크. 네 테마 모두 있습니다. */
        fun fourCut(context: Context, theme: FrameTheme): FrameArtwork? =
            load(context, FOUR_CUT[theme])

        private fun load(context: Context, spec: Spec?): FrameArtwork? {
            spec ?: return null
            return runCatching {
                context.assets.open("frames/${spec.asset}").use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()?.let { FrameArtwork(it, spec.windows) }
        }
    }
}
