package com.youngs.picview.util

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.youngs.picview.ui.model.SpotItem
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

    /** 찜한 장소의 정보(JSON). 홈 목록에서 밀려나도 찜 목록에 보이게 합니다. 뒤에 contentId 가 붙습니다. */
    private const val KEY_FAVORITE_SNAPSHOT_PREFIX = "favorite_spot_"
    private const val KEY_DIARY_FEELING_PREFIX = "diary_feelings_"
    private const val KEY_POSE_HINT_SEEN = "pose_hint_seen"
    private const val KEY_FEATURE_TOUR_SEEN = "feature_tour_seen"
    private const val KEY_GUIDE_OVERLAY = "guide_overlay_on"
    private const val KEY_CAMERA_TOUR_SEEN = "camera_tour_seen"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 시니어 간편 모드 사용 여부. */
    fun isSeniorMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SENIOR_MODE, false)

    /**
     * 시니어 모드를 켜고 끕니다. 글씨 크기도 같이 움직입니다.
     *
     * 화면에서는 이 모드를 "큰 글씨"라고 부르는데, 테마만 바꾸면 버튼과
     * 여백은 커져도 글자는 그대로라 "큰 글씨로 했는데 글씨가 안 커진다"는
     * 말이 나옵니다. 켤 때는 글씨를 최소 "크게"로 올리고, 끌 때는 그렇게
     * 올라간 것만 되돌립니다 — 사용자가 직접 "매우 크게"를 골랐다면
     * 그 선택은 남겨 둡니다.
     */
    fun setSeniorMode(context: Context, enabled: Boolean) {
        val step = fontStep(context)
        val nextStep = when {
            enabled && step == FontStep.NORMAL -> FontStep.LARGE
            !enabled && step == FontStep.LARGE -> FontStep.NORMAL
            else -> step
        }
        prefs(context).edit()
            .putBoolean(KEY_SENIOR_MODE, enabled)
            .putString(KEY_FONT_STEP, nextStep.name)
            .apply()
    }

    /** 온보딩을 이미 봤는지. 최초 실행 분기에 씁니다. */
    fun isOnboarded(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDED, false)

    fun setOnboarded(context: Context) {
        prefs(context).edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    /**
     * 촬영 화면의 "포즈 예시" 안내를 이미 봤는지.
     *
     * 한 번은 말해 주되 두 번은 말하지 않습니다. 매번 뜨는 안내는
     * 그때부터 도움이 아니라 치워야 할 것이 됩니다.
     */
    fun isPoseHintSeen(context: Context): Boolean =
        prefs(context).getBoolean(KEY_POSE_HINT_SEEN, false)

    fun setPoseHintSeen(context: Context) {
        prefs(context).edit().putBoolean(KEY_POSE_HINT_SEEN, true).apply()
    }

    /**
     * 첫 화면의 버튼 안내(코치마크)를 이미 봤는지.
     *
     * 온보딩이 "이 앱이 무엇인지"라면 이 안내는 "어디를 누르면 무엇이
     * 되는지"입니다. 첫 실행 뒤 한 번만 보이고, MY 탭의 "앱 사용법 다시
     * 보기"로 되살릴 수 있습니다.
     */
    fun isFeatureTourSeen(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FEATURE_TOUR_SEEN, false)

    fun setFeatureTourSeen(context: Context, seen: Boolean) {
        prefs(context).edit().putBoolean(KEY_FEATURE_TOUR_SEEN, seen).apply()
    }

    /** 촬영 화면의 버튼 안내를 이미 봤는지. 홈의 안내와 따로 셉니다 — 카메라는 나중에 처음 엽니다. */
    fun isCameraTourSeen(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CAMERA_TOUR_SEEN, false)

    fun setCameraTourSeen(context: Context, seen: Boolean) {
        prefs(context).edit().putBoolean(KEY_CAMERA_TOUR_SEEN, seen).apply()
    }

    /**
     * 촬영 화면의 구도 가이드(격자·서는 자리)를 켜 둘지. 기본은 켬.
     * 카메라 격자처럼 화면에서 바로 끄고 켜며, 마지막 상태를 기억합니다.
     */
    fun isGuideOverlayOn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_GUIDE_OVERLAY, true)

    fun setGuideOverlayOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_GUIDE_OVERLAY, on).apply()
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

    /**
     * 찜을 토글하고, 토글 후 찜 상태를 돌려줍니다.
     *
     * 담을 때는 장소 정보도 스냅샷으로 남깁니다. 홈 목록은 관광공사 API 에서
     * 100건만 받아 와 구성이 바뀌므로, id 만 남기면 나중에 목록에서 그 장소를
     * 못 찾아 "찜 3곳"인데 2곳만 보이는 일이 생깁니다.
     */
    fun toggleFavorite(context: Context, spot: SpotItem): Boolean {
        val current = favoriteSpots(context).toMutableSet()
        val nowFavorite = !current.remove(spot.contentId)
        if (nowFavorite) current.add(spot.contentId)
        prefs(context).edit()
            .putStringSet(KEY_FAVORITES, current)
            .apply {
                if (nowFavorite) putString(KEY_FAVORITE_SNAPSHOT_PREFIX + spot.contentId, gson.toJson(spot))
                else remove(KEY_FAVORITE_SNAPSHOT_PREFIX + spot.contentId)
            }
            .apply()
        return nowFavorite
    }

    /** 관광공사에서 내려간 장소처럼 더는 보여 줄 수 없는 찜을 지웁니다. */
    fun removeFavorites(context: Context, contentIds: Collection<String>) {
        if (contentIds.isEmpty()) return
        val current = favoriteSpots(context).toMutableSet()
        current.removeAll(contentIds.toSet())
        prefs(context).edit()
            .putStringSet(KEY_FAVORITES, current)
            .apply { contentIds.forEach { remove(KEY_FAVORITE_SNAPSHOT_PREFIX + it) } }
            .apply()
    }

    /** 찜한 장소의 스냅샷을 갱신합니다. 홈 목록에서 다시 만난 장소는 최신 정보로 덮습니다. */
    fun saveFavoriteSnapshot(context: Context, spot: SpotItem) {
        prefs(context).edit()
            .putString(KEY_FAVORITE_SNAPSHOT_PREFIX + spot.contentId, gson.toJson(spot))
            .apply()
    }

    /** 찜할 때 남긴 장소 정보. 스냅샷을 남기기 전에 찜한 것은 null 입니다. */
    fun favoriteSnapshot(context: Context, contentId: String): SpotItem? =
        prefs(context).getString(KEY_FAVORITE_SNAPSHOT_PREFIX + contentId, null)
            ?.let { runCatching { gson.fromJson(it, SpotItem::class.java) }.getOrNull() }

    private val gson by lazy { Gson() }

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
            .filter {
                it.startsWith(KEY_DIARY_FEELING_PREFIX) || it.startsWith(KEY_FAVORITE_SNAPSHOT_PREFIX)
            }
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
