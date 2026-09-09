package com.youngs.picview.domain

import com.youngs.picview.domain.photo.PhotoTraits
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 밝기 배열로 만든 합성 이미지로 반영·실루엣 판정을 검증합니다.
 * 실제 사진은 없어도, 구조가 맞으면 잡고 아니면 안 잡는지는 여기서 봅니다.
 */
class PhotoTraitsTest {

    private val w = 48
    private val h = 48

    /** 위쪽 절반에 무작위 결을 깔고, 아래쪽은 그것을 뒤집어 어둡게 — 수면 반영. */
    @Test
    fun `위아래가 거울처럼 되풀이되면 반영으로 읽는다`() {
        val rnd = Random(1)
        val luma = IntArray(w * h)
        for (y in 0 until h / 2) for (x in 0 until w) {
            val v = 60 + rnd.nextInt(150)
            luma[y * w + x] = v
            // 아래쪽은 뒤집힌 상이 실제보다 어둡고 흐릿하게
            luma[(h - 1 - y) * w + x] = (v * 0.7f).toInt() + rnd.nextInt(10)
        }
        assertTrue(PhotoTraits.isReflection(luma, w, h))
    }

    @Test
    fun `좌우가 거울처럼 되풀이되면 반영으로 읽는다`() {
        val rnd = Random(2)
        val luma = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w / 2) {
            val v = 40 + rnd.nextInt(180)
            luma[y * w + x] = v
            luma[y * w + (w - 1 - x)] = v
        }
        assertTrue(PhotoTraits.isReflection(luma, w, h))
    }

    @Test
    fun `무작위 결은 반영이 아니다`() {
        val rnd = Random(3)
        val luma = IntArray(w * h) { 30 + rnd.nextInt(200) }
        assertFalse(PhotoTraits.isReflection(luma, w, h))
    }

    /** 하늘(위 밝음)과 땅(아래 어두움)처럼 가로줄만 있는 사진은 뒤집어도 비슷하지만 거울은 아닙니다. */
    @Test
    fun `위아래 밝기 기울기만 있는 사진은 반영이 아니다`() {
        val luma = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            luma[y * w + x] = 230 - (y * 180 / h) + (x % 3)
        }
        assertFalse(PhotoTraits.isReflection(luma, w, h))
    }

    @Test
    fun `검은 형태와 밝은 하늘로 갈라진 사진은 실루엣이다`() {
        val luma = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            // 아래쪽 40%는 어두운 형태, 나머지는 밝은 하늘
            if (y > h * 0.6 || (x in 20..28 && y > h * 0.3)) 20 else 220
        }
        assertTrue(PhotoTraits.isSilhouette(luma))
    }

    @Test
    fun `중간 밝기가 많은 보통 사진은 실루엣이 아니다`() {
        val rnd = Random(4)
        val luma = IntArray(w * h) { 70 + rnd.nextInt(100) }
        assertFalse(PhotoTraits.isSilhouette(luma))
    }

    @Test
    fun `밝기만 다른 단색 두 띠는 결이 없어 반영으로 읽지 않는다`() {
        val luma = IntArray(w * h) { i -> if (i / w < h / 2) 200 else 100 }
        assertFalse(PhotoTraits.isReflection(luma, w, h))
    }
}
