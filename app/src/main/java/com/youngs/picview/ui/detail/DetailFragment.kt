package com.youngs.picview.ui.detail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.youngs.picview.BuildConfig
import com.youngs.picview.R
import com.youngs.picview.data.api.RetrofitClient
import com.youngs.picview.data.model.ImageItem
import com.youngs.picview.databinding.FragmentDetailBinding
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDetailBinding.bind(view)

        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        setListeners()
    }

    private fun setListeners() {
        val spot = arguments?.getSerializable(ARG_SPOT) as? SpotItem ?: return

        binding.tvDetailTitle.text = spot.title
        binding.tvDetailAddress.text = spot.addr1
        binding.tvDetailAddress.isVisible = spot.addr1.isNotBlank()

        binding.layoutDetailScore.isVisible = spot.score > 0
        binding.tvDetailScore.text = spot.score.toString()

        loadImages(spot)
        loadTip(spot)

        binding.btnStartGuide.setOnClickListener {
            val intent = Intent(requireContext(), GuideActivity::class.java).apply {
                putExtra("SPOT_NAME", spot.title)
            }
            startActivity(intent)
        }

        binding.btnNavigate.setOnClickListener { openNavigation(spot) }
    }

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
                val list = response.response.body.items.item ?: emptyList()
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
            _binding?.tvDetailTip?.text = cachedTip
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.tourApiService.getDetailCommon(
                    serviceKey = BuildConfig.TOUR_API_KEY,
                    contentId = spot.contentId
                )

                val overview = response.response.body.items.item?.firstOrNull()?.overview
                    ?: "이 장소는 삼분할 구도를 활용해 인물과 배경을 조화롭게 담아보세요!"
                detailCache[spot.contentId] = overview

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
