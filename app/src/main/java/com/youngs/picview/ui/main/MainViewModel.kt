package com.youngs.picview.ui.main

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.youngs.picview.domain.light.SunTimes
import com.youngs.picview.domain.score.PhotoScore
import com.youngs.picview.ui.model.SpotItem

class MainViewModel : ViewModel() {

    val weatherData = MutableLiveData<String>()
    val goldenHourData = MutableLiveData<String>()

    /**
     * 관측 기온(℃). 받아오지 못하면 null 입니다.
     *
     * [weatherData] 는 화면에 그대로 띄우는 문장이라 상황에 따라 "지금 정읍은 24℃"
     * 도 되고 "정읍의 촬영지" 도 됩니다. 홈의 기온 칸이 그 문장을 잘라 쓰면 문구를
     * 손질할 때 엉뚱한 낱말이 기온 자리에 박히므로 숫자는 따로 들고 있습니다.
     */
    val temperatureC = MutableLiveData<Double?>(null)

    val isLoading = MutableLiveData<Boolean>(true)

    /** 사용자가 당겨서 새로고침한 경우. 스플래시 대신 목록 위 스피너를 씁니다. */
    val isRefreshing = MutableLiveData(false)

    /** 촬영지 목록을 끝내 받아오지 못했는지. 화면에 '다시 시도' 를 띄우는 데 씁니다. */
    val loadFailed = MutableLiveData(false)

    /** 마지막으로 데이터를 받아온 시각(elapsedRealtime). 자동 갱신 판단에 씁니다. */
    var lastLoadedAt: Long = 0L

    /**
     * 오늘의 일출·일몰. 출사 코스 계산의 기준점입니다.
     * 천문 API 가 실패하면 [SunTimes.EMPTY] 로 남고, 코스는 시계 기준 근사로 돕니다.
     */
    var sunTimes: SunTimes = SunTimes.EMPTY

    /**
     * 스팟별 포토스코어 근거(contentId -> 항목별 점수).
     *
     * SpotItem 에 담아 Bundle 로 넘기지 않는 이유: 상세로 갈 때마다 직렬화 비용이
     * 붙고, 날씨가 바뀌어 점수가 갱신되면 전달된 사본이 낡은 값을 들고 있게 됩니다.
     * 화면 간 공유 ViewModel 에서 조회하는 편이 항상 최신입니다.
     */
    var scoreBreakdowns: Map<String, PhotoScore> = emptyMap()

    fun scoreOf(contentId: String): PhotoScore? = scoreBreakdowns[contentId]

    // 원본 데이터
    val spotData = MutableLiveData<List<SpotItem>>()

    // MediatorLiveData로 변경하여 spotData 변화를 감지
    val filteredSpots = MediatorLiveData<List<SpotItem>>()

    private var currentSpotCategory = SpotCategory.ALL

    var cachedWeather: String? = null
    var cachedGoldenHour: String? = null
    var cachedSpots: List<SpotItem>? = null

    init {
        // spotData가 변경될 때마다 updateFilteredList 실행
        filteredSpots.addSource(spotData) { updateFilteredList() }
    }

    fun setCategory(spotCategory: SpotCategory) {
        currentSpotCategory = spotCategory
        updateFilteredList()
    }

    private fun updateFilteredList() {
        val all = spotData.value ?: return

        filteredSpots.value = when (currentSpotCategory) {
            SpotCategory.ALL -> all
            SpotCategory.NATURE -> all.filter { it.contentTypeId == "12" }
            SpotCategory.CULTURE -> all.filter { it.contentTypeId == "14" }
            SpotCategory.LEPORTS -> all.filter { it.contentTypeId == "28" }
            SpotCategory.FOOD -> all.filter { it.contentTypeId == "39" }
        }
    }

    fun getCurrentCategory() = currentSpotCategory

    fun isCurrentlyGoldenHour(): Boolean = cachedGoldenHour?.contains("진행") == true
}