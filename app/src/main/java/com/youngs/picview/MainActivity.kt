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

//        preLoadData{
//            // 로딩 완료 콜백 함수: 데이터가 다 들어왔을 때만 실행됨
//            if (savedInstanceState == null) {
//                supportFragmentManager.beginTransaction()
//                    .replace(R.id.fragment_container, MainFragment())
//                    .commit()
//            }
//        }

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

//                val spotResponse = RetrofitClient.tourApiService.getJeongeupSpots(BuildConfig.TOUR_API_KEY)
//                val scoreEngine = PhotoScoreEngine() // 점수 계산 엔진 인스턴스

//                viewModel.cachedSpots = spotResponse.response.body.items.item.map { item ->
//                    // 1. contentTypeId를 활용한 스마트한 팁 생성
//                    val generatedTip = when (item.contentTypeId) {
//                        "12" -> "이 장소의 자연 경관을 살리기 위해 광각 렌즈나 삼분할 구도를 추천합니다." // 관광지
//                        "14" -> "건물의 직선미와 대칭을 활용해 정적인 분위기를 담아보세요." // 문화시설
//                        "28" -> "역동적인 순간을 포착하기 위해 셔터 스피드를 확보하세요." // 레포츠
//                        else -> "배경과 피사체의 조화를 고려해 촬영해 보세요."
//                    }
//
//                    SpotItem(
//                        contentId = item.contentid,
//                        contentTypeId = item.contentTypeId ?: "12",
//                        title = item.title,
//                        addr1 = item.addr1 ?: "",
//                        tip = generatedTip,
//                        imageUrl = item.firstimage ?: ""
//                    )
//                }.sortedByDescending { scoreEngine.calculateScore(it) } // 점수 높은 순으로 정렬 완료!
//
//                viewModel.spotData.postValue(viewModel.cachedSpots)


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

    private fun parseTime(timeStr: String): LocalTime {
        val h = timeStr.trim().substring(0, 2).toInt()
        val m = timeStr.trim().substring(2, 4).toInt()
        return LocalTime.of(h, m)
    }
}

