package com.youngs.picview.data.model

import com.google.gson.annotations.SerializedName

data class ImageResponse(
    @SerializedName("response") val response: ImageResponseBody
)

data class ImageResponseBody(
    @SerializedName("body") val body: ImageBody
)

data class ImageBody(
    @SerializedName("items") val items: ImageItems
)

data class ImageItems(
    @SerializedName("item") val item: List<ImageItem>? // 데이터가 없을 수도 있으므로 List?
)

data class ImageItem(
    @SerializedName("originimgurl") val originImgUrl: String, // 원본 이미지
    @SerializedName("smallimageurl") val smallImageUrl: String // 썸네일
)