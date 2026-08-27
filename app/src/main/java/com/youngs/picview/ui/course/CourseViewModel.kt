package com.youngs.picview.ui.course

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.youngs.picview.BuildConfig
import com.youngs.picview.data.api.RetrofitClient
import com.youngs.picview.data.repository.CourseRepository
import com.youngs.picview.domain.course.CoursePlanner
import com.youngs.picview.domain.course.CourseRequest
import com.youngs.picview.domain.course.Narrators
import com.youngs.picview.domain.course.ShootingCourse
import com.youngs.picview.domain.course.TemplateNarrator
import com.youngs.picview.domain.light.SunTimes
import com.youngs.picview.ui.model.SpotItem
import com.youngs.picview.util.retryOrNull
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

    /**
     * 이 코스의 출사 예정일. [generate] 를 부른 화면이 넘겨 줍니다.
     *
     * 결과 화면도 이 값을 읽습니다 — 날짜가 안 보이면 내일 새벽 코스를
     * 오늘 밤에 짜 놓고도 언제 것인지 알 수 없습니다.
     */
    var planDate: java.time.LocalDate = java.time.LocalDate.now()
        private set

    /**
     * 마지막 생성에 쓴 재료.
     *
     * 결과 화면에서 날짜·시각만 바꿔 다시 짤 수 있어야 하는데, 그러려면
     * 촬영지 풀과 나머지 조건(동행·체류·이동·목적)을 그대로 들고 있어야
     * 합니다. 없으면 입력 화면까지 되돌아가 처음부터 고르게 됩니다.
     */
    private var lastSpots: List<SpotItem> = emptyList()
    private var lastRequest: CourseRequest? = null

    /** 결과 화면에서 날짜·시각을 고칠 수 있는 상태인지. 저장된 코스는 불가. */
    val canReschedule: Boolean get() = lastRequest != null

    /** 지금 코스의 출발 시각. 날짜를 바꿔도 시각은 그대로 이어 갑니다. */
    val startTime: java.time.LocalTime?
        get() = lastRequest?.startTime

    /** 다시 짜는 중인지. 천문 조회가 끼어 있어 즉시 끝나지 않습니다. */
    private val _rescheduling = MutableLiveData(false)
    val rescheduling: LiveData<Boolean> = _rescheduling

    fun generate(
        spots: List<SpotItem>,
        sun: SunTimes,
        request: CourseRequest,
        date: java.time.LocalDate = java.time.LocalDate.now()
    ) {
        planDate = date
        lastSpots = spots
        lastRequest = request

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

    /**
     * 저장한 코스를 화면에 올리면서 **다시 짤 재료도 되살립니다.**
     *
     * 처음에는 저장한 코스의 날짜를 못 고치게 막았습니다. 조건(촬영지 풀 ·
     * 동행 · 체류)이 저장돼 있지 않아 다시 짤 수 없다고 봤기 때문입니다.
     * 그런데 "이건 내일 하자"가 가장 자주 생기는 자리가 바로 저장해 둔
     * 코스입니다. 막아 두면 정작 필요한 곳에서 못 쓰게 됩니다.
     *
     * 없는 것은 되살릴 수 있는 만큼만 되살립니다.
     *   · 촬영지 풀 — 지금 목록 전체로 갈음합니다
     *   · 출발·종료 시각 — 첫 정거장 도착과 마지막 정거장 출발에서 역산
     *   · 이동 수단 — 저장돼 있습니다
     *   · 곳 수 — 저장된 정거장 수를 그대로 상한으로 둡니다
     *   · 동행·촬영 목적 — 남아 있지 않아 기본값(전체)으로 둡니다
     *
     * 다시 짠 결과는 저장본을 덮어쓰지 않습니다. 새 코스로 화면에 뜨고,
     * 마음에 들면 사용자가 다시 저장합니다.
     */
    fun adoptSaved(
        course: ShootingCourse,
        date: java.time.LocalDate,
        summary: String,
        spots: List<SpotItem>
    ) {
        planDate = date
        lastSpots = spots

        val start = course.startTime
        val end = course.endTime
        lastRequest = if (spots.isEmpty() || start == null || end == null || end <= start) {
            // 되살릴 수 없으면 조용히 못 고치는 상태로 둡니다.
            // 반쯤 복원한 조건으로 엉뚱한 코스를 내놓는 것보다 낫습니다.
            null
        } else {
            CourseRequest(
                startTime = start,
                endTime = end,
                travelMode = course.travelMode,
                maxStops = course.stops.size.coerceAtLeast(1)
            )
        }

        _course.value = course
        _narration.value = summary
        _saved.value = SaveState.IDLE
    }

    /**
     * 날짜·출발 시각만 바꿔 같은 조건으로 다시 짭니다.
     *
     * 결과를 보고 나서야 "한 시간 늦게 나갈걸", "이건 내일 하자"가 생깁니다.
     * 그때마다 입력 화면으로 되돌아가 동행·체류·이동·목적을 다시 고르게
     * 하면 아무도 고치지 않습니다. 나머지 조건은 그대로 두고 둘만 바꿉니다.
     *
     * 고른 날짜의 일출·일몰을 새로 받아오는 것이 핵심입니다. 오늘 값을
     * 그대로 쓰면 몇 주 뒤 코스의 골든아워 슬롯이 통째로 어긋납니다
     * (정읍 일몰은 8월 19:34, 12월 17:25).
     *
     * @param fallbackSun 천문 조회가 실패했을 때 쓸 값(대개 오늘 것).
     */
    fun reschedule(
        date: java.time.LocalDate,
        startAt: java.time.LocalTime,
        fallbackSun: SunTimes
    ) {
        val base = lastRequest ?: return
        val spots = lastSpots
        if (spots.isEmpty()) return

        _rescheduling.value = true

        viewModelScope.launch {
            val sun = sunTimesFor(date) ?: fallbackSun

            // 체류 시간(끝 - 시작)은 유지한 채 시작만 옮깁니다.
            // 자정을 넘기면 LocalTime 이 되감기므로 23:30 에서 끊습니다.
            val span = java.time.Duration.between(base.startTime, base.endTime).toMinutes()
            val end = startAt.plusMinutes(span)
                .takeIf { it > startAt } ?: java.time.LocalTime.of(23, 30)

            _rescheduling.value = false
            generate(spots, sun, base.copy(startTime = startAt, endTime = end), date)
        }
    }

    /**
     * 그 날짜의 일출·일몰. 오늘이면 호출자가 이미 들고 있으므로 null 을 줍니다.
     * 실패해도 null 로 돌아가 호출자가 가진 값으로 이어 갑니다.
     */
    private suspend fun sunTimesFor(date: java.time.LocalDate): SunTimes? {
        if (date == java.time.LocalDate.now()) return null

        val locdate = date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
        val astro = retryOrNull("ASTRO_DATE") {
            RetrofitClient.weatherApiService.getAreaRiseSetInfo(
                BuildConfig.TOUR_API_KEY, locdate
            )
        }?.response?.body?.items?.item ?: return null

        return SunTimes(
            sunrise = SunTimes.parse(astro.sunrise),
            sunset = SunTimes.parse(astro.sunset),
            meridian = SunTimes.parse(astro.meridian)
        )
    }

    fun saveCurrent() {
        val course = _course.value ?: return
        if (course.isEmpty) return

        viewModelScope.launch {
            val ok = runCatching {
                repository.save(course, _narration.value.orEmpty(), planDate)
            }.isSuccess
            _saved.value = if (ok) SaveState.SUCCESS else SaveState.FAILED
        }
    }

    fun consumeSaved() {
        _saved.value = SaveState.IDLE
    }
}
