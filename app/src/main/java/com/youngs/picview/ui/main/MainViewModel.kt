package com.youngs.picview.ui.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.youngs.picview.ui.model.SpotItem

class MainViewModel : ViewModel() {
    // UI가 관찰할 수 있는 데이터 홀더
    val weatherData = MutableLiveData<String>()
    val goldenHourData = MutableLiveData<String>()
    val spotData = MutableLiveData<List<SpotItem>>()

    var cachedWeather: String? = null
    var cachedGoldenHour: String? = null
    var cachedSpots: List<SpotItem>? = null

    val isLoading = MutableLiveData<Boolean>(true)

    val filteredSpots = MutableLiveData<List<SpotItem>>()
    private var currentSpotCategory = SpotCategory.ALL

    fun setRawData(spots: List<SpotItem>) {
        cachedSpots = spots
        filterData() // 데이터 처음 들어왔을 때 필터링
    }

    fun setCategory(spotCategory: SpotCategory) {
        currentSpotCategory = spotCategory
        filterData()
    }

    private fun filterData() {
        val all = cachedSpots ?: return

        filteredSpots.value = when (currentSpotCategory) {
            SpotCategory.ALL -> all
            // null 체크를 위해 safe call(?.) 후 비교 (null이면 false가 됨)
            SpotCategory.NATURE -> all.filter { it.contentTypeId == "12" }
            SpotCategory.CULTURE -> all.filter { it.contentTypeId == "14" }
            SpotCategory.LEPORTS -> all.filter { it.contentTypeId == "28" }
            SpotCategory.FOOD -> all.filter { it.contentTypeId == "39" }
        }
    }
}