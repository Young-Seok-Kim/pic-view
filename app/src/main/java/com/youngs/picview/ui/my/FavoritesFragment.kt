package com.youngs.picview.ui.my

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.youngs.picview.MainActivity
import com.youngs.picview.R
import com.youngs.picview.data.repository.CourseRepository
import com.youngs.picview.data.repository.FavoriteSpots
import com.youngs.picview.databinding.FragmentFavoritesBinding
import com.youngs.picview.databinding.ItemFavoriteBinding
import com.youngs.picview.domain.spot.ShotPurpose
import com.youngs.picview.domain.spot.SpotFactsTable
import com.youngs.picview.domain.spot.SpotTheme
import com.youngs.picview.ui.detail.DetailFragment
import com.youngs.picview.ui.main.MainViewModel
import com.youngs.picview.ui.model.SpotItem
import com.youngs.picview.util.AppPrefs
import com.youngs.picview.util.applyTopSystemBarInset
import kotlinx.coroutines.launch

/**
 * 찜한 촬영지.
 *
 * 하트는 예전부터 있었지만([AppPrefs.toggleFavorite]) 저장한 것을 다시
 * 볼 화면이 없었습니다. 다시 못 찾는 저장은 없는 기능과 같습니다.
 *
 * 단순 목록으로 두지 않은 것은 '저장 후 방치' 때문입니다. 세 가지를 더 둡니다.
 *
 *   · 촬영 목적 필터 — 지도와 같은 축이라 한 번 배운 것을 다시 씁니다
 *   · 방문 상태 배지 — 무엇이 아직 남았는지가 목록의 첫 정보가 됩니다
 *   · "지금 빛과 맞아요" — 오늘 지금 나갈 이유가 있는 곳을 위로 올립니다
 */
class FavoritesFragment : Fragment(R.layout.fragment_favorites) {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var adapter: FavoriteAdapter

    /** null 이면 전체. 지도 필터와 같은 [ShotPurpose] 축입니다. */
    private var filter: ShotPurpose? = null

    /** 다녀온 곳의 contentId. 방문 기록에서 한 번만 읽어 들고 있습니다. */
    private var visitedIds: Set<String> = emptySet()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentFavoritesBinding.bind(view)

        binding.rootFavorites.applyTopSystemBarInset()
        binding.btnFavoritesBack.setOnClickListener { parentFragmentManager.popBackStack() }

        adapter = FavoriteAdapter(
            onClick = { row ->
                (activity as? MainActivity)?.pushScreen(DetailFragment.newInstance(row.spot))
            },
            onRemove = { row ->
                AppPrefs.toggleFavorite(requireContext(), row.spot)
                render()
            }
        )
        binding.rvFavorites.adapter = adapter

        buildFilterChips()
        loadVisits()
    }

    override fun onResume() {
        super.onResume()
        // 상세나 목록에서 찜을 바꾸고 돌아올 수 있습니다.
        render()
    }

    private fun loadVisits() {
        viewLifecycleOwner.lifecycleScope.launch {
            visitedIds = runCatching {
                CourseRepository(requireContext()).visitedContentIds()
            }.getOrDefault(emptySet())
            render()
        }
    }

    private fun buildFilterChips() {
        val group = binding.chipsFavoritesFilter
        group.removeAllViews()

        // 첫 칩은 '전체'. tag 가 null 인 것이 곧 필터 없음입니다.
        val entries: List<Pair<String, ShotPurpose?>> =
            listOf(getString(R.string.favorites_filter_all) to null) +
                ShotPurpose.values().map { it.label to it }

        entries.forEachIndexed { index, (label, purpose) ->
            group.addView(
                Chip(requireContext()).apply {
                    id = View.generateViewId()
                    text = label
                    isCheckable = true
                    isChecked = index == 0
                    tag = purpose
                }
            )
        }

        group.setOnCheckedStateChangeListener { chips, checked ->
            val chip = checked.firstOrNull()?.let { chips.findViewById<Chip>(it) }
            filter = chip?.tag as? ShotPurpose
            render()
        }
    }

    /**
     * 찜한 장소를 모아 그립니다.
     *
     * 홈 목록(100건)에 없는 찜도 빠뜨리지 않도록 [FavoriteSpots.resolve] 로
     * 스냅샷·API 까지 찾아봅니다. 그래서 비동기이고, 화면이 사라진 뒤에
     * 결과가 오면 그냥 버립니다.
     */
    private fun render() {
        if (_binding == null) return
        val known = mainViewModel.spotData.value.orEmpty()
        viewLifecycleOwner.lifecycleScope.launch {
            val resolved = FavoriteSpots.resolve(requireContext(), known)
            val context = context ?: return@launch
            // 관광공사에서 내려간 장소는 찜에서 지워야 MY 의 "n곳"과 이 목록이 맞습니다.
            AppPrefs.removeFavorites(context, resolved.gone)
            renderRows(resolved.spots)
        }
    }

    private fun renderRows(spots: List<SpotItem>) {
        val view = _binding ?: return
        val favoriteIds = AppPrefs.favoriteSpots(requireContext())
        val phase = mainViewModel.sunTimes.phaseNow()

        val rows = spots
            .map { spot ->
                val facts = SpotFactsTable.of(spot.title, spot.contentTypeId)
                FavoriteRow(
                    spot = spot,
                    purpose = ShotPurpose.of(
                        spot.title, facts, SpotTheme.of(spot.contentTypeId)
                    ),
                    signal = "${facts.bestPhase.shortLabel} · ${facts.bestPhase.lightCharacter}",
                    visited = spot.contentId in visitedIds,
                    // 지금 빛이 이 장소의 최적 구간이면 오늘 나갈 이유가 있습니다.
                    matchesNow = facts.bestPhase == phase
                )
            }
            .filter { filter == null || it.purpose == filter }
            // 지금 빛이 맞는 곳 → 아직 안 간 곳 순. 찜 목록의 쓸모는 여기 있습니다.
            .sortedWith(
                compareByDescending<FavoriteRow> { it.matchesNow }
                    .thenBy { it.visited }
                    .thenBy { it.spot.title }
            )

        adapter.submitList(rows)
        view.tvFavoritesCount.text = getString(R.string.favorites_count, rows.size)
        view.tvFavoritesEmpty.isVisible = rows.isEmpty()
        view.rvFavorites.isVisible = rows.isNotEmpty()
        view.tvFavoritesEmpty.setText(
            if (favoriteIds.isEmpty()) R.string.favorites_empty
            else R.string.favorites_empty_filtered
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/** 화면이 쓰는 한 줄. 원본 [SpotItem] 에 상태 셋을 붙인 것입니다. */
data class FavoriteRow(
    val spot: SpotItem,
    val purpose: ShotPurpose,
    val signal: String,
    val visited: Boolean,
    val matchesNow: Boolean
)

private class FavoriteAdapter(
    private val onClick: (FavoriteRow) -> Unit,
    private val onRemove: (FavoriteRow) -> Unit
) : ListAdapter<FavoriteRow, FavoriteAdapter.VH>(DIFF) {

    class VH(val binding: ItemFavoriteBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemFavoriteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = getItem(position)
        val context = holder.itemView.context

        with(holder.binding) {
            tvFavoriteTitle.text = row.spot.title
            tvFavoriteSignal.text = "${row.signal} · ${row.purpose.label}"

            tvFavoriteState.setText(
                if (row.visited) R.string.favorites_state_visited
                else R.string.favorites_state_todo
            )
            val stateColor = ContextCompat.getColor(
                context,
                if (row.visited) R.color.theme_nature_container else R.color.maple_50
            )
            tvFavoriteState.background?.mutate()?.setTint(stateColor)
            tvFavoriteState.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (row.visited) R.color.theme_nature else R.color.maple_700
                )
            )

            tvFavoriteNudge.isVisible = row.matchesNow
            tvFavoriteNudge.setText(R.string.favorites_match_now)

            Glide.with(ivFavorite)
                .load(row.spot.imageUrl.takeIf { it.isNotBlank() })
                .placeholder(R.drawable.bg_image_placeholder)
                .error(R.drawable.bg_image_placeholder)
                .centerCrop()
                .into(ivFavorite)

            cardFavorite.setOnClickListener { onClick(row) }
            btnFavoriteRemove.setOnClickListener { onRemove(row) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FavoriteRow>() {
            override fun areItemsTheSame(oldItem: FavoriteRow, newItem: FavoriteRow) =
                oldItem.spot.contentId == newItem.spot.contentId

            override fun areContentsTheSame(oldItem: FavoriteRow, newItem: FavoriteRow) =
                oldItem == newItem
        }
    }
}
