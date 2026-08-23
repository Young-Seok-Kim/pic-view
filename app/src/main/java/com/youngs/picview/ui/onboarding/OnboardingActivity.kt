package com.youngs.picview.ui.onboarding

import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.youngs.picview.MainActivity
import com.youngs.picview.R
import com.youngs.picview.databinding.ActivityOnboardingBinding
import com.youngs.picview.util.AppPrefs

/**
 * 최초 실행 안내 (NAVIGATION_FLOW §1).
 *
 * 4장 구성 — 3장(YOUR SISEON)에서 보기 모드를 고르면 마지막 장으로
 * 넘어가고, 4장의 "다음 출사 계획 보기"로 앱을 시작합니다.
 * 로그인·회원가입 단계는 없습니다 — 이 앱은 계정을 두지 않고,
 * 모든 기록이 단말에 남습니다.
 *
 * 시니어 테마를 여기서는 적용하지 않습니다([AppCompatActivity] 상속).
 * 모드를 아직 안 골랐는데 미리 큰 글씨로 보여 주면 선택이 무의미해집니다.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    /** 3장에서 고른 보기 모드. 안 고르고 넘겼으면 일반 모드로 시작합니다. */
    private var pendingSenior: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyInsets()
        setupPager()
        setupActions()
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootOnboarding) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
    }

    private fun setupPager() {
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
        renderActions(0)
    }

    private fun setupActions() {
        binding.btnOnboardingSkip.setOnClickListener {
            // 다시 보기로 왔으면 설정을 건드리지 않고 조용히 닫습니다.
            if (intent.getBooleanExtra(EXTRA_REVIEW, false)) finish()
            else finishOnboarding(senior = pendingSenior ?: false)
        }

        binding.btnOnboardingNext.setOnClickListener {
            binding.pagerOnboarding.currentItem = binding.pagerOnboarding.currentItem + 1
        }

        binding.btnOnboardingSenior.setOnClickListener { chooseMode(senior = true) }
        binding.btnOnboardingNormal.setOnClickListener { chooseMode(senior = false) }
        binding.btnOnboardingFinish.setOnClickListener {
            finishOnboarding(senior = pendingSenior ?: false)
        }
    }

    /** 3장에서 모드를 고르면 바로 끝내지 않고 마지막 장을 보여 줍니다. */
    private fun chooseMode(senior: Boolean) {
        pendingSenior = senior
        binding.pagerOnboarding.currentItem = PAGE_MODE + 1
    }

    /** 1·2장 다음 / 3장 모드 선택 / 4장 시작 버튼. */
    private fun renderActions(position: Int) {
        val isModePage = position == PAGE_MODE
        val isLast = position == PAGES.lastIndex
        binding.btnOnboardingNext.isVisible = !isModePage && !isLast
        binding.btnOnboardingSenior.isVisible = isModePage
        binding.btnOnboardingNormal.isVisible = isModePage
        binding.btnOnboardingFinish.isVisible = isLast
    }

    private fun finishOnboarding(senior: Boolean) {
        AppPrefs.setSeniorMode(this, senior)
        AppPrefs.setOnboarded(this)

        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

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
        renderDots(0)
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

        fun intent(context: Context) = Intent(context, OnboardingActivity::class.java)

        /**
         * MY 탭의 "앱 사용법 다시 보기".
         *
         * 첫 실행과 같은 화면이지만, 건너뛰기가 모드를 바꾸거나 앱을 다시
         * 시작하지 않고 그냥 닫힙니다 — 보러 온 것이지 설정하러 온 것이
         * 아니라서입니다. 마지막 장의 모드 버튼은 그대로 동작합니다.
         */
        fun reviewIntent(context: Context): Intent =
            intent(context).putExtra(EXTRA_REVIEW, true)
    }
}
