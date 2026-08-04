package com.youngs.picview.ui.senior

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.youngs.picview.MainActivity
import com.youngs.picview.R
import com.youngs.picview.databinding.FragmentSeniorHomeBinding
import com.youngs.picview.domain.spot.SpotFactsTable
import com.youngs.picview.ui.detail.DetailFragment
import com.youngs.picview.ui.main.MainViewModel
import com.youngs.picview.ui.model.SpotItem
import com.youngs.picview.util.AppPrefs

/**
 * 시니어 홈 (스펙 §17-4 탭1).
 *
 * 화면당 액션을 3개로 제한합니다. 일반 홈의 정보 밀도를 그대로 두면
 * 글씨만 키워도 오히려 더 찾기 어려워집니다.
 */
class SeniorHomeFragment : Fragment(R.layout.fragment_senior_home), MainActivity.TabRoot {

    private var _binding: FragmentSeniorHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    private var topSpot: SpotItem? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSeniorHomeBinding.bind(view)

        applyTopInset()
        setupActions()
        observeData()
    }

    private fun applyTopInset() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollSeniorHome) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top)
            insets
        }
    }

    private fun observeData() {
        viewModel.spotData.observe(viewLifecycleOwner) { render() }
        viewModel.weatherData.observe(viewLifecycleOwner) { render() }
        viewModel.goldenHourData.observe(viewLifecycleOwner) { render() }
        render()
    }

    private fun render() {
        val phase = viewModel.sunTimes.phaseNow()

        binding.tvSeniorWeather.text = listOfNotNull(
            viewModel.weatherData.value?.takeIf { it.isNotBlank() },
            phase.label
        ).joinToString(" · ")
        binding.tvSeniorLight.text = phase.hint

        // 오늘 가장 점수 높은 한 곳만 크게 보여 줍니다.
        val spot = viewModel.spotData.value?.firstOrNull()
        topSpot = spot
        binding.cardSeniorSpot.isVisible = spot != null
        if (spot != null) {
            binding.tvSeniorSpotTitle.text = spot.title
            binding.tvSeniorSpotDesc.text =
                SpotFactsTable.of(spot.title, spot.contentTypeId).note
            binding.tvSeniorSpotAddr.text = spot.addr1
            binding.tvSeniorSpotAddr.isVisible = spot.addr1.isNotBlank()
        }
    }

    private fun setupActions() {
        val activity = activity as? MainActivity

        binding.btnSeniorCourse.setOnClickListener { activity?.selectTab(R.id.tab_course) }
        binding.btnSeniorHelp.setOnClickListener { activity?.selectTab(R.id.tab_help) }

        // 오디오 가이드는 장소가 있어야 의미가 있으므로 대표 스팟 상세로 보냅니다.
        binding.btnSeniorAudio.setOnClickListener {
            topSpot?.let { activity?.pushScreen(DetailFragment.newInstance(it)) }
                ?: activity?.selectTab(R.id.tab_course)
        }

        binding.cardSeniorSpot.setOnClickListener {
            topSpot?.let { activity?.pushScreen(DetailFragment.newInstance(it)) }
        }

        binding.btnSeniorToNormal.setOnClickListener {
            AppPrefs.setSeniorMode(requireContext(), false)
            requireActivity().recreate()
        }
    }

    override fun scrollToTop() {
        _binding?.scrollSeniorHome?.smoothScrollTo(0, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
