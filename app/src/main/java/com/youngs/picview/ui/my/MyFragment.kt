package com.youngs.picview.ui.my

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.youngs.picview.BuildConfig
import com.youngs.picview.MainActivity
import com.youngs.picview.R
import com.youngs.picview.data.local.VisitLogEntity
import com.youngs.picview.databinding.FragmentMyBinding
import com.youngs.picview.databinding.ItemTasteAxisBinding
import com.youngs.picview.domain.mission.Missions
import com.youngs.picview.domain.my.PhotoTaste
import com.youngs.picview.domain.my.TasteAxis
import com.youngs.picview.domain.season.SeasonHighlight
import com.youngs.picview.domain.season.SeasonHighlights
import com.youngs.picview.domain.spot.SpotFactsTable
import com.youngs.picview.ui.course.CourseInputFragment
import com.youngs.picview.ui.detail.DetailFragment
import com.youngs.picview.ui.guide.GuideActivity
import com.youngs.picview.ui.frame.PhotoFrameActivity
import com.youngs.picview.ui.main.MainViewModel
import com.youngs.picview.ui.mission.MissionFragment
import com.youngs.picview.ui.onboarding.OnboardingActivity
import com.youngs.picview.util.AppPrefs
import com.youngs.picview.util.FontStep
import com.youngs.picview.util.SunCountdown
import com.youngs.picview.util.applyTopSystemBarInset
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * MY — 나의 시선 (시안 개편).
 *
 * 로그인이 없으므로 프로필(아바타·레벨·가입일)과 고객센터는 두지 않습니다.
 * 계정이 아니라 **기록**이 곧 프로필입니다.
 *
 * 화면은 위에서 아래로 시간축이 넓어지게 놓았습니다.
 * 오늘의 시선(지금) → 이번 달의 촬영 리듬 → 최근 아카이브·촬영 성향 →
 * 다음에 가볼 정읍(다음 계절) → 빛 수집 노트(모아 온 전부).
 * 마지막 줄만 기록이 아니라 기록을 다루는 일입니다.
 *
 * 히어로의 빛·날씨는 [MainViewModel] 이 이미 받아 둔 값을 씁니다. MY 탭이
 * 같은 API 를 또 부르면 홈과 다른 숫자가 나올 수 있습니다.
 */
class MyFragment : Fragment(R.layout.fragment_my), MainActivity.TabRoot {

    private var _binding: FragmentMyBinding? = null
    private val binding get() = _binding!!

    /** 아카이브 카드가 목록으로 갈지 촬영으로 갈지 가르는 값. */
    private var hasArchivePhotos = false

    private val viewModel: MyViewModel by viewModels()

    /** 빛·날씨·촬영지 목록. Activity 범위라 홈과 같은 값을 봅니다. */
    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var photoAdapter: MyPhotoAdapter
    private lateinit var noteAdapter: LightNoteAdapter

    /** 이번 달 1일 0시(epoch millis). 월 통계의 경계선입니다. */
    private val monthStart: Long by lazy {
        LocalDate.now().withDayOfMonth(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMyBinding.bind(view)

        binding.scrollMy.applyTopSystemBarInset()

        setupActions()
        setupLists()
        renderToday()
        renderNextTrip()

        viewModel.courses.observe(viewLifecycleOwner) {
            binding.tvStatCourse.text = it.size.toString()
        }
        viewModel.visits.observe(viewLifecycleOwner) { visits ->
            renderRhythm(visits)
            renderArchive(visits)
            renderTaste(visits)
            renderLightNote(visits)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshOnReturn()
    }

    /**
     * 상세·찜 목록은 이 화면 위에 얹히고(pushScreen 은 hide 만 함) 이 화면은
     * 계속 RESUMED 라, 돌아와도 onResume 이 오지 않습니다. 숨김이 풀리는
     * 순간이 "돌아온" 순간입니다.
     */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && _binding != null) refreshOnReturn()
    }

    private fun refreshOnReturn() {
        // 찜 목록에서 찜을 빼거나, 관광공사에 없는 찜이 정리됐을 수 있으므로 "n곳"을 다시 셉니다.
        viewModel.visits.value?.let { renderFavoritesSummary(it) }
        // 빛은 가만히 있어도 흐릅니다. 탭으로 돌아왔을 때 "석양까지 58분"이
        // 아까 그대로면 화면이 시간을 놓친 것처럼 보입니다.
        renderToday()
    }

    private fun setupActions() {
        val openCourses = View.OnClickListener {
            (activity as? MainActivity)?.pushScreen(SavedCoursesFragment())
        }
        binding.layoutStatCourses.setOnClickListener(openCourses)

        val openVisits = View.OnClickListener {
            (activity as? MainActivity)?.pushScreen(VisitedFragment())
        }
        binding.layoutStatShots.setOnClickListener(openVisits)
        binding.tvArchiveAll.setOnClickListener(openVisits)
        binding.tvArchiveByCourse.setOnClickListener {
            (activity as? MainActivity)?.pushScreen(CoursePhotosFragment())
        }

        // 아카이브 카드는 통째로 목적지입니다. 다만 사진이 하나도 없을 때
        // 빈 목록으로 보내는 건 "없다"를 두 번 말하는 것이라, 그때는
        // 오늘의 촬영으로 내보냅니다.
        binding.cardArchive.setOnClickListener {
            if (hasArchivePhotos) {
                (activity as? MainActivity)?.pushScreen(VisitedFragment())
            } else {
                startTodayShoot()
            }
        }
        binding.tvArchiveStart.setOnClickListener { startTodayShoot() }

        val openMissions = View.OnClickListener {
            (activity as? MainActivity)?.pushScreen(MissionFragment())
        }
        binding.layoutStatMissions.setOnClickListener(openMissions)

        binding.cardFavorites.setOnClickListener {
            (activity as? MainActivity)?.pushScreen(FavoritesFragment())
        }

        binding.btnTodayShoot.setOnClickListener { startTodayShoot() }
        // 카드 자체는 그 장소로 가는 문입니다. 사진과 이름을 보여 주면서
        // 눌러도 아무 일이 없으면 화면이 닫힌 것처럼 느껴집니다.
        binding.cardToday.setOnClickListener { openTodaySpot() }
        binding.btnNextPlan.setOnClickListener {
            (activity as? MainActivity)?.pushScreen(CourseInputFragment())
        }

        binding.btnMySettings.setOnClickListener { showAppInfo() }
        binding.layoutExport.setOnClickListener { exportPhotos() }
        binding.layoutReset.setOnClickListener { confirmReset() }
        binding.layoutQuickAccess.setOnClickListener { showAccessibilityDialog() }
    }

    private fun setupLists() {
        photoAdapter = MyPhotoAdapter { visit ->
            val uri = visit.photoUri ?: return@MyPhotoAdapter
            // 사진을 누르면 포토 프레임으로 — 미션 "나만의 정읍 엽서"의 길입니다.
            startActivity(
                PhotoFrameActivity.intent(
                    requireContext(), Uri.parse(uri), visit.title, visit.visitedAt
                )
            )
        }
        binding.rvMyPhotos.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvMyPhotos.adapter = photoAdapter

        noteAdapter = LightNoteAdapter {
            (activity as? MainActivity)?.pushScreen(MissionFragment())
        }
        binding.rvLightNote.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvLightNote.adapter = noteAdapter
    }

    // ─────────────────────── 오늘의 시선 ───────────────────────

    /**
     * 지금 나가야 할 이유 한 장.
     *
     * 장소는 점수 1위 촬영지(홈이 쓰는 그 순서)를, 문장은 그 장소의 촬영
     * 지식을 씁니다. 아직 목록을 못 받았으면 우화정으로 채웁니다 — 빈 카드를
     * 두느니 정읍에서 가장 자주 권하게 되는 자리를 보여 주는 편이 낫습니다.
     */
    private fun renderToday() {
        val binding = _binding ?: return

        val sky = mainViewModel.skyState.value
        val date = LocalDate.now().format(DATE)
        binding.tvTodayMeta.text = if (sky != null) {
            getString(R.string.my_today_meta, sky.label, date)
        } else {
            date
        }

        val countdown = SunCountdown.untilNextEvent(requireContext(), mainViewModel.sunTimes)
        binding.tvTodayCountdown.isVisible = countdown != null
        binding.tvTodayCountdown.text = countdown.orEmpty()

        val spot = mainViewModel.spotData.value?.firstOrNull()
        if (spot == null) {
            binding.tvTodayPlace.setText(R.string.my_today_place_fallback)
            binding.tvTodayLine.setText(R.string.my_today_fallback)
            binding.ivTodayPhoto.setImageResource(R.drawable.spot_uhwajeong)
            return
        }

        binding.tvTodayPlace.text = spot.title
        binding.tvTodayLine.text = SpotFactsTable.of(spot.title, spot.contentTypeId).note

        Glide.with(binding.ivTodayPhoto)
            .load(spot.imageUrl.takeIf { it.isNotBlank() })
            .placeholder(R.drawable.spot_uhwajeong)
            .error(R.drawable.spot_uhwajeong)
            .centerCrop()
            .into(binding.ivTodayPhoto)
    }

    /**
     * 오늘의 시선 카드를 누르면 그 장소의 상세로.
     *
     * 카드가 보여 주는 장소는 [renderToday] 와 같은 목록 1위입니다. 아직
     * 목록이 없으면 우화정 사진을 대신 보여 주고 있는 상태라 갈 상세가
     * 없으므로 탐색 탭으로 보냅니다.
     */
    private fun openTodaySpot() {
        val spot = mainViewModel.spotData.value?.firstOrNull()
        if (spot == null) {
            (activity as? MainActivity)?.selectTab(R.id.tab_explore)
            return
        }
        (activity as? MainActivity)?.pushScreen(DetailFragment.newInstance(spot))
    }

    /**
     * 히어로의 CTA — 지금 추천 장소로 포즈 가이드를 엽니다.
     *
     * 목록을 아직 못 받았으면 장소를 특정할 수 없어 촬영한 사진을 기록에
     * 붙일 자리가 없습니다. 그때는 탐색 탭으로 보내 장소부터 고르게 합니다.
     */
    private fun startTodayShoot() {
        val spot = mainViewModel.spotData.value?.firstOrNull()
        if (spot == null) {
            (activity as? MainActivity)?.selectTab(R.id.tab_explore)
            return
        }
        startActivity(
            Intent(requireContext(), GuideActivity::class.java).apply {
                putExtra(GuideActivity.EXTRA_SPOT_NAME, spot.title)
                putExtra(GuideActivity.EXTRA_SPOT_TYPE, spot.contentTypeId)
                putExtra(GuideActivity.EXTRA_PHASE, mainViewModel.sunTimes.phaseNow().name)
                putExtra(GuideActivity.EXTRA_CONTENT_ID, spot.contentId)
            }
        )
    }

    // ─────────────────────── 이번 달의 촬영 리듬 ───────────────────────

    private fun renderRhythm(visits: List<VisitLogEntity>) {
        // 촬영 기록 — 이번 달에 직접 찍어 남긴 사진 수.
        binding.tvStatShots.text = visits
            .count { !it.photoUri.isNullOrBlank() && it.visitedAt >= monthStart }
            .toString()

        binding.tvStatMission.text = Missions.progress(visits).count { it.isComplete }.toString()

        renderFavoritesSummary(visits)
    }

    /**
     * 찜 줄의 한 줄 요약 — "8곳 · 아직 안 간 곳 5".
     *
     * 개수만 적으면 눌러 볼 이유가 약합니다. 아직 남은 곳 수가 보이면
     * 그것이 곧 다음에 나갈 이유가 됩니다.
     */
    private fun renderFavoritesSummary(visits: List<VisitLogEntity>) {
        val view = _binding ?: return
        val favorites = AppPrefs.favoriteSpots(requireContext())
        val visited = visits.map { it.contentId }.toSet()
        val todo = favorites.count { it !in visited }

        view.tvFavoritesSummary.text = if (favorites.isEmpty()) {
            getString(R.string.favorites_summary_empty)
        } else {
            getString(R.string.favorites_summary, favorites.size, todo)
        }
    }

    // ─────────────────────── 최근 촬영 아카이브 ───────────────────────

    private fun renderArchive(visits: List<VisitLogEntity>) {
        val photos = visits
            .filter { !it.photoUri.isNullOrBlank() }
            .distinctBy { it.photoUri }
            .take(4)

        hasArchivePhotos = photos.isNotEmpty()
        binding.rvMyPhotos.isVisible = photos.isNotEmpty()
        binding.layoutArchiveEmpty.isVisible = photos.isEmpty()
        binding.tvArchiveAll.isVisible = photos.isNotEmpty()
        binding.tvArchiveByCourse.isVisible = photos.isNotEmpty()
        binding.cardArchive.contentDescription = getString(
            if (photos.isEmpty()) R.string.cd_my_archive_empty else R.string.cd_my_archive
        )
        photoAdapter.submitList(photos)
    }

    // ─────────────────────── 나의 촬영 성향 ───────────────────────

    private fun renderTaste(visits: List<VisitLogEntity>) {
        val taste = PhotoTaste.of(visits)

        val axisViews = listOf(
            binding.tasteReflection, binding.tasteGolden, binding.tasteWater
        )

        if (taste == null) {
            // 사진 한두 장으로 "68%"를 말하면 그건 통계가 아니라 장식입니다.
            axisViews.forEach { it.root.isVisible = false }
            binding.dividerTaste.isVisible = false
            binding.tvTasteBasis.text =
                getString(R.string.my_taste_locked, PhotoTaste.MIN_SAMPLE)
            binding.tvTasteInsight.isVisible = false
            return
        }

        binding.tvTasteBasis.text = getString(R.string.my_taste_basis, taste.sampleSize)
        binding.dividerTaste.isVisible = true

        val icons = listOf(R.drawable.ic_glyph_drop, R.drawable.ic_sun, R.drawable.ic_wave)
        axisViews.forEachIndexed { index, axisBinding ->
            axisBinding.root.isVisible = true
            bindAxis(axisBinding, taste.axes[index], icons[index])
        }

        binding.tvTasteInsight.isVisible = taste.insight != null
        binding.tvTasteInsight.text = taste.insight.orEmpty()
    }

    private fun bindAxis(view: ItemTasteAxisBinding, axis: TasteAxis, iconRes: Int) {
        view.ivAxisIcon.setImageResource(iconRes)
        view.tvAxisLabel.text = axis.label
        view.progressAxis.progress = axis.percent
        view.tvAxisPercent.text = getString(R.string.my_taste_percent, axis.percent)
    }

    // ─────────────────────── 다음에 가볼 정읍 ───────────────────────

    private fun renderNextTrip() {
        val next: SeasonHighlight = SeasonHighlights.upcoming(limit = 1).firstOrNull() ?: return

        binding.tvNextTitle.text = next.title
        binding.tvNextTip.text = next.tip
        binding.ivNextPhoto.setImageResource(next.photoRes)

        val days = next.daysUntilPeak()
        binding.tvNextDday.text = if (next.isPeakNow()) {
            getString(R.string.my_next_peak_now)
        } else {
            "· " + getString(R.string.my_next_peak_in, days.toInt())
        }
    }

    // ─────────────────────── 빛 수집 노트 ───────────────────────

    private fun renderLightNote(visits: List<VisitLogEntity>) {
        val progress = Missions.progress(visits)
        noteAdapter.submitList(progress)
        binding.tvLightNoteCount.text = getString(
            R.string.my_light_note_count,
            progress.count { it.isComplete },
            progress.size
        )
    }

    // ─────────────────────── 사진 내보내기 ───────────────────────

    /**
     * 직접 찍은 사진을 다른 앱으로 보냅니다.
     *
     * 사진은 이미 갤러리(MediaStore)에 있고 [VisitLogEntity.photoUri] 는 그
     * 주소입니다. 그래서 파일을 새로 만들거나 압축하지 않고 공유 시트에
     * 그대로 넘깁니다 — 앱이 사진을 한 벌 더 만들면 용량만 두 배가 됩니다.
     */
    private fun exportPhotos() {
        val uris = viewModel.visits.value
            .orEmpty()
            .mapNotNull { it.photoUri }
            .distinct()
            .map(Uri::parse)

        if (uris.isEmpty()) {
            toast(getString(R.string.my_export_none))
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.my_export)
            .setMessage(getString(R.string.my_export_message, uris.size))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ -> share(uris) }
            .show()
    }

    private fun share(uris: List<Uri>) {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.my_export)))
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.my_export_failed))
        }
    }

    // ─────────────────────── 데이터 초기화 ───────────────────────

    private fun confirmReset() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.my_reset)
            .setMessage(R.string.my_reset_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.my_reset_confirm) { _, _ ->
                viewModel.resetAll {
                    if (_binding == null) return@resetAll
                    toast(getString(R.string.my_reset_done))
                }
            }
            .show()
    }

    // ─────────────────────── 접근성 · 글씨 크기 · 앱 정보 ───────────────────────

    /** 큰 글씨 모드와 글씨 크기를 한 자리에서 고릅니다. */
    private fun showAccessibilityDialog() {
        val seniorOn = AppPrefs.isSeniorMode(requireContext())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.my_accessibility)
            .setMessage(if (seniorOn) R.string.my_senior_title_on else R.string.my_senior_desc)
            .setPositiveButton(R.string.my_font_size) { _, _ -> showFontDialog() }
            .setNeutralButton(
                if (seniorOn) R.string.my_senior_off else R.string.my_senior_on
            ) { _, _ ->
                AppPrefs.setSeniorMode(requireContext(), !seniorOn)
                // 테마와 탭 구성이 Activity 생성 시점에 정해지므로 재생성합니다.
                requireActivity().recreate()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showFontDialog() {
        val steps = listOf(FontStep.NORMAL, FontStep.LARGE, FontStep.XLARGE)
        val labels = steps.map { it.label }.toTypedArray()
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

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun scrollToTop() {
        _binding?.scrollMy?.smoothScrollTo(0, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일")
    }
}
