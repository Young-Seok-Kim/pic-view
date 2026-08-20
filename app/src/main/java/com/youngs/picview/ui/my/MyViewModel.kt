package com.youngs.picview.ui.my

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.youngs.picview.data.local.SavedCourseWithStops
import com.youngs.picview.data.repository.CourseRepository
import kotlinx.coroutines.launch

/** MY 탭 상태. 저장한 코스와 방문 통계를 DB 에서 흘려 보냅니다. */
class MyViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = CourseRepository(app)

    val courses: LiveData<List<SavedCourseWithStops>> =
        repository.observeCourses().asLiveData()

    val courseCount: LiveData<Int> = repository.observeCourseCount().asLiveData()

    val visitedSpotCount: LiveData<Int> = repository.observeVisitedSpotCount().asLiveData()

    /** 방문 기록 전체. 이번 달 통계·최근 촬영·촬영 스타일이 여기서 계산됩니다. */
    val visits: LiveData<List<com.youngs.picview.data.local.VisitLogEntity>> =
        repository.observeVisits().asLiveData()

    fun deleteCourse(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
