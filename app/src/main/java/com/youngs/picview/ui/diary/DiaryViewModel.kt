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

    /**
     * 사용자가 고친 일기를 저장합니다.
     *
     * 고친 뒤에는 `generatedByLlm` 을 false 로 둡니다. 이 값은 통계·디버깅용
     * 이라 화면에는 안 나오지만, 사람이 손댄 글을 LLM 생성물로 세면 숫자가
     * 틀립니다.
     */
    fun saveEdit(day: DiaryDay, title: String, body: String) {
        viewModelScope.launch { repository.saveEdit(day, title, body) }
    }
}
