package com.youngs.picview.domain.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 사진 한 장을 기기 안에서 읽습니다.
 *
 * 두 갈래로 읽습니다.
 *  - **무엇이 찍혔나** → ML Kit 이미지 라벨링(번들 모델, 오프라인).
 *    호수·강·폭포·바닷가면 수변, 노을이면 석양.
 *  - **어떻게 찍었나** → [PhotoTraits] 의 밝기 계산. 반영(대칭)과 실루엣.
 *
 * 사진은 어디로도 보내지 않습니다. 계정이 없고 기록을 단말에만 두는 앱이라
 * 사진만 서버로 나가는 것은 앞뒤가 맞지 않습니다.
 */
class PhotoAnalyzer(context: Context) {

    private val appContext = context.applicationContext

    private val labeler: ImageLabeler by lazy {
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(LABEL_MIN_CONFIDENCE)
                .build()
        )
    }

    /**
     * @return 읽지 못했으면 null — 사진이 갤러리에서 지워졌거나 열 수 없는 경우.
     */
    suspend fun read(uri: Uri): PhotoReading? = withContext(Dispatchers.IO) {
        val bitmap = decode(uri, LABEL_EDGE) ?: return@withContext null

        val labels = runCatching { labeler.process(InputImage.fromBitmap(bitmap, 0)).await() }
            .onFailure { Log.w(TAG, "라벨링 실패 $uri", it) }
            .getOrNull()
            ?.map { it.text }
            .orEmpty()

        // 구도 계산은 작은 그림이면 충분하고, 작을수록 빠릅니다.
        val small = Bitmap.createScaledBitmap(
            bitmap,
            scaledWidth(bitmap, TRAIT_EDGE),
            scaledHeight(bitmap, TRAIT_EDGE),
            true
        )
        val luma = lumaOf(small)

        PhotoReading(
            reflection = PhotoTraits.isReflection(luma, small.width, small.height),
            silhouette = PhotoTraits.isSilhouette(luma),
            waterside = labels.any { it in WATER_LABELS },
            sunset = labels.any { it in SUNSET_LABELS },
            labels = labels
        )
    }

    /** 긴 변이 [maxEdge] 근처가 되도록 줄여서 읽습니다. 원본은 수 MB 라 그대로 올리면 무겁습니다. */
    private fun decode(uri: Uri, maxEdge: Int): Bitmap? = runCatching {
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxEdge && bounds.outHeight / (sample * 2) >= maxEdge) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.onFailure { Log.w(TAG, "사진 열기 실패 $uri", it) }.getOrNull()

    private fun scaledWidth(b: Bitmap, edge: Int): Int =
        if (b.width >= b.height) edge else (edge * b.width / b.height).coerceAtLeast(1)

    private fun scaledHeight(b: Bitmap, edge: Int): Int =
        if (b.height >= b.width) edge else (edge * b.height / b.width).coerceAtLeast(1)

    private fun lumaOf(bitmap: Bitmap): IntArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        return IntArray(pixels.size) { i ->
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            (r * 299 + g * 587 + b * 114) / 1000
        }
    }

    /** Play Services Task 를 코루틴으로. 별도 의존성을 더하지 않으려고 직접 감쌉니다. */
    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }

    companion object {
        private const val TAG = "PhotoAnalyzer"

        /** 라벨러에 넣는 긴 변. 모델 입력이 작아서 이 이상은 낭비입니다. */
        private const val LABEL_EDGE = 480

        /** 구도 계산용 긴 변. */
        private const val TRAIT_EDGE = 160

        private const val LABEL_MIN_CONFIDENCE = 0.6f

        /**
         * ML Kit 기본 라벨 가운데 물가를 뜻하는 것들.
         * (기본 모델에는 "Water" 라벨이 없습니다. 있는 것으로 조합합니다)
         */
        private val WATER_LABELS = setOf(
            "Lake", "River", "Beach", "Waterfall", "Underwater",
            "Boat", "Sailboat", "Speedboat", "Canoe", "Kayak",
            "Surfing", "Surfboard", "Waterskiing", "Windsurfing", "Waterfowl"
        )

        private val SUNSET_LABELS = setOf("Sunset")
    }
}
