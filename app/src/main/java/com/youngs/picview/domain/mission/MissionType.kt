package com.youngs.picview.domain.mission

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.youngs.picview.R

/**
 * 미션 유형.
 *
 * 미션이 여섯 개일 때는 목록이 그냥 목록이지만, 스물 몇 개가 되면 "무엇을
 * 해볼까"를 고르는 화면이 됩니다. 그때 필요한 것이 갈래입니다. 빛을 보러
 * 갈 것인지, 계절을 보러 갈 것인지, 기록을 남길 것인지가 먼저 정해지고
 * 그 안에서 하나를 고르게 됩니다.
 *
 * 아이콘은 종류를 **보조**할 뿐이라 언제나 글자와 함께 씁니다. 아이콘만
 * 두면 ⇄ 가 비교인지 이동인지 사람마다 다르게 읽습니다.
 *
 * @param doneVerb 완료했을 때 쓰는 말. "다녀왔어요"가 아니라 **행동이 남는
 *   말**입니다. 방문은 앱이 대신 눌러 줄 수 있지만 "빛을 발견"은 사람이
 *   한 일이라, 문구 하나가 미션의 성격을 통째로 바꿉니다.
 */
enum class MissionType(
    val label: String,
    val icon: String,
    val doneVerb: String,
    @ColorRes val colorRes: Int,
    @ColorRes val softColorRes: Int,
    /** 필터 칩에 쓰는 벡터 아이콘. 글자 아이콘([icon])은 카드 원 안에 남습니다. */
    @DrawableRes val iconRes: Int
) {
    LIGHT("빛", "☀", "빛을 발견했어요", R.color.mission_light, R.color.mission_light_soft, R.drawable.ic_sun),
    WEATHER("날씨", "☁", "색을 기록했어요", R.color.mission_weather, R.color.mission_weather_soft, R.drawable.ic_cloud),
    SEASON("계절", "❀", "계절을 관찰했어요", R.color.mission_season, R.color.mission_season_soft, R.drawable.ic_leaf),
    PLACE("장소", "⌖", "장면을 발견했어요", R.color.mission_place, R.color.mission_place_soft, R.drawable.ic_place),
    COMPARE("비교", "⇄", "차이를 비교했어요", R.color.mission_compare, R.color.mission_compare_soft, R.drawable.ic_compare),
    RECORD("기록", "✎", "정읍을 완성했어요", R.color.mission_record, R.color.mission_record_soft, R.drawable.ic_pencil),
    SHARE("공유", "↗", "오늘의 정읍을 소개했어요", R.color.mission_share, R.color.mission_share_soft, R.drawable.ic_share)
}
