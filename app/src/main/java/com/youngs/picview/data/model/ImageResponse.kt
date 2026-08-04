package com.youngs.picview.data.model

import com.google.gson.annotations.SerializedName

/** 관광공사 detailImage2 응답. 사진이 한 장도 없는 장소가 흔합니다. */
data class ImageResponse(
    @SerializedName("response") val response: ImageResponseBody? = null
)

data class ImageResponseBody(
    @SerializedName("body") val body: ImageBody? = null
)

data class ImageBody(
    @SerializedName("items") val items: ImageItems? = null
)

data class ImageItems(
    @SerializedName("item") val item: List<ImageItem>? = null
)

data class ImageItem(
    @SerializedName("originimgurl") val originImgUrl: String? = null,
    @SerializedName("smallimageurl") val smallImageUrl: String? = null
)
