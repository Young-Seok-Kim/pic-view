package com.youngs.picview.util

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import androidx.core.text.HtmlCompat

/**
 * 관광공사 개요를 읽기 좋은 형태로 다듬습니다.
 *
 * API 가 주는 overview 는 태그가 섞인 한 덩어리 문장이라 그대로 띄우면
 * 열 줄 넘는 벽이 됩니다. 세 가지를 손봅니다.
 *
 *   1. HTML 태그를 걷어낸다 (<br>, &nbsp; 등이 그대로 보이는 경우가 있음)
 *   2. 두 문장마다 빈 줄을 넣어 문단으로 끊는다
 *   3. 촬영에 쓸모 있는 낱말에 형광펜을 친다
 *
 * 3번이 이 앱다운 부분입니다. 같은 소개글이라도 출사하는 사람에게 필요한 건
 * 연혁이 아니라 "언제·무엇이 보이는가"라서, 그 낱말만 눈에 먼저 들어오게 합니다.
 */
object OverviewFormatter {

    /** 형광펜을 칠 낱말. 빛·시기·피사체처럼 촬영 판단에 쓰이는 것들입니다. */
    private val KEYWORDS = listOf(
        // 빛과 시간
        "일출", "일몰", "해돋이", "노을", "야경", "새벽", "황혼",
        // 시기
        "봄", "여름", "가을", "겨울", "사계절", "개화", "만개", "절정",
        // 피사체
        "단풍", "벚꽃", "구절초", "연꽃", "설경", "물안개", "억새", "수국",
        "계곡", "폭포", "저수지", "호수", "능선", "정상", "전망",
        "한옥", "정자", "누각", "고택", "서원", "석탑",
        // 촬영 여건
        "전망대", "포토존", "산책로", "둘레길", "야간조명", "조명"
    )

    private const val HIGHLIGHT = 0x40E0A32E // 골든 20% — 글자를 덮지 않을 만큼만

    fun format(raw: String?): CharSequence {
        if (raw.isNullOrBlank()) return ""

        val plain = HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_COMPACT)
            .toString()
            .replace(Regex("[ \\t]+"), " ")
            .trim()

        val builder = SpannableStringBuilder(paragraphize(plain))
        highlight(builder)
        return builder
    }

    /**
     * 두 문장마다 빈 줄을 넣습니다.
     *
     * 문장 수로 끊는 이유: 글자 수로 끊으면 문장 한가운데가 갈라집니다.
     * 마침표 뒤에 공백이 오는 자리만 경계로 봅니다(소수점·줄임표 보호).
     */
    private fun paragraphize(text: String): String {
        val sentences = text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        if (sentences.size <= 2) return text

        return sentences
            .chunked(2)
            .joinToString("\n\n") { it.joinToString(" ") }
    }

    private fun highlight(builder: SpannableStringBuilder) {
        val text = builder.toString()
        KEYWORDS.forEach { word ->
            var from = text.indexOf(word)
            while (from >= 0) {
                builder.setSpan(
                    BackgroundColorSpan(HIGHLIGHT),
                    from, from + word.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    from, from + word.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                from = text.indexOf(word, from + word.length)
            }
        }
    }
}
