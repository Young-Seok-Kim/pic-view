package com.youngs.picview.data.model

import com.google.gson.annotations.SerializedName

/**
 * 관광공사 detailIntro2 응답.
 *
 * 유형(contentTypeId)마다 필드 이름이 다릅니다. 같은 "이용요금"인데
 * 관광지·문화시설은 `usefee`, 레포츠는 `usefeeleports` 입니다.
 * 그래서 한 모델에 이름별로 다 받아 두고 [feeText] 에서 골라 씁니다.
 *
 * 값은 금액이 아니라 문장입니다. 정읍 실데이터 예:
 *   - `무료`
 *   - `공연, 전시에 따라 다름`
 *   - (비어 있음)
 * 그래서 숫자로 파싱하지 않고 그대로 보여 줍니다.
 */
data class DetailIntroResponse(
    @SerializedName("response") val response: DetailIntroBody?
)

data class DetailIntroBody(
    @SerializedName("body") val body: DetailIntroItems?
)

data class DetailIntroItems(
    @SerializedName("items") val items: DetailIntroItemWrap?
)

data class DetailIntroItemWrap(
    @SerializedName("item") val item: List<DetailIntroItem>?
)

data class DetailIntroItem(
    // 관광지 · 문화시설
    @SerializedName("usefee") val useFee: String? = null,
    @SerializedName("parkingfee") val parkingFee: String? = null,
    @SerializedName("usetime") val useTime: String? = null,
    @SerializedName("usetimeculture") val useTimeCulture: String? = null,
    @SerializedName("restdate") val restDate: String? = null,
    @SerializedName("restdateculture") val restDateCulture: String? = null,

    // 레포츠
    @SerializedName("usefeeleports") val useFeeLeports: String? = null,
    @SerializedName("parkingfeeleports") val parkingFeeLeports: String? = null,
    @SerializedName("usetimeleports") val useTimeLeports: String? = null,

    // 음식점
    @SerializedName("firstmenu") val firstMenu: String? = null,
    @SerializedName("opentimefood") val openTimeFood: String? = null,
    @SerializedName("restdatefood") val restDateFood: String? = null,
    @SerializedName("parkingfood") val parkingFood: String? = null
) {
    /** 입장료. 유형별로 흩어진 필드 중 값이 있는 것을 씁니다. */
    val feeText: String?
        get() = listOf(useFee, useFeeLeports).pickClean()

    /** 주차료. 음식점은 주차 가능 여부만 오므로 그대로 보여 줍니다. */
    val parkingText: String?
        get() = listOf(parkingFee, parkingFeeLeports, parkingFood).pickClean()

    /** 운영 시간. */
    val hoursText: String?
        get() = listOf(useTime, useTimeCulture, useTimeLeports, openTimeFood).pickClean()

    /** 쉬는 날. */
    val restText: String?
        get() = listOf(restDate, restDateCulture, restDateFood).pickClean()

    /** 보여 줄 것이 하나도 없으면 섹션 자체를 감춥니다. */
    val hasAnything: Boolean
        get() = listOf(feeText, parkingText, hoursText, restText).any { !it.isNullOrBlank() }
}

/**
 * 값이 있는 첫 필드를 골라 HTML 조각을 걷어냅니다.
 *
 * 개요는 [com.youngs.picview.util.OverviewFormatter] 가 정리하지만 이 필드들은
 * 표의 한 칸에 그대로 들어가서, `09:00~18:00<br>` 처럼 태그가 화면에 노출됐습니다.
 * `<br>` 은 줄바꿈으로 살립니다 — 운영 시간 뒤에 붙는 비고가 한 줄로 뭉치지 않게.
 */
private fun List<String?>.pickClean(): String? =
    firstOrNull { !it.isNullOrBlank() }
        ?.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        ?.replace(Regex("<[^>]+>"), "")
        ?.replace("&nbsp;", " ")
        ?.replace("&amp;", "&")
        ?.replace("&lt;", "<")
        ?.replace("&gt;", ">")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
