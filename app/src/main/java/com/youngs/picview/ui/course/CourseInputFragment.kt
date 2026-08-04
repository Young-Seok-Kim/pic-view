package com.youngs.picview.ui.course

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCourseInputBinding.bind(view)

        applyTopInset()
        renderSunCard()
        binding.btnMakeCourse.setOnClickListener { generate() }

        // 촬영지 로딩이 끝나면 일출·일몰 카드를 갱신합니다.
        mainViewModel.goldenHourData.observe(viewLifecycleOwner) { renderSunCard() }
    }

    private fun applyTopInset() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollCourseInput) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top)
            insets
        }
    }

    // ─────────────────────── 오늘의 빛 ───────────────────────

    private fun renderSunCard() {
        val sun = mainViewModel.sunTimes
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

        val minutes = sun.minutesToNextGolden()
        binding.tvNextGolden.text = when {
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
        courseViewModel.generate(spots, mainViewModel.sunTimes, request)

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
        val sun = mainViewModel.sunTimes
        val start = selectedStartTime(sun)
        val hours = selectedDurationHours()

        return CourseRequest(
            startTime = start,
            // 자정을 넘기면 LocalTime 이 되감기므로 23:30 에서 끊습니다.
            endTime = start.plusHours(hours.toLong())
                .takeIf { it > start } ?: LocalTime.of(23, 30),
            travelMode = selectedTravelMode(),
            subjects = selectedSubjects(),
            maxStops = if (hours >= 8) 6 else if (hours >= 5) 5 else 3
        )
    }

    /** 선택한 시간대의 시작 시각. 이미 지난 시각이면 지금으로 당깁니다. */
    private fun selectedStartTime(sun: SunTimes): LocalTime {
        val now = LocalTime.now()
        val picked = when (binding.chipsWhen.checkedChipId) {
            R.id.chip_when_sunrise ->
                sun.sunrise?.minusMinutes(SunTimes.GOLDEN_MIN) ?: LocalTime.of(5, 30)
            R.id.chip_when_morning -> LocalTime.of(9, 0)
            R.id.chip_when_sunset ->
                sun.sunset?.minusHours(3) ?: LocalTime.of(16, 0)
            else -> now
        }
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
        else -> TravelMode.CAR
    }

    private fun selectedSubjects(): Set<Subject> = buildSet {
        val ids = binding.chipsSubject.checkedChipIds
        if (R.id.chip_subject_landscape in ids) add(Subject.LANDSCAPE)
        if (R.id.chip_subject_architecture in ids) add(Subject.ARCHITECTURE)
        if (R.id.chip_subject_food in ids) add(Subject.FOOD)
        if (R.id.chip_subject_night in ids) add(Subject.NIGHT)
    }

    override fun scrollToTop() {
        _binding?.scrollCourseInput?.smoothScrollTo(0, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
