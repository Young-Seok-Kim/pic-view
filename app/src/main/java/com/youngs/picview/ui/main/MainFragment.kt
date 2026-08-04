package com.youngs.picview.ui.main

import com.youngs.picview.util.applyTopSystemBarInset
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import com.youngs.picview.MainActivity
import com.youngs.picview.R
import com.youngs.picview.databinding.FragmentMainBinding
import com.youngs.picview.ui.adapter.SpotAdapter
import com.youngs.picview.ui.detail.DetailFragment
import com.youngs.picview.ui.map.MapFragment
import com.youngs.picview.ui.model.SpotItem

class MainFragment : Fragment(R.layout.fragment_main) {
    private var recyclerViewState: Parcelable? = null

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    /** 카테고리 ↔ 칩 id 매핑. */
    private val chipToCategory by lazy {
        mapOf(
            R.id.chip_all to SpotCategory.ALL,
            R.id.chip_nature to SpotCategory.NATURE,
            R.id.chip_culture to SpotCategory.CULTURE,
            R.id.chip_leports to SpotCategory.LEPORTS,
            R.id.chip_food to SpotCategory.FOOD
        )
    }

    override fun onPause() {
        super.onPause()
        recyclerViewState = binding.rvPhotoSpots.layoutManager?.onSaveInstanceState()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMainBinding.bind(view)

        // 헤더가 접히면 칩 줄이 화면 맨 위로 올라옵니다.
        // AppBar 가 상태바를 피하지 않으면 그때 칩이 시계·배터리에 겹칩니다.
        binding.appbar.applyTopSystemBarInset()

        setListeners()
        setObserve()
        restoreCategorySelection()

        recyclerViewState?.let {
            binding.rvPhotoSpots.layoutManager?.onRestoreInstanceState(it)
        }
    }

    private fun setObserve() {
        val spotAdapter = SpotAdapter { spot -> openDetail(spot) }
        binding.rvPhotoSpots.adapter = spotAdapter

        viewModel.weatherData.observe(viewLifecycleOwner) { binding.tvWeatherStatus.text = it }
        viewModel.goldenHourData.observe(viewLifecycleOwner) { binding.tvGoldenHour.text = it }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.isVisible = loading
        }

        viewModel.isRefreshing.observe(viewLifecycleOwner) { refreshing ->
            binding.swipeRefresh.isRefreshing = refreshing
        }

        viewModel.filteredSpots.observe(viewLifecycleOwner) { filteredList ->
            spotAdapter.updateData(filteredList)
            renderListState(filteredList.isEmpty())
        }

        // 목록을 아예 못 받아온 경우엔 '다시 시도' 를 띄웁니다.
        viewModel.loadFailed.observe(viewLifecycleOwner) {
            renderListState(viewModel.filteredSpots.value.isNullOrEmpty())
        }
    }

    /** 목록 / 빈 상태 / 로드 실패 세 가지를 한 곳에서 정리합니다. */
    private fun renderListState(isEmpty: Boolean) {
        val failed = viewModel.loadFailed.value == true

        binding.rvPhotoSpots.isVisible = !isEmpty
        binding.layoutEmpty.isVisible = isEmpty
        binding.btnOpenNaverMap.isVisible = !isEmpty

        if (!isEmpty) return

        binding.tvEmptyTitle.setText(if (failed) R.string.error_title else R.string.empty_title)
        binding.tvEmptyDesc.setText(if (failed) R.string.error_desc else R.string.empty_desc)
        binding.btnRetry.isVisible = failed
    }

    private fun setListeners() {
        binding.swipeRefresh.setColorSchemeResources(R.color.maple_500)
        binding.swipeRefresh.setOnRefreshListener {
            (activity as? MainActivity)?.refresh(userInitiated = true)
        }

        binding.chipGroupCategory.setOnCheckedStateChangeListener { _, checkedIds ->
            val category = chipToCategory[checkedIds.firstOrNull()] ?: return@setOnCheckedStateChangeListener
            viewModel.setCategory(category)
            binding.rvPhotoSpots.scrollToPosition(0)
            binding.appbar.setExpanded(true, true)
        }

        // 목록을 내리면 FAB 를 라벨 없는 작은 형태로 접어 화면을 덜 가리게 한다.
        binding.rvPhotoSpots.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy > 6) binding.btnOpenNaverMap.shrink()
                else if (dy < -6) binding.btnOpenNaverMap.extend()
            }
        })

        binding.btnRetry.setOnClickListener {
            (activity as? MainActivity)?.reloadData()
        }

        binding.btnOpenNaverMap.setOnClickListener {
            (activity as? MainActivity)?.pushScreen(MapFragment())
        }
    }

    /** 상세 화면에서 돌아왔을 때 이전에 고른 카테고리를 그대로 보여줍니다. */
    private fun restoreCategorySelection() {
        val current = viewModel.getCurrentCategory()
        val chipId = chipToCategory.entries.first { it.value == current }.key
        if (binding.chipGroupCategory.checkedChipId != chipId) {
            binding.chipGroupCategory.check(chipId)
        }
    }

    private fun openDetail(spot: SpotItem) {
        (activity as? MainActivity)?.pushScreen(DetailFragment.newInstance(spot))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
