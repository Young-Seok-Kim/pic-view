package com.youngs.picview

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kakao.sdk.common.KakaoSdk
import com.naver.maps.map.MapFragment
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.youngs.picview.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private var naverMap: NaverMap? = null
    private val TAG = "MainActivity_Log"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Edge-to-Edge 전체 화면 활성화
        enableEdgeToEdge()

        // 2. 뷰바인딩 초기화 및 setContentView 설정
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        // 4. Edge-to-Edge 여백 분리 제어 적용
        setupWindowInsets()

        // 5. 네이버 지도 프래그먼트 로드 및 이벤트 바인딩
        initNaverMap()
    }

    /**
     * 배경(네이버 맵)은 상태바 뒤로 꽉 차게 밀어 넣고,
     * 실제 UI 컨텐츠(content_container)만 시스템 바 두께만큼 패딩을 주어 겹침 방지
     */
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.contentContainer) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // 최상위 레이아웃(binding.main) 대신 UI 컨테이너에만 패딩을 준다!
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    /**
     * XML에 선언한 MapFragment를 찾아와서 비동기로 네이버 지도 객체를 요청
     */
    private fun initNaverMap() {
        val fm = supportFragmentManager
        val mapFragment = fm.findFragmentById(R.id.map_container) as MapFragment?
            ?: MapFragment.newInstance().also {
                fm.beginTransaction().add(R.id.map_container, it).commit()
            }

        // 지도가 준비되면 onMapReady() 콜백 함수가 호출됨
        mapFragment.getMapAsync(this)
    }

    /**
     * 네이버 지도가 메모리에 로드 완료되어 조작 가능할 때 실행되는 콜백
     */
    override fun onMapReady(map: NaverMap) {
        this.naverMap = map
        Log.d(TAG, "네이버 지도 로드 완료! 마커 및 카메라 제어 가능")

        // 💡 맵 초기 설정 예시 (내 위치 버튼 활성화 등)
        val uiSettings = map.uiSettings
        uiSettings.isLocationButtonEnabled = true // 내 위치 버튼 켜기
        uiSettings.isZoomControlEnabled = false   // 기본 줌 버튼은 가리고 커스텀할 준비
    }
}