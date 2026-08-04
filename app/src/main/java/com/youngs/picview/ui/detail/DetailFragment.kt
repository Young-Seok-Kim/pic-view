package com.youngs.picview.ui.detail

import android.content.Intent
import android.net.Uri
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
import com.youngs.picview.BuildConfig
import com.youngs.picview.R
import com.youngs.picview.data.api.RetrofitClient
import com.youngs.picview.data.model.ImageItem
import com.youngs.picview.data.repository.CourseRepository
import com.youngs.picview.databinding.FragmentDetailBinding
import com.youngs.picview.databinding.ItemScoreFactorBinding
import com.youngs.picview.domain.score.ScoreFactor
import com.youngs.picview.domain.spot.SpotFactsTable
import com.youngs.picview.ui.guide.GuideOverlayView
import com.youngs.picview.ui.main.MainViewModel
import com.youngs.picview.util.AppPrefs
import com.youngs.picview.util.TtsController
import com.youngs.picview.ui.adapter.ImagePagerAdapter
import com.youngs.picview.ui.guide.GuideActivity
import com.youngs.picview.ui.model.SpotItem
import kotlinx.coroutines.launch
import java.net.URLEncoder

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDetailBinding.bind(view)

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
        setupCheckin(spot)

        loadImages(spot)
        loadTip(spot)

        binding.btnStartGuide.setOnClickListener {
            val intent = Intent(requireContext(), GuideActivity::class.java).apply {
                putExtra(GuideActivity.EXTRA_SPOT_NAME, spot.title)
                putExtra(GuideActivity.EXTRA_SPOT_TYPE, spot.contentTypeId)
                // 포즈 추천이 지금의 빛을 반영해야 하므로 함께 넘깁니다.
                putExtra(GuideActivity.EXTRA_PHASE, mainViewModel.sunTimes.phaseNow().name)
            }
            startActivity(intent)
        }

        binding.btnNavigate.setOnClickListener { openNavigation(spot) }
    }

    // ───────────────────── 방문 기록 ─────────────────────

    /**
     * "다녀왔어요" — 출사 기록(일기 탭)의 원천 데이터를 남깁니다.
     *
     * GPS 자동 체크인 대신 수동 버튼을 쓰는 이유:
     *  - 위치 권한을 상시 요구하지 않아도 됨(스토어 심사·배터리 모두 유리)
     *  - 검색만 해 본 곳과 실제로 다녀온 곳이 섞이지 않음
     */
    private fun setupCheckin(spot: SpotItem) {
        binding.btnCheckin.setOnClickListener {
            val phase = mainViewModel.sunTimes.phaseNow()
            viewLifecycleOwner.lifecycleScope.launch {
                val logged = runCatching {
                    CourseRepository(requireContext()).logVisit(spot, phase)
                }.getOrDefault(false)

                val message = if (logged) R.string.detail_checkin_done
                else R.string.detail_checkin_already
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ───────────────────── 촬영 정보 ─────────────────────

    /** 이 장소를 언제·어느 방향에서·어떤 구도로 찍는지. 출사 앱의 핵심 정보입니다. */
    private fun renderShootingFacts(spot: SpotItem) {
        val facts = SpotFactsTable.of(spot.title, spot.contentTypeId)

        binding.tvDetailBestTime.text = getString(
            R.string.detail_best_time_format, facts.bestPhase.label, facts.facing.label
        )
        binding.tvDetailFactsNote.text = facts.note
        binding.tvDetailGuide.text = getString(
            R.string.course_guide_format, guideName(facts.guide)
        )
    }

    private fun guideName(type: GuideOverlayView.GuideType): String = getString(
        when (type) {
            GuideOverlayView.GuideType.THIRDS -> R.string.guide_name_thirds
            GuideOverlayView.GuideType.SYMMETRY -> R.string.guide_name_symmetry
            GuideOverlayView.GuideType.CENTER -> R.string.guide_name_center
        }
    )

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

        // 점수를 가장 많이 올린 항목(또는 깎은 항목)을 한 줄로 요약합니다.
        val highlight = score.penalty ?: score.topFactor
        binding.tvScoreHeadline.text = highlight?.reason.orEmpty()

        container.removeAllViews()
        score.factors.forEach { factor ->
            container.addView(buildFactorRow(factor))
        }

        header.setOnClickListener { toggleFactors() }
        applyFactorsVisibility()
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
            _binding?.tvDetailTip?.text = cachedTip
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
                _binding?.tvDetailTip?.text = overview
            } catch (e: Exception) {
                Log.e("DETAIL_ERROR", "API 호출 실패: ", e)
                _binding?.tvDetailTip?.text =
                    "이곳은 삼분할 구도를 활용해 배경과 인물을 배치하면 더욱 안정적인 사진을 얻을 수 있습니다."
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
