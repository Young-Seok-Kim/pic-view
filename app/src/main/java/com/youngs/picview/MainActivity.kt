package com.youngs.picview

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.youngs.picview.data.api.RetrofitClient
import com.youngs.picview.databinding.ActivityMainBinding
import com.youngs.picview.ui.main.MainFragment
import com.youngs.picview.ui.main.MainViewModel
import com.youngs.picview.ui.main.PhotoScoreEngine
import com.youngs.picview.ui.model.SpotItem
import com.youngs.picview.ui.model.SpotScoreContext
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.Duration

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. 로딩이 끝날 때까지 스플래시 화면을 유지하도록 조건 설정
        splashScreen.setKeepOnScreenCondition {
            // viewModel의 로딩 상태가 true인 동안 스플래시 유지
            viewModel.isLoading.value == true
        }
        preLoadData()


        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, MainFragment())
                .commit()
        }


        setupWindowInsets()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentContainer) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun preLoadData() {
        if (viewModel.cachedWeather != null) {
            viewModel.isLoading.value = false
            return
        }

        lifecycleScope.launch {
            try {
                val now = LocalDateTime.now()
                val dateStr = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                val timeStr = now.minusHours(1).format(DateTimeFormatter.ofPattern("HHmm"))

                // 1. 날씨 호출
                val weatherResponse = RetrofitClient.weatherApiService.getUltraSrtNcst(
                    serviceKey = BuildConfig.TOUR_API_KEY,
                    baseDate = dateStr,
                    baseTime = timeStr
                )
                val temp = weatherResponse.response.body.items.item
                    .firstOrNull { it.category == "T1H" }?.obsrValue ?: "--"

                // 2. 일출/일몰 호출 (정읍 좌표 사용)
                val astroResponse = RetrofitClient.weatherApiService.getAreaRiseSetInfo(
                    serviceKey = BuildConfig.TOUR_API_KEY,
                    locdate = dateStr,
                )

                val astro = astroResponse.response.body.items.item

                // 3. 골든아워 계산
                val goldenText = run {
                    val sunrise = parseTime(astro.sunrise)
                    val sunset = parseTime(astro.sunset)
                    val nowTime = LocalTime.now()

                    val diffSunrise = Duration.between(nowTime, sunrise).toMinutes()
                    val diffSunset = Duration.between(nowTime, sunset).toMinutes()

                    when {
                        Math.abs(diffSunrise) <= 60 -> "일출 골든아워 진행 중 🌅"
                        Math.abs(diffSunset) <= 60 -> "일몰 골든아워 진행 중 🌇"
                        nowTime.isBefore(sunrise) -> "일출까지 ${diffSunrise / 60}시간 ${diffSunrise % 60}분"
                        nowTime.isBefore(sunset) -> "일몰까지 ${diffSunset / 60}시간 ${diffSunset % 60}분"
                        else -> "내일 일출을 기다려보세요"
                    }
                }
                val response = RetrofitClient.tourApiService.getJeongeupSpots(BuildConfig.TOUR_API_KEY)
                val rawSpots = response.response.body.items.item

                val isGoldenHour = goldenText.contains("진행") // 여기서 판단

                viewModel.cachedSpots = rawSpots.map { item ->
                    val contentTypeId = item.contentTypeId ?: "0" // null일 경우 기본값 처리

                    // 1. 카테고리별 특성 추측 (하드코딩 대신 로직으로 분류)
                    val (inferredDirection, inferredBestTime) = when (contentTypeId) {
                        "12" -> "WEST" to "SUNSET"    // 자연은 일몰/서쪽이 최고
                        "14" -> "NONE" to "AFTERNOON" // 문화재는 낮이 밝음
                        "28" -> "NONE" to "AFTERNOON" // 레포츠는 활동적인 낮 시간
                        "39" -> "NONE" to "AFTERNOON" // 맛집은 점심/저녁 시간
                        else -> "NONE" to "AFTERNOON"
                    }

                    val spot = SpotItem(
                        contentId = item.contentid,
                        contentTypeId = contentTypeId,
                        title = item.title,
                        addr1 = item.addr1,
                        tip = getGeneratedTip(contentTypeId),
                        imageUrl = item.firstimage ?: "",
                        mapx = item.mapx,
                        mapy = item.mapy
                    )

                    val pty = weatherResponse.response.body.items.item
                        .firstOrNull { it.category == "PTY" }?.obsrValue ?: "0"
                    val isRaining = pty != "0" // 0이 아니면 강수/강설 중

                    // 2. 점수 계산을 위한 컨텍스트 구성
                    val context = SpotScoreContext(
                        spot = spot,
                        currentTemp = 20.0, // 실제 온도 데이터 사용 필요
                        isGoldenHour = isGoldenHour,
                        isRaining = isRaining,
                        userDistance = 0.0, // GPS 위치 기반 거리 계산 로직 사용 필요
                        direction = inferredDirection, // 추론된 방향값 적용
                        bestTime = inferredBestTime   // 추론된 최적 시간대 적용
                    )

                    // 점수 계산 및 할당
                    spot.score = PhotoScoreEngine.calculateScore(context)
                    spot
                }.sortedByDescending { it.score }

                viewModel.spotData.postValue(viewModel.cachedSpots)

                // 4. 데이터 저장 및 LiveData 방출
                val weatherResult = "정읍의 현재 기온, ${temp}℃"
                viewModel.cachedWeather = weatherResult
                viewModel.cachedGoldenHour = goldenText

                viewModel.weatherData.postValue(weatherResult)
                viewModel.goldenHourData.postValue(goldenText)

            } catch (e: Exception) {
                Log.e("PRELOAD_ERROR", "사전 로딩 실패: ${e.message}")
                viewModel.weatherData.postValue("정보를 불러올 수 없습니다.")
                viewModel.goldenHourData.postValue("정보 없음")
            } finally {
                viewModel.isLoading.postValue(false)
            }
        }
    }

    private fun getGeneratedTip(contentTypeId: String): String = when (contentTypeId) {
        "12" -> "이 장소의 자연 경관을 살리기 위해 광각 렌즈나 삼분할 구도를 추천합니다."
        "14" -> "건물의 직선미와 대칭을 활용해 정적인 분위기를 담아보세요."
        "28" -> "역동적인 순간을 포착하기 위해 셔터 스피드를 확보하세요."
        else -> "배경과 피사체의 조화를 고려해 촬영해 보세요."
    }

    // 골든아워 시간대 판별 (간단 로직)
    private fun isNowGoldenHour(): Boolean {
        val hour = java.time.LocalTime.now().hour
        return hour in 17..19 // 예시: 오후 5시~7시를 골든아워로 간주
    }

    private fun parseTime(timeStr: String): LocalTime {
        val h = timeStr.trim().substring(0, 2).toInt()
        val m = timeStr.trim().substring(2, 4).toInt()
        return LocalTime.of(h, m)
    }
}

