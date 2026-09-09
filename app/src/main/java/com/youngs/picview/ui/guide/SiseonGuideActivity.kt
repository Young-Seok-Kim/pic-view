package com.youngs.picview.ui.guide

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.youngs.picview.R
import com.youngs.picview.databinding.ActivitySiseonGuideBinding
import com.youngs.picview.databinding.ItemGuidePhotoBinding
import com.youngs.picview.domain.guide.SiseonGuide
import com.youngs.picview.domain.guide.SiseonGuideItem
import com.youngs.picview.util.applyTopSystemBarInset

/**
 * 시선 가이드 — 구도 14종을 한 장씩 넘기며 배우고 바로 써 보는 화면.
 *
 * 웹 시안(GuidePage.tsx)의 안드로이드 판입니다. 구도명 라벨 + 사진
 * 캐러셀, 내 사진 비교, 촬영 포인트와 시그널. 시안에 있던 구도·빛·움직임·
 * 체크 탭과 실전 미션 시트는 뺐습니다 — 같은 말을 두 번 하거나, 현장에서
 * 앱을 조작하게 만드는 것들이었습니다. 찍는 일은 카메라 화면이 맡습니다.
 *
 * 홈에서 오면 그 장소의 이름·사진·빛 상태·추천 구도가 함께 넘어와
 * "오늘의 추천 한 장"이 그 장소의 말로 시작합니다.
 */
class SiseonGuideActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySiseonGuideBinding

    private var selectedIndex = 0
    private var contextId = "sunset"
    private var spotTitle: String? = null


    private val comparePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@registerForActivityResult
            binding.layoutCompareEmpty.isVisible = false
            binding.layoutCompareGrid.isVisible = true
            binding.btnCompareUpload.setText(R.string.siseon_upload_change)
            Glide.with(this).load(uri).centerCrop().into(binding.ivCompareMine)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySiseonGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.layoutGuideHeader.applyTopSystemBarInset()

        spotTitle = intent.getStringExtra(EXTRA_SPOT_TITLE)
        contextId = intent.getStringExtra(EXTRA_CONTEXT) ?: "sunset"
        selectedIndex = SiseonGuide.items.indexOf(
            SiseonGuide.byId(intent.getStringExtra(EXTRA_GUIDE_ID))
        )

        binding.btnGuideBack.setOnClickListener { finish() }
        binding.tvGuideCount.text = getString(R.string.siseon_count, SiseonGuide.items.size)

        setupCarousel()
        setupContextChips()

        binding.btnCompareUpload.setOnClickListener { comparePicker.launch("image/*") }
        binding.btnShootNow.setOnClickListener { openCamera() }

        renderSelected()
        renderContext()
    }

    // ───────────────────── 캐러셀 ─────────────────────

    private fun setupCarousel() {
        binding.vpGuide.adapter = PhotoAdapter(SiseonGuide.items)
        binding.vpGuide.setCurrentItem(selectedIndex, false)
        binding.vpGuide.registerOnPageChangeCallback(
            object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    selectedIndex = position
                    renderSelected()
                }
            }
        )
        binding.btnGuidePrev.setOnClickListener { moveGuide(-1) }
        binding.btnGuideNext.setOnClickListener { moveGuide(1) }
    }

    private fun moveGuide(direction: Int) {
        val count = SiseonGuide.items.size
        binding.vpGuide.setCurrentItem((selectedIndex + direction + count) % count, true)
    }

    private class PhotoAdapter(private val items: List<SiseonGuideItem>) :
        RecyclerView.Adapter<PhotoAdapter.Holder>() {

        class Holder(val binding: ItemGuidePhotoBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            ItemGuidePhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.binding.ivGuidePhoto.setImageResource(items[position].imageRes)
        }
    }

    // ───────────────────── 구도 선택 반영 ─────────────────────

    private fun renderSelected() {
        val item = SiseonGuide.items[selectedIndex]
        binding.tvGuideEnglish.text = item.english
        binding.tvGuideKorean.text = item.title
        binding.tvGuideDesc.text = item.description
        binding.tvGuideTips.text = item.tips.joinToString("\n") { "• $it" }
        binding.tvCompareGuideCaption.text =
            getString(R.string.siseon_compare_caption_guide, item.title)
        binding.ivCompareGuide.setImageResource(item.imageRes)

        val signal = SiseonGuide.signalOf(item.id)
        binding.tvSignalLabel.text = signal.label
        binding.tvSignalTitle.text = signal.title
        binding.tvSignalValue.text = signal.value
        binding.tvSignalHint.text = signal.hint
        binding.tvSignalDetail.text = signal.detail

        renderTodayLine()
    }

    /**
     * 날씨 탭이 바꾸는 모든 것.
     *
     * 예전에는 여기서 문구 넷만 갈아 끼웠습니다. 그래서 탭을 눌러도
     * 사진과 구도가 그대로라 "필터가 왜 있지"가 됐습니다. 지금은
     * 참고 사진·라벨·볼 것·전용 기능·복습 질문·색까지 함께 바뀝니다.
     */
    private fun renderContext() {
        val context = SiseonGuide.contextById(contextId)

        val tone = ContextCompat.getColor(this, context.toneRes)

        // ① 참고 사진 + 라벨
        binding.ivWeatherPhoto.setImageResource(context.photoRes)
        binding.tvWeatherLabelEn.text = context.photoLabelEn
        binding.tvWeatherLabelKo.text = context.photoLabelKo
        binding.layoutWeatherLabel.background?.mutate()?.setTint(tone)

        // ② 이 날씨의 컨셉과 볼 것
        binding.tvWeatherConcept.text = "${context.emoji} ${context.chipLabel} · ${context.concept}"
        binding.tvWeatherConcept.setTextColor(tone)
        binding.tvWeatherPoint.text = context.guidePoint

        // ③ 전용 기능 두셋
        binding.chipsWeatherTools.removeAllViews()
        context.tools.forEach { tool ->
            binding.chipsWeatherTools.addView(
                Chip(this).apply {
                    text = tool
                    isCheckable = false
                    isClickable = false
                    chipBackgroundColor = ColorStateList.valueOf(
                        ContextCompat.getColor(this@SiseonGuideActivity, context.toneContainerRes)
                    )
                    setTextColor(tone)
                    chipStrokeWidth = 0f
                }
            )
        }

        // ④ MY FRAME 복습 질문
        binding.tvFrameQuestion.text = "${context.emoji} ${context.frameQuestion}"

        renderTodayLine()
    }

    /**
     * 날씨를 고르면 구도 캐러셀도 그 날씨의 구도로 옮겨 갑니다.
     *
     * 사진만 바뀌고 아래 구도 설명이 그대로면 화면이 두 이야기를 합니다.
     * 사용자가 직접 캐러셀을 넘긴 뒤에는 건드리지 않습니다 — 그때는
     * 구도를 고르는 중이라 발밑이 흔들리면 안 됩니다.
     */
    private fun syncGuideToContext() {
        val target = SiseonGuide.items.indexOfFirst {
            it.id == SiseonGuide.contextById(contextId).guideId
        }
        if (target >= 0 && target != selectedIndex) {
            binding.vpGuide.setCurrentItem(target, true)
        }
    }

    /** 오늘의 추천 한 장. 홈에서 온 장소가 있으면 그 이름으로 말합니다. */
    private fun renderTodayLine() {
        val item = SiseonGuide.items[selectedIndex]
        val context = SiseonGuide.contextById(contextId)
        binding.tvTodayEyebrow.text =
            getString(R.string.siseon_today_eyebrow, context.chipLabel)
        binding.tvTodayLine.text = getString(
            R.string.siseon_today_line,
            spotTitle ?: "지금 있는 곳",
            item.title
        )
        binding.tvTodayMeta.text = "${context.value} · ${context.hint}"
    }

    /**
     * 날씨 탭.
     *
     * 여섯 탭이 같은 색이면 무엇을 고른 상태인지 글자를 읽어야 압니다.
     * 이모지 + 날씨 톤 색으로 이중 코딩해 훑기만 해도 구분되게 합니다.
     */
    private fun setupContextChips() {
        SiseonGuide.contexts.forEach { context ->
            val tone = ContextCompat.getColor(this, context.toneRes)
            val container = ContextCompat.getColor(this, context.toneContainerRes)
            binding.chipsGuideContext.addView(
                Chip(this).apply {
                    id = android.view.View.generateViewId()
                    text = "${context.emoji} ${context.chipLabel}"
                    isCheckable = true
                    isChecked = context.id == contextId
                    tag = context.id
                    // 고른 탭만 그 날씨의 색으로 채웁니다.
                    chipBackgroundColor = ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_checked),
                            intArrayOf()
                        ),
                        intArrayOf(container, ContextCompat.getColor(this@SiseonGuideActivity, R.color.bg_card))
                    )
                    setTextColor(
                        ColorStateList(
                            arrayOf(
                                intArrayOf(android.R.attr.state_checked),
                                intArrayOf()
                            ),
                            intArrayOf(
                                tone,
                                ContextCompat.getColor(
                                    this@SiseonGuideActivity, R.color.text_secondary
                                )
                            )
                        )
                    )
                    chipStrokeWidth = 1 * resources.displayMetrics.density
                    chipStrokeColor = ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_checked),
                            intArrayOf()
                        ),
                        intArrayOf(
                            tone,
                            ContextCompat.getColor(this@SiseonGuideActivity, R.color.divider)
                        )
                    )
                    isCheckedIconVisible = false
                }
            )
        }
        binding.chipsGuideContext.setOnCheckedStateChangeListener { group, checked ->
            val chip = checked.firstOrNull()?.let { group.findViewById<Chip>(it) }
            contextId = (chip?.tag as? String) ?: contextId
            renderContext()
            syncGuideToContext()
        }
    }

    /**
     * "이대로 찍기" — 이 장소의 촬영 화면으로.
     *
     * 예전에는 여기서 미션 시트가 열렸습니다. 찍자고 눌렀는데 체크리스트가
     * 나오면 한 단계가 더 생기는 것이라, 이제는 바로 카메라입니다. 구도는
     * 카메라 화면이 피사체·포즈에 맞춰 그려 줍니다.
     */
    private fun openCamera() {
        startActivity(
            Intent(this, GuideActivity::class.java).apply {
                putExtra(GuideActivity.EXTRA_SPOT_NAME, spotTitle ?: "")
                putExtra(GuideActivity.EXTRA_PHASE, intent.getStringExtra(EXTRA_PHASE))
                putExtra(GuideActivity.EXTRA_CONTENT_ID, intent.getStringExtra(EXTRA_CONTENT_ID))
            }
        )
    }

    companion object {
        private const val EXTRA_SPOT_TITLE = "spot_title"

        /** 촬영지 식별자. "이대로 찍기"로 연 카메라가 사진을 이 장소의 기록에 붙입니다. */
        private const val EXTRA_CONTENT_ID = "content_id"

        /** 들어올 때의 빛 구간 이름. 카메라의 포즈 추천이 씁니다. */
        private const val EXTRA_PHASE = "phase"
        private const val EXTRA_CONTEXT = "context_id"
        private const val EXTRA_GUIDE_ID = "guide_id"

        /**
         * @param spotTitle 홈에서 고른 장소 이름 (없으면 일반 문구)
         * @param contextId 빛 상황 칩 id — [SiseonGuide.contextIdFor]
         * @param guideId   첫 구도 id — [SiseonGuide.guideIdFor]
         * @param contentId 촬영지 식별자. 카메라로 넘겨 사진을 그 장소의 기록에 붙입니다.
         * @param phaseName 지금의 빛 구간 이름([LightPhase.name]). 카메라의 포즈 추천이 씁니다.
         */
        fun intent(
            context: Context,
            spotTitle: String? = null,
            contextId: String? = null,
            guideId: String? = null,
            contentId: String? = null,
            phaseName: String? = null
        ): Intent = Intent(context, SiseonGuideActivity::class.java)
            .putExtra(EXTRA_SPOT_TITLE, spotTitle)
            .putExtra(EXTRA_CONTEXT, contextId)
            .putExtra(EXTRA_GUIDE_ID, guideId)
            .putExtra(EXTRA_CONTENT_ID, contentId)
            .putExtra(EXTRA_PHASE, phaseName)
    }
}
