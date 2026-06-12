package com.youngs.picview.data.model

import com.google.gson.annotations.SerializedName

data class TourResponse(
    @SerializedName("response") val response: Response
)

data class Response(
    @SerializedName("body") val body: Body
)

data class Body(
    @SerializedName("items") val items: Items
)

data class Items(
    @SerializedName("item") val item: List<TourItem>
)

data class TourItem(
    @SerializedName("contentid") val contentid: String,
    @SerializedName("contenttypeid") val contentTypeId: String?,
    @SerializedName("title") val title: String,
    @SerializedName("addr1") val addr1: String,
    @SerializedName("firstimage") val firstimage: String?,
    @SerializedName("mapx") val mapx: String,
    @SerializedName("mapy") val mapy: String
)