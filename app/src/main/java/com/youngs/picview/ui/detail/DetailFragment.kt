package com.youngs.picview.ui.detail

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
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

class DetailFragment : Fragment(R.layout.fragment_detail) {
    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    companion object {
        private val imageCache = mutableMapOf<String, List<ImageItem>>()
        private val detailCache = mutableMapOf<String, String>()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDetailBinding.bind(view)

        val spot = arguments?.getSerializable("spot") as? SpotItem

        spot?.let { item ->
            // UI 데이터 바인딩
            binding.tvDetailTitle.text = item.title
            binding.tvDetailAddress.text = item.addr1

            // 함수 호출
            loadImages(item)
            loadTip(item)

            binding.btnStartGuide.setOnClickListener {
                val intent = Intent(requireContext(), GuideActivity::class.java).apply {
                    putExtra("SPOT_NAME", item.title)
                }
                startActivity(intent)
            }
        }
    }

    private fun loadImages(spot: SpotItem) {
        val cachedImages = imageCache[spot.contentId]
        if (cachedImages != null) {
            setupViewPager(cachedImages)
        } else {
            _binding?.progressBar?.visibility = View.VISIBLE
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
                } finally {
                    _binding?.progressBar?.visibility = View.GONE
                }
            }
        }
    }

    private fun setupViewPager(list: List<ImageItem>) {
        if (list.isEmpty()) {
            binding.vpDetailImages.visibility = View.GONE
            binding.ivArrowLeft.visibility = View.GONE
            binding.ivArrowRight.visibility = View.GONE
            return
        }

        binding.vpDetailImages.adapter = ImagePagerAdapter(list)

        // 초기 화살표 상태
        binding.ivArrowRight.visibility = if (list.size <= 1) View.GONE else View.VISIBLE
        binding.ivArrowLeft.visibility = View.GONE

        binding.vpDetailImages.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.ivArrowLeft.visibility = if (position == 0) View.GONE else View.VISIBLE
                binding.ivArrowRight.visibility = if (position == list.size - 1) View.GONE else View.VISIBLE
            }
        })

        binding.ivArrowLeft.setOnClickListener { binding.vpDetailImages.currentItem -= 1 }
        binding.ivArrowRight.setOnClickListener { binding.vpDetailImages.currentItem += 1 }
    }

    private fun loadTip(spot: SpotItem) {
        val cachedTip = detailCache[spot.contentId]
        if (cachedTip != null) {
            // 여기도 binding 접근 시 주의해야 합니다.
            _binding?.tvDetailTip?.text = cachedTip
        } else {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val response = RetrofitClient.tourApiService.getDetailCommon(
                        serviceKey = BuildConfig.TOUR_API_KEY,
                        contentId = spot.contentId
                    )

                    Log.d("DETAIL_RESPONSE", response.toString())
                    val overview = response.response?.body?.items?.item?.firstOrNull()?.overview
                        ?: "이 장소는 삼분할 구도를 활용해 인물과 배경을 조화롭게 담아보세요!"
                    detailCache[spot.contentId] = overview

                    // [수정] _binding이 null이 아닐 때만 텍스트 설정
                    _binding?.tvDetailTip?.text = overview
                } catch (e: Exception) {
                    Log.e("DETAIL_ERROR", "API 호출 실패: ", e) // e를 같이 출력하면 에러 이유가 정확히 나옴
                    _binding?.tvDetailTip?.text = "이곳은 삼분할 구도를 활용해 배경과 인물을 배치하면 더욱 안정적인 사진을 얻을 수 있습니다."
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}