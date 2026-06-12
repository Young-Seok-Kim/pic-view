package com.youngs.picview.data.model

import com.google.gson.annotations.SerializedName

data class DetailCommonResponse(
    @SerializedName("response") val response: DetailResponse
)

data class DetailResponse(
    @SerializedName("body") val body: DetailBody
)

data class DetailBody(
    @SerializedName("items") val items: DetailItems
)

data class DetailItems(
    @SerializedName("item") val item: List<DetailItem>
)

data class DetailItem(
    @SerializedName("overview") val overview: String // 우리가 필요한 팁 데이터!
)