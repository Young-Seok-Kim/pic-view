package com.youngs.picview.ui.course

import com.youngs.picview.util.applyTopSystemBarInset
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.youngs.picview.MainActivity
import com.youngs.picview.R
import com.youngs.picview.data.repository.CourseRepository
import com.youngs.picview.data.repository.planDate
import com.youngs.picview.data.repository.toShootingCourse
import com.youngs.picview.databinding.FragmentCourseResultBinding
import com.youngs.picview.domain.course.ShootingCourse
import com.youngs.picview.ui.detail.DetailFragment
import com.youngs.picview.ui.main.MainViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 빛 스케줄 타임라인.
 *
 * 두 가지 경로로 열립니다.
 *  - 방금 만든 코스 : [CourseViewModel] 이 들고 있는 결과를 그립니다.
 *  - 저장한 코스    : [ARG_COURSE_ID] 로 열면 Room 에서 불러와 같은 화면에 그립니다.
 *
 * 저장한 코스를 볼 때는 '저장' 버튼을 숨깁니다(이미 저장돼 있으니까).
 */
class CourseResultFragment : Fragment(R.layout.fragment_course_result) {

    private var _binding: FragmentCourseResultBinding? = null
    private val binding get() = _binding!!

    private val courseViewModel: CourseViewModel by activityViewModels()

    /** 천문 조회가 실패했을 때 쓸 오늘의 일출·일몰. */
    private val mainViewModel: MainViewModel by activityViewModels()

    private val savedCourseId: Long?
        get() = arguments?.getLong(ARG_COURSE_ID, -1L)?.takeIf { it >= 0L }

    private lateinit var adapter: CourseStopAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCourseResultBinding.bind(view)

        applyTopInset()

        adapter = CourseStopAdapter { stop ->
            (activity as? MainActivity)?.pushScreen(DetailFragment.newInstance(stop.spot))
        }
        binding.rvCourseStops.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCourseStops.adapter = adapter

        binding.btnResultBack.setOnClickListener { parentFragmentManager.popBackStack() }

        val courseId = savedCourseId
        if (courseId != null) showSavedCourse(courseId) else showGeneratedCourse()
    }

    // ─────────────────── 방금 만든 코스 ───────────────────

    private fun showGeneratedCourse() {
        binding.btnResultSave.isVisible = true
        binding.btnResultSave.setOnClickListener { courseViewModel.saveCurrent() }

        courseViewModel.saved.observe(viewLifecycleOwner) { state ->
            val message = when (state) {
                SaveState.SUCCESS -> R.string.course_saved
                SaveState.FAILED -> R.string.course_save_failed
                SaveState.IDLE -> return@observe
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            courseViewModel.consumeSaved()
        }

        // 설명 문구는 규칙 요약 → LLM 문장 순으로 두 번 들어옵니다.
        // 타임라인은 이미 떠 있으므로 문구 생성 중에도 화면을 막지 않습니다.
        observeCourse(savedTitle = null)
    }

    // ─────────────────── 저장한 코스 ───────────────────

    private fun showSavedCourse(courseId: Long) {
        binding.btnResultSave.isVisible = false
        binding.progressResult.isVisible = true

        viewLifecycleOwner.lifecycleScope.launch {
            val saved = runCatching {
                CourseRepository(requireContext()).get(courseId)
            }.getOrNull()

            val view = _binding ?: return@launch
            view.progressResult.isVisible = false

            if (saved == null) {
                view.tvResultNarration.text = getString(R.string.course_saved_missing)
                return@launch
            }

            view.tvResultTitle.text = saved.course.title

            // 저장한 코스도 날짜·출발 시각을 고칠 수 있게 재료를 되살립니다.
            // 되살린 뒤로는 방금 만든 코스와 같은 길을 탑니다 — 화면은
            // 언제나 ViewModel 이 들고 있는 코스를 그립니다.
            courseViewModel.adoptSaved(
                course = saved.toShootingCourse(),
                date = saved.course.planDate(),
                summary = saved.course.summary,
                spots = mainViewModel.spotData.value.orEmpty()
            )
            observeCourse(savedTitle = saved.course.title)
        }
    }

    /**
     * ViewModel 의 코스를 화면에 겁니다.
     *
     * 저장한 코스로 들어왔더라도 날짜를 고치면 **새 코스**가 되므로,
     * 그때부터는 저장 버튼이 나타나야 합니다. 첫 방출은 저장본 그대로라
     * 세지 않고, 그 뒤의 방출부터 "다시 짠 것"으로 봅니다.
     */
    private fun observeCourse(savedTitle: String?) {
        var emissions = 0

        courseViewModel.course.observe(viewLifecycleOwner) { course ->
            course ?: return@observe
            emissions++

            val rescheduled = savedTitle != null && emissions > 1
            if (rescheduled) {
                // 다시 짠 코스는 아직 저장 전입니다. 제목도 새 코스의 것으로.
                binding.btnResultSave.isVisible = true
                binding.tvResultTitle.setText(R.string.course_today_title)
            }

            renderDate(courseViewModel.planDate, editable = courseViewModel.canReschedule)
            render(course, courseViewModel.planDate)
        }

        courseViewModel.narration.observe(viewLifecycleOwner) {
            binding.tvResultNarration.text = it
        }
        courseViewModel.narrating.observe(viewLifecycleOwner) { generating ->
            binding.tvResultNarration.alpha = if (generating) 0.7f else 1f
        }

        // 날짜를 바꾸면 그 날짜의 일출·일몰을 새로 받아오는 동안 잠깐 뜹니다.
        courseViewModel.rescheduling.observe(viewLifecycleOwner) { busy ->
            binding.progressResult.isVisible = busy
        }
    }

    // ─────────────────── 공통 렌더 ───────────────────

    private fun render(course: ShootingCourse, date: java.time.LocalDate) {
        // 타임라인이 스스로 날짜를 말하게 합니다. 시각만 있으면 그것이
        // 어느 날의 13시인지 알 수 없고, 하루를 넘기는 코스에서는
        // "둘째 날 06시"와 "첫날 06시"가 같은 줄로 보입니다.
        adapter.submitList(CourseStopAdapter.rowsOf(course.stops, date))

        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        val sun = course.sun
        if (sun.hasData) {
            binding.tvResultSunrise.text =
                getString(R.string.course_sunrise_at, sun.sunrise!!.format(fmt))
            binding.tvResultSunset.text =
                getString(R.string.course_sunset_at, sun.sunset!!.format(fmt))
        } else {
            binding.tvResultSunrise.text = getString(R.string.course_sun_unknown)
            binding.tvResultSunset.text = ""
        }

        renderStats(course)
    }

    /**
     * 숫자 세 칸 — 몇 곳 · 얼마나 걸리나 · 얼마나 머나.
     *
     * 예전에는 이 셋이 "3곳 · 2시간 54분 · 3km" 한 줄이라 값과 값 사이가
     * 가운뎃점 하나로만 갈렸습니다. 칸을 나누고 라벨을 아래에 붙이면
     * 훑어보는 것만으로 어느 숫자가 무엇인지 읽힙니다.
     */
    private fun renderStats(course: ShootingCourse) {
        binding.tvResultStatCount.text = getString(R.string.course_stat_count, course.stops.size)

        val hours = course.totalMinutes / 60
        val minutes = course.totalMinutes % 60
        binding.tvResultStatTime.text = when {
            hours <= 0 -> getString(R.string.home_duration_m, minutes.toLong())
            minutes == 0 -> getString(R.string.course_stat_hours, hours)
            else -> getString(R.string.home_duration_hm, hours.toLong(), minutes.toLong())
        }

        binding.tvResultStatDistance.text =
            getString(R.string.course_stat_km, course.totalDistanceKm)
    }

    /**
     * 이 코스가 어느 날 몇 시의 것인지. 저장한 코스는 저장된 날짜를 씁니다.
     *
     * @param editable 방금 만든 코스면 눌러서 고칠 수 있습니다. 저장된 코스는
     *                 조건(촬영지 풀·동행·체류)이 남아 있지 않아 다시 짤 수
     *                 없으므로 글자만 보여 줍니다 — 눌리는데 아무 일도
     *                 일어나지 않는 것이 가장 나쁩니다.
     */
    private fun renderDate(date: java.time.LocalDate, editable: Boolean) {
        val day = date.format(
            DateTimeFormatter.ofPattern(
                getString(R.string.course_date_format), java.util.Locale.KOREAN
            )
        )
        val start = courseViewModel.startTime

        binding.tvResultDate.text = if (editable && start != null) {
            getString(R.string.course_date_with_start, day, start.format(HOUR_MINUTE))
        } else {
            day
        }

        // 고칠 수 없으면 알약과 화살표를 걷어 평범한 글줄로 돌립니다.
        binding.tvResultDate.isClickable = editable
        binding.tvResultDate.background = if (editable) {
            ContextCompat.getDrawable(requireContext(), R.drawable.bg_pill_dark)
        } else {
            null
        }
        TextViewCompat.setCompoundDrawableTintList(binding.tvResultDate, null)
        binding.tvResultDate.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (editable) R.drawable.ic_calendar else 0, 0,
            if (editable) R.drawable.baseline_chevron_right_24 else 0, 0
        )
        TextViewCompat.setCompoundDrawableTintList(
            binding.tvResultDate,
            android.content.res.ColorStateList.valueOf(0xB3FFFFFF.toInt())
        )

        binding.tvResultDate.setOnClickListener(
            if (editable) View.OnClickListener { pickDate(date) } else null
        )
    }

    // ─────────────────── 날짜 · 출발 시각 고치기 ───────────────────

    /**
     * 날짜 → 시각 순으로 묻고 그 자리에서 다시 짭니다.
     *
     * 둘을 한 번에 묻는 다이얼로그를 만들 수도 있지만, 안드로이드의 기본
     * 날짜·시각 선택기를 이어 붙이는 편이 배우지 않아도 쓸 수 있습니다.
     */
    private fun pickDate(current: java.time.LocalDate) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.course_cond_date)
            .setSelection(current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .setCalendarConstraints(
                // 지난 날짜의 코스는 짤 이유가 없습니다.
                CalendarConstraints.Builder()
                    .setValidator(DateValidatorPointForward.now())
                    .build()
            )
            .build()

        picker.addOnPositiveButtonClickListener { millis ->
            val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            pickStartTime(date)
        }
        picker.show(parentFragmentManager, "result_date")
    }

    private fun pickStartTime(date: java.time.LocalDate) {
        val current = courseViewModel.startTime ?: java.time.LocalTime.of(9, 0)
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(current.hour)
            .setMinute(current.minute)
            .setTitleText(R.string.course_cond_start)
            .build()

        picker.addOnPositiveButtonClickListener {
            courseViewModel.reschedule(
                date,
                java.time.LocalTime.of(picker.hour, picker.minute),
                mainViewModel.sunTimes
            )
        }
        picker.show(parentFragmentManager, "result_start_time")
    }

    private fun applyTopInset() {
        binding.layoutResultTop.applyTopSystemBarInset()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val HOUR_MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        private const val ARG_COURSE_ID = "course_id"

        /** 저장한 코스를 여는 진입점. */
        fun forSaved(courseId: Long) = CourseResultFragment().apply {
            arguments = Bundle().apply { putLong(ARG_COURSE_ID, courseId) }
        }
    }
}
