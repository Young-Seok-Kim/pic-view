package com.youngs.picview.util

import com.youngs.picview.BuildConfig

/**
 * API 키 상태 판별.
 *
 * local.properties 에 키가 없으면 local.defaults.properties 의 "UNSET" 이
 * 대신 들어옵니다. 그 상태로 API 를 부르면 401/400 이 나고 재시도까지 돌면서
 * 로딩만 길어지므로, 호출 전에 여기서 걸러 폴백으로 넘깁니다.
 */
object ApiKeys {

    private const val SENTINEL = "UNSET"

    fun isUsable(key: String?): Boolean =
        !key.isNullOrBlank() && key != SENTINEL

    /** Gemini 키가 설정돼 있는지. 없으면 코스 설명을 템플릿으로 만듭니다. */
    val hasGemini: Boolean get() = isUsable(BuildConfig.GEMINI_API_KEY)

    val gemini: String get() = BuildConfig.GEMINI_API_KEY
}
