package com.youngs.picview.ui.main

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.youngs.picview.ui.model.SpotItem

class MainViewModel : ViewModel() {

    val weatherData = MutableLiveData<String>()
    val goldenHourData = MutableLiveData<String>()
    val isLoading = MutableLiveData<Boolean>(true)

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