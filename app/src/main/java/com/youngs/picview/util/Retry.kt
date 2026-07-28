package com.youngs.picview.util

import android.util.Log
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

/**
 * 공공데이터 API 가 간헐적으로 timeout 을 내기 때문에 짧게 재시도합니다.
 *
 * @param attempts 최초 시도를 포함한 총 시도 횟수
 * @param initialDelayMs 첫 재시도까지 기다리는 시간 (이후 2배씩 증가)
 * @return 성공한 결과. 모든 시도가 실패하면 null.
 */
suspend fun <T> retryOrNull(
    tag: String,
    attempts: Int = 3,
    initialDelayMs: Long = 700L,
    block: suspend () -> T
): T? {
    var delayMs = initialDelayMs

    repeat(attempts) { index ->
        try {
            return block()
        } catch (e: CancellationException) {
            throw e // 화면을 벗어난 경우까지 재시도하면 안 됩니다.
        } catch (e: Exception) {
            val isLast = index == attempts - 1
            Log.w(tag, "요청 실패 (${index + 1}/$attempts): ${e.message}")
            if (isLast) return null
            delay(delayMs)
            delayMs *= 2
        }
    }
    return null
}
