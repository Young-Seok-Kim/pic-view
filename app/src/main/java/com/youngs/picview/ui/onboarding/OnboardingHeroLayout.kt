package com.youngs.picview.ui.onboarding

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * 온보딩 장의 대표 사진 자리. 글이 차지하고 남은 높이를 받되 너무 작아지거나
 * 커지지 않게 [android.R.attr.minHeight]·[android.R.attr.maxHeight] 사이로 잡습니다.
 *
 * 장 하나는 ScrollView 안의 세로 LinearLayout 이고, 이 뷰만 `0dp + weight` 입니다.
 * 그러면 글씨가 커져(큰 글씨 모드·시스템 글꼴 배율) 글이 길어진 만큼 사진이
 * 줄어들고, 화면이 넉넉하면 사진이 늘어납니다. 사진이 최소 높이까지 줄어도
 * 글이 다 안 들어가는 극단에서는 ScrollView 가 넘긴 만큼을 스크롤합니다 —
 * 예전처럼 사진을 고정 높이로 두면 작은 화면에서 제목이 위로, 안내 문구가
 * 아래로 잘렸습니다.
 *
 * 두 가지를 직접 처리합니다.
 *  - 높이가 정해지지 않은 채(UNSPECIFIED·AT_MOST) 재면 최소 높이로 답합니다.
 *    ScrollView 는 자식을 높이 미지정으로 재고, 그때 LinearLayout 의 weight 는
 *    "내용 크기"로 동작해 사진이 원본 크기를 통째로 차지합니다. 최소 높이로
 *    답해야 "글 + 최소 사진"이 화면에 들어가는지가 먼저 판정되고, 들어가면
 *    ScrollView(fillViewport)가 정확한 높이로 다시 재서 남는 만큼 사진이 큽니다.
 *  - 정확한 크기(EXACTLY)로 재면 FrameLayout 이 minHeight 를 무시하므로
 *    최소·최대 사이로 직접 잘라 넣습니다.
 */
class OnboardingHeroLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val maxHeight: Int

    init {
        val a = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.maxHeight))
        maxHeight = a.getDimensionPixelSize(0, Int.MAX_VALUE)
        a.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val floor = minimumHeight
        val ceiling = maxOf(floor, maxHeight)
        val size = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(heightMeasureSpec).coerceIn(floor, ceiling)
        } else {
            floor
        }
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY))
    }
}
