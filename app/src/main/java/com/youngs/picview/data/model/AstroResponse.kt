package com.youngs.picview.data.model

data class AstroResponse(val response: AstroBody)
data class AstroBody(val body: AstroItems)
data class AstroItems(
    val items: AstroItemContainer, // null이 아님을 보장
    val totalCount: Int
)
data class AstroItemContainer(
    val item: AstroItem // 이제 item은 단일 객체로 확실히 들어옵니다
)
data class AstroItem(
    val sunrise: String,
    val sunset: String
)