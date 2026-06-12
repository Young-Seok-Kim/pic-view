package com.youngs.picview.ui.main

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.youngs.picview.R
import com.youngs.picview.databinding.FragmentMainBinding
import com.youngs.picview.ui.adapter.SpotAdapter
import com.youngs.picview.ui.detail.DetailFragment
import com.youngs.picview.ui.model.SpotItem
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

        setListeners()
        setObserve()
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

        viewModel.weatherData.observe(viewLifecycleOwner) { binding.tvWeatherStatus.text = it }
        viewModel.goldenHourData.observe(viewLifecycleOwner) { binding.tvGoldenHour.text = it }
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.filteredSpots.observe(viewLifecycleOwner) { filteredList ->
            spotAdapter.updateData(filteredList)
            binding.rvPhotoSpots.scrollToPosition(0)
        }

        viewModel.weatherData.observe(viewLifecycleOwner) { binding.tvWeatherStatus.text = it }
        viewModel.goldenHourData.observe(viewLifecycleOwner) { binding.tvGoldenHour.text = it }

        // 데이터가 로딩되어 뷰모델에 들어오면 자동으로 어댑터 갱신
        viewModel.spotData.observe(viewLifecycleOwner) { spots ->
            spotAdapter.updateData(spots)
        }

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
    }

    private fun setListeners() {
        binding.btnAll.setOnClickListener { viewModel.setCategory(SpotCategory.ALL) }
        binding.btnNature.setOnClickListener { viewModel.setCategory(SpotCategory.NATURE) }
        binding.btnCulture.setOnClickListener { viewModel.setCategory(SpotCategory.CULTURE) }
        binding.btnLeports.setOnClickListener { viewModel.setCategory(SpotCategory.LEPORTS) }
        binding.btnFood.setOnClickListener { viewModel.setCategory(SpotCategory.FOOD) }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
