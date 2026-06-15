package com.youngs.picview.ui.main

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.youngs.picview.R
import com.youngs.picview.databinding.FragmentMainBinding
import com.youngs.picview.ui.adapter.SpotAdapter
import com.youngs.picview.ui.detail.DetailFragment
import com.youngs.picview.ui.map.MapFragment
import com.youngs.picview.ui.model.SpotItem
import kotlin.getValue
import android.os.Parcelable

class MainFragment : Fragment(R.layout.fragment_main) {
    private var recyclerViewState: Parcelable? = null

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    companion object {
        private var cachedSpots: List<SpotItem>? = null
    }

    private val viewModel: MainViewModel by activityViewModels()
    override fun onPause() {
        super.onPause()
        // 현재 스크롤 위치 저장
        recyclerViewState = binding.rvPhotoSpots.layoutManager?.onSaveInstanceState()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMainBinding.bind(view)

        setListeners()
        setObserve()
        recyclerViewState?.let {
            binding.rvPhotoSpots.layoutManager?.onRestoreInstanceState(it)
        }
    }

    private fun setObserve() {
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

        // 1. 날씨 및 골든아워 관찰 (중복 제거)
        viewModel.weatherData.observe(viewLifecycleOwner) { binding.tvWeatherStatus.text = it }
        viewModel.goldenHourData.observe(viewLifecycleOwner) { binding.tvGoldenHour.text = it }

        // 2. 로딩 상태 관찰
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        // 3. 필터링된 스팟 데이터 및 타이틀 관찰
        viewModel.filteredSpots.observe(viewLifecycleOwner) { filteredList ->
            spotAdapter.updateData(filteredList)

            val title = when (viewModel.getCurrentCategory()) {
                SpotCategory.ALL -> "촬영지 추천"
                SpotCategory.NATURE -> "자연 속 촬영지"
                SpotCategory.CULTURE -> "문화가 있는 촬영지"
                SpotCategory.LEPORTS -> "레포츠 촬영지"
                SpotCategory.FOOD -> "맛집 촬영지"
            }
            binding.tvListTitle.text = title
        }
    }

    private fun setListeners() {
        binding.btnAll.setOnClickListener { viewModel.setCategory(SpotCategory.ALL) }
        binding.btnNature.setOnClickListener { viewModel.setCategory(SpotCategory.NATURE) }
        binding.btnCulture.setOnClickListener { viewModel.setCategory(SpotCategory.CULTURE) }
        binding.btnLeports.setOnClickListener { viewModel.setCategory(SpotCategory.LEPORTS) }
        binding.btnFood.setOnClickListener { viewModel.setCategory(SpotCategory.FOOD) }
        binding.btnOpenNaverMap.setOnClickListener {
            val mapFragment = MapFragment()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, mapFragment) // R.id.fragment_container는 메인 액티비티의 컨테이너 ID
                .addToBackStack(null) // 뒤로가기 가능하게 설정
                .commit()
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
