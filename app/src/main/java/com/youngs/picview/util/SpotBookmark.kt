package com.youngs.picview.util

import android.content.Context
import com.youngs.picview.ui.model.SpotItem

/**
 * 장소 하나를 담아 두는 일.
 *
 * 예전에는 이것이 **정거장 하나짜리 코스**를 만들어 코스 목록에 넣었습니다
 * (`PlanQuickSave`). 그래서 홈·지도·일기에서 "출사 계획에 담기"를 누를
 * 때마다 1곳짜리 코스가 하나씩 새로 생겼고, 코스 목록이 "명봉도서관",
 * "정읍 김명관 고택"처럼 한 곳씩 든 코스로 채워졌습니다. 담은 곳들이
 * 모여 한 계획이 되는 것이 아니라 **저장할 때마다 따로 놀았습니다.**
 *
 * 장소를 담는 일은 이미 찜([AppPrefs.toggleFavorite])이 하고 있었습니다.
 * 같은 뜻의 동작이 둘로 갈려 하나는 코스를 만들고 하나는 목록에만 담으니
 * 어느 쪽을 눌러야 할지 알 수 없었습니다. **찜 하나로 합칩니다.**
 *
 * 담아 둔 곳으로 코스를 짜는 길은 따로 있습니다 — 코스 탭의 '직접 고르기'
 * 에서 찜한 곳을 골라 담으면, 순서는 빛이 세웁니다
 * ([com.youngs.picview.ui.course.SpotPickerFragment]).
 */
object SpotBookmark {

    /**
     * 찜을 토글하고 토글 후 상태를 돌려줍니다.
     *
     * @return true 면 방금 담긴 것, false 면 방금 뺀 것
     */
    fun toggle(context: Context, spot: SpotItem): Boolean =
        AppPrefs.toggleFavorite(context, spot)

    fun isSaved(context: Context, contentId: String): Boolean =
        AppPrefs.isFavorite(context, contentId)
}
