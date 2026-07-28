package com.youngs.picview.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import com.naver.maps.map.overlay.Marker
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraUpdate
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.naver.maps.map.LocationTrackingMode
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.MapFragment
import com.naver.maps.map.overlay.InfoWindow
import com.naver.maps.map.overlay.Overlay
import com.naver.maps.map.util.FusedLocationSource
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
    private lateinit var locationSource: FusedLocationSource
    private var hasFittedCamera = false

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    /** 한반도 범위를 벗어나거나 값이 없는 좌표는 버립니다. */
    private fun SpotItem.toLatLngOrNull(): LatLng? {
        val lat = mapy.toDoubleOrNull() ?: return null
        val lng = mapx.toDoubleOrNull() ?: return null
        if (lat !in 33.0..39.0 || lng !in 124.0..132.0) return null
        return LatLng(lat, lng)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMapBinding.bind(view)

        locationSource = FusedLocationSource(this, 1000)

        binding.btnMapBack.setOnClickListener { parentFragmentManager.popBackStack() }

        // MapFragment 로드
        val fm = childFragmentManager
        val mapFragment = fm.findFragmentById(R.id.map_view_container) as MapFragment?
            ?: MapFragment.newInstance().also {
                fm.beginTransaction().add(R.id.map_view_container, it).commit()
            }
        mapFragment.getMapAsync(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String?>,
        grantResults: IntArray
    ) {
        if (locationSource.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            if (!locationSource.isActivated) { // 권한 거부 시
                naverMap?.locationTrackingMode = LocationTrackingMode.None
            }
            return
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onMapReady(map: NaverMap) {
        this.naverMap = map

        map.locationSource = locationSource
        map.uiSettings.isLocationButtonEnabled = true

        // 내 위치는 표시하되 카메라를 따라가지는 않습니다.
        // Follow 로 두면 정읍 밖에서 앱을 열었을 때 촬영지가 하나도 안 보입니다.
        map.locationTrackingMode = LocationTrackingMode.NoFollow

        val infoWindow = InfoWindow()
        infoWindow.adapter = object : InfoWindow.DefaultTextAdapter(requireContext()) {
            override fun getText(infoWindow: InfoWindow): CharSequence {
                return (infoWindow.marker as Marker).captionText
            }
        }

        viewModel.filteredSpots.observe(viewLifecycleOwner) { spots ->
            // 기존 마커 정리 (마커를 두 번 만들면 지도에서 지워지지 않고 쌓입니다)
            infoWindow.close()
            markers.forEach { it.map = null }
            markers.clear()

            // 좌표가 비어 있거나 한반도 밖인 데이터가 섞여 있습니다.
            // 걸러내지 않으면 (0,0) 마커 때문에 지도가 전 세계로 축소됩니다.
            spots.mapNotNull { spot -> spot.toLatLngOrNull()?.let { spot to it } }
                .forEach { (spot, position) ->
                val marker = Marker()
                marker.position = position
                marker.captionText = spot.title
                marker.iconTintColor = ContextCompat.getColor(requireContext(), R.color.brand_500)

                // 88개가 기본 크기로 깔리면 서로 겹쳐 지도가 안 보입니다.
                // 크기를 줄이고, 겹치는 마커와 캡션은 지도가 알아서 숨기게 합니다.
                marker.width = dp(22)
                marker.height = dp(30)
                marker.isHideCollidedMarkers = true
                marker.isHideCollidedCaptions = true
                marker.captionMinZoom = 11.0
                marker.captionTextSize = 11f

                marker.tag = spot
                marker.map = naverMap

                marker.onClickListener = Overlay.OnClickListener {
                    infoWindow.open(marker)
                    true // 이벤트를 소비했음을 알림
                }
                markers.add(marker)
            }

            // 좌표가 없어 지도에 못 올린 곳은 개수에서도 빼야 숫자가 맞습니다.
            binding.tvMapCount.text = getString(R.string.map_spot_count, markers.size)

            // 지도를 처음 열면 촬영지 전체가 한눈에 들어오도록 맞춥니다.
            if (!hasFittedCamera && markers.isNotEmpty()) {
                val bounds = LatLngBounds.Builder()
                    .apply { markers.forEach { include(it.position) } }
                    .build()
                naverMap?.moveCamera(CameraUpdate.fitBounds(bounds, dp(56)))
                hasFittedCamera = true
            }

            // 정보창을 누르면 상세로 이동
            infoWindow.onClickListener = Overlay.OnClickListener {
                (infoWindow.marker?.tag as? SpotItem)?.let { item ->
                    parentFragmentManager.beginTransaction()
                        .setCustomAnimations(
                            R.anim.slide_in_right, R.anim.fade_out,
                            R.anim.fade_in, R.anim.slide_out_right
                        )
                        .replace(
                            R.id.fragment_container,
                            com.youngs.picview.ui.detail.DetailFragment.newInstance(item)
                        )
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