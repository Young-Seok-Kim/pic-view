package com.youngs.picview.ui.main

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.youngs.picview.ui.model.SpotItem

class MainViewModel : ViewModel() {

    val weatherData = MutableLiveData<String>()
    val goldenHourData = MutableLiveData<String>()
    val isLoading = MutableLiveData<Boolean>(true)

    /** 사용자가 당겨서 새로고침한 경우. 스플래시 대신 목록 위 스피너를 씁니다. */
    val isRefreshing = MutableLiveData(false)

    /** 촬영지 목록을 끝내 받아오지 못했는지. 화면에 '다시 시도' 를 띄우는 데 씁니다. */
    val loadFailed = MutableLiveData(false)

    /** 마지막으로 데이터를 받아온 시각(elapsedRealtime). 자동 갱신 판단에 씁니다. */
    var lastLoadedAt: Long = 0L

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