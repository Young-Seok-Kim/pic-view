package com.youngs.picview.ui.course

import com.youngs.picview.util.applyTopSystemBarInset
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.youngs.picview.MainActivity
import com.youngs.picview.R
import com.youngs.picview.databinding.FragmentCourseInputBinding
import com.youngs.picview.domain.course.CourseRequest
import com.youngs.picview.domain.course.Subject
import com.youngs.picview.domain.light.SunTimes
import com.youngs.picview.ui.main.MainViewModel
import com.youngs.picview.util.TravelMode
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import androidx.lifecycle.lifecycleScope
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.youngs.picview.BuildConfig
import com.youngs.picview.data.api.RetrofitClient
import com.youngs.picview.util.retryOrNull
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * 출사 조건 입력.
 *
 * 일반 여행앱의 예산·인원 입력 대신 촬영 조건을 받습니다.
 * 여기서 받은 값이 그대로 [CourseRequest] 가 되고, 빛 스케줄이 계산됩니다.
 */
class CourseInputFragment : Fragment(R.layout.fragment_course_input),
    MainActivity.TabRoot {

    private var _binding: FragmentCourseInputBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private val courseViewModel: CourseViewModel by activityViewModels()

    /** 다녀올 날짜. 기본은 오늘입니다. */
    private var tripDate: LocalDate = LocalDate.now()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCourseInputBinding.bind(view)

        applyTopInset()
        renderDate()
        renderSunCard()

        binding.cardCourseDate.setOnClickListener { pickDate() }
        binding.btnMakeCourse.setOnClickListener { generate() }

        // 촬영지 로딩이 끝나면 일출·일몰 카드를 갱신합니다.
        mainViewModel.goldenHourData.observe(viewLifecycleOwner) { renderSunCard() }
    }

    // ─────────────────────── 여행 날짜 ───────────────────────

    private fun renderDate() {
        binding.tvCourseDate.text = tripDate.format(
            DateTimeFormatter.ofPattern(getString(R.string.course_date_format), Locale.KOREAN)
        )
    }

    private fun pickDate() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.course_date)
            .setSelection(tripDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .setCalendarConstraints(
                // 지난 날짜의 코스는 짤 이유가 없습니다.
                CalendarConstraints.Builder()
                    .setValidator(DateValidatorPointForward.now())
                    .build()
            )
            .build()

        picker.addOnPositiveButtonClickListener { millis ->
            tripDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            renderDate()
            loadSunTimesFor(tripDate)
        }
        picker.show(parentFragmentManager, "course_date")
    }

    /**
     * 고른 날짜의 일출·일몰을 받아옵니다.
     *
     * 오늘 값을 그대로 쓰면 안 됩니다. 정읍의 일몰은 8월 19:34, 12월 17:25 로
     * 두 시간 넘게 차이가 나서, 몇 주 뒤 코스를 오늘 기준으로 짜면 골든아워
     * 슬롯이 통째로 어긋납니다.
     */
    private fun loadSunTimesFor(date: LocalDate) {
        if (date == LocalDate.now()) {
            // 오늘은 앱 시작 때 이미 받아 둔 값을 씁니다.
            dateSunTimes = null
            renderSunCard()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val locdate = date.format(DateTimeFormatter.BASIC_ISO_DATE)
            val astro = retryOrNull("ASTRO_DATE") {
                RetrofitClient.weatherApiService.getAreaRiseSetInfo(
                    BuildConfig.TOUR_API_KEY, locdate
                )
            }?.response?.body?.items?.item

            dateSunTimes = SunTimes(
                sunrise = SunTimes.parse(astro?.sunrise),
                sunset = SunTimes.parse(astro?.sunset),
                meridian = SunTimes.parse(astro?.meridian)
            )
            _binding ?: return@launch
            renderSunCard()
        }
    }

    /** 오늘이 아닌 날짜를 골랐을 때의 일출·일몰. 오늘이면 null 입니다. */
    private var dateSunTimes: SunTimes? = null

    /** 코스 계산에 쓸 일출·일몰. 고른 날짜 기준입니다. */
    private val activeSunTimes: SunTimes
        get() = dateSunTimes ?: mainViewModel.sunTimes

    private fun applyTopInset() {
        binding.scrollCourseInput.applyTopSystemBarInset()
    }

    // ─────────────────────── 오늘의 빛 ───────────────────────

    private fun renderSunCard() {
        val sun = activeSunTimes
        val fmt = DateTimeFormatter.ofPattern("HH:mm")

        binding.tvSunTimes.text = if (sun.hasData) {
            getString(
                R.string.course_sun_format,
                sun.sunrise!!.format(fmt),
                sun.sunset!!.format(fmt)
            )
        } else {
            getString(R.string.course_sun_unknown)
        }

        // "다음 골든아워까지 N분"은 오늘에만 뜻이 있습니다.
        // 다른 날짜를 골랐으면 남은 시간이 아니라 그날의 골든아워 시각을 알려야 합니다.
        val isToday = tripDate == LocalDate.now()

        binding.tvLightHeading.setText(
            if (isToday) R.string.course_today_light else R.string.course_that_day_light
        )

        val minutes = sun.minutesToNextGolden()
        binding.tvNextGolden.text = when {
            // 다른 날짜를 골랐으면 바로 윗줄이 이미 그날의 일출·일몰을 보여 줍니다.
            // 여기서 같은 시각을 다시 쓰면 카드에 같은 값이 두 번 나옵니다.
            !isToday -> ""
            sun.isGoldenHourNow() -> getString(R.string.course_golden_now)
            minutes != null -> getString(
                R.string.course_next_golden, minutes / 60, minutes % 60
            )
            else -> ""
        }
        binding.tvNextGolden.isVisible = binding.tvNextGolden.text.isNotBlank()
    }

    // ─────────────────────── 코스 생성 ───────────────────────

    private fun generate() {
        val spots = mainViewModel.spotData.value.orEmpty()
        if (spots.isEmpty()) {
            showError(getString(R.string.course_need_spots))
            return
        }

        val request = buildRequest()
        courseViewModel.generate(spots, activeSunTimes, request)

        val course = courseViewModel.course.value
        if (course == null || course.isEmpty) {
            showError(getString(R.string.course_no_result))
            return
        }

        binding.tvCourseError.isVisible = false
        (activity as? MainActivity)?.pushScreen(CourseResultFragment())
    }

    private fun showError(message: String) {
        binding.tvCourseError.text = message
        binding.tvCourseError.isVisible = true
    }

    private fun buildRequest(): CourseRequest {
        val sun = activeSunTimes
        val start = selectedStartTime(sun)
        val hours = selectedDurationHours()

        return CourseRequest(
            startTime = start,
            // 자정을 넘기면 LocalTime 이 되감기므로 23:30 에서 끊습니다.
            endTime = start.plusHours(hours.toLong())
                .takeIf { it > start } ?: LocalTime.of(23, 30),
            travelMode = selectedTravelMode(),
            subjects = selectedSubjects(),
            maxStops = if (hours >= 8) 6 else if (hours >= 5) 5 else 3,
            stayFactor = selectedPartyStayFactor()
        )
    }

    /**
     * 선택한 시간대의 시작 시각.
     *
     * 오늘이면 이미 지난 시각으로 코스를 짤 수 없으므로 지금으로 당깁니다.
     * 다른 날짜면 지금과 견줄 이유가 없어 고른 시각을 그대로 씁니다.
     * (이 처리를 빼면 밤에 내일 일출 코스를 짤 때 시작이 22시로 밀립니다)
     */
    private fun selectedStartTime(sun: SunTimes): LocalTime {
        val now = LocalTime.now()
        val picked = when (binding.chipsWhen.checkedChipId) {
            // 일출 코스는 블루아워(시민박명)부터 잡습니다. 해가 뜬 뒤에
            // 출발하면 가장 좋은 빛의 앞머리를 이동에 써 버립니다.
            R.id.chip_when_sunrise ->
                sun.civilDawn ?: sun.sunrise?.minusMinutes(SunTimes.BLUE_MIN) ?: LocalTime.of(5, 30)
            R.id.chip_when_morning -> LocalTime.of(9, 0)
            R.id.chip_when_sunset ->
                sun.sunset?.minusHours(3) ?: LocalTime.of(16, 0)
            else -> if (tripDate == LocalDate.now()) now else LocalTime.of(9, 0)
        }
        if (tripDate != LocalDate.now()) return picked
        return if (picked < now) now else picked
    }

    private fun selectedDurationHours(): Int = when (binding.chipsDuration.checkedChipId) {
        R.id.chip_duration_short -> 2
        R.id.chip_duration_full -> 10
        else -> 5
    }

    private fun selectedTravelMode(): TravelMode = when (binding.chipsTravel.checkedChipId) {
        R.id.chip_travel_transit -> TravelMode.TRANSIT
        R.id.chip_travel_walk -> TravelMode.WALK
        // KTX 로 정읍역까지 온 뒤 렌트하므로, 시내 이동은 자가용과 같습니다.
        R.id.chip_travel_rail -> TravelMode.CAR
        else -> TravelMode.CAR
    }

    private fun selectedSubjects(): Set<Subject> = buildSet {
        val ids = binding.chipsSubject.checkedChipIds
        if (R.id.chip_subject_landscape in ids) add(Subject.LANDSCAPE)
        if (R.id.chip_subject_architecture in ids) add(Subject.ARCHITECTURE)
        if (R.id.chip_subject_food in ids) add(Subject.FOOD)
        if (R.id.chip_subject_night in ids) add(Subject.NIGHT)
        if (R.id.chip_subject_nature in ids) add(Subject.NATURE)
        if (R.id.chip_subject_healing in ids) add(Subject.HEALING)
        if (R.id.chip_subject_history in ids) add(Subject.HISTORY)
        if (R.id.chip_subject_activity in ids) add(Subject.ACTIVITY)
    }

    /**
     * 인원 구성.
     *
     * 코스 배치 자체는 바꾸지 않고 체류 시간에만 씁니다. 가족·친구가 함께면
     * 한 곳에서 머무는 시간이 길어지므로 같은 시간에 도는 곳이 줄어듭니다.
     */
    private fun selectedPartyStayFactor(): Double = when (binding.chipsParty.checkedChipId) {
        R.id.chip_party_couple -> 1.1
        R.id.chip_party_family -> 1.35
        R.id.chip_party_friends -> 1.25
        else -> 1.0
    }

    override fun scrollToTop() {
        _binding?.scrollCourseInput?.smoothScrollTo(0, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
