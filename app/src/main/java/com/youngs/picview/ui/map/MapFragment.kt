package com.youngs.picview.ui.map

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.MapFragment
import com.youngs.picview.R
import com.youngs.picview.databinding.FragmentMapBinding

class MapFragment : Fragment(R.layout.fragment_map), OnMapReadyCallback {
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private var naverMap: NaverMap? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMapBinding.bind(view)

        // MapFragment 로드
        val fm = childFragmentManager
        val mapFragment = fm.findFragmentById(R.id.map_view_container) as MapFragment?
            ?: MapFragment.newInstance().also {
                fm.beginTransaction().add(R.id.map_view_container, it).commit()
            }
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: NaverMap) {
        this.naverMap = map
        map.uiSettings.isLocationButtonEnabled = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}