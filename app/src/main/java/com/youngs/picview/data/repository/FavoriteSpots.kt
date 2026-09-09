package com.youngs.picview.data.repository

import android.content.Context
import com.youngs.picview.BuildConfig
import com.youngs.picview.data.api.RetrofitClient
import com.youngs.picview.ui.model.SpotItem
import com.youngs.picview.util.AppPrefs

/**
 * 찜한 촬영지를 [SpotItem] 으로 돌려줍니다.
 *
 * 홈이 들고 있는 촬영지 목록은 관광공사 API 에서 받은 것이라, 관광공사가
 * 항목을 내리거나 바꾸면 예전에 찜해 둔 곳이 목록에서 사라집니다. 그러면
 * "찜 3곳"인데 목록에는 2곳만 보이는 일이 생깁니다. 그래서 찜은 저장할 때
 * 장소 정보를 함께 남기고([AppPrefs.toggleFavorite]), 목록을 그릴 때는
 * 세 곳에서 차례로 찾습니다.
 *
 *  1. 지금 홈이 들고 있는 목록 (가장 최신, 점수도 있음)
 *  2. 찜할 때 남긴 스냅샷 (오프라인에서도 보임)
 *  3. 관광공사 상세 조회 (스냅샷이 없는 예전 찜 — 찾으면 스냅샷으로 남김)
 *
 * 관광공사가 "그런 장소는 없다"고 답한 찜은 [Resolved.gone] 으로 알려 줍니다.
 * 부르는 쪽이 찜에서 지워야 개수와 목록이 다시 맞습니다. 네트워크 오류는
 * 없는 것과 다르므로 지우지 않습니다.
 */
object FavoriteSpots {

    class Resolved(
        val spots: List<SpotItem>,
        /** 관광공사에 더는 없는 찜의 contentId. */
        val gone: List<String>,
    )

    /** [known] 은 홈이 들고 있는 촬영지. 여기 있는 것은 그것을 우선합니다. */
    suspend fun resolve(context: Context, known: List<SpotItem>): Resolved {
        val ids = AppPrefs.favoriteSpots(context)
        if (ids.isEmpty()) return Resolved(emptyList(), emptyList())
        val byId = known.associateBy { it.contentId }
        val spots = mutableListOf<SpotItem>()
        val gone = mutableListOf<String>()
        for (id in ids) {
            val local = byId[id]?.also { AppPrefs.saveFavoriteSnapshot(context, it) }
                ?: AppPrefs.favoriteSnapshot(context, id)
            if (local != null) {
                spots += local
                continue
            }
            when (val fetched = fetch(id)) {
                is Fetched.Found -> {
                    AppPrefs.saveFavoriteSnapshot(context, fetched.spot)
                    spots += fetched.spot
                }
                Fetched.Gone -> gone += id
                Fetched.Unknown -> Unit
            }
        }
        return Resolved(spots, gone)
    }

    private sealed interface Fetched {
        class Found(val spot: SpotItem) : Fetched
        object Gone : Fetched
        object Unknown : Fetched
    }

    private suspend fun fetch(contentId: String): Fetched {
        val body = runCatching {
            RetrofitClient.tourApiService.getDetailCommon(
                serviceKey = BuildConfig.TOUR_API_KEY,
                contentId = contentId
            ).response?.body
        }.getOrNull() ?: return Fetched.Unknown
        val item = body.items?.item?.firstOrNull()
        if (item == null) {
            // 응답은 정상인데 항목이 없으면 관광공사에서 내려간 장소입니다.
            return if (body.totalCount == 0) Fetched.Gone else Fetched.Unknown
        }
        val title = item.title?.takeIf { it.isNotBlank() } ?: return Fetched.Unknown
        return Fetched.Found(
            SpotItem(
                contentId = contentId,
                contentTypeId = item.contenttypeid,
                title = title,
                addr1 = item.addr1.orEmpty(),
                tip = "",
                imageUrl = item.firstimage.orEmpty(),
                mapx = item.mapx.orEmpty(),
                mapy = item.mapy.orEmpty()
            )
        )
    }
}
