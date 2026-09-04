package com.youngs.picview.ui.detail

import android.content.Intent
import android.net.Uri
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.youngs.picview.BuildConfig
import com.youngs.picview.MainActivity
import com.youngs.picview.R
import com.youngs.picview.data.api.RetrofitClient
import com.youngs.picview.data.model.ImageItem
import com.youngs.picview.data.repository.CourseRepository
import com.youngs.picview.data.repository.DiaryRepository
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.youngs.picview.databinding.FragmentDetailBinding
import com.youngs.picview.databinding.ItemPeopleTipBinding
import com.youngs.picview.databinding.ItemScoreFactorBinding
import com.youngs.picview.databinding.ItemVisitRowBinding
import com.youngs.picview.domain.course.CourseStop
import com.youngs.picview.domain.course.ShootingCourse
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.light.SunTimes
import com.youngs.picview.domain.mission.Missions
import com.youngs.picview.domain.score.ScoreFactor
import com.youngs.picview.domain.spot.Facing
import com.youngs.picview.domain.spot.ShotTokens
import com.youngs.picview.domain.spot.SpotFacts
import com.youngs.picview.domain.weather.SkyState
import com.youngs.picview.domain.weather.WeatherAdvice
import com.youngs.picview.domain.weather.WeatherAdviser
import com.youngs.picview.domain.spot.SpotFactsTable
import com.youngs.picview.ui.guide.GuideOverlayView
import com.youngs.picview.ui.main.MainViewModel
import com.youngs.picview.ui.mission.MissionFragment
import com.youngs.picview.util.AppPrefs
import com.youngs.picview.util.TravelMode
import com.youngs.picview.util.TtsController
import com.youngs.picview.util.applyTopSystemBarInsetAsMargin
import com.youngs.picview.ui.adapter.ImagePagerAdapter
import com.youngs.picview.ui.guide.GuideActivity
import com.youngs.picview.ui.photo.PhotoDeleter
import com.youngs.picview.ui.photo.PhotoViewerActivity
import android.app.Activity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.youngs.picview.ui.model.SpotItem
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.time.LocalTime
import kotlin.math.roundToInt
import com.youngs.picview.util.OverviewDigest
import com.youngs.picview.util.OverviewFormatter

class DetailFragment : Fragment(R.layout.fragment_detail) {
    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_SPOT = "spot"

        private val imageCache = mutableMapOf<String, List<ImageItem>>()
        private val detailCache = mutableMapOf<String, String>()

        fun newInstance(spot: SpotItem) = DetailFragment().apply {
            arguments = Bundle().apply { putSerializable(ARG_SPOT, spot) }
        }
    }

    private val mainViewModel: MainViewModel by activityViewModels()

    private var tts: TtsController? = null

    /** 오디오로 읽어 줄 원문. 개요를 받아오면 채워집니다. */
    private var audioText: String = ""

    private var factorsExpanded = false

    /** 장소 설명 펼침 여부. 기본은 접힘입니다. */
    private var infoExpanded = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDetailBinding.bind(view)

        // 히어로 사진은 상태바 뒤까지 깔리는 게 맞지만, 그 위의 버튼과
        // 페이지 점은 시계·배터리와 겹치면 안 됩니다. 마진으로 내립니다.
        binding.btnBack.applyTopSystemBarInsetAsMargin()
        binding.layoutHeroActions.applyTopSystemBarInsetAsMargin()
        binding.layoutIndicator.applyTopSystemBarInsetAsMargin()

        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        setupTts()
        setListeners()
    }

    // ───────────────────── 오디오 가이드 ─────────────────────

    private fun setupTts() {
        val controller = TtsController(
            requireContext(),
            seniorMode = AppPrefs.isSeniorMode(requireContext())
        )
        viewLifecycleOwner.lifecycle.addObserver(controller)
        tts = controller

        controller.available.observe(viewLifecycleOwner) { ready ->
            binding.btnAudioGuide.isEnabled = ready
            binding.btnAudioSpeed.isVisible = ready
            if (!ready) binding.btnAudioGuide.setText(R.string.detail_audio_unavailable)
        }

        controller.speaking.observe(viewLifecycleOwner) { speaking ->
            binding.btnAudioGuide.setText(
                if (speaking) R.string.detail_audio_stop else R.string.detail_audio_play
            )
            // 읽는 동안 화면이 꺼지면 안내가 끊긴 것처럼 느껴집니다.
            binding.root.keepScreenOn = speaking
        }

        renderSpeedLabel()
        binding.btnAudioSpeed.setOnClickListener { cycleSpeed() }
        binding.btnAudioGuide.setOnClickListener {
            val text = audioText.ifBlank { binding.tvDetailTip.text.toString() }
            controller.toggle(text)
        }
    }

    /** 0.8 → 1.0 → 1.2 순환. 시니어 배려로 느린 속도부터 시작합니다. */
    private fun cycleSpeed() {
        val controller = tts ?: return
        controller.rate = when (controller.rate) {
            TtsController.SLOW_RATE -> TtsController.NORMAL_RATE
            TtsController.NORMAL_RATE -> TtsController.FAST_RATE
            else -> TtsController.SLOW_RATE
        }
        renderSpeedLabel()
        // 속도는 다음 발화부터 적용되므로 재생 중이면 다시 시작합니다.
        if (controller.speaking.value == true) {
            controller.stop()
            controller.speak(audioText)
        }
    }

    private fun renderSpeedLabel() {
        val rate = tts?.rate ?: TtsController.NORMAL_RATE
        binding.btnAudioSpeed.text =
            getString(R.string.detail_audio_speed, "%.1f".format(rate))
    }

    private fun setListeners() {
        val spot = arguments?.getSerializable(ARG_SPOT) as? SpotItem ?: return

        binding.tvDetailTitle.text = spot.title
        binding.tvDetailAddress.text = spot.addr1
        binding.tvDetailAddress.isVisible = spot.addr1.isNotBlank()

        binding.layoutDetailScore.isVisible = spot.score > 0
        binding.tvDetailScore.text = spot.score.toString()

        renderShootingFacts(spot)
        renderScoreBreakdown(spot)
        renderPlaceMissions(spot)
        setupCheckin(spot)
        setupMyShots(spot)

        loadImages(spot)
        loadTip(spot)
        loadVisitInfo(spot)

        binding.btnStartGuide.setOnClickListener { startGuide(spot) }

        binding.btnNavigate.setOnClickListener { openNavigation(spot) }
        setupFavorite(spot)
        binding.btnShare.setOnClickListener { shareSpot(spot) }
    }

    // ───────────────────── 찜 · 공유 ─────────────────────

    /** 하트 토글. 계정이 없으므로 단말(AppPrefs)에만 남습니다. */
    private fun setupFavorite(spot: SpotItem) {
        fun render(favorite: Boolean) {
            binding.btnFavorite.setImageResource(
                if (favorite) R.drawable.ic_heart_filled else R.drawable.ic_heart
            )
        }
        render(AppPrefs.isFavorite(requireContext(), spot.contentId))

        binding.btnFavorite.setOnClickListener {
            val favorite = AppPrefs.toggleFavorite(requireContext(), spot)
            render(favorite)
            Toast.makeText(
                requireContext(),
                if (favorite) R.string.detail_favorited else R.string.detail_unfavorited,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /** 장소명·주소·추천 시간을 문자 그대로 공유합니다. */
    private fun shareSpot(spot: SpotItem) {
        val text = getString(
            R.string.detail_share_text,
            spot.title,
            spot.addr1,
            binding.tvDetailBestTime.text
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, spot.title))
    }

    // ───────────────────── 방문 기록 ─────────────────────

    /** 촬영 가이드(앱 카메라)로 갑니다. 여기서 찍은 사진이 이 장소의 방문 기록이 됩니다. */
    private fun startGuide(spot: SpotItem) {
        val intent = Intent(requireContext(), GuideActivity::class.java).apply {
            putExtra(GuideActivity.EXTRA_SPOT_NAME, spot.title)
            putExtra(GuideActivity.EXTRA_SPOT_TYPE, spot.contentTypeId)
            // 포즈 추천이 지금의 빛을 반영해야 하므로 함께 넘깁니다.
            putExtra(GuideActivity.EXTRA_PHASE, mainViewModel.sunTimes.phaseNow().name)
            // 촬영한 사진을 이 장소의 방문 기록에 붙이기 위해 필요합니다.
            putExtra(GuideActivity.EXTRA_CONTENT_ID, spot.contentId)
        }
        startActivity(intent)
    }

    /**
     * "다녀왔어요" — 손으로 누르는 체크가 아니라, 촬영 기록으로 저절로 채워지는 표시입니다.
     *
     * 이 앱의 카메라(촬영 가이드, 시선 가이드 미션)로 사진을 찍으면 그 장소의
     * 방문 기록이 남고, 여기는 그 기록을 보여 줄 뿐입니다. 손으로 체크하지
     * 않는 이유:
     *  - 검색만 해 본 곳과 실제로 찍고 온 곳이 섞이지 않음
     *  - GPS 상시 권한 없이도 "그 자리에 있었다"는 증거(사진)가 남음
     *
     * 아직 기록이 없으면 버튼이 촬영 화면으로 이어 주고, 있으면 그 날짜를
     * 보여 주며 눌렀을 때 일기로 갑니다. Flow 라서 촬영하고 돌아오면 바로 바뀝니다.
     */
    private fun setupCheckin(spot: SpotItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            CourseRepository(requireContext()).observeVisits(spot.contentId).collect { visits ->
                val binding = _binding ?: return@collect
                val latest = visits.firstOrNull()
                if (latest == null) {
                    binding.btnCheckin.text = getString(R.string.detail_checkin_not_yet)
                    binding.btnCheckin.setIconResource(R.drawable.ic_camera)
                    binding.btnCheckin.setOnClickListener { startGuide(spot) }
                } else {
                    val date = Instant.ofEpochMilli(latest.visitedAt)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern(getString(R.string.detail_checkin_date_pattern)))
                    binding.btnCheckin.text = getString(R.string.detail_checkin_visited, date)
                    binding.btnCheckin.setIconResource(R.drawable.ic_check)
                    binding.btnCheckin.setOnClickListener {
                        // 일기 탭에서 들어온 경우 selectTab 이 같은 탭이라
                        // 백스택을 정리하지 않으므로, 상세부터 닫습니다.
                        parentFragmentManager.popBackStack()
                        (activity as? MainActivity)?.selectTab(R.id.tab_diary)
                    }
                }
            }
        }
    }

    /**
     * "내가 찍은 사진" 띠.
     *
     * 다녀온 곳을 눌러 들어오면 "다녀왔어요 · 9월 4일 촬영"까지는 보였지만
     * 정작 그때 무엇을 찍었는지는 볼 수 없었습니다. 이 장소의 촬영 기록에
     * 붙은 사진을 전부 가로로 늘어놓고, 누르면 그 장부터 크게 펼칩니다.
     * Flow 라서 촬영하고 돌아오면 바로 늘어납니다.
     */
    private fun setupMyShots(spot: SpotItem) {
        val adapter = MyShotAdapter(
            onClick = { index ->
                val photos = myShots
                if (index !in photos.indices) return@MyShotAdapter
                startActivity(
                    PhotoViewerActivity.intent(
                        requireContext(),
                        photoIds = photos.map { it.id },
                        uris = photos.map { it.uri },
                        takenAt = photos.map { it.takenAt },
                        place = spot.title,
                        start = index
                    )
                )
            },
            onLongClick = { photo ->
                PhotoDeleter.confirm(requireContext()) { deleteShot(photo) }
            }
        )
        binding.rvMyShots.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            CourseRepository(requireContext()).observePhotos(spot.contentId).collect { photos ->
                val binding = _binding ?: return@collect
                myShots = photos
                adapter.submitList(photos)
                binding.layoutMyShots.isVisible = photos.isNotEmpty()
                binding.tvMyShotsTitle.text =
                    getString(R.string.detail_my_shots_count, photos.size)
            }
        }
    }

    /** 띠에 올라와 있는 사진. 누른 순간 큰 화면에 넘길 목록입니다. */
    private var myShots: List<com.youngs.picview.data.local.VisitPhotoEntity> = emptyList()

    /** 시스템 삭제 확인창이 필요했던 사진. 승인되면 기록에서 뺍니다. */
    private var pendingShotDelete: com.youngs.picview.data.local.VisitPhotoEntity? = null
    private val shotDeleteRequest =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val photo = pendingShotDelete ?: return@registerForActivityResult
            pendingShotDelete = null
            if (result.resultCode == Activity.RESULT_OK) {
                viewLifecycleOwner.lifecycleScope.launch {
                    PhotoDeleter.removeRecord(requireContext(), photo.id)
                }
            }
        }

    /**
     * 띠에서 꾹 눌러 지우기.
     *
     * 띠는 Flow 를 보고 있어서 기록에서 빠지는 순간 저절로 줄어듭니다.
     * 여기서는 지우기만 하고 화면은 건드리지 않습니다.
     */
    private fun deleteShot(photo: com.youngs.picview.data.local.VisitPhotoEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            val context = context ?: return@launch
            when (val outcome = PhotoDeleter.delete(context, photo.id, photo.uri)) {
                PhotoDeleter.Outcome.Done ->
                    Toast.makeText(context, R.string.photo_viewer_deleted, Toast.LENGTH_SHORT).show()
                is PhotoDeleter.Outcome.NeedsSystemPrompt -> {
                    pendingShotDelete = photo
                    shotDeleteRequest.launch(IntentSenderRequest.Builder(outcome.sender).build())
                }
                PhotoDeleter.Outcome.Failed ->
                    Toast.makeText(context, R.string.photo_viewer_delete_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ───────────────────── 방문 정보 ─────────────────────

    /**
     * 입장료·주차·운영시간.
     *
     * 관광공사 detailIntro2 의 값은 금액이 아니라 "무료" · "공연, 전시에 따라
     * 다름" 같은 문장입니다. 숫자로 파싱하려 들면 대부분 실패하므로 그대로
     * 보여 줍니다. 값이 하나도 없으면 섹션 자체를 감춥니다 — 빈 표가 있으면
     * "정보가 없다"가 아니라 "앱이 고장났다"로 읽힙니다.
     */
    private fun loadVisitInfo(spot: SpotItem) {
        val typeId = spot.contentTypeId
        if (typeId.isNullOrBlank()) return

        viewLifecycleOwner.lifecycleScope.launch {
            val item = runCatching {
                RetrofitClient.tourApiService.getDetailIntro(
                    serviceKey = BuildConfig.TOUR_API_KEY,
                    contentId = spot.contentId,
                    contentTypeId = typeId
                )
            }.getOrNull()?.response?.body?.items?.item?.firstOrNull()

            val binding = _binding ?: return@launch
            if (item == null || !item.hasAnything) {
                binding.layoutDetailVisit.isVisible = false
                return@launch
            }

            val rows = listOfNotNull(
                item.feeText?.let { getString(R.string.detail_visit_fee) to it },
                item.parkingText?.let { getString(R.string.detail_visit_parking) to it },
                item.hoursText?.let { getString(R.string.detail_visit_hours) to it },
                item.restText?.let { getString(R.string.detail_visit_rest) to it }
            )

            binding.tableDetailVisit.removeAllViews()
            rows.forEach { (label, value) ->
                val row = ItemVisitRowBinding.inflate(layoutInflater, binding.tableDetailVisit, false)
                row.tvVisitLabel.text = label
                row.tvVisitValue.text = value
                binding.tableDetailVisit.addView(row.root)
            }
            binding.layoutDetailVisit.isVisible = true
        }
    }

    // ───────────────────── 촬영 정보 ─────────────────────

    /**
     * 이 장소를 언제·어느 방향에서·어떤 구도로 찍는지.
     *
     * 화면을 다시 짜면서 그래프를 얹었습니다. 예전에는 "일몰 골든아워 ·
     * 서향" 한 줄뿐이라 그것이 지금으로부터 얼마나 먼 이야기인지 알 수
     * 없었습니다. 그래프가 지금 위치를 찍어 주고, 그 아래 문장이 몇 시부터
     * 몇 시까지인지를 닫습니다.
     */
    private fun renderShootingFacts(spot: SpotItem) {
        val facts = SpotFactsTable.of(spot.title, spot.contentTypeId)
        val sun = mainViewModel.sunTimes
        val sky = mainViewModel.skyState.value

        binding.viewShootWindow.sunTimes = sun

        val window = pickWindow(sun, facts.bestPhase)
        val phase = window?.phase ?: facts.bestPhase

        // 제목이 "오늘의 촬영 적기" 로 고정돼 있으면 아침·저녁 어느 쪽
        // 이야기인지 그래프를 읽어야 압니다. 제목에서 바로 말합니다.
        binding.tvShootWindowTitle.text = getString(
            R.string.detail_window_title,
            getString(
                if (phase == LightPhase.SUNRISE) R.string.detail_window_morning
                else R.string.detail_window_evening
            )
        )

        binding.tvDetailBestTime.text = window?.let {
            getString(R.string.detail_window_range, it.start.hhmm(), it.end.hhmm())
        } ?: getString(R.string.course_sun_unknown)

        // "서향"은 지도의 말이고 "서향 빛"이 사진의 말입니다(시안 표기).
        // 실내·무관은 빛의 방향이 없으므로 이름 그대로 둡니다.
        binding.tvDetailFacing.text = if (facts.facing.bearing != null) {
            getString(R.string.detail_facing_light, facts.facing.label)
        } else {
            facts.facing.label
        }

        // 가운데 칸은 시안대로 "이 시간에 무엇을 담게 되는가"입니다.
        binding.tvDetailSubject.text = phase.subject

        // 날씨가 구도를 바꿉니다. 흐린 날의 정면 촬영과 맑은 날의 넓은
        // 풍경은 같은 장소에서도 다른 사진이 됩니다.
        val advice = WeatherAdviser.of(sky, phase, facts.facing, facts.guide)

        // 날씨 한 줄은 위 라이브 카드가 이미 말했습니다. 여기서는 일정만
        // 말합니다. 같은 문장을 두 번 쓰면 카드가 둘인 이유가 사라집니다.
        binding.tvDetailLightLine.text = window?.let {
            getString(
                R.string.detail_window_next,
                getString(
                    if (phase == LightPhase.SUNRISE) R.string.detail_window_morning
                    else R.string.detail_window_evening
                ),
                it.start.hhmm(), it.end.hhmm()
            )
        } ?: getString(R.string.course_sun_unknown)
        binding.tvDetailFactsNote.text = advice.action
        renderGuideTabs(facts, advice.guide, phase)

        renderLiveWeather(sky, advice)
        binding.btnSavePlan.setOnClickListener { savePlan(spot, facts) }
    }

    /**
     * 구도 · 방향 · 인물 탭 (시안의 촬영 가이드).
     *
     * 예전에는 셋을 알약 세 개로 요약만 했습니다. 이름은 알려 주지만
     * 현장에서 필요한 것은 "그래서 어떻게 서고 어디를 보나"라서,
     * 탭 하나가 물음 하나에 답하도록 폈습니다.
     *
     * 구도가 날씨에 따라 바뀌므로([WeatherAdviser]) 구도 탭 내용도 함께
     * 바뀝니다.
     */
    private fun renderGuideTabs(
        facts: SpotFacts,
        guide: GuideOverlayView.GuideType,
        phase: LightPhase
    ) {
        binding.toggleGuideTabs.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            binding.layoutGuideComposition.isVisible = checkedId == R.id.btn_tab_composition
            binding.layoutGuideDirection.isVisible = checkedId == R.id.btn_tab_direction
            binding.layoutGuidePeople.isVisible = checkedId == R.id.btn_tab_people
        }

        renderCompositionTab(guide)
        renderDirectionTab(facts.facing, phase)
        renderPeopleTab()
    }

    /** 구도 탭 — 이 장소 사진 위에 실제 가이드 선을 얹어 보여 줍니다. */
    private fun renderCompositionTab(guide: GuideOverlayView.GuideType) {
        binding.viewCompOverlay.guideType = guide

        binding.tvCompTitle.setText(
            when (guide) {
                GuideOverlayView.GuideType.THIRDS -> R.string.guide_comp_title_thirds
                GuideOverlayView.GuideType.SYMMETRY -> R.string.guide_comp_title_symmetry
                GuideOverlayView.GuideType.CENTER -> R.string.guide_comp_title_center
            }
        )
        binding.tvCompDesc.setText(
            when (guide) {
                GuideOverlayView.GuideType.THIRDS -> R.string.guide_comp_desc_thirds
                GuideOverlayView.GuideType.SYMMETRY -> R.string.guide_comp_desc_symmetry
                GuideOverlayView.GuideType.CENTER -> R.string.guide_comp_desc_center
            }
        )
        binding.tvCompWhen.setText(
            when (guide) {
                GuideOverlayView.GuideType.THIRDS -> R.string.guide_comp_when_thirds
                GuideOverlayView.GuideType.SYMMETRY -> R.string.guide_comp_when_symmetry
                GuideOverlayView.GuideType.CENTER -> R.string.guide_comp_when_center
            }
        )

        // 다른 구도 제안 — 지금 것을 뺀 나머지 구도와, 각도·역광 계열 제안.
        val alternatives =
            GuideOverlayView.GuideType.entries
                .filter { it != guide }
                .map { ShotTokens.of(it).text } +
                listOf("⛰ 낮은 각도", "◐ 실루엣")

        val group = binding.chipsCompAlt
        group.removeAllViews()
        alternatives.forEach { label ->
            group.addView(
                Chip(requireContext()).apply {
                    text = label
                    isClickable = false
                    isCheckable = false
                    chipBackgroundColor = null
                    background = ContextCompat.getDrawable(context, R.drawable.bg_shot_token)
                    setTextColor(ContextCompat.getColor(context, R.color.maple_700))
                    textSize = 12f
                    chipStrokeWidth = 0f
                    chipMinHeight = resources.displayMetrics.density * 30
                    setEnsureMinTouchTargetSize(false)
                }
            )
        }
    }

    /** 방향 탭 — 나침반 위에 촬영 방향을 켭니다. */
    private fun renderDirectionTab(facing: Facing, phase: LightPhase) {
        // 실내·무관은 가리킬 방위가 없습니다. 나침반을 빈 채로 두면
        // "고장난 그림"으로 읽히므로 그림 자체를 뺍니다.
        binding.viewFacingCompass.isVisible = facing.bearing != null
        binding.viewFacingCompass.facing = facing

        val timeWord = when (phase) {
            LightPhase.SUNRISE, LightPhase.BLUE_DAWN -> "해뜰 무렵"
            LightPhase.SUNSET, LightPhase.BLUE_DUSK -> "해질 무렵"
            else -> phase.label
        }
        binding.tvDirHeadline.text =
            getString(R.string.guide_dir_headline, facing.label, timeWord)

        binding.tvDirDesc.setText(
            when (facing) {
                Facing.WEST -> R.string.guide_dir_desc_west
                Facing.EAST -> R.string.guide_dir_desc_east
                Facing.SOUTH -> R.string.guide_dir_desc_south
                Facing.NORTH -> R.string.guide_dir_desc_north
                Facing.INDOOR -> R.string.guide_dir_desc_indoor
                Facing.ANY -> R.string.guide_dir_desc_any
            }
        )
    }

    /** 인물 탭 — 시안의 번호 팁 세 줄. */
    private fun renderPeopleTab() {
        val container = binding.layoutGuidePeople
        if (container.childCount > 0) return

        val tips = listOf(
            R.string.guide_people_1_label to R.string.guide_people_1_desc,
            R.string.guide_people_2_label to R.string.guide_people_2_desc,
            R.string.guide_people_3_label to R.string.guide_people_3_desc
        )
        tips.forEachIndexed { index, (labelRes, descRes) ->
            val row = ItemPeopleTipBinding.inflate(layoutInflater, container, false)
            row.tvTipNumber.text = (index + 1).toString()
            row.tvTipLabel.setText(labelRes)
            row.tvTipDesc.setText(descRes)
            container.addView(row.root)
        }
    }

    /**
     * 이 장소에서 지금 할 수 있는 미션.
     *
     * 전체 미션을 다 보여 주지 않습니다. 스무 개 목록을 현장에서 훑게 하면
     * 아무것도 안 합니다. 이 장소에서 할 수 있고, 아직 안 끝났고, 지금
     * 빛에 맞는 것부터 최대 셋입니다.
     *
     * 같은 `missionId` 를 쓰므로 여기서 사진을 남기면 전체 미션의 진행률도
     * 함께 올라갑니다. 두 화면이 따로 세면 어긋난 숫자가 남습니다.
     */
    private fun renderPlaceMissions(spot: SpotItem) {
        val adapter = PlaceMissionAdapter { goToMissions() }
        binding.rvPlaceMissions.adapter = adapter
        binding.tvMissionsMore.setOnClickListener { goToMissions() }

        val phase = mainViewModel.sunTimes.phaseNow()

        viewLifecycleOwner.lifecycleScope.launch {
            DiaryRepository(requireContext()).observeDays().collect { days ->
                val visits = days.flatMap { it.visits }
                val here = Missions.forPlace(spot.title, visits, phase)

                binding.layoutPlaceMissions.isVisible = here.isNotEmpty()
                adapter.submitList(here)
            }
        }
    }

    private fun goToMissions() {
        (activity as? MainActivity)?.pushScreen(MissionFragment())
    }

    /**
     * 지금 이 자리의 날씨.
     *
     * 숫자만 두면 날씨 앱이 됩니다. 그 날씨가 사진을 어떻게 바꾸는지를
     * 한 줄로 붙여야 출사 앱의 정보가 됩니다.
     */
    private fun renderLiveWeather(sky: SkyState?, advice: WeatherAdvice) {
        val temp = mainViewModel.temperatureC.value
        val feels = mainViewModel.feelsLikeC.value
        val humidity = mainViewModel.humidityPercent.value

        // 관측을 하나도 못 받았으면 카드를 감춥니다. 빈 껍데기가 남는 것보다
        // 없는 편이 낫습니다.
        binding.cardLiveWeather.isVisible = temp != null || sky != null

        binding.tvLiveTemp.text = temp?.let { "${it.roundToInt()}°" } ?: "—"

        val skyLabel = sky?.let { "${it.emoji} ${it.label}" } ?: getString(R.string.detail_no_weather)
        binding.tvLiveSky.text = if (humidity != null) {
            getString(R.string.detail_sky_humidity, skyLabel, humidity.roundToInt())
        } else {
            skyLabel
        }

        // 체감·습도는 시안대로 오른쪽 숫자 열에 모읍니다. 왼쪽 문장에는
        // 날씨가 사진을 어떻게 바꾸는지 한 줄만 남깁니다.
        binding.tvLiveNote.text = advice.headline

        binding.tvLiveFeels.isVisible = feels != null
        feels?.let {
            binding.tvLiveFeels.text = getString(R.string.detail_feels_short, it.roundToInt())
        }
        binding.tvLiveHumidity.isVisible = humidity != null
        humidity?.let {
            binding.tvLiveHumidity.text =
                getString(R.string.detail_humidity_short, it.roundToInt())
        }
    }

    /**
     * 보여 줄 골든아워 창을 고릅니다.
     *
     * 이 장소의 최적 구간이 골든아워면 그 창을 그대로 씁니다. 서향 고택에
     * 아침 골든아워를 권하면 해를 등지고 서게 되니 맞지 않습니다.
     * 최적 구간이 골든아워가 아니면(한낮이 좋은 실내 등) 오늘 남은 것 중
     * 가까운 쪽을 씁니다.
     */
    private fun pickWindow(sun: SunTimes, best: LightPhase) =
        sun.upcomingGoldenWindows(LocalTime.MIN).firstOrNull { it.phase == best }
            ?: sun.upcomingGoldenWindows().firstOrNull()
            ?: sun.upcomingGoldenWindows(LocalTime.MIN).firstOrNull()

    private fun LocalTime.hhmm() = "%02d:%02d".format(hour, minute)

    /**
     * 이 장소 한 곳짜리 출사 일정을 저장합니다.
     *
     * 하단 CTA(촬영하기·길찾기)는 "지금 움직인다"이고 이건 "나중에 온다"라
     * 성격이 다릅니다. 그래서 빛 카드 안에 뒀습니다.
     *
     * 새 저장소를 만들지 않고 기존 코스 저장을 그대로 씁니다. 정거장이
     * 하나뿐인 코스일 뿐이라, 저장하면 코스 목록에 그대로 나타납니다.
     */
    private fun savePlan(spot: SpotItem, facts: SpotFacts) {
        val sun = mainViewModel.sunTimes
        val window = pickWindow(sun, facts.bestPhase)

        val arrive = window?.start ?: LocalTime.of(9, 0)
        val stop = CourseStop(
            spot = spot,
            facts = facts,
            arriveAt = arrive,
            leaveAt = window?.end ?: arrive.plusMinutes(60),
            phase = window?.phase ?: facts.bestPhase,
            travelMinutes = 0,
            travelKm = 0.0,
            reason = facts.note,
            isHighlight = true
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val saved = runCatching {
                CourseRepository(requireContext()).save(
                    ShootingCourse(
                        stops = listOf(stop),
                        sun = sun,
                        travelMode = TravelMode.CAR,
                        totalDistanceKm = 0.0
                    ),
                    summary = binding.tvDetailBestTime.text.toString()
                )
            }.isSuccess

            Toast.makeText(
                requireContext(),
                if (saved) R.string.detail_plan_saved else R.string.detail_plan_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ───────────────────── 포토스코어 근거 ─────────────────────

    /**
     * 점수를 항목별로 펼쳐 보여 줍니다.
     *
     * 추천 점수는 근거가 보여야 신뢰가 생깁니다.
     * 기본은 한 줄 요약만 보여 주고, 눌러야 전체가 펼쳐집니다.
     */
    private fun renderScoreBreakdown(spot: SpotItem) {
        val score = mainViewModel.scoreOf(spot.contentId)
        val header = binding.layoutScoreHeader
        val container = binding.layoutScoreFactors

        if (score == null || score.factors.isEmpty()) {
            header.isVisible = false
            binding.tvScoreHeadline.isVisible = false
            container.isVisible = false
            return
        }

        // 강점 둘과 발목을 잡은 것 하나를 엮어 설명합니다.
        // 항목 하나만 보여 주면 "비가 오지 않아요" 로 끝나 버립니다.
        binding.tvScoreTitle.setText(R.string.detail_score_title)
        binding.gaugeScore.score = score.total
        binding.tvScoreHeadline.text = score.summary

        container.removeAllViews()
        score.factors.forEach { factor ->
            container.addView(buildFactorRow(factor))
        }

        header.setOnClickListener { toggleFactors() }
        applyFactorsVisibility()
        setupInfoToggle()
    }

    /**
     * 장소 설명은 접어 둡니다.
     *
     * 역사·건축 이야기는 길고 출사 결정에는 쓰이지 않습니다. 예전에는 이것이
     * 화면 중반을 통째로 차지해 정작 점수와 촬영 정보를 아래로 밀어냈습니다.
     */
    private fun setupInfoToggle() {
        binding.layoutInfoHeader.setOnClickListener {
            infoExpanded = !infoExpanded
            applyInfoVisibility()
        }
        applyInfoVisibility()
    }

    private fun applyInfoVisibility() {
        binding.layoutInfoBody.isVisible = infoExpanded
        binding.tvInfoToggle.setText(
            if (infoExpanded) R.string.detail_info_collapse else R.string.detail_info_expand
        )
    }

    private fun toggleFactors() {
        factorsExpanded = !factorsExpanded
        applyFactorsVisibility()
    }

    private fun applyFactorsVisibility() {
        binding.layoutScoreFactors.isVisible = factorsExpanded
        binding.tvScoreToggle.setText(
            if (factorsExpanded) R.string.detail_score_collapse
            else R.string.detail_score_expand
        )
    }

    private fun buildFactorRow(factor: ScoreFactor): View {
        val row = ItemScoreFactorBinding.inflate(layoutInflater, binding.layoutScoreFactors, false)

        row.tvFactorLabel.text = getString(factor.kind.labelRes)
        row.tvFactorValue.text = getString(
            R.string.detail_factor_value,
            trimNumber(factor.earned),
            trimNumber(factor.max)
        )
        row.tvFactorReason.text = factor.reason
        row.pbFactor.progress = (factor.ratio * 100).toInt()

        // 점수를 깎은 항목은 빨간 계열로 구분합니다.
        val color = ContextCompat.getColor(
            requireContext(),
            if (factor.earned < 0) R.color.maple_500 else R.color.golden_600
        )
        row.pbFactor.progressDrawable?.mutate()?.let { drawable ->
            (drawable as? android.graphics.drawable.LayerDrawable)
                ?.findDrawableByLayerId(android.R.id.progress)
                ?.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
        }
        row.tvFactorValue.setTextColor(color)

        return row.root
    }

    /** 12.0 → "12", 12.5 → "12.5" */
    private fun trimNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

    private fun openNavigation(spot: SpotItem) {
        val encodedName = URLEncoder.encode(spot.title, "UTF-8")

        // 1. 네이버 지도 앱 실행 스킴
        val appUrl = "nmap://route/car?dlat=${spot.mapy}&dlng=${spot.mapx}" +
                "&dname=$encodedName&appname=${BuildConfig.APPLICATION_ID}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(appUrl)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        // 2. 앱이 없으면 웹 지도로 폴백
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
        } else {
            val webUrl = "https://map.naver.com/v5/directions/-/" +
                    "${spot.mapx},${spot.mapy},$encodedName,,,/car"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
        }
    }

    private fun loadImages(spot: SpotItem) {
        val cachedImages = imageCache[spot.contentId]
        if (cachedImages != null) {
            setupViewPager(cachedImages)
            return
        }

        binding.progressBar.isVisible = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.tourApiService.getSpotImages(
                    serviceKey = BuildConfig.TOUR_API_KEY,
                    contentId = spot.contentId
                )
                // 사진이 없는 장소는 items 자체가 비어서 오기도 합니다.
                val list = response.response?.body?.items?.item
                    ?.filter { !it.originImgUrl.isNullOrBlank() }
                    .orEmpty()
                imageCache[spot.contentId] = list
                setupViewPager(list)
            } catch (e: Exception) {
                Log.e("IMAGE_ERROR", "이미지 로드 실패: ${e.message}")
                setupViewPager(emptyList())
            } finally {
                _binding?.progressBar?.isVisible = false
            }
        }
    }

    private fun setupViewPager(list: List<ImageItem>) {
        val binding = _binding ?: return

        if (list.isEmpty()) {
            binding.vpDetailImages.isVisible = false
            binding.layoutNoImage.isVisible = true
            binding.ivArrowLeft.isVisible = false
            binding.ivArrowRight.isVisible = false
            binding.layoutIndicator.isVisible = false
            return
        }

        binding.vpDetailImages.isVisible = true
        binding.layoutNoImage.isVisible = false
        binding.vpDetailImages.adapter = ImagePagerAdapter(list)

        // 구도 탭의 예시 컷 — 이 장소의 첫 사진 위에 가이드 선을 얹습니다.
        // 남의 장소 사진으로 설명하면 "여기서 이렇게"가 아니게 됩니다.
        Glide.with(this)
            .load(list.first().originImgUrl)
            .centerCrop()
            .into(binding.ivCompSample)

        buildIndicator(list.size)
        updatePageUi(0, list.size)

        binding.vpDetailImages.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updatePageUi(position, list.size)
                }
            }
        )

        binding.ivArrowLeft.setOnClickListener { binding.vpDetailImages.currentItem -= 1 }
        binding.ivArrowRight.setOnClickListener { binding.vpDetailImages.currentItem += 1 }
    }

    /** 사진 장수만큼 인디케이터 점을 만들어 둡니다. */
    private fun buildIndicator(count: Int) {
        val container = binding.layoutIndicator
        container.removeAllViews()
        container.isVisible = count > 1
        if (count <= 1) return

        val density = resources.displayMetrics.density
        repeat(count) {
            val dot = View(requireContext())
            dot.layoutParams = LinearLayout.LayoutParams(
                (6 * density).toInt(), (6 * density).toInt()
            ).apply { marginStart = if (it == 0) 0 else (5 * density).toInt() }
            dot.setBackgroundResource(R.drawable.dot_indicator_inactive)
            container.addView(dot)
        }
    }

    /** 현재 페이지에 맞춰 화살표와 인디케이터 상태를 갱신합니다. */
    private fun updatePageUi(position: Int, total: Int) {
        val binding = _binding ?: return

        binding.ivArrowLeft.isVisible = position > 0
        binding.ivArrowRight.isVisible = position < total - 1

        val density = resources.displayMetrics.density
        for (i in 0 until binding.layoutIndicator.childCount) {
            val dot = binding.layoutIndicator.getChildAt(i)
            val active = i == position
            dot.setBackgroundResource(
                if (active) R.drawable.dot_indicator_active else R.drawable.dot_indicator_inactive
            )
            dot.layoutParams = (dot.layoutParams as LinearLayout.LayoutParams).apply {
                width = ((if (active) 18 else 6) * density).toInt()
            }
            dot.requestLayout()
        }
    }

    private fun loadTip(spot: SpotItem) {
        val cachedTip = detailCache[spot.contentId]
        if (cachedTip != null) {
            audioText = cachedTip
            renderOverview(cachedTip)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.tourApiService.getDetailCommon(
                    serviceKey = BuildConfig.TOUR_API_KEY,
                    contentId = spot.contentId
                )

                val overview = response.response?.body?.items?.item
                    ?.firstOrNull()?.overview
                    ?.takeIf { it.isNotBlank() }
                    ?: "이 장소는 삼분할 구도를 활용해 인물과 배경을 조화롭게 담아보세요!"
                detailCache[spot.contentId] = overview

                audioText = overview
                renderOverview(overview)
            } catch (e: Exception) {
                Log.e("DETAIL_ERROR", "API 호출 실패: ", e)
                renderOverview(
                    "이곳은 삼분할 구도를 활용해 배경과 인물을 배치하면 더욱 안정적인 사진을 얻을 수 있습니다."
                )
            }
        }
    }

    /**
     * 장소 설명을 세 갈래로 나눠 앉힙니다.
     *
     *   태그   — 어떤 곳인지 한눈에 (#공연장 #전시)
     *   세 줄  — 접어 둔 채로도 남는 요지
     *   전문   — 펼친 사람만 보는 원문 (형광펜 그대로)
     *
     * 셋 다 같은 원문에서 나오므로 서로 어긋나지 않고, 요약은 원문의
     * 앞 문장을 그대로 써 없는 말이 섞이지 않습니다.
     */
    private fun renderOverview(overview: String) {
        val view = _binding ?: return

        view.tvDetailTip.text = OverviewFormatter.format(overview)
        view.tvInfoSummary.text = OverviewDigest.summaryOf(overview)

        val tags = OverviewDigest.tagsOf(overview)
        view.chipsInfoTags.removeAllViews()
        view.chipsInfoTags.isVisible = tags.isNotEmpty()
        tags.forEach { tag ->
            view.chipsInfoTags.addView(
                Chip(requireContext()).apply {
                    text = tag
                    isCheckable = false
                    isClickable = false
                    chipStrokeWidth = 0f
                    textSize = 12f
                    chipBackgroundColor = ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.maple_50)
                    )
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.maple_700))
                    chipMinHeight = 28 * resources.displayMetrics.density
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
