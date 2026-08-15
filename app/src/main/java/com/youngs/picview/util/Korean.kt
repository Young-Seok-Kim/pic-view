package com.youngs.picview.util

/**
 * 받침에 따라 갈리는 조사·어미를 고릅니다.
 *
 * 빛 구간 이름을 문장에 넣으면 "지금은 야간**이에요**" 와 "지금은 일출
 * 골든아워**예요**" 로 어미가 갈립니다. 하나로 고정하면 여덟 구간 중 절반이
 * 어색해지고, 앱이 한국어를 모르는 것처럼 보입니다.
 *
 * 한글 음절은 유니코드에서 (초성, 중성, 종성) 순으로 규칙적으로 배열돼 있어
 * 종성 인덱스만 보면 받침 유무를 알 수 있습니다.
 */
fun String.byBatchim(withBatchim: String, withoutBatchim: String): String {
    val last = trimEnd().lastOrNull() ?: return withoutBatchim
    if (last !in '가'..'힣') return withoutBatchim
    return if ((last - '가') % 28 != 0) withBatchim else withoutBatchim
}
