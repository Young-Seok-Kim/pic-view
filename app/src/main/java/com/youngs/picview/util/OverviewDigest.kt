package com.youngs.picview.util

import androidx.core.text.HtmlCompat

/**
 * 관광공사 개요를 훑어 읽을 수 있는 형태로 줄입니다.
 *
 * [OverviewFormatter] 는 같은 글을 **읽기 좋게** 다듬습니다(문단 나누기,
 * 형광펜). 이것은 **읽지 않아도 되게** 만듭니다 — 태그 몇 개와 세 줄.
 *
 * 나눠 둔 이유는 쓰임이 다르기 때문입니다. 펼친 사람은 전문을 읽고,
 * 펼치지 않은 사람은 이 요약만 봅니다. 접힌 자리에 아무것도 없으면
 * "장소 정보"라는 제목만 남아 무엇이 들었는지 알 수 없습니다.
 *
 * 요약은 새로 쓰지 않고 **원문의 앞 세 문장을 그대로** 씁니다. 관광공사
 * 개요는 첫 문단이 그 장소가 무엇인지를 말하는 구조라 앞이 곧 요지이고,
 * 무엇보다 없는 말을 지어내지 않습니다.
 */
object OverviewDigest {

    /**
     * 태그로 뽑을 낱말.
     *
     * 형광펜 낱말([OverviewFormatter.KEYWORDS] 과 겹치는 것도 있습니다)과
     * 다른 점은 **장소의 종류**를 말하는 것만 골랐다는 것입니다. 태그는
     * "여기가 어떤 곳인가"에 답해야 하므로 빛이나 계절은 넣지 않습니다.
     */
    private val TAG_WORDS = listOf(
        "공연장", "전시", "뮤지컬", "연극", "무용", "국악", "음악회", "미술관", "박물관",
        "체험", "축제", "공원", "산책로", "둘레길", "전망대", "폭포", "계곡", "저수지",
        "호수", "한옥", "고택", "서원", "향교", "사찰", "정자", "누각", "석탑",
        "문화재", "천연기념물", "국립공원", "도립공원", "야영장", "캠핑", "온천"
    )

    /**
     * 요약에 쓸 문장 수.
     *
     * 관광공사 개요는 한 문장이 길어(쉬표로 여러 절을 이음) 세 문장이면
     * 화면에서 다섯 줄을 넘어갑니다. 둘로 줄이고, 그래도 넘치면 화면이
     * 세 줄에서 자릅니다.
     */
    private const val SENTENCE_LIMIT = 2

    /** 태그는 다섯을 넘기면 줄바꿈이 생겨 오히려 훑기 어려워집니다. */
    private const val TAG_LIMIT = 5

    /** "#공연장 #전시 #뮤지컬" — 없으면 빈 목록. */
    fun tagsOf(raw: String?): List<String> {
        val text = plain(raw)
        if (text.isBlank()) return emptyList()
        return TAG_WORDS
            .filter { it in text }
            .distinct()
            .take(TAG_LIMIT)
            .map { "#$it" }
    }

    /** 앞 세 문장. 원문 그대로라 새로 지어낸 말이 섞이지 않습니다. */
    fun summaryOf(raw: String?): String {
        val text = plain(raw)
        if (text.isBlank()) return ""

        return text
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(SENTENCE_LIMIT)
            .joinToString(" ")
    }

    private fun plain(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_COMPACT)
            .toString()
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
