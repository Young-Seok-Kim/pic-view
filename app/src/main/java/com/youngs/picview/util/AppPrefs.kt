package com.youngs.picview.util

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * 앱 로컬 설정 저장소.
 *
 * 이 앱은 로그인을 두지 않습니다. 코스·일기·미션·배지가 전부 단말에서 완결되고,
 * 지자체(B2G) 통계도 기획상 "익명 비식별 집계"이기 때문에 계정이 필요 없습니다.
 * 대신 설치마다 한 번 만들어지는 [installId] 로 체크인 로그를 묶습니다.
 *
 * 나중에 기기 간 동기화가 필요해지면 UserProfileRepository 뒤에 서버 구현을
 * 끼우면 되고, 화면 코드는 건드릴 필요가 없습니다.
 */
object AppPrefs {

    private const val FILE = "app_prefs"

    private const val KEY_SENIOR_MODE = "senior_mode"
    private const val KEY_FONT_STEP = "font_scale_step"
    private const val KEY_ONBOARDED = "onboarding_done"
    private const val KEY_INSTALL_ID = "install_id"
    private const val KEY_FAVORITES = "favorite_spots"
    private const val KEY_DIARY_FEELING_PREFIX = "diary_feelings_"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 시니어 간편 모드 사용 여부. */
    fun isSeniorMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SENIOR_MODE, false)

    fun setSeniorMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SENIOR_MODE, enabled).apply()
    }

    /** 온보딩을 이미 봤는지. 최초 실행 분기에 씁니다. */
    fun isOnboarded(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDED, false)

    fun setOnboarded(context: Context) {
        prefs(context).edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    /** 글씨 크기 단계. */
    fun fontStep(context: Context): FontStep {
        val name = prefs(context).getString(KEY_FONT_STEP, null)
        return FontStep.entries.firstOrNull { it.name == name } ?: FontStep.NORMAL
    }

    fun setFontStep(context: Context, step: FontStep) {
        prefs(context).edit().putString(KEY_FONT_STEP, step.name).apply()
    }

    /**
     * 찜한 촬영지 contentId 집합.
     *
     * 상세 히어로의 하트 버튼이 씁니다. 계정이 없으므로 단말에만 남고,
     * 목록 화면 노출 같은 확장은 이 집합을 읽는 쪽에서 결정합니다.
     */
    fun favoriteSpots(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()

    fun isFavorite(context: Context, contentId: String): Boolean =
        contentId in favoriteSpots(context)

    /** 찜을 토글하고, 토글 후 찜 상태를 돌려줍니다. */
    fun toggleFavorite(context: Context, contentId: String): Boolean {
        val current = favoriteSpots(context).toMutableSet()
        val nowFavorite = !current.remove(contentId)
        if (nowFavorite) current.add(contentId)
        prefs(context).edit().putStringSet(KEY_FAVORITES, current).apply()
        return nowFavorite
    }

    /**
     * 그날의 감정 태그(시안 — 오늘의 감정).
     *
     * 일기 본문과 달리 감정은 고르는 것이라 글자로 안 쓰게 됩니다.
     * 날짜 키별 집합으로 단말에만 남습니다.
     */
    fun diaryFeelings(context: Context, dateKey: String): Set<String> =
        prefs(context).getStringSet(KEY_DIARY_FEELING_PREFIX + dateKey, emptySet()) ?: emptySet()

    /** 감정을 토글하고 토글 후 집합을 돌려줍니다. */
    fun toggleDiaryFeeling(context: Context, dateKey: String, feeling: String): Set<String> {
        val current = diaryFeelings(context, dateKey).toMutableSet()
        if (!current.remove(feeling)) current.add(feeling)
        prefs(context).edit()
            .putStringSet(KEY_DIARY_FEELING_PREFIX + dateKey, current)
            .apply()
        return current
    }

    /**
     * 기록성 설정만 지웁니다 — 찜한 촬영지와 날짜별 감정 태그.
     *
     * 큰 글씨 모드·글씨 크기·온보딩 여부·설치 식별자는 남깁니다. 그것들은
     * "무엇을 했는가"가 아니라 "이 앱을 어떻게 쓰는가"라서, 기록 초기화로
     * 함께 사라지면 화면이 갑자기 낯설어집니다.
     */
    fun clearRecords(context: Context) {
        val p = prefs(context)
        val editor = p.edit()
        editor.remove(KEY_FAVORITES)
        p.all.keys
            .filter { it.startsWith(KEY_DIARY_FEELING_PREFIX) }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    /**
     * 익명 설치 식별자. 최초 호출 시 생성해 저장합니다.
     * 개인정보가 아니며, 앱을 지우면 사라집니다.
     */
    fun installId(context: Context): String {
        val p = prefs(context)
        p.getString(KEY_INSTALL_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        p.edit().putString(KEY_INSTALL_ID, id).apply()
        return id
    }
}

/**
 * 글씨 크기 3단계 (스펙 §17-4 탭3 / §11 도움탭).
 *
 * 화면마다 textSize 를 갈아끼우는 대신 Configuration.fontScale 로
 * 앱 전체 sp 를 한 번에 배율 조정합니다. 레이아웃을 하나도 안 고쳐도
 * 모든 글자가 같은 비율로 커집니다.
 *
 * 기준 본문 14sp → 보통 14sp / 크게 17.5sp / 매우 크게 22.4sp.
 * 시니어 모드는 여기에 더해 CTA 글자를 별도로 키웁니다.
 */
enum class FontStep(val scale: Float, val label: String) {
    NORMAL(1.0f, "보통"),
    LARGE(1.25f, "크게"),
    XLARGE(1.6f, "매우 크게")
}
