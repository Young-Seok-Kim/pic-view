package com.youngs.picview.ui.onboarding

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.youngs.picview.MainActivity
import com.youngs.picview.R
import com.youngs.picview.databinding.ActivityOnboardingBinding
import com.youngs.picview.ui.base.BaseActivity
import com.youngs.picview.util.AppPrefs

/**
 * 최초 실행 안내 (NAVIGATION_FLOW §1).
 *
 * 4장 구성 — 3장(YOUR SISEON)에서 보기 모드를 고르고, 4장의
 * "다음 출사 계획 보기"로 앱을 시작합니다.
 * 로그인·회원가입 단계는 없습니다 — 이 앱은 계정을 두지 않고,
 * 모든 기록이 단말에 남습니다.
 *
 * 3장의 모드 버튼은 **그 자리에서** 적용됩니다. 전에는 누르면 다음 장으로
 * 넘어갔는데, 그러면 "큰 글씨"를 눌렀는데 글씨는 그대로고 화면만 바뀌어
 * 뭐가 된 건지 알 수 없었습니다. 지금은 누르는 즉시 이 화면이 큰 글씨로
 * 다시 그려져서, 고른 결과를 바로 눈으로 확인하고 "다음"으로 넘어갑니다.
 * 그래서 [BaseActivity] 를 상속합니다 — 재생성 때 테마와 글씨 배율이
 * 같이 반영돼야 합니다.
 */
class OnboardingActivity : BaseActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyInsets()
        setupPager(savedInstanceState?.getInt(STATE_PAGE, 0) ?: 0)
        setupActions()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_PAGE, binding.pagerOnboarding.currentItem)
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootOnboarding) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
    }

    /**
     * @param restoredPage 재생성 전에 보고 있던 장. ViewPager2 가 위치는 스스로
     * 되살리지만 그때 onPageSelected 를 부르지 않아서, 점과 버튼은 여기서
     * 직접 그 장에 맞춥니다.
     */
    private fun setupPager(restoredPage: Int) {
        binding.pagerOnboarding.adapter = PageAdapter(PAGES)
        buildDots()

        binding.pagerOnboarding.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    renderDots(position)
                    renderActions(position)
                }
            }
        )
        renderDots(restoredPage)
        renderActions(restoredPage)
    }

    private fun setupActions() {
        binding.btnOnboardingSkip.setOnClickListener {
            // 다시 보기로 왔으면 조용히 닫습니다. 모드를 바꿨다면 그건 이미
            // 저장돼 있고, 본 화면이 돌아올 때 스스로 다시 그립니다.
            if (isReview) finish() else finishOnboarding()
        }

        binding.btnOnboardingNext.setOnClickListener {
            binding.pagerOnboarding.currentItem = binding.pagerOnboarding.currentItem + 1
        }

        binding.btnOnboardingSenior.setOnClickListener { chooseMode(senior = true) }
        binding.btnOnboardingNormal.setOnClickListener { chooseMode(senior = false) }
        binding.btnOnboardingFinish.setOnClickListener { finishOnboarding() }
    }

    /**
     * 보기 모드를 고릅니다. 저장하고 이 화면을 다시 그립니다.
     *
     * 테마와 글씨 배율은 Activity 를 만들 때만 정해지므로 재생성이 필요합니다.
     * 보던 장은 [onSaveInstanceState] 로 이어집니다.
     */
    private fun chooseMode(senior: Boolean) {
        if (AppPrefs.isSeniorMode(this) == senior) return
        AppPrefs.setSeniorMode(this, senior)
        recreate()
    }

    /** 1·2장 다음 / 3장 모드 선택 + 다음 / 4장 시작 버튼. */
    private fun renderActions(position: Int) {
        val isModePage = position == PAGE_MODE
        val isLast = position == PAGES.lastIndex
        binding.btnOnboardingNext.isVisible = !isLast
        binding.btnOnboardingSenior.isVisible = isModePage
        binding.btnOnboardingNormal.isVisible = isModePage
        binding.btnOnboardingFinish.isVisible = isLast
        if (isModePage) renderModeChoice()
    }

    /**
     * 지금 고른 모드에 체크를 붙이고 색을 채웁니다.
     *
     * 두 버튼이 똑같이 생기면 어느 쪽이 지금 상태인지 알 수 없어,
     * 사람들이 이미 켜진 것을 또 누르거나 아무것도 안 고른 줄 압니다.
     * 고른 쪽은 초록으로 채웁니다 — 붉은색은 아래 "다음"이 쓰는 행동의
     * 색이라, 같은 색이면 상태와 행동이 구분되지 않습니다.
     */
    private fun renderModeChoice() {
        val senior = AppPrefs.isSeniorMode(this)
        styleChoice(binding.btnOnboardingSenior, selected = senior)
        styleChoice(binding.btnOnboardingNormal, selected = !senior)
    }

    private fun styleChoice(button: MaterialButton, selected: Boolean) {
        val context = button.context
        if (selected) {
            button.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, R.color.moss_500))
            button.setTextColor(ContextCompat.getColor(context, R.color.white))
            button.icon = AppCompatResources.getDrawable(context, R.drawable.ic_check)
            button.iconTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white))
        } else {
            button.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, R.color.maple_50))
            button.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            button.icon = null
        }
    }

    /**
     * 안내를 끝내고 본 화면으로.
     *
     * 다시 보기로 왔다면 첫 실행 때처럼 버튼 안내(코치마크)를 다시 켭니다.
     * "앱 사용법 다시 보기"를 누른 사람이 원한 게 바로 그것입니다.
     */
    private fun finishOnboarding() {
        AppPrefs.setOnboarded(this)
        if (isReview) {
            AppPrefs.setFeatureTourSeen(this, false)
            AppPrefs.setCameraTourSeen(this, false)
        }

        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    private val isReview: Boolean get() = intent.getBooleanExtra(EXTRA_REVIEW, false)

    // ───────────────────────── 인디케이터 ─────────────────────────

    private fun buildDots() {
        val container = binding.layoutOnboardingDots
        container.removeAllViews()
        val density = resources.displayMetrics.density

        repeat(PAGES.size) { index ->
            val dot = View(this)
            dot.layoutParams = ViewGroup.MarginLayoutParams(
                (7 * density).toInt(), (7 * density).toInt()
            ).apply { marginStart = if (index == 0) 0 else (6 * density).toInt() }
            dot.setBackgroundResource(R.drawable.dot_indicator_inactive)
            container.addView(dot)
        }
    }

    private fun renderDots(active: Int) {
        val container = binding.layoutOnboardingDots
        val density = resources.displayMetrics.density

        for (i in 0 until container.childCount) {
            val dot = container.getChildAt(i)
            val isActive = i == active
            dot.setBackgroundResource(
                if (isActive) R.drawable.dot_indicator_active
                else R.drawable.dot_indicator_inactive
            )
            dot.layoutParams = (dot.layoutParams as ViewGroup.MarginLayoutParams).apply {
                width = ((if (isActive) 20 else 7) * density).toInt()
            }
            dot.requestLayout()
        }
    }

    // ───────────────────────── 페이지 ─────────────────────────

    /**
     * 페이지마다 장식이 달라 레이아웃을 통째로 바꿔 끼웁니다.
     * 내용은 전부 XML에 박혀 있어 바인딩할 것이 없습니다.
     */
    private class PageAdapter(private val layouts: List<Int>) :
        RecyclerView.Adapter<PageAdapter.PageViewHolder>() {

        class PageViewHolder(view: View) : RecyclerView.ViewHolder(view)

        override fun getItemViewType(position: Int) = layouts[position]

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = PageViewHolder(
            LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        )

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) = Unit

        override fun getItemCount() = layouts.size
    }

    companion object {
        private val PAGES = listOf(
            R.layout.item_onboarding_light_match,
            R.layout.item_onboarding_light_route,
            R.layout.item_onboarding_siseon,
            R.layout.item_onboarding_after_frame
        )

        /** 보기 모드를 고르는 장(0부터). */
        private const val PAGE_MODE = 2

        private const val EXTRA_REVIEW = "review"
        private const val STATE_PAGE = "page"

        fun intent(context: Context) = Intent(context, OnboardingActivity::class.java)

        /**
         * MY 탭의 "앱 사용법 다시 보기".
         *
         * 첫 실행과 같은 화면이지만, 건너뛰기가 앱을 다시 시작하지 않고
         * 그냥 닫힙니다 — 보러 온 것이지 설정하러 온 것이 아니라서입니다.
         * 3장의 모드 버튼은 그대로 동작하고, 끝까지 보면 첫 화면의
         * 버튼 안내도 다시 나옵니다.
         */
        fun reviewIntent(context: Context): Intent =
            intent(context).putExtra(EXTRA_REVIEW, true)
    }
}
