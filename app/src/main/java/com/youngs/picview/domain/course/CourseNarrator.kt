package com.youngs.picview.domain.course

import android.util.Log
import com.youngs.picview.data.api.GeminiRequest
import com.youngs.picview.data.api.RetrofitClient
import com.youngs.picview.util.ApiKeys
import com.youngs.picview.util.retryOrNull
import java.time.format.DateTimeFormatter

/**
 * 완성된 출사 코스에 붙일 설명 문구를 만듭니다.
 *
 * 코스 자체는 [CoursePlanner] 가 규칙으로 확정합니다. LLM 은 그 결과를
 * 사람 말로 옮기는 역할만 합니다. 이렇게 나눈 이유:
 *
 *  - LLM 이 실패해도(키 없음·할당량 초과·네트워크 끊김·안전필터) 코스는 그대로 나옴
 *  - 심사 시연 중 LLM 이 엉뚱한 동선을 지어낼 위험이 없음
 *  - 호출 비용이 코스 생성이 아니라 문장 한 개분으로 줄어듦
 */
interface CourseNarrator {
    suspend fun narrate(course: ShootingCourse): String
}

/** LLM 없이 규칙만으로 만드는 문구. 항상 성공합니다. */
object TemplateNarrator : CourseNarrator {
    override suspend fun narrate(course: ShootingCourse): String {
        if (course.isEmpty) return "조건에 맞는 촬영지를 찾지 못했어요"

        val golden = course.goldenStops.firstOrNull()
        val head = if (golden != null) {
            "${golden.spot.title}의 ${golden.phase.label}에 맞춰 동선을 짰어요."
        } else {
            "이동 거리를 줄이면서 빛이 좋은 순서로 짰어요."
        }

        val indoor = course.stops.firstOrNull {
            it.phase == com.youngs.picview.domain.light.LightPhase.MIDDAY
        }
        val tail = indoor?.let {
            " 빛이 강한 한낮엔 ${it.spot.title}으로 잠시 피해 가세요."
        }.orEmpty()

        return head + tail
    }
}

/**
 * Gemini 로 문구를 만들고, 실패하면 [TemplateNarrator] 로 떨어집니다.
 *
 * 키가 비어 있으면 아예 호출하지 않습니다(빈 키로 호출하면 400 이 나고
 * 재시도까지 돌면서 로딩만 길어짐).
 */
class GeminiNarrator(
    private val apiKey: String = ApiKeys.gemini,
    private val model: String = MODEL
) : CourseNarrator {

    override suspend fun narrate(course: ShootingCourse): String {
        if (course.isEmpty) return TemplateNarrator.narrate(course)
        if (!ApiKeys.isUsable(apiKey)) return TemplateNarrator.narrate(course)

        val response = retryOrNull("GEMINI", attempts = 2) {
            RetrofitClient.geminiApiService.generateContent(
                model = model,
                apiKey = apiKey,
                request = GeminiRequest.of(buildPrompt(course), maxTokens = 220)
            )
        }

        val text = response?.firstText()
        if (text == null) {
            Log.w("GEMINI", "문구 생성 실패 → 템플릿으로 대체")
            return TemplateNarrator.narrate(course)
        }
        return text.trim().removeSurrounding("\"")
    }

    /**
     * 프롬프트에 "코스를 짜라"고 시키지 않는 게 핵심입니다.
     * 이미 확정된 코스를 주고 설명만 요청합니다.
     * 그래야 모델이 없는 장소를 지어내거나 순서를 흔들 수 없습니다.
     */
    private fun buildPrompt(course: ShootingCourse): String {
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        val timeline = course.stops.joinToString("\n") { stop ->
            "- ${stop.arriveAt.format(fmt)} ${stop.spot.title} " +
                    "(${stop.phase.label}, ${stop.facts.facing.label})"
        }
        val sunrise = course.sun.sunrise?.format(fmt) ?: "정보 없음"
        val sunset = course.sun.sunset?.format(fmt) ?: "정보 없음"

        return """
            너는 전북 정읍의 사진 촬영 안내자다.
            아래는 이미 확정된 출사 코스다. 순서와 장소는 절대 바꾸지 마라.

            일출 $sunrise / 일몰 $sunset
            $timeline

            위 코스를 왜 이 순서로 도는지 2문장으로 설명해라.
            조건:
            - 빛(골든아워, 방위, 한낮 강한 빛)을 근거로 들 것
            - 목록에 없는 장소를 언급하지 말 것
            - 존댓말, 담백하게. 이모지·과장 표현 금지
            - 설명 문장만 출력. 머리말이나 따옴표 없이
        """.trimIndent()
    }

    companion object {
        private const val MODEL = "gemini-2.5-flash"
    }
}

/** 앱에서 쓰는 기본 구현. 키가 있으면 Gemini, 없으면 템플릿. */
object Narrators {
    val default: CourseNarrator by lazy {
        if (ApiKeys.hasGemini) GeminiNarrator() else TemplateNarrator
    }
}
