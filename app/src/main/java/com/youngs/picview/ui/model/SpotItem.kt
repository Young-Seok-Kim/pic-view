package com.youngs.picview.ui.model

import java.io.Serializable

/**
 * 관광지 정보를 담는 데이터 클래스입니다.
 * @param title 관광지 이름
 * @param imageUrl 이미지를 불러올 URL 주소
 */
data class SpotItem(
    val contentId : String,
    val contentTypeId: String?,
    val title: String,
    val addr1: String,
    val tip: String,
    val imageUrl: String,
    var score: Int = 0,
    val mapx: String, // 경도
    val mapy: String  // 위도
) : Serializable