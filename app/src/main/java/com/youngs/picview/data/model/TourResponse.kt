package com.youngs.picview.data.model

import com.google.gson.annotations.SerializedName

/**
 * 관광공사 areaBasedList2 응답.
 *
 * Gson 은 리플렉션으로 객체를 만들기 때문에 Kotlin 의 non-null 검사를 우회합니다.
 * `val title: String` 으로 선언해도 응답에 필드가 없으면 null 이 들어오고,
 * 그 값을 처음 쓰는 쪽에서 NPE 가 납니다.
 *
 * 관광공사 API 는 실제로 주소·좌표·이미지가 비어 있는 항목을 섞어서 주므로
 * 서버 응답 계층은 전부 nullable 로 두고, 도메인 모델(SpotItem)로 옮길 때
 * 기본값을 채웁니다.
 */
data class TourResponse(
    @SerializedName("response") val response: Response? = null
)

data class Response(
    @SerializedName("body") val body: Body? = null
)

data class Body(
    @SerializedName("items") val items: Items? = null,
    @SerializedName("totalCount") val totalCount: Int? = null
)

data class Items(
    @SerializedName("item") val item: List<TourItem>? = null
)

data class TourItem(
    @SerializedName("contentid") val contentid: String? = null,
    @SerializedName("contenttypeid") val contentTypeId: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("addr1") val addr1: String? = null,
    @SerializedName("firstimage") val firstimage: String? = null,
    @SerializedName("mapx") val mapx: String? = null,
    @SerializedName("mapy") val mapy: String? = null,
    /** 수정일(yyyyMMddHHmmss). 정보 최신성 점수에 씁니다. */
    @SerializedName("modifiedtime") val modifiedTime: String? = null
)
