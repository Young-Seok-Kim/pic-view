package com.youngs.picview

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.youngs.picview.data.api.RetrofitClient
import com.youngs.picview.databinding.ActivityMainBinding
import com.youngs.picview.ui.base.BaseActivity
import com.youngs.picview.domain.light.SunTimes
import com.youngs.picview.ui.common.PlaceholderFragment
import com.youngs.picview.ui.course.CourseInputFragment
import com.youngs.picview.ui.diary.DiaryFragment
import com.youngs.picview.ui.home.HomeFragment
import com.youngs.picview.ui.my.MyFragment
import com.youngs.picview.ui.onboarding.OnboardingActivity
import com.youngs.picview.ui.senior.SeniorHelpFragment
import com.youngs.picview.ui.senior.SeniorHomeFragment
import com.youngs.picview.ui.main.MainFragment
import com.youngs.picview.ui.main.MainViewModel
import com.youngs.picview.domain.score.PhotoScoreEngine
import com.youngs.picview.ui.model.SpotItem
import com.youngs.picview.ui.model.SpotScoreContext
import com.youngs.picview.util.AppPrefs
import com.youngs.picview.util.retryOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    /** 탭별 Fragment 를 살려 둡니다. 탭을 오갈 때 스크롤 위치가 유지됩니다. */
    private val tabFragments = mutableMapOf<Int, Fragment>()
    private var currentTabId: Int = R.id.tab_home

    private val isSenior get() = AppPrefs.isSeniorMode(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // 최초 실행이면 안내부터. 여기서 모드를 고르고 돌아옵니다.
        // setContentView 전에 넘겨야 본 화면이 한 프레임 비치지 않습니다.
        if (!AppPrefs.isOnboarded(this)) {
            startActivity(OnboardingActivity.intent(this))
            finish()
            return
        }

        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        splashScreen.setKeepOnScreenCondition { viewModel.isLoading.value == true }

        setupBottomNav()
        setupWindowInsets()
        setupBackStackListener()

        if (savedInstanceState == null) {
            switchTab(R.id.tab_home)
        } else {
            currentTabId = savedInstanceState.getInt(STATE_TAB, R.id.tab_home)
            // 탭 정리·복원·선택까지 여기서 함께 처리합니다.
            restoreTabReferences()
        }

        preLoadData()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TAB, currentTabId)
    }

    // ─────────────────────────────── 탭 ───────────────────────────────

    private fun setupBottomNav() {
        // 레이아웃에 5탭이 박혀 있으므로 비우고 모드에 맞는 메뉴를 다시 넣습니다.
        binding.navBottom.menu.clear()
        binding.navBottom.inflateMenu(
            if (isSenior) R.menu.bottom_nav_senior else R.menu.bottom_nav_normal
        )
        if (isSenior) {
            binding.navBottom.minimumHeight =
                resources.getDimensionPixelSize(R.dimen.bottom_nav_height_senior)
        }

        binding.navBottom.setOnItemSelectedListener { item ->
            if (item.itemId == currentTabId && supportFragmentManager.backStackEntryCount == 0) {
                // 같은 탭 재탭 → 루트로 스크롤 업 (NAVIGATION_FLOW 부록)
                (tabFragments[item.itemId] as? TabRoot)?.scrollToTop()
            } else {
                // 다른 탭으로 가기 전에 쌓인 상세 화면을 정리합니다.
                clearBackStack()
                switchTab(item.itemId)
            }
            true
        }
    }

    private fun switchTab(itemId: Int) {
        val tx = supportFragmentManager.beginTransaction()

        tabFragments.forEach { (id, fragment) ->
            if (id != itemId) tx.hide(fragment)
        }

        val existing = tabFragments[itemId]
        if (existing == null) {
            val fragment = createTabFragment(itemId)
            tabFragments[itemId] = fragment
            tx.add(R.id.fragment_container, fragment, tagFor(itemId))
        } else {
            tx.show(existing)
        }

        tx.commitNow()
        currentTabId = itemId
    }

    /**
     * Activity 재생성 후 기존 Fragment 를 다시 붙잡습니다.
     *
     * 모드 전환(일반 ↔ 간편)으로 재생성된 경우가 까다롭습니다.
     * 예를 들어 MY 탭에서 간편 모드로 바꾸면 간편 모드에는 MY 탭이 없는데
     * MY Fragment 는 FragmentManager 에 남아 화면에 그대로 보입니다.
     * (탭바는 '홈'인데 내용은 MY 인 상태)
     *
     * 그래서 현재 모드에 없는 탭의 Fragment 는 정리하고, 선택된 탭도
     * 유효한 것으로 되돌립니다.
     */
    private fun restoreTabReferences() {
        val validIds = if (isSenior) SENIOR_TAB_IDS else NORMAL_TAB_IDS

        // 1. 현재 모드에 없는 탭 Fragment 제거
        val stale = supportFragmentManager.fragments.filter { fragment ->
            val tag = fragment.tag ?: return@filter false
            tag.startsWith(TAB_TAG_PREFIX) && validIds.none { tagFor(it) == tag }
        }
        if (stale.isNotEmpty()) {
            supportFragmentManager.beginTransaction()
                .apply { stale.forEach { remove(it) } }
                .commitNow()
        }

        // 2. 살아남은 탭만 다시 참조
        validIds.forEach { id ->
            supportFragmentManager.findFragmentByTag(tagFor(id))?.let { tabFragments[id] = it }
        }

        // 3. 선택 탭이 현재 모드에 없으면 홈으로
        if (currentTabId !in validIds) {
            currentTabId = R.id.tab_home
        }
        switchTab(currentTabId)
        binding.navBottom.selectedItemId = currentTabId
    }

    private fun createTabFragment(itemId: Int): Fragment = when {
        // 간편 모드는 홈·도움을 전용 화면으로 바꿉니다.
        // 코스 탭은 그대로 씁니다 — 입력 항목이 이미 단순하고, 시니어 테마가
        // 글씨·버튼·여백을 자동으로 키워 주기 때문입니다.
        isSenior && itemId == R.id.tab_home -> SeniorHomeFragment()
        isSenior && itemId == R.id.tab_help -> SeniorHelpFragment()
        else -> createNormalTabFragment(itemId)
    }

    private fun createNormalTabFragment(itemId: Int): Fragment = when (itemId) {
        // 탐색 탭 : 기존 포토스코어 목록 화면을 그대로 씁니다.
        R.id.tab_explore -> MainFragment()

        // 홈 탭 : 지금의 빛 + 오늘 좋은 스팟
        R.id.tab_home -> HomeFragment()

        // 코스 탭 : 출사 조건 입력 → 빛 스케줄
        R.id.tab_course -> CourseInputFragment()

        // 일기 탭 : 방문 기록 → 출사 일기
        R.id.tab_diary -> DiaryFragment()
        // MY 탭 : 저장한 코스 · 접근성 설정
        R.id.tab_my -> MyFragment()
        R.id.tab_help -> PlaceholderFragment.newInstance("도움", "📞")
        else -> PlaceholderFragment.newInstance("준비 중", "✨")
    }

    private fun tagFor(itemId: Int) = "$TAB_TAG_PREFIX$itemId"

    /** 다른 화면(홈의 CTA 등)에서 탭을 바꿀 때 씁니다. 탭바 선택 상태도 같이 갱신됩니다. */
    fun selectTab(itemId: Int) {
        if (binding.navBottom.selectedItemId == itemId) return
        binding.navBottom.selectedItemId = itemId
    }

    /** 탭 Fragment 가 구현하면 같은 탭 재탭 시 맨 위로 올라갑니다. */
    interface TabRoot {
        fun scrollToTop()
    }

    // ────────────────────────── 화면 전환 ──────────────────────────

    /**
     * 상세·지도처럼 탭 위에 덮이는 화면을 띄웁니다.
     *
     * replace 대신 add + hide 를 쓰는 이유: replace 는 컨테이너의 다른 탭
     * Fragment 까지 전부 제거해서, 뒤로 가기로 돌아왔을 때 탭 상태가 날아갑니다.
     */
    fun pushScreen(fragment: Fragment) {
        val current = tabFragments[currentTabId]
        val tx = supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right, R.anim.fade_out,
                R.anim.fade_in, R.anim.slide_out_right
            )
            .add(R.id.fragment_container, fragment)
        if (current != null) tx.hide(current)
        tx.addToBackStack(null).commit()
    }

    private fun clearBackStack() {
        while (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStackImmediate()
        }
    }

    /** 상세 화면이 떠 있는 동안에는 탭바를 숨깁니다. */
    private fun setupBackStackListener() {
        supportFragmentManager.addOnBackStackChangedListener {
            val onRoot = supportFragmentManager.backStackEntryCount == 0
            binding.navBottom.isVisible = onRoot
            binding.navDivider.isVisible = onRoot
        }
    }

    // ─────────────────────────── 인셋 ───────────────────────────

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // 탭바만 하단 제스처바를 피합니다. 상단은 각 화면이 직접 처리합니다.
            binding.navBottom.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    // ─────────────────────────── 데이터 ───────────────────────────

    override fun onResume() {
        super.onResume()
        val elapsed = SystemClock.elapsedRealtime() - viewModel.lastLoadedAt
        if (viewModel.lastLoadedAt > 0L && elapsed > STALE_AFTER_MS) {
            refresh(userInitiated = false)
        }
    }

    /**
     * 캐시를 비우고 날씨·일출·촬영지를 다시 받아옵니다.
     *
     * @param userInitiated 당겨서 새로고침이면 true. 목록 위 스피너를 띄웁니다.
     */
    fun refresh(userInitiated: Boolean) {
        if (viewModel.isLoading.value == true || viewModel.isRefreshing.value == true) return

        viewModel.cachedWeather = null
        viewModel.cachedGoldenHour = null
        viewModel.loadFailed.value = false

        if (userInitiated) viewModel.isRefreshing.value = true else viewModel.isLoading.value = true
        preLoadData()
    }

    fun reloadData() = refresh(userInitiated = false)

    private fun preLoadData() {
        if (viewModel.cachedWeather != null) {
            viewModel.isLoading.value = false
            viewModel.isRefreshing.value = false
            return
        }

        lifecycleScope.launch {
            try {
                // 초단기실황은 매시 40분 이후에 그 시각 관측값이 올라옵니다.
                // 40분 이전이면 한 시간 전을 기준으로 잡습니다.
                //
                // 날짜와 시각을 반드시 '같은 시각'에서 뽑아야 합니다.
                // 예전에는 날짜만 now 에서, 시각은 now.minusHours(1) 에서 뽑아
                // 00:00~00:59 사이에 (오늘 날짜 + 2330) 이라는 없는 조합이 되어
                // NO_DATA(03) 가 떨어졌습니다.
                val base = LocalDateTime.now().let { if (it.minute < 40) it.minusHours(1) else it }
                val dateStr = base.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                val timeStr = base.format(DateTimeFormatter.ofPattern("HH")) + "00"

                // 세 API 는 서로 의존하지 않으므로 동시에 호출합니다.
                // 각각 독립적으로 재시도하고, 하나가 실패해도 나머지는 그대로 보여 줍니다.
                val weatherAsync = async {
                    retryOrNull("WEATHER_API") {
                        RetrofitClient.weatherApiService.getUltraSrtNcst(
                            serviceKey = BuildConfig.TOUR_API_KEY,
                            baseDate = dateStr,
                            baseTime = timeStr
                        )
                    }
                }
                val astroAsync = async {
                    retryOrNull("ASTRO_API") {
                        RetrofitClient.weatherApiService.getAreaRiseSetInfo(
                            serviceKey = BuildConfig.TOUR_API_KEY,
                            locdate = dateStr
                        )
                    }
                }
                val spotsAsync = async {
                    retryOrNull("TOUR_API") {
                        RetrofitClient.tourApiService.getJeongeupSpots(BuildConfig.TOUR_API_KEY)
                    }
                }

                // 1. 날씨
                val weatherItems =
                    weatherAsync.await()?.response?.body?.items?.item.orEmpty()
                val temp = weatherItems.firstOrNull { it.category == "T1H" }?.obsrValue
                val tempValue = temp?.toDoubleOrNull() ?: 20.0
                val pty = weatherItems.firstOrNull { it.category == "PTY" }?.obsrValue ?: "0"
                val isRaining = pty != "0"

                // 2. 일출/일몰 → 골든아워
                // 여기서 만든 SunTimes 가 출사 코스 계산의 기준점이 됩니다.
                val astro = astroAsync.await()?.response?.body?.items?.item
                val sunTimes = SunTimes(
                    sunrise = SunTimes.parse(astro?.sunrise),
                    sunset = SunTimes.parse(astro?.sunset),
                    meridian = SunTimes.parse(astro?.meridian)
                )
                viewModel.sunTimes = sunTimes

                val isGoldenHour = sunTimes.isGoldenHourNow()
                val goldenText = goldenHourText(sunTimes)

                // 3. 촬영지 목록
                val rawSpots = spotsAsync.await()?.response?.body?.items?.item.orEmpty()
                val lastIndex = (rawSpots.size - 1).coerceAtLeast(1)
                val breakdowns = mutableMapOf<String, com.youngs.picview.domain.score.PhotoScore>()

                val spots = rawSpots.mapIndexedNotNull { index, item ->
                    val contentId = item.contentid ?: return@mapIndexedNotNull null
                    val contentTypeId = item.contentTypeId ?: "0"

                    val (inferredDirection, inferredBestTime) = when (contentTypeId) {
                        "12" -> "WEST" to "SUNSET"    // 자연은 일몰/서쪽이 최고
                        else -> "NONE" to "AFTERNOON"
                    }

                    val spot = SpotItem(
                        contentId = contentId,
                        contentTypeId = contentTypeId,
                        title = item.title.orEmpty(),
                        addr1 = item.addr1.orEmpty(),
                        tip = "",
                        imageUrl = item.firstimage.orEmpty(),
                        mapx = item.mapx.orEmpty(),
                        mapy = item.mapy.orEmpty()
                    )

                    val context = SpotScoreContext(
                        spot = spot,
                        currentTemp = tempValue,
                        isGoldenHour = isGoldenHour,
                        isRaining = isRaining,
                        userDistance = 0.0,
                        direction = inferredDirection,
                        bestTime = inferredBestTime,
                        freshnessRank = index.toDouble() / lastIndex,
                        hasPhoto = !item.firstimage.isNullOrBlank()
                    )
                    // 총점과 근거를 함께 계산합니다. 상세 화면이 "왜 이 점수인지"를
                    // 설명하려면 항목별 값이 필요합니다.
                    val evaluated = PhotoScoreEngine.evaluate(context)
                    spot.score = evaluated.total
                    breakdowns[contentId] = evaluated
                    spot
                }.sortedByDescending { it.score }

                viewModel.scoreBreakdowns = breakdowns
                viewModel.spotData.postValue(spots)

                val weatherResult = if (temp != null) "지금 정읍은 ${temp}℃" else "정읍의 촬영지"
                if (temp != null || spots.isNotEmpty()) {
                    viewModel.cachedWeather = weatherResult
                    viewModel.cachedGoldenHour = goldenText
                }

                viewModel.weatherData.postValue(weatherResult)
                viewModel.goldenHourData.postValue(goldenText)
                viewModel.loadFailed.postValue(spots.isEmpty())
                if (spots.isNotEmpty()) {
                    viewModel.lastLoadedAt = SystemClock.elapsedRealtime()
                }

            } catch (e: CancellationException) {
                throw e // 화면을 벗어난 경우까지 실패로 처리하면 안 됩니다.
            } catch (e: Exception) {
                Log.e("PRELOAD_ERROR", "사전 로딩 실패: ${e.message}")
                viewModel.weatherData.postValue("정보를 불러올 수 없습니다.")
                viewModel.goldenHourData.postValue("정보 없음")
                viewModel.loadFailed.postValue(true)
            } finally {
                viewModel.isLoading.postValue(false)
                viewModel.isRefreshing.postValue(false)
            }
        }
    }

    // ───────────────────────── 골든아워 ─────────────────────────

    /**
     * 화면에 띄울 골든아워 문구.
     *
     * 판정 자체는 [SunTimes] 가 합니다. 예전에는 반대로 이 문구에 "진행"이
     * 들어있는지로 점수를 계산해서, 문구를 다듬으면 점수가 조용히 바뀌었습니다.
     */
    private fun goldenHourText(sun: SunTimes): String {
        if (!sun.hasData) return "일출·일몰 정보 없음"

        val now = LocalTime.now()
        val phase = sun.phaseNow()
        if (phase.isGolden) {
            return if (phase == com.youngs.picview.domain.light.LightPhase.SUNRISE) {
                "일출 골든아워 진행 중 🌅"
            } else {
                "일몰 골든아워 진행 중 🌇"
            }
        }

        val toSunrise = sun.sunrise?.let { Duration.between(now, it).toMinutes() }
        val toSunset = sun.sunset?.let { Duration.between(now, it).toMinutes() }
        return when {
            toSunrise != null && toSunrise > 0 ->
                "일출까지 ${toSunrise / 60}시간 ${toSunrise % 60}분"
            toSunset != null && toSunset > 0 ->
                "일몰까지 ${toSunset / 60}시간 ${toSunset % 60}분"
            else -> "내일 일출을 기다려보세요"
        }
    }

    companion object {
        private const val STATE_TAB = "current_tab"
        private const val TAB_TAG_PREFIX = "tab_"

        /** 이 시간이 지난 뒤 앱으로 돌아오면 데이터를 다시 받아옵니다. */
        private const val STALE_AFTER_MS = 10 * 60 * 1000L

        /** 일출·일몰 전후 이 분(分) 안쪽을 골든아워로 봅니다. */
        private const val GOLDEN_HOUR_MINUTES = 60L

        private val NORMAL_TAB_IDS = listOf(
            R.id.tab_home, R.id.tab_course, R.id.tab_explore, R.id.tab_diary, R.id.tab_my
        )
        private val SENIOR_TAB_IDS = listOf(R.id.tab_home, R.id.tab_course, R.id.tab_help)
    }
}
