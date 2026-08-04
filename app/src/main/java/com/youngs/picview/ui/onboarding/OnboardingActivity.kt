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
import com.youngs.picview.databinding.ItemOnboardingPageBinding
import com.youngs.picview.util.AppPrefs

/**
 * 최초 실행 안내 (NAVIGATION_FLOW §1).
 *
 * 3장을 넘긴 뒤 마지막에서 사용 모드를 고릅니다.
 * 로그인·회원가입 단계는 없습니다 — 이 앱은 계정을 두지 않고,
 * 모든 기록이 단말에 남습니다.
 *
 * 시니어 테마를 여기서는 적용하지 않습니다([AppCompatActivity] 상속).
 * 모드를 아직 안 골랐는데 미리 큰 글씨로 보여 주면 선택이 무의미해집니다.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

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
        binding.btnOnboardingSkip.setOnClickListener { finishOnboarding(senior = false) }

        binding.btnOnboardingNext.setOnClickListener {
            binding.pagerOnboarding.currentItem = binding.pagerOnboarding.currentItem + 1
        }

        binding.btnOnboardingSenior.setOnClickListener { finishOnboarding(senior = true) }
        binding.btnOnboardingNormal.setOnClickListener { finishOnboarding(senior = false) }
    }

    /** 마지막 장에서만 모드 선택 버튼을 보여 줍니다. */
    private fun renderActions(position: Int) {
        val isLast = position == PAGES.lastIndex
        binding.btnOnboardingNext.isVisible = !isLast
        binding.btnOnboardingSenior.isVisible = isLast
        binding.btnOnboardingNormal.isVisible = isLast
        binding.btnOnboardingSkip.isVisible = !isLast
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

    private data class Page(
        val emoji: String,
        val titleRes: Int,
        val descRes: Int,
        val tintRes: Int
    )

    private class PageAdapter(private val pages: List<Page>) :
        RecyclerView.Adapter<PageAdapter.PageViewHolder>() {

        class PageViewHolder(val binding: ItemOnboardingPageBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = PageViewHolder(
            ItemOnboardingPageBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val page = pages[position]
            val context = holder.itemView.context

            with(holder.binding) {
                tvOnboardingEmoji.text = page.emoji
                tvOnboardingTitle.setText(page.titleRes)
                tvOnboardingDesc.setText(page.descRes)

                layoutOnboardingSymbol.background?.mutate()?.setColorFilter(
                    ContextCompat.getColor(context, page.tintRes), PorterDuff.Mode.SRC_IN
                )
            }
        }

        override fun getItemCount() = pages.size
    }

    companion object {
        private val PAGES = listOf(
            Page(
                emoji = "📸",
                titleRes = R.string.onboarding_1_title,
                descRes = R.string.onboarding_1_desc,
                tintRes = R.color.maple_50
            ),
            Page(
                emoji = "🌅",
                titleRes = R.string.onboarding_2_title,
                descRes = R.string.onboarding_2_desc,
                tintRes = R.color.golden_100
            ),
            Page(
                emoji = "👀",
                titleRes = R.string.onboarding_3_title,
                descRes = R.string.onboarding_3_desc,
                tintRes = R.color.moss_50
            )
        )

        fun intent(context: Context) = Intent(context, OnboardingActivity::class.java)
    }
}
