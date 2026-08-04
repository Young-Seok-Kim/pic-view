package com.youngs.picview.ui.diary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.youngs.picview.data.repository.DiaryRepository
import com.youngs.picview.domain.diary.DiaryDay
import kotlinx.coroutines.launch

class DiaryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = DiaryRepository(app)

    val days: LiveData<List<DiaryDay>> = repository.observeDays().asLiveData()

    /** 지금 일기를 만들고 있는 날짜 키. 해당 카드만 로딩 표시합니다. */
    private val _generating = MutableLiveData<String?>(null)
    val generating: LiveData<String?> = _generating

    fun generate(day: DiaryDay) {
        if (_generating.value != null) return
        _generating.value = day.dateKey

        viewModelScope.launch {
            runCatching { repository.generate(day) }
            _generating.value = null
        }
    }

    fun delete(dateKey: String) {
        viewModelScope.launch { repository.delete(dateKey) }
    }
}
