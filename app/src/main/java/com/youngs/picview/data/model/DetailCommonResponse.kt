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
    @SerializedName("items") val items: DetailItems? = null
)

data class DetailItems(
    @SerializedName("item") val item: List<DetailItem>? = null
)

data class DetailItem(
    /** 장소 개요. 오디오 가이드(TTS) 원문으로도 씁니다. */
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("homepage") val homepage: String? = null
)
