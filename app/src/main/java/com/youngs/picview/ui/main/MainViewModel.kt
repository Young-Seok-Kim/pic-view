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
}