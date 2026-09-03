package com.youngs.picview.ui.main

import com.youngs.picview.util.applyTopSystemBarInset
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.youngs.picview.MainActivity
import com.youngs.picview.R
import com.youngs.picview.databinding.FragmentMainBinding
import com.youngs.picview.domain.guide.SiseonGuide
import com.youngs.picview.domain.light.LightPhase
import com.youngs.picview.domain.spot.SpotFactsTable
import com.youngs.picview.ui.adapter.SpotAdapter
import com.youngs.picview.ui.detail.DetailFragment
import com.youngs.picview.ui.guide.SiseonGuideActivity
import com.youngs.picview.ui.map.MapFragment
import com.youngs.picview.ui.model.SpotItem
import com.youngs.picview.util.AppPrefs
import com.youngs.picview.util.LatLng
import com.youngs.picview.util.distanceKmTo
import java.time.Duration
import java.time.LocalTime

/**
 * 탐색 — 빛이 맞는 출사 (시안).
 *
 * 위에서부터 약도 → 정렬 → 장소 카드. 첫 줄의 물음이 "무엇을"이 아니라
 * "어떤 순서로 볼까"입니다 — 추천순(포토스코어), 거리순(시내 기준),
 * 빛 좋은 시간순(그 장소의 빛이 맞는 다음 시각이 가까운 순).
 * 카테고리는 오른쪽 필터 버튼 뒤에 있습니다.
 */
class MainFragment : Fragment(R.layout.fragment_main) {
    private var recyclerViewState: Parcelable? = null

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    private enum class SortMode { RECO, DIST, LIGHT }
    private var sortMode = SortMode.RECO

    private lateinit var spotAdapter: SpotAdapter

    override fun onPause() {
        super.onPause()
        recyclerViewState = binding.rvPhotoSpots.layoutManager?.onSaveInstanceState()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMainBinding.bind(view)

        // 헤더가 접히면 정렬 줄이 화면 맨 위로 올라옵니다.
        // AppBar 가 상태바를 피하지 않으면 그때 칩이 시계·배터리에 겹칩니다.
        binding.appbar.applyTopSystemBarInset()

        setListeners()
        setObserve()
        renderLightLine()

        recyclerViewState?.let {
            binding.rvPhotoSpots.layoutManager?.onRestoreInstanceState(it)
        }
    }

    private fun setObserve() {
        spotAdapter = SpotAdapter(
            onItemClick = { spot -> openDetail(spot) },
            onGuideClick = { spot -> openGuide(spot) },
            onFavoriteClick = { spot -> toastFavorite(spot) }
        )
        binding.rvPhotoSpots.adapter = spotAdapter
        setupMapLink()

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.isVisible = loading
        }

        viewModel.isRefreshing.observe(viewLifecycleOwner) { refreshing ->
            binding.swipeRefresh.isRefreshing = refreshing
        }

        viewModel.filteredSpots.observe(viewLifecycleOwner) { filteredList ->
            val ordered = sorted(filteredList)
            spotAdapter.updateData(ordered)
            renderMapPins(ordered)
            renderListState(filteredList.isEmpty())
        }

        // 목록을 아예 못 받아온 경우엔 '다시 시도' 를 띄웁니다.
        viewModel.loadFailed.observe(viewLifecycleOwner) {
            renderListState(viewModel.filteredSpots.value.isNullOrEmpty())
        }

        // 천문 정보가 도착하면 머리말의 빛 상태줄을 다시 씁니다.
        viewModel.goldenHourData.observe(viewLifecycleOwner) { renderLightLine() }
    }

    /**
     * "지금은 석양 · 일몰까지 58분" — 시안의 탐색 머리말.
     *
     * 질문("어디서 어떤 장면을 찍을까요?") 바로 아래에서 지금 빛이
     * 답의 첫 단서가 됩니다. 천문 정보가 없으면 기본 소개 문구로 물러납니다.
     */
    private fun renderLightLine() {
        val view = _binding ?: return
        val sun = viewModel.sunTimes
        val now = LocalTime.now()
        val rise = sun.sunrise
        val set = sun.sunset

        if (rise == null || set == null) {
            view.tvTagline.setText(R.string.explore_sub)
            return
        }

        val phase = sun.phaseAt(now)
        val phaseWord = when (phase) {
            LightPhase.SUNSET -> "석양"
            LightPhase.SUNRISE -> "아침빛"
            else -> phase.label
        }
        val tail = when {
            now < rise -> getString(
                R.string.map_until_sunrise, minutesText(Duration.between(now, rise).toMinutes())
            )
            now < set -> getString(
                R.string.map_until_sunset, minutesText(Duration.between(now, set).toMinutes())
            )
            else -> getString(R.string.course_hero_sun_down)
        }
        view.tvTagline.text = getString(R.string.explore_light_line, phaseWord, tail)
    }

    /** 한 시간이 안 되면 "분"만 씁니다. "0시간 58분"은 읽는 데 방해가 됩니다. */
    private fun minutesText(minutes: Long): String = if (minutes >= 60) {
        getString(R.string.home_duration_hm, minutes / 60, minutes % 60)
    } else {
        getString(R.string.home_duration_m, minutes)
    }

    // ───────────────────── 약도 ↔ 목록 연동 ─────────────────────

    /**
     * 약도의 핀 셋에 지금 목록의 맨 위 세 곳을 앉힙니다.
     *
     * 예전에는 "내장산 자락"·"정읍사공원"이 레이아웃에 박혀 있어, 정렬을
     * 바꾸든 카테고리를 걸든 약도는 늘 같은 그림이었습니다. 그러니 위에
     * 지도가 있어도 아래 목록과 아무 관계가 없었습니다.
     */
    private fun renderMapPins(spots: List<SpotItem>) {
        val view = _binding ?: return
        val pins = listOf(
            Triple(view.layoutMapPin1, view.ivMapPin1, view.tvMapPin1),
            Triple(view.layoutMapPin2, view.ivMapPin2, view.tvMapPin2),
            Triple(view.layoutMapPin3, view.ivMapPin3, view.tvMapPin3)
        )
        pins.forEachIndexed { index, (group, _, label) ->
            val spot = spots.getOrNull(index)
            group.isVisible = spot != null
            label.text = spot?.title.orEmpty()
        }
        highlightPin(0)
    }

    /**
     * 목록을 스크롤하면 맨 위에 온 카드의 핀이 커집니다.
     *
     * 실제 지도를 살아 있는 채로 얹으면 목록 스크롤과 지도 스크롤이
     * 싸우므로, 약도 쪽을 목록에 맞춰 움직이는 방향으로 이었습니다.
     */
    private fun setupMapLink() {
        binding.rvPhotoSpots.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    val first = (rv.layoutManager as? LinearLayoutManager)
                        ?.findFirstVisibleItemPosition() ?: return
                    highlightPin(first)
                }
            }
        )
    }

    /** 지금 보고 있는 카드의 핀만 크게. 나머지는 원래 크기로 돌립니다. */
    private fun highlightPin(position: Int) {
        val view = _binding ?: return
        listOf(view.ivMapPin1, view.ivMapPin2, view.ivMapPin3)
            .forEachIndexed { index, pin ->
                val active = index == position
                pin.animate()
                    .scaleX(if (active) 1.45f else 1f)
                    .scaleY(if (active) 1.45f else 1f)
                    .setDuration(160)
                    .start()
            }
    }

    private fun toastFavorite(spot: SpotItem) {
        Toast.makeText(
            requireContext(),
            if (AppPrefs.isFavorite(requireContext(), spot.contentId)) {
                R.string.detail_favorited
            } else {
                R.string.detail_unfavorited
            },
            Toast.LENGTH_SHORT
        ).show()
    }

    // ───────────────────── 정렬 ─────────────────────

    private fun sorted(list: List<SpotItem>): List<SpotItem> = when (sortMode) {
        SortMode.RECO -> list.sortedByDescending { it.score }
        SortMode.DIST -> list.sortedBy { spot ->
            LatLng.parseOrNull(spot.mapy, spot.mapx)
                ?.let { SpotAdapter.CITY_CENTER.distanceKmTo(it) }
                ?: Double.MAX_VALUE
        }
        SortMode.LIGHT -> list.sortedBy { minutesUntilBestLight(it) }
    }

    /**
     * 이 장소의 빛이 맞는 다음 시각까지 남은 분.
     * 오늘 지나갔으면 내일 같은 시각으로 넘겨 셉니다.
     */
    private fun minutesUntilBestLight(spot: SpotItem): Long {
        val sun = viewModel.sunTimes
        val phase = SpotFactsTable.of(spot.title, spot.contentTypeId).bestPhase
        val at: LocalTime = when (phase) {
            LightPhase.BLUE_DAWN -> sun.civilDawn ?: sun.sunrise?.minusMinutes(25)
            LightPhase.SUNRISE -> sun.sunrise
            LightPhase.MORNING -> sun.sunrise?.plusMinutes(60)
            LightPhase.MIDDAY -> LocalTime.of(11, 0)
            LightPhase.AFTERNOON -> LocalTime.of(14, 0)
            LightPhase.SUNSET -> sun.sunset?.minusMinutes(40)
            LightPhase.BLUE_DUSK -> sun.sunset
            LightPhase.NIGHT -> sun.civilDusk ?: sun.sunset?.plusMinutes(30)
        } ?: return Long.MAX_VALUE

        val diff = Duration.between(LocalTime.now(), at).toMinutes()
        return if (diff >= 0) diff else diff + MINUTES_PER_DAY
    }

    /** 목록 / 빈 상태 / 로드 실패 세 가지를 한 곳에서 정리합니다. */
    private fun renderListState(isEmpty: Boolean) {
        val failed = viewModel.loadFailed.value == true

        binding.rvPhotoSpots.isVisible = !isEmpty
        binding.layoutEmpty.isVisible = isEmpty

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

        binding.chipGroupSort.setOnCheckedStateChangeListener { _, checkedIds ->
            sortMode = when (checkedIds.firstOrNull()) {
                R.id.chip_sort_dist -> SortMode.DIST
                R.id.chip_sort_light -> SortMode.LIGHT
                else -> SortMode.RECO
            }
            val ordered = sorted(viewModel.filteredSpots.value.orEmpty())
            spotAdapter.updateData(ordered)
            renderMapPins(ordered)
            binding.rvPhotoSpots.scrollToPosition(0)
            binding.appbar.setExpanded(true, true)
        }

        binding.btnCategoryFilter.setOnClickListener { showCategoryMenu(it) }

        binding.btnRetry.setOnClickListener {
            (activity as? MainActivity)?.reloadData()
        }

        binding.cardMapPreview.setOnClickListener {
            (activity as? MainActivity)?.pushScreen(MapFragment())
        }
        binding.btnMapExpand.setOnClickListener {
            (activity as? MainActivity)?.pushScreen(MapFragment())
        }
    }

    /** 카테고리 선택. 시안의 첫 줄을 정렬에 내주고 이쪽으로 옮겼습니다. */
    private fun showCategoryMenu(anchor: View) {
        val categories = listOf(
            R.string.category_all to SpotCategory.ALL,
            R.string.category_nature to SpotCategory.NATURE,
            R.string.category_culture to SpotCategory.CULTURE,
            R.string.category_leports to SpotCategory.LEPORTS,
            R.string.category_food to SpotCategory.FOOD
        )
        PopupMenu(requireContext(), anchor).apply {
            categories.forEachIndexed { index, (labelRes, _) ->
                menu.add(0, index, index, getString(labelRes))
            }
            setOnMenuItemClickListener { item ->
                viewModel.setCategory(categories[item.itemId].second)
                binding.rvPhotoSpots.scrollToPosition(0)
                true
            }
            show()
        }
    }

    private fun openDetail(spot: SpotItem) {
        (activity as? MainActivity)?.pushScreen(DetailFragment.newInstance(spot))
    }

    private fun openGuide(spot: SpotItem) {
        val facts = SpotFactsTable.of(spot.title, spot.contentTypeId)
        startActivity(
            SiseonGuideActivity.intent(
                requireContext(),
                spotTitle = spot.title,
                contextId = SiseonGuide.contextIdFor(facts.bestPhase),
                guideId = SiseonGuide.guideIdFor(facts),
                // 미션에서 찍은 사진이 이 장소의 방문 기록("다녀왔어요")이 되도록.
                contentId = spot.contentId,
                phaseName = viewModel.sunTimes.phaseNow().name
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val MINUTES_PER_DAY = 24 * 60L
    }
}
