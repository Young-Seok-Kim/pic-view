package com.youngs.picview.ui.main

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.youngs.picview.BuildConfig
import com.youngs.picview.R
import com.youngs.picview.data.api.RetrofitClient
import com.youngs.picview.data.api.WeatherApiService
import com.youngs.picview.databinding.FragmentMainBinding
import com.youngs.picview.ui.adapter.SpotAdapter
import com.youngs.picview.ui.detail.DetailFragment
import com.youngs.picview.ui.map.MapFragment
import com.youngs.picview.ui.model.SpotItem
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.Duration
import kotlin.getValue

class MainFragment : Fragment(R.layout.fragment_main) {
    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    companion object {
        private var cachedSpots: List<SpotItem>? = null
    }

    private val viewModel: MainViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMainBinding.bind(view)

        viewModel.weatherData.observe(viewLifecycleOwner) { weather ->
            binding.tvWeatherStatus.text = weather
        }

        viewModel.goldenHourData.observe(viewLifecycleOwner) { golden ->
            binding.tvGoldenHour.text = golden
        }

        // 로딩 상태에 따라 프로그레스바 조절
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        val spotAdapter = SpotAdapter(emptyList()) { spot ->
            val detailFragment = DetailFragment().apply {
                arguments = Bundle().apply {
                    putSerializable("spot", spot)
                }
            }
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, detailFragment)
                .addToBackStack(null)
                .commit()
        }

        binding.rvPhotoSpots.adapter = spotAdapter

        viewModel.weatherData.observe(viewLifecycleOwner) { binding.tvWeatherStatus.text = it }
        viewModel.goldenHourData.observe(viewLifecycleOwner) { binding.tvGoldenHour.text = it }

        // 데이터가 로딩되어 뷰모델에 들어오면 자동으로 어댑터 갱신
        viewModel.spotData.observe(viewLifecycleOwner) { spots ->
            spotAdapter.updateData(spots)
        }

//        if (cachedSpots != null) {
//            spotAdapter.updateData(cachedSpots!!)
//        } else {
//            binding.progressBar.visibility = View.VISIBLE
//            viewLifecycleOwner.lifecycleScope.launch {
//                try {
//                    val response = RetrofitClient.tourApiService.getJeongeupSpots(BuildConfig.TOUR_API_KEY)
//                    val rawSpots = response.response.body.items.item
//                    cachedSpots = rawSpots.map { item ->
//                        // 1. 이름 키워드 기반으로 팁을 자동 생성하는 함수
//                        val generatedTip = when {
//                            item.title.contains("산") || item.title.contains("봉") -> "광각 렌즈를 사용하여 웅장한 지형과 하늘을 함께 담아보세요."
//                            item.title.contains("공원") || item.title.contains("정원") -> "자연광이 좋은 낮 시간에 꽃과 인물을 조화롭게 담아보세요."
//                            item.title.contains("사찰") || item.title.contains("탑") -> "건물의 직선미와 대칭을 활용하여 정적인 구도로 촬영해 보세요."
//                            else -> "삼분할 구도를 활용해 배경과 인물을 배치하면 더욱 안정적인 사진을 얻을 수 있습니다."
//                        }
//
//                        SpotItem(
//                            contentId = item.contentid,
//                            title = item.title,
//                            imageUrl = item.firstimage ?: "",
//                            addr1 = item.addr1,
//                            tip = generatedTip // 생성한 팁 넣기
//                        )
//                    }
//                    spotAdapter.updateData(cachedSpots!!)
//                } catch (e: Exception) {
//                    Log.e("API_ERROR", "통신 실패: ${e.message}")
//                } finally {
//                    binding.progressBar.visibility = View.GONE
//                }
//            }
//        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}