package com.youngs.picview.domain.diary

import android.util.Log
import com.youngs.picview.data.api.GeminiRequest
import com.youngs.picview.data.api.RetrofitClient
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.util.ApiKeys
import com.youngs.picview.util.retryOrNull
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 일기 생성 결과. */
data class DiaryDraft(
    val title: String,
    val body: String,
    val generatedByLlm: Boolean
)

/**
 * 하루 방문 기록으로 출사 일기 초안을 씁니다.
 *
 * 코스 설명과 달리 여기는 LLM 이 진짜 값어치를 하는 자리입니다.
 * "몇 시에 어디를 갔다"는 사실을 문장으로 엮는 건 규칙으로는 못 합니다.
 * 다만 실패해도 화면이 비면 안 되므로 템플릿 폴백을 둡니다.
 */
interface DiaryWriter {
    suspend fun write(day: DiaryDay): DiaryDraft
}

/** LLM 없이 기록을 나열하는 폴백. 항상 성공합니다. */
object TemplateDiaryWriter : DiaryWriter {

    override suspend fun write(day: DiaryDay): DiaryDraft {
        val dateText = day.date.format(DATE_FMT)
        val places = day.visits.joinToString(", ") { it.title }

        val body = buildString {
            append("$dateText, 정읍에서 ${day.visits.size}곳을 다녀왔다.\n\n")
            day.visits.forEach { visit ->
                val time = visit.visitedAt.toDiaryLocalTime().format(TIME_FMT)
                val phase = runCatching { LightPhase.valueOf(visit.phaseName) }
                    .getOrNull()?.label.orEmpty()
                append("$time  ${visit.title}")
                if (phase.isNotBlank()) append(" · $phase")
                append("\n")
            }
            if (day.goldenVisits > 0) {
                append("\n골든아워에 ${day.goldenVisits}번 셔터를 눌렀다.")
            }
        }

        return DiaryDraft(
            title = "$dateText 정읍 출사",
            body = body.trim(),
            generatedByLlm = false
        )
    }

    private val DATE_FMT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
    private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
}

/** Gemini 로 일기를 쓰고, 실패하면 [TemplateDiaryWriter] 로 떨어집니다. */
class GeminiDiaryWriter(
    private val apiKey: String = ApiKeys.gemini,
    private val model: String = MODEL
) : DiaryWriter {

    override suspend fun write(day: DiaryDay): DiaryDraft {
        if (day.visits.isEmpty()) return TemplateDiaryWriter.write(day)
        if (!ApiKeys.isUsable(apiKey)) return TemplateDiaryWriter.write(day)

        val response = retryOrNull("DIARY", attempts = 2) {
            RetrofitClient.geminiApiService.generateContent(
                model = model,
                apiKey = apiKey,
                request = GeminiRequest.of(buildPrompt(day), maxTokens = 900, temperature = 0.9)
            )
        }

        val text = response?.firstText()
        if (text.isNullOrBlank()) {
            Log.w("DIARY", "일기 생성 실패 → 템플릿으로 대체")
            return TemplateDiaryWriter.write(day)
        }

        return parse(text, day)
    }

    /**
     * 방문 사실만 주고 "지어내지 말 것"을 명시합니다.
     * 사진 앱이라 없던 장소나 없던 사건이 일기에 들어가면 신뢰가 깨집니다.
     */
    private fun buildPrompt(day: DiaryDay): String {
        val dateText = day.date.format(PROMPT_DATE)
        val log = day.visits.joinToString("\n") { visit ->
            val time = visit.visitedAt.toDiaryLocalTime().format(TIME)
            val phase = runCatching { LightPhase.valueOf(visit.phaseName) }
                .getOrNull()?.label.orEmpty()
            "- $time ${visit.title}${if (phase.isNotBlank()) " ($phase)" else ""}"
        }

        return """
            아래는 전북 정읍에서 사진을 찍으며 하루를 보낸 방문 기록이다.
            이걸 바탕으로 짧은 출사 일기를 써라.

            날짜: $dateText
            방문 기록:
            $log

            조건:
            - 첫 줄에 "제목: " 으로 시작하는 한 줄 제목
            - 그다음 줄부터 본문. 3~4문장, 3문단 이내
            - 기록에 없는 장소·사건·감정 사실을 지어내지 말 것
            - 빛과 시간대(골든아워, 한낮 등)를 자연스럽게 녹일 것
            - 1인칭 담백한 문체("~했다"). 이모지 금지
        """.trimIndent()
    }

    /** "제목: ..." 첫 줄을 제목으로 분리합니다. */
    private fun parse(text: String, day: DiaryDay): DiaryDraft {
        val lines = text.trim().lines()
        val titleLine = lines.firstOrNull { it.contains("제목") }
        val title = titleLine
            ?.substringAfter(":")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "${day.date.format(PROMPT_DATE)} 정읍 출사"

        val body = lines
            .filterNot { it === titleLine }
            .joinToString("\n")
            .trim()
            .ifBlank { text.trim() }

        return DiaryDraft(title = title, body = body, generatedByLlm = true)
    }

    companion object {
        private const val MODEL = "gemini-2.5-flash"
        private val PROMPT_DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

object DiaryWriters {
    val default: DiaryWriter by lazy {
        if (ApiKeys.hasGemini) GeminiDiaryWriter() else TemplateDiaryWriter
    }
}
