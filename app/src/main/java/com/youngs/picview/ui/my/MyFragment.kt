package com.youngs.picview.ui.my

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.youngs.picview.BuildConfig
import com.youngs.picview.MainActivity
import com.youngs.picview.R
import com.youngs.picview.data.local.SavedCourseWithStops
import com.youngs.picview.data.local.VisitLogEntity
import com.youngs.picview.databinding.FragmentMyBinding
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.mission.Missions
import com.youngs.picview.domain.spot.ShotTokens
import com.youngs.picview.domain.spot.SpotFactsTable
import com.youngs.picview.ui.frame.PhotoFrameActivity
import com.youngs.picview.ui.mission.MissionFragment
import com.youngs.picview.ui.onboarding.OnboardingActivity
import com.youngs.picview.util.AppPrefs
import com.youngs.picview.util.FontStep
import com.youngs.picview.util.applyTopSystemBarInset
import com.youngs.picview.util.byBatchim
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * MY — 나의 시선 (시안 구조).
 *
 * 로그인이 없으므로 프로필(아바타·레벨·가입일)과 고객센터는 두지 않습니다.
 * 계정이 아니라 **기록**이 곧 프로필입니다 — 이번 달의 통계, 최근 촬영,
 * 촬영 스타일이 전부 로컬 방문 기록에서 계산됩니다.
 *
 * 접근성·글씨 크기는 아래 퀵링크에서 다이얼로그로 다룹니다. 시니어 전환과
 * 글씨 크기는 Activity 재생성이 필요해서(테마·fontScale 은 화면 생성
 * 시점에만 적용됨) 확인 후 recreate 합니다.
 */
class MyFragment : Fragment(R.layout.fragment_my), MainActivity.TabRoot {

    private var _binding: FragmentMyBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MyViewModel by viewModels()

    /** 이번 달 1일 0시(epoch millis). 월 통계의 경계선입니다. */
    private val monthStart: Long by lazy {
        LocalDate.now().withDayOfMonth(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
    }

    /** 지난 달 1일 0시(epoch millis). */
    private val lastMonthStart: Long by lazy {
        LocalDate.now().withDayOfMonth(1).minusMonths(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMyBinding.bind(view)

        binding.scrollMy.applyTopSystemBarInset()

        val now = YearMonth.now()
        binding.tvMyMonth.text = "📅 ${now.year}. ${now.monthValue}"

        setupActions()
        setupPhotos()

        viewModel.courses.observe(viewLifecycleOwner) { renderCourses(it) }
        viewModel.visits.observe(viewLifecycleOwner) { visits ->
            renderVisitStats(visits)
            renderPhotos(visits)
            renderStyle(visits)
            renderMission(visits)
        }
    }

    private fun setupActions() {
        val openCourses = View.OnClickListener {
            (activity as? MainActivity)?.pushScreen(SavedCoursesFragment())
        }
        binding.layoutStatCourses.setOnClickListener(openCourses)
        binding.btnCourseAll.setOnClickListener(openCourses)
        binding.cardSavedCourse.setOnClickListener(openCourses)

        val openVisits = View.OnClickListener {
            (activity as? MainActivity)?.pushScreen(VisitedFragment())
        }
        binding.layoutStatVisits.setOnClickListener(openVisits)
        binding.tvMyPhotosMore.setOnClickListener(openVisits)

        val openMissions = View.OnClickListener {
            (activity as? MainActivity)?.pushScreen(MissionFragment())
        }
        binding.layoutStatMissions.setOnClickListener(openMissions)
        binding.btnMyMissions.setOnClickListener(openMissions)
        binding.cardNextMission.setOnClickListener(openMissions)

        binding.btnMySettings.setOnClickListener { showAppInfo() }
        binding.layoutQuickAccess.setOnClickListener { showSeniorDialog() }
        binding.layoutQuickFont.setOnClickListener { showFontDialog() }
        binding.layoutQuickInfo.setOnClickListener { showAppInfo() }
    }

    // ─────────────────────── 이번 달의 시선 ───────────────────────

    /** "+2 / -1 / +0" — 부호를 붙여 지난 달과의 차이를 말합니다. */
    private fun signed(value: Int): String = if (value >= 0) "+$value" else "$value"

    private fun renderVisitStats(visits: List<VisitLogEntity>) {
        // 다녀온 장소 — 누적 수. 증감은 "이번 달 새로 가 본 곳 - 지난 달 새로 가 본 곳".
        val allPlaces = visits.map { it.contentId }.toSet()
        val newThisMonth = allPlaces.size -
            visits.filter { it.visitedAt < monthStart }.map { it.contentId }.toSet().size
        val beforeLastMonth = visits.filter { it.visitedAt < lastMonthStart }
            .map { it.contentId }.toSet().size
        val newLastMonth = visits.filter { it.visitedAt < monthStart }
            .map { it.contentId }.toSet().size - beforeLastMonth

        binding.tvStatVisit.text = allPlaces.size.toString()
        binding.tvStatVisitDelta.text =
            getString(R.string.my_month_delta, signed(newThisMonth - newLastMonth))

        // 완성한 미션 — 지금 기준과 이번 달이 시작되기 전 기준의 차이.
        val doneNow = Missions.progress(visits).count { it.isComplete }
        val doneBefore = Missions
            .progress(visits.filter { it.visitedAt < monthStart })
            .count { it.isComplete }

        binding.tvStatMission.text = doneNow.toString()
        binding.tvStatMissionDelta.text =
            getString(R.string.my_month_delta, signed(doneNow - doneBefore))
    }

    // ─────────────────────── 최근 촬영 ───────────────────────

    private lateinit var photoAdapter: MyPhotoAdapter

    private fun setupPhotos() {
        photoAdapter = MyPhotoAdapter { visit ->
            val uri = visit.photoUri ?: return@MyPhotoAdapter
            // 사진을 누르면 포토 프레임으로 — 미션 "나만의 정읍 엽서"의 길입니다.
            startActivity(
                PhotoFrameActivity.intent(
                    requireContext(), Uri.parse(uri), visit.title, visit.visitedAt
                )
            )
        }
        binding.rvMyPhotos.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvMyPhotos.adapter = photoAdapter
    }

    private fun renderPhotos(visits: List<VisitLogEntity>) {
        val photos = visits
            .filter { !it.photoUri.isNullOrBlank() }
            .distinctBy { it.photoUri }
            .take(6)

        binding.cardMyPhotos.isVisible = photos.isNotEmpty()
        photoAdapter.submitList(photos)
    }

    // ─────────────────────── 나의 촬영 스타일 ───────────────────────

    /**
     * 방문 기록에서 취향을 계산합니다.
     *
     * 구도는 다녀온 장소의 추천 구도 중 가장 잦은 것, 빛은 체크인할 때
     * 가장 잦았던 빛 구간, 장소는 가장 여러 번 간 두 곳입니다.
     * 스스로 적은 적 없는 프로필이 기록만으로 만들어집니다.
     */
    private fun renderStyle(visits: List<VisitLogEntity>) {
        binding.cardMyStyle.isVisible = visits.isNotEmpty()
        if (visits.isEmpty()) return

        val topGuide = visits
            .groupingBy { SpotFactsTable.of(it.title, null).guide }
            .eachCount()
            .maxByOrNull { it.value }?.key
        if (topGuide != null) {
            binding.tvStyleGuide.text =
                getString(R.string.my_style_guide_line, ShotTokens.of(topGuide).label)
        }

        val topPhase = visits
            .mapNotNull { runCatching { LightPhase.valueOf(it.phaseName) }.getOrNull() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }?.key
        if (topPhase != null) {
            binding.tvStyleLight.text =
                getString(R.string.my_style_light_line, topPhase.label)
        }

        val topPlaces = visits
            .groupingBy { it.title }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(2)
            .joinToString(", ") { it.key }
        binding.tvStylePlace.text =
            topPlaces + topPlaces.byBatchim("을", "를") + " 자주 찾았어요"
    }

    // ─────────────────────── 저장한 출사 코스 ───────────────────────

    private fun renderCourses(courses: List<SavedCourseWithStops>) {
        binding.tvStatCourse.text = courses.size.toString()

        val thisMonth = courses.count { it.course.createdAt >= monthStart }
        val lastMonth = courses.count {
            it.course.createdAt in lastMonthStart until monthStart
        }
        binding.tvStatCourseDelta.text =
            getString(R.string.my_month_delta, signed(thisMonth - lastMonth))

        val latest = courses.firstOrNull()
        if (latest == null) {
            binding.tvCourseTitle.setText(R.string.my_no_course)
            binding.tvCourseMeta.text = ""
            binding.ivCourseThumb.setImageResource(R.drawable.bg_image_placeholder)
            return
        }

        binding.tvCourseTitle.text = latest.course.title
        binding.tvCourseMeta.text = getString(
            R.string.my_course_meta, latest.stops.size, latest.course.totalKm
        )

        Glide.with(binding.ivCourseThumb)
            .load(latest.orderedStops.firstOrNull()?.imageUrl?.takeIf { it.isNotBlank() })
            .placeholder(R.drawable.bg_image_placeholder)
            .error(R.drawable.bg_image_placeholder)
            .centerCrop()
            .into(binding.ivCourseThumb)
    }

    // ─────────────────────── 이어서 할 미션 ───────────────────────

    private fun renderMission(visits: List<VisitLogEntity>) {
        val next = Missions.progress(visits).firstOrNull { !it.isComplete }

        if (next == null) {
            binding.tvMissionTitle.text = getString(R.string.my_mission_done_all)
            binding.tvMissionHint.isVisible = false
            binding.layoutMissionProgress.isVisible = false
            return
        }

        binding.tvMissionTitle.text = next.mission.title
        binding.tvMissionHint.isVisible = true
        binding.tvMissionHint.text = next.mission.nextHint
        binding.ivMissionStamp.setImageResource(next.mission.stampRes)
        binding.layoutMissionProgress.isVisible = true
        binding.tvMissionCount.text =
            getString(R.string.my_mission_progress, next.current, next.target)
        binding.progressMission.max = 100
        binding.progressMission.progress = (next.ratio * 100).toInt()
    }

    // ─────────────────────── 접근성 · 글씨 크기 · 앱 정보 ───────────────────────

    private fun showSeniorDialog() {
        val enabling = !AppPrefs.isSeniorMode(requireContext())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                if (enabling) R.string.senior_switch_title
                else R.string.senior_switch_back_title
            )
            .setMessage(
                if (enabling) R.string.senior_switch_desc
                else R.string.senior_switch_back_desc
            )
            .setPositiveButton(R.string.confirm) { _, _ ->
                AppPrefs.setSeniorMode(requireContext(), enabling)
                // 테마와 탭 구성이 Activity 생성 시점에 정해지므로 재생성합니다.
                requireActivity().recreate()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showFontDialog() {
        val steps = listOf(FontStep.NORMAL, FontStep.LARGE, FontStep.XLARGE)
        val labels = arrayOf(
            getString(R.string.font_size_normal),
            getString(R.string.font_size_large),
            getString(R.string.font_size_xlarge)
        )
        val current = steps.indexOf(AppPrefs.fontStep(requireContext()))

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.my_font_size)
            .setSingleChoiceItems(labels, current) { dialog, index ->
                dialog.dismiss()
                if (index == current) return@setSingleChoiceItems
                AppPrefs.setFontStep(requireContext(), steps[index])
                // fontScale 은 attachBaseContext 에서만 반영되므로 재생성이 필요합니다.
                requireActivity().recreate()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAppInfo() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.app_name)
            .setMessage(getString(R.string.my_footer, BuildConfig.VERSION_NAME))
            .setPositiveButton(R.string.confirm, null)
            .setNeutralButton(R.string.my_replay_onboarding) { _, _ ->
                // 온보딩 다시 보기 — 처음 봤던 그 화면을 그대로 다시 띄웁니다.
                startActivity(OnboardingActivity.reviewIntent(requireContext()))
            }
            .show()
    }

    override fun scrollToTop() {
        _binding?.scrollMy?.smoothScrollTo(0, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
