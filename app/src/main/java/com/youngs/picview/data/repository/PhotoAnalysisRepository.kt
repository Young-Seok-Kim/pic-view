package com.youngs.picview.data.repository

import android.content.Context
import android.net.Uri
import com.youngs.picview.data.local.PhotoAnalysisEntity
import com.youngs.picview.data.local.PicViewDatabase
import com.youngs.picview.domain.photo.PhotoAnalyzer
import com.youngs.picview.domain.photo.PhotoReading
import com.youngs.picview.domain.photo.PhotoTraits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 사진 읽기 결과 저장소.
 *
 * 한 장은 한 번만 읽습니다. 읽은 결과는 [PhotoAnalysisEntity] 로 남고,
 * 방법의 판([PhotoTraits.VERSION])이 올라가면 그때만 다시 읽습니다.
 */
class PhotoAnalysisRepository(context: Context) {

    private val dao = PicViewDatabase.get(context).photoAnalysisDao()
    private val analyzer = PhotoAnalyzer(context)

    /** 한 번에 한 묶음만 읽습니다. MY 탭이 두 번 열려도 같은 사진을 두 번 읽지 않습니다. */
    private val reading = Mutex()

    /**
     * 이 프로세스에서 읽기에 실패한 사진. 지워졌거나 열 수 없는 사진을
     * 방문 목록이 갱신될 때마다 또 여는 일이 없게 합니다. 앱을 다시 켜면
     * 한 번 더 시도합니다.
     */
    private val failed = mutableSetOf<String>()

    /** 사진 주소 → 읽은 결과. 옛 판으로 읽은 것은 뺍니다. */
    fun observeReadings(): Flow<Map<String, PhotoReading>> =
        dao.observeAll().map { rows ->
            rows.filter { it.version >= PhotoTraits.VERSION }
                .associate { it.uri to it.toReading() }
        }

    /**
     * 아직 안 읽은 사진을 차례로 읽어 저장합니다.
     *
     * 저장할 때마다 [observeReadings] 가 새 값을 흘려 보내므로, 화면은
     * 한 장 읽힐 때마다 조금씩 채워집니다.
     */
    suspend fun readMissing(uris: Collection<String>) {
        if (uris.isEmpty()) return
        reading.withLock {
            val done = dao.analyzedUris(PhotoTraits.VERSION).toSet()
            val todo = uris.distinct().filter { it !in done && it !in failed }
            for (uri in todo) {
                val result = analyzer.read(Uri.parse(uri))
                if (result == null) {
                    failed += uri
                    continue
                }
                dao.upsert(result.toEntity(uri))
            }
        }
    }

    suspend fun clear() {
        dao.deleteAll()
        failed.clear()
    }

    private fun PhotoAnalysisEntity.toReading() = PhotoReading(
        reflection = reflection,
        silhouette = silhouette,
        waterside = waterside,
        sunset = sunset,
        labels = labels.split(',').filter { it.isNotBlank() }
    )

    private fun PhotoReading.toEntity(uri: String) = PhotoAnalysisEntity(
        uri = uri,
        reflection = reflection,
        silhouette = silhouette,
        waterside = waterside,
        sunset = sunset,
        labels = labels.joinToString(","),
        version = PhotoTraits.VERSION,
        analyzedAt = System.currentTimeMillis()
    )
}
