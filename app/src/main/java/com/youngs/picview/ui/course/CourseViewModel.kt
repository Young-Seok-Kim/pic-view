package com.youngs.picview.ui.course

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.youngs.picview.data.repository.CourseRepository
import com.youngs.picview.domain.course.CoursePlanner
import com.youngs.picview.domain.course.CourseRequest
import com.youngs.picview.domain.course.Narrators
import com.youngs.picview.domain.course.ShootingCourse
import com.youngs.picview.domain.course.TemplateNarrator
import com.youngs.picview.domain.light.SunTimes
import com.youngs.picview.ui.model.SpotItem
import kotlinx.coroutines.launch

/** 코스 저장 진행 상태. */
enum class SaveState { IDLE, SUCCESS, FAILED }

/**
 * 출사 코스 생성·저장 상태.
 *
 * 코스는 [CoursePlanner] 가 즉시(동기) 만들고, 설명 문구만 나중에 붙습니다.
 * 그래서 LLM 응답을 기다리는 동안에도 타임라인은 이미 화면에 떠 있습니다.
 */
class CourseViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = CourseRepository(app)

    private val _course = MutableLiveData<ShootingCourse?>()
    val course: LiveData<ShootingCourse?> = _course

    private val _narration = MutableLiveData<String>()
    val narration: LiveData<String> = _narration

    /** 설명 문구를 생성하는 중인지. 타임라인 표시와는 무관합니다. */
    private val _narrating = MutableLiveData(false)
    val narrating: LiveData<Boolean> = _narrating

    /**
     * 저장 결과. 화면이 한 번 소비하고 [SaveState.IDLE] 로 되돌립니다.
     *
     * nullable LiveData(`MutableLiveData<Boolean?>`) 대신 열거형을 쓰는 이유:
     * lint 의 NullSafeMutableLiveData 가 nullable 타입 인자를 인식하지 못해
     * `value = null` 을 fatal 로 잡고 **릴리즈 빌드를 통째로 막습니다**.
     * 상태가 셋(대기·성공·실패)이라 열거형이 의미상으로도 더 정확합니다.
     */
    private val _saved = MutableLiveData(SaveState.IDLE)
    val saved: LiveData<SaveState> = _saved

    fun generate(spots: List<SpotItem>, sun: SunTimes, request: CourseRequest) {
        val result = CoursePlanner.plan(spots, sun, request)
        _course.value = result
        _saved.value = SaveState.IDLE

        if (result.isEmpty) {
            _narration.value = ""
            return
        }

        _narrating.value = true

        viewModelScope.launch {
            // 규칙 기반 설명을 먼저 보여 주고, LLM 문구가 오면 갈아끼웁니다.
            // 이렇게 하면 LLM 이 느리거나 실패해도 화면이 비어 보이지 않습니다.
            //
            // 여기에 fallbackSummary() 를 쓰면 안 됩니다. 그 문구는 아래 통계 칩
            // ("5곳 · 5시간 29분 · 20km")에 이미 쓰이고 있어서, 같은 줄이
            // 카드에 두 번 겹쳐 보입니다.
            _narration.value = TemplateNarrator.narrate(result)
            // 널 가능성을 LiveData 대입 전에 끝내 둡니다.
            // 대입식에 nullable 값이 흘러 들어가면 lint 의 NullSafeMutableLiveData 가
            // 널 검사(`!= null`, `isNullOrBlank()`)를 따라가지 못해 오탐을 내고,
            // 그 오탐 하나로 릴리즈 빌드가 통째로 막힙니다.
            val text: String = runCatching { Narrators.default.narrate(result) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                .orEmpty()

            if (text.isNotEmpty()) _narration.value = text
            _narrating.value = false
        }
    }

    fun saveCurrent() {
        val course = _course.value ?: return
        if (course.isEmpty) return

        viewModelScope.launch {
            val ok = runCatching {
                repository.save(course, _narration.value.orEmpty())
            }.isSuccess
            _saved.value = if (ok) SaveState.SUCCESS else SaveState.FAILED
        }
    }

    fun consumeSaved() {
        _saved.value = SaveState.IDLE
    }
}
