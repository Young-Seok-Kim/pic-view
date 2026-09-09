package com.youngs.picview.data.model

import com.google.gson.annotations.SerializedName

/** 관광공사 detailCommon2 응답. overview 가 비어 있는 장소가 있습니다. */
data class DetailCommonResponse(
    @SerializedName("response") val response: DetailResponse? = null
)

data class DetailResponse(
    @SerializedName("body") val body: DetailBody? = null
)

data class DetailBody(
    /** 0건이면 `""` 로 와서 null 이 됩니다(TourApiGson). 그때는 [totalCount] 로 판단합니다. */
    @SerializedName("items") val items: DetailItems? = null,
    @SerializedName("totalCount") val totalCount: Int? = null
)

data class DetailItems(
    @SerializedName("item") val item: List<DetailItem>? = null
)

data class DetailItem(
    /** 장소 개요. 오디오 가이드(TTS) 원문으로도 씁니다. */
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("homepage") val homepage: String? = null,
    // 아래는 홈 목록에 없는 찜 장소를 id 로만 알 때 SpotItem 을 만들기 위한 기본 정보.
    @SerializedName("title") val title: String? = null,
    @SerializedName("addr1") val addr1: String? = null,
    @SerializedName("firstimage") val firstimage: String? = null,
    @SerializedName("mapx") val mapx: String? = null,
    @SerializedName("mapy") val mapy: String? = null,
    @SerializedName("contenttypeid") val contenttypeid: String? = null
)
