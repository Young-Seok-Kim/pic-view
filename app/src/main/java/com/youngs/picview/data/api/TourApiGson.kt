package com.youngs.picview.data.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.youngs.picview.data.model.DetailIntroItemWrap
import com.youngs.picview.data.model.DetailItems
import com.youngs.picview.data.model.ImageItems
import com.youngs.picview.data.model.Items
import java.lang.reflect.Type

/**
 * 관광공사 API 응답용 Gson.
 *
 * 결과가 0건이면 `"items": ""` 처럼 객체 자리에 빈 문자열이 옵니다. 기본
 * Gson 은 여기서 파싱 예외를 던져 "없는 장소"와 "네트워크 오류"를 구분할 수
 * 없게 됩니다. 아래 역직렬화기는 객체가 아닌 값을 null 로 읽어, 0건 응답이
 * `items = null, totalCount = 0` 으로 정상 파싱되게 합니다.
 */
object TourApiGson {

    val instance: Gson by lazy {
        val plain = Gson()
        GsonBuilder()
            .registerTypeAdapter(Items::class.java, ObjectOrNull(plain))
            .registerTypeAdapter(DetailItems::class.java, ObjectOrNull(plain))
            .registerTypeAdapter(ImageItems::class.java, ObjectOrNull(plain))
            .registerTypeAdapter(DetailIntroItemWrap::class.java, ObjectOrNull(plain))
            .create()
    }

    /** JSON 객체면 평범하게 읽고, 빈 문자열 같은 다른 값이면 null. */
    private class ObjectOrNull(private val plain: Gson) : JsonDeserializer<Any?> {
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): Any? = if (json.isJsonObject) plain.fromJson(json, typeOfT) else null
    }
}
