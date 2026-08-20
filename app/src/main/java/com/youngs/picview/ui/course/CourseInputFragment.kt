package com.youngs.picview.ui.course

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.youngs.picview.BuildConfig
import com.youngs.picview.MainActivity
import com.youngs.picview.R
import com.youngs.picview.data.api.RetrofitClient
import com.youngs.picview.data.repository.CourseRepository
import com.youngs.picview.databinding.FragmentCourseInputBinding
import com.youngs.picview.databinding.ItemCourseConditionBinding
import com.youngs.picview.domain.course.CoursePlanner
import com.youngs.picview.domain.course.CourseRequest
import com.youngs.picview.domain.course.ShootingCourse
import com.youngs.picview.domain.course.Subject
import com.youngs.picview.domain.course.TemplateNarrator
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.light.SunTimes
import com.youngs.picview.ui.detail.DetailFragment
import com.youngs.picview.ui.main.MainViewModel
import com.youngs.picview.util.TravelMode
import com.youngs.picview.util.applyTopSystemBarInset
import com.youngs.picview.util.retryOrNull
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 계획 — 출사 조건 입력 (빛 코스 시안).
 *
 * 위에서부터 "지금, 정읍의 빛" 히어로 → 조건 카드 다섯 칸 →
 * 오늘의 빛 코스 미리보기 → 빛 타임라인 → 빛 스케줄 만들기 순서입니다.
 * 조건을 바꾸는 즉시 미리보기 코스가 다시 계산되므로, 버튼을 누르기 전에
 * 오늘의 빛에 맞는 동선이 어떤 모양인지 먼저 보입니다.
 */
class CourseInputFragment : Fragment(R.layout.fragment_course_input),
    MainActivity.TabRoot {

    private var _binding: FragmentCourseInputBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private val courseViewModel: CourseViewModel by activityViewModels()

    // ─────────────── 선택 상태 ───────────────

    /** 다녀올 날짜. 기본은 오늘입니다. */
    private var tripDate: LocalDate = LocalDate.now()

    private var partyIndex = 0
    private var durationIndex = 1
    private var travelIndex = 0

    /** 담고 싶은 피사체. 기본은 풍경 — 이 앱의 첫 손님이 풍경 사진가입니다. */
    private val subjects = linkedSetOf(Subject.LANDSCAPE)

    private lateinit var previewAdapter: CoursePreviewAdapter
    private val conditionValues = mutableListOf<() -> String>()
    private val conditionBindings = mutableListOf<ItemCourseConditionBinding>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCourseInputBinding.bind(view)

        binding.scrollCourseInput.applyTopSystemBarInset()

        setupPreviewList()
        buildConditionCards()
        renderDate()
        renderHero()
        renderTimeline()

        binding.cardCourseDate.setOnClickListener { pickDate() }
        binding.btnMakeCourse.setOnClickListener { generate() }
        binding.tvOtherCourse.setOnClickListener { generate() }
        binding.btnSavePlan.setOnClickListener { savePlan() }

        // 촬영지·천문 정보가 도착하면 히어로와 미리보기를 갱신합니다.
        mainViewModel.goldenHourData.observe(viewLifecycleOwner) {
            renderHero()
            renderTimeline()
            regeneratePreview()
        }
        mainViewModel.spotData.observe(viewLifecycleOwner) { regeneratePreview() }
    }

    // ─────────────────────── 여행 날짜 ───────────────────────

    private fun renderDate() {
        binding.tvCourseDate.text = tripDate.format(
            DateTimeFormatter.ofPattern(getString(R.string.course_date_format), Locale.KOREAN)
        )
        renderConditionValues()
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
            dateSunTimes = null
            renderTimeline()
            regeneratePreview()
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
            renderTimeline()
            regeneratePreview()
        }
    }

    /** 오늘이 아닌 날짜를 골랐을 때의 일출·일몰. 오늘이면 null 입니다. */
    private var dateSunTimes: SunTimes? = null

    /** 코스 계산에 쓸 일출·일몰. 고른 날짜 기준입니다. */
    private val activeSunTimes: SunTimes
        get() = dateSunTimes ?: mainViewModel.sunTimes

    // ─────────────────────── 히어로 : 지금, 정읍의 빛 ───────────────────────

    /**
     * 히어로의 그림·글자를 지금 빛 구간에 맞춥니다.
     *
     * 배경은 구간별로 그린 장면(일출·낮·석양·블루아워·밤)입니다.
     * 블루아워·밤은 배경이 어두워 글자도 밝은 쪽으로 함께 바꿔야 읽힙니다.
     */
    private fun renderHero() {
        val sun = mainViewModel.sunTimes
        val now = LocalTime.now()
        val phase = sun.phaseAt(now)

        val heroRes = when (phase) {
            LightPhase.SUNRISE -> R.drawable.bg_hero_sunrise
            LightPhase.MORNING, LightPhase.MIDDAY, LightPhase.AFTERNOON -> R.drawable.bg_hero_day
            LightPhase.SUNSET -> R.drawable.bg_hero_sunset
            LightPhase.BLUE_DAWN, LightPhase.BLUE_DUSK -> R.drawable.bg_hero_blue
            LightPhase.NIGHT -> R.drawable.bg_hero_night
        }
        binding.ivHero.setImageResource(heroRes)

        val dark = phase == LightPhase.NIGHT ||
            phase == LightPhase.BLUE_DAWN || phase == LightPhase.BLUE_DUSK
        val base = color(if (dark) R.color.white else R.color.text_primary)
        val muted = if (dark) 0xB3FFFFFF.toInt() else color(R.color.text_secondary)
        val accent = color(if (dark) R.color.hero_accent else R.color.maple_500)

        binding.tvHeroDot.setTextColor(accent)
        binding.tvHeroEyebrow.setTextColor(muted)
        binding.tvHeroGolden.setTextColor(muted)
        binding.tvHeroHeadline.setTextColor(base)
        binding.tvHeroHeadline.text = heroHeadline(sun, now, phase, accent)
        binding.tvHeroGolden.text = heroGoldenLine(sun, now)
    }

    /** "석양까지 58분" — 남은 시간만 강조색으로 띄웁니다. */
    private fun heroHeadline(
        sun: SunTimes,
        now: LocalTime,
        phase: LightPhase,
        accentColor: Int
    ): CharSequence {
        if (!sun.hasData) return phase.label
        if (phase.isGolden) return getString(R.string.course_hero_golden_now)

        val sunrise = sun.sunrise!!
        val sunset = sun.sunset!!
        val (template, minutes) = when {
            now < sunrise ->
                R.string.course_hero_until_sunrise to minutesBetween(now, sunrise)
            now < sunset ->
                R.string.course_hero_until_sunset to minutesBetween(now, sunset)
            else -> return getString(R.string.course_hero_sun_down)
        }

        val duration = durationText(minutes)
        val text = getString(template, duration)
        val start = text.indexOf(duration)
        if (start < 0) return text
        return SpannableString(text).apply {
            setSpan(
                ForegroundColorSpan(accentColor),
                start, start + duration.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    /** "오늘 골든아워 18:40 ~ 19:20 ⓘ" — 해가 진 뒤에는 내일 일출로 넘깁니다. */
    private fun heroGoldenLine(sun: SunTimes, now: LocalTime): String {
        if (!sun.hasData) return getString(R.string.course_hero_golden_unknown)

        val evening = sun.upcomingGoldenWindows(LocalTime.MIN)
            .lastOrNull { it.phase == LightPhase.SUNSET }
        if (evening != null && now <= evening.end) {
            return getString(
                R.string.course_hero_golden,
                evening.start.format(HOUR_MINUTE),
                evening.end.format(HOUR_MINUTE)
            )
        }

        val next = sun.nextGolden(now)
            ?: return getString(R.string.course_hero_golden_unknown)
        return getString(R.string.course_hero_golden_tomorrow, next.start.format(HOUR_MINUTE))
    }

    private fun minutesBetween(from: LocalTime, to: LocalTime): Long =
        java.time.Duration.between(from, to).toMinutes()

    /** 한 시간이 안 되면 "분"만 씁니다. "0시간 58분"은 읽는 데 방해가 됩니다. */
    private fun durationText(minutes: Long): String = if (minutes >= 60) {
        getString(R.string.home_duration_hm, minutes / 60, minutes % 60)
    } else {
        getString(R.string.home_duration_m, minutes)
    }

    private fun color(resId: Int) = ContextCompat.getColor(requireContext(), resId)

    // ─────────────────────── 조건 카드 ───────────────────────

    private fun buildConditionCards() {
        val container = binding.layoutConditions
        container.removeAllViews()
        conditionValues.clear()
        conditionBindings.clear()

        data class Condition(
            val labelRes: Int,
            val iconRes: Int,
            val value: () -> String,
            val onClick: () -> Unit
        )

        val conditions = listOf(
            Condition(R.string.course_cond_date, R.drawable.ic_calendar, {
                tripDate.format(
                    DateTimeFormatter.ofPattern(
                        getString(R.string.course_cond_date_format), Locale.KOREAN
                    )
                )
            }) { pickDate() },
            Condition(R.string.course_cond_party, R.drawable.ic_people, {
                partyLabels()[partyIndex]
            }) { pickSingle(R.string.course_cond_party, partyLabels(), partyIndex) { partyIndex = it } },
            Condition(R.string.course_cond_duration, R.drawable.ic_clock, {
                durationLabels()[durationIndex]
            }) { pickSingle(R.string.course_cond_duration, durationLabels(), durationIndex) { durationIndex = it } },
            Condition(R.string.course_cond_travel, R.drawable.ic_car, {
                travelLabels()[travelIndex]
            }) { pickSingle(R.string.course_cond_travel, travelLabels(), travelIndex) { travelIndex = it } },
            Condition(R.string.course_cond_subject, R.drawable.baseline_camera_alt_24, {
                subjectValueLabel()
            }) { pickSubjects() }
        )

        conditions.forEach { condition ->
            val item = ItemCourseConditionBinding.inflate(layoutInflater, container, false)
            item.tvCondLabel.setText(condition.labelRes)
            item.ivCondIcon.setImageResource(condition.iconRes)
            item.tvCondValue.text = condition.value()
            item.cardCond.setOnClickListener { condition.onClick() }
            conditionValues.add(condition.value)
            conditionBindings.add(item)
            container.addView(item.root)
        }
    }

    private fun renderConditionValues() {
        conditionBindings.forEachIndexed { index, item ->
            item.tvCondValue.text = conditionValues[index]()
        }
    }

    private fun partyLabels() = listOf(
        getString(R.string.course_party_solo),
        getString(R.string.course_party_couple),
        getString(R.string.course_party_family),
        getString(R.string.course_party_friends)
    )

    private fun durationLabels() = listOf(
        getString(R.string.course_duration_short),
        getString(R.string.course_duration_med),
        getString(R.string.course_duration_half),
        getString(R.string.course_duration_full)
    )

    private fun travelLabels() = listOf(
        getString(R.string.course_travel_car),
        getString(R.string.course_travel_transit),
        getString(R.string.course_travel_walk),
        getString(R.string.course_travel_rail)
    )

    private fun subjectValueLabel(): String {
        val picked = subjects.toList()
        return when {
            picked.isEmpty() -> getString(R.string.course_subject_all)
            picked.size == 1 -> picked.first().label
            else -> getString(R.string.course_subject_more, picked.first().label, picked.size - 1)
        }
    }

    private fun pickSingle(
        titleRes: Int,
        labels: List<String>,
        current: Int,
        onPicked: (Int) -> Unit
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes)
            .setSingleChoiceItems(labels.toTypedArray(), current) { dialog, index ->
                onPicked(index)
                renderConditionValues()
                regeneratePreview()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun pickSubjects() {
        val all = Subject.values().toList()
        val checked = all.map { it in subjects }.toBooleanArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.course_cond_subject)
            .setMultiChoiceItems(
                all.map { it.label }.toTypedArray(), checked
            ) { _, index, isChecked -> checked[index] = isChecked }
            .setPositiveButton(R.string.confirm) { _, _ ->
                subjects.clear()
                all.forEachIndexed { index, subject ->
                    if (checked[index]) subjects.add(subject)
                }
                renderConditionValues()
                regeneratePreview()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ─────────────────────── 코스 미리보기 ───────────────────────

    private fun setupPreviewList() {
        previewAdapter = CoursePreviewAdapter { stop ->
            (activity as? MainActivity)?.pushScreen(DetailFragment.newInstance(stop.spot))
        }
        binding.rvCoursePreview.adapter = previewAdapter

        val snap = PagerSnapHelper()
        snap.attachToRecyclerView(binding.rvCoursePreview)

        binding.rvCoursePreview.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return
                val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val view = snap.findSnapView(lm) ?: return
                updateDots(lm.getPosition(view))
            }
        })
    }

    /**
     * 지금 조건으로 코스를 즉시 계산해 미리보기에 올립니다.
     *
     * [CoursePlanner] 는 순수 계산이라 버튼을 누르기 전에 미리 돌려도
     * 비용이 없습니다. 조건이 바뀔 때마다 다시 부릅니다.
     */
    private fun regeneratePreview() {
        val view = _binding ?: return
        val spots = mainViewModel.spotData.value.orEmpty()

        val course = if (spots.isEmpty()) null
        else CoursePlanner.plan(spots, activeSunTimes, buildRequest())

        val stops = course?.stops.orEmpty()
        previewAdapter.submitList(stops)
        view.tvCourseEmpty.isVisible = stops.isEmpty()
        view.rvCoursePreview.isVisible = stops.isNotEmpty()
        buildDots(stops.size)
    }

    private fun buildDots(count: Int) {
        val container = binding.layoutCourseDots
        container.removeAllViews()
        if (count <= 1) return

        repeat(count) { index ->
            val dot = View(requireContext())
            val size = resources.getDimensionPixelSize(R.dimen.space_s)
            dot.layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply {
                marginStart = size / 2
                marginEnd = size / 2
            }
            dot.setBackgroundResource(
                if (index == 0) R.drawable.dot_indicator_active
                else R.drawable.dot_indicator_inactive
            )
            container.addView(dot)
        }
    }

    private fun updateDots(active: Int) {
        val container = _binding?.layoutCourseDots ?: return
        for (index in 0 until container.childCount) {
            container.getChildAt(index).setBackgroundResource(
                if (index == active) R.drawable.dot_indicator_active
                else R.drawable.dot_indicator_inactive
            )
        }
    }

    // ─────────────────────── 빛 타임라인 ───────────────────────

    private fun renderTimeline() {
        val sun = activeSunTimes
        binding.tvTlSunrise.text = sun.sunrise?.format(HOUR_MINUTE) ?: PLACEHOLDER
        binding.tvTlSunset.text = sun.sunset?.format(HOUR_MINUTE) ?: PLACEHOLDER

        val evening = sun.upcomingGoldenWindows(LocalTime.MIN)
            .lastOrNull { it.phase == LightPhase.SUNSET }
        binding.tvTlGolden.text = evening?.let {
            "${it.start.format(HOUR_MINUTE)} ~ ${it.end.format(HOUR_MINUTE)}"
        } ?: PLACEHOLDER
    }

    // ─────────────────────── 코스 생성 · 저장 ───────────────────────

    private fun generate() {
        val spots = mainViewModel.spotData.value.orEmpty()
        if (spots.isEmpty()) {
            showError(getString(R.string.course_need_spots))
            return
        }

        courseViewModel.generate(spots, activeSunTimes, buildRequest())

        val course = courseViewModel.course.value
        if (course == null || course.isEmpty) {
            showError(getString(R.string.course_no_result))
            return
        }

        binding.tvCourseError.isVisible = false
        (activity as? MainActivity)?.pushScreen(CourseResultFragment())
    }

    /** 미리보기의 코스를 그대로 내 코스에 저장합니다. */
    private fun savePlan() {
        val spots = mainViewModel.spotData.value.orEmpty()
        if (spots.isEmpty()) {
            showError(getString(R.string.course_need_spots))
            return
        }

        val course: ShootingCourse = CoursePlanner.plan(spots, activeSunTimes, buildRequest())
        if (course.isEmpty) {
            showError(getString(R.string.course_no_result))
            return
        }

        binding.tvCourseError.isVisible = false
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = runCatching {
                CourseRepository(requireContext()).save(course, TemplateNarrator.narrate(course))
            }.isSuccess
            Toast.makeText(
                requireContext(),
                if (ok) R.string.course_saved else R.string.course_save_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showError(message: String) {
        binding.tvCourseError.text = message
        binding.tvCourseError.isVisible = true
    }

    private fun buildRequest(): CourseRequest {
        val start = startTime()
        val hours = durationHours()

        return CourseRequest(
            startTime = start,
            // 자정을 넘기면 LocalTime 이 되감기므로 23:30 에서 끊습니다.
            endTime = start.plusHours(hours.toLong())
                .takeIf { it > start } ?: LocalTime.of(23, 30),
            travelMode = travelMode(),
            subjects = subjects.toSet(),
            maxStops = if (hours >= 8) 6 else if (hours >= 5) 5 else 3,
            stayFactor = stayFactor()
        )
    }

    /**
     * 코스 시작 시각.
     *
     * 오늘이면 지금부터 — 이미 지난 시각으로 코스를 짤 수 없습니다.
     * 다른 날짜면 오전 9시부터 하루를 계획합니다.
     */
    private fun startTime(): LocalTime =
        if (tripDate == LocalDate.now()) LocalTime.now() else LocalTime.of(9, 0)

    private fun durationHours(): Int = when (durationIndex) {
        0 -> 2
        1 -> 4
        2 -> 6
        else -> 10
    }

    private fun travelMode(): TravelMode = when (travelIndex) {
        1 -> TravelMode.TRANSIT
        2 -> TravelMode.WALK
        // KTX 로 정읍역까지 온 뒤 렌트하므로, 시내 이동은 자가용과 같습니다.
        else -> TravelMode.CAR
    }

    /**
     * 인원 구성에 따른 체류 시간 배수. 여럿이 움직이면 한 곳에 머무는
     * 시간이 길어져 같은 시간에 도는 곳이 줄어듭니다.
     */
    private fun stayFactor(): Double = when (partyIndex) {
        1 -> 1.1
        2 -> 1.35
        3 -> 1.25
        else -> 1.0
    }

    override fun scrollToTop() {
        _binding?.scrollCourseInput?.smoothScrollTo(0, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val HOUR_MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private const val PLACEHOLDER = "—"
    }
}
