package com.youngs.picview.ui.map

import android.os.Bundle
import com.naver.maps.map.overlay.Marker
import com.naver.maps.geometry.LatLng
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.MapFragment
import com.naver.maps.map.overlay.InfoWindow
import com.naver.maps.map.overlay.Overlay
import com.youngs.picview.R
import com.youngs.picview.databinding.FragmentMapBinding
import com.youngs.picview.ui.main.MainViewModel
import com.youngs.picview.ui.model.SpotItem

class MapFragment : Fragment(R.layout.fragment_map), OnMapReadyCallback {
    private val markers = mutableListOf<Marker>()
    private val viewModel: MainViewModel by activityViewModels()
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

        val infoWindow = InfoWindow()
        infoWindow.adapter = object : InfoWindow.DefaultTextAdapter(requireContext()) {
            override fun getText(infoWindow: InfoWindow): CharSequence {
                return (infoWindow.marker as Marker).captionText
            }
        }

        viewModel.filteredSpots.observe(viewLifecycleOwner) { spots ->
            // 2. 기존 마커들 삭제
            markers.forEach { it.map = null }
            markers.clear()

            spots.forEach { spot ->
                val marker = Marker() // apply 밖으로 빼서 명시적으로 제어
                marker.position = LatLng(spot.mapy.toDouble(), spot.mapx.toDouble())
                marker.captionText = spot.title
                marker.map = naverMap // 여기서 직접 할당

                marker.onClickListener = Overlay.OnClickListener { overlay ->
                    infoWindow.open(marker)
                    true // 이벤트를 소비했음을 알림
                }
                markers.add(marker)
            }

// 마커 생성 루프 내부
            spots.forEach { spot ->
                val marker = Marker()
                marker.position = LatLng(spot.mapy.toDouble(), spot.mapx.toDouble())
                marker.captionText = spot.title
                marker.map = naverMap

                // 1. 마커에 spot 데이터를 태그로 저장
                marker.tag = spot

                marker.onClickListener = Overlay.OnClickListener {
                    infoWindow.open(marker)
                    true
                }
            }

// 2. 정보창 클릭 리스너 (루프 밖으로 빼세요)
            infoWindow.onClickListener = Overlay.OnClickListener { overlay ->
                // 정보창에 연결된 마커에서 tag를 꺼내와 spot으로 변환
                val spot = infoWindow.marker?.tag as? SpotItem

                spot?.let { item ->
                    val detailFragment = com.youngs.picview.ui.detail.DetailFragment().apply {
                        arguments = Bundle().apply {
                            putSerializable("spot", item)
                        }
                    }
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, detailFragment)
                        .addToBackStack(null)
                        .commit()
                }
                true
            }
        }

        map.setOnMapClickListener { _, _ ->
            infoWindow.close()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}