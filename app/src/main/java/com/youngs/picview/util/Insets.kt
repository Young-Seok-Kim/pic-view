package com.youngs.picview.util

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding

/**
 * 상태바 높이만큼 위쪽 패딩을 더합니다.
 *
 * 리스너만 걸면 화면에 따라 인셋이 오지 않습니다. 인셋은 창이 처음 붙을 때 한 번
 * 배포되는데, 탭을 눌러 뒤늦게 추가된 프래그먼트의 뷰는 그 시점을 이미 지나쳤기
 * 때문입니다. 그래서 리스너를 건 뒤 requestApplyInsets 로 재배포를 요청합니다.
 *
 * 패딩은 XML 값에 더합니다. 대입해 버리면 레이아웃이 잡아 둔 여백이 사라져
 * 제목이 상태바에 붙습니다.
 */
fun View.applyTopSystemBarInset() {
    val basePadding = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.updatePadding(top = basePadding + bars.top)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

/**
 * 상태바 높이만큼 위쪽 마진을 더합니다.
 *
 * 사진이 상태바 뒤까지 깔리는 히어로 화면에서 씁니다. 배경은 그대로
 * 끝까지 차야 하고 **버튼만** 내려와야 하므로, 패딩을 쓰면 버튼의
 * 배경(유리 원)까지 같이 늘어나 버립니다.
 */
fun View.applyTopSystemBarInsetAsMargin() {
    val baseMargin = (layoutParams as ViewGroup.MarginLayoutParams).topMargin
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = baseMargin + bars.top
        }
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
