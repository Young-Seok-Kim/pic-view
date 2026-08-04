package com.youngs.picview.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Gemini generateContent.
 *
 * ⚠️ 키를 앱에 넣고 직접 호출하는 방식은 임시입니다.
 * APK 를 디컴파일하면 키가 그대로 노출되고, 남이 내 할당량을 씁니다.
 * 스토어 배포 전에 Cloudflare Workers 같은 얇은 프록시로 옮기고
 * baseUrl 만 교체할 예정입니다. (호출부 코드는 그대로 둘 수 있게 분리해 뒀습니다)
 */
interface GeminiApiService {

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

data class GeminiRequest(
    @SerializedName("contents") val contents: List<GeminiContent>,
    @SerializedName("generationConfig") val generationConfig: GeminiConfig = GeminiConfig()
) {
    companion object {
        /** 단발 프롬프트용 간편 생성자. */
        fun of(prompt: String, maxTokens: Int = 800, temperature: Double = 0.8) =
            GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt)))),
                generationConfig = GeminiConfig(
                    temperature = temperature,
                    maxOutputTokens = maxTokens
                )
            )
    }
}

data class GeminiContent(
    @SerializedName("parts") val parts: List<GeminiPart>,
    @SerializedName("role") val role: String? = null
)

data class GeminiPart(
    @SerializedName("text") val text: String? = null
)

data class GeminiConfig(
    @SerializedName("temperature") val temperature: Double = 0.8,
    @SerializedName("maxOutputTokens") val maxOutputTokens: Int = 800,
    @SerializedName("topP") val topP: Double = 0.95,
    /**
     * 2.5 계열은 추론(thinking) 모델이라, 끄지 않으면 추론 토큰이
     * maxOutputTokens 를 먼저 다 써서 **본문이 중간에 잘립니다.**
     * (실제로 "오전에는 동향인 정읍" 하고 끊겼습니다)
     *
     * 두 문장짜리 설명에는 추론이 필요 없으므로 0 으로 끕니다.
     * 응답도 빨라지고 토큰 비용도 줄어듭니다.
     */
    @SerializedName("thinkingConfig") val thinkingConfig: GeminiThinking = GeminiThinking()
)

data class GeminiThinking(
    @SerializedName("thinkingBudget") val thinkingBudget: Int = 0
)

data class GeminiResponse(
    @SerializedName("candidates") val candidates: List<GeminiCandidate>? = null,
    @SerializedName("promptFeedback") val promptFeedback: GeminiFeedback? = null
) {
    /** 첫 후보의 텍스트. 차단·빈 응답이면 null. */
    fun firstText(): String? = candidates
        ?.firstOrNull()
        ?.content
        ?.parts
        ?.mapNotNull { it.text }
        ?.joinToString("")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

data class GeminiCandidate(
    @SerializedName("content") val content: GeminiContent? = null,
    @SerializedName("finishReason") val finishReason: String? = null
)

data class GeminiFeedback(
    @SerializedName("blockReason") val blockReason: String? = null
)
