package com.youngs.picview.ui.mission

import android.app.Application
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.lifecycle.asLiveData
import com.youngs.picview.R
import com.youngs.picview.data.repository.DiaryRepository
import com.youngs.picview.databinding.FragmentMissionBinding
import com.youngs.picview.domain.mission.MissionProgress
import com.youngs.picview.domain.mission.Missions

/**
 * 미션 & 배지 (스펙 §16-5 ⑦).
 *
 * 별도 저장소가 없습니다. 방문 기록만으로 매번 판정하므로
 * "미션 완료 처리를 깜빡해서 배지가 안 나오는" 종류의 버그가 원천적으로 없습니다.
 */
class MissionFragment : Fragment(R.layout.fragment_mission) {

    private var _binding: FragmentMissionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MissionViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMissionBinding.bind(view)

        applyTopInset()
        binding.btnMissionBack.setOnClickListener { parentFragmentManager.popBackStack() }

        val adapter = MissionAdapter()
        binding.rvMissions.adapter = adapter

        viewModel.progress.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            renderSummary(list)
        }
    }

    private fun renderSummary(list: List<MissionProgress>) {
        val earned = list.count { it.isComplete }
        binding.tvMissionSummary.text =
            getString(R.string.mission_summary, earned, list.size)
        binding.tvMissionSummaryDesc.setText(
            if (earned == 0) R.string.mission_summary_empty else R.string.mission_summary_desc
        )
    }

    private fun applyTopInset() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootMission) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top)
            insets
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class MissionViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = DiaryRepository(app)

    /** 방문 기록이 바뀌면 미션 진행도가 자동으로 다시 계산됩니다. */
    val progress: LiveData<List<MissionProgress>> =
        repository.observeDays().asLiveData().map { days ->
            Missions.progress(days.flatMap { it.visits })
        }
}
