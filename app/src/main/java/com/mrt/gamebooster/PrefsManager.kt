package com.mrt.gamebooster

import android.content.Context
import android.content.SharedPreferences

/**
 * Quản lý lưu trữ cấu hình.
 *
 * Vị trí vùng overlay được lưu dưới dạng TỈ LỆ (0f..1f) so với chiều rộng/cao màn hình
 * thay vì tọa độ pixel tuyệt đối. Đây là điểm mấu chốt để vùng "cố định" giữ đúng
 * vị trí tương đối khi người dùng xoay ngang/dọc màn hình khi chơi game.
 */
class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "game_booster_prefs"

        private const val KEY_ZONE_X_RATIO = "zone_x_ratio"
        private const val KEY_ZONE_Y_RATIO = "zone_y_ratio"
        private const val KEY_ZONE_SIZE_DP = "zone_size_dp"
        private const val KEY_ZONE_ENABLED = "zone_enabled"
        private const val KEY_ZONE_LOCKED = "zone_locked"

        private const val KEY_BOOSTER_KILL_BG = "booster_kill_bg"
        private const val KEY_BOOSTER_DND = "booster_dnd"
        private const val KEY_BOOSTER_KEEP_SCREEN_ON = "booster_keep_screen_on"
        private const val KEY_BOOSTER_PERFORMANCE_MODE = "booster_performance_mode"

        private const val DEFAULT_SIZE_DP = 64f
    }

    // ---- Cấu hình vùng cố định (fixed touch zone) ----

    var zoneXRatio: Float
        get() = prefs.getFloat(KEY_ZONE_X_RATIO, 0.85f)
        set(value) = prefs.edit().putFloat(KEY_ZONE_X_RATIO, value.coerceIn(0f, 1f)).apply()

    var zoneYRatio: Float
        get() = prefs.getFloat(KEY_ZONE_Y_RATIO, 0.5f)
        set(value) = prefs.edit().putFloat(KEY_ZONE_Y_RATIO, value.coerceIn(0f, 1f)).apply()

    var zoneSizeDp: Float
        get() = prefs.getFloat(KEY_ZONE_SIZE_DP, DEFAULT_SIZE_DP)
        set(value) = prefs.edit().putFloat(KEY_ZONE_SIZE_DP, value.coerceIn(24f, 200f)).apply()

    var zoneEnabled: Boolean
        get() = prefs.getBoolean(KEY_ZONE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ZONE_ENABLED, value).apply()

    /** Khi khóa: overlay không thể kéo di chuyển nữa, chỉ nhận sự kiện chạm cố định. */
    var zoneLocked: Boolean
        get() = prefs.getBoolean(KEY_ZONE_LOCKED, false)
        set(value) = prefs.edit().putBoolean(KEY_ZONE_LOCKED, value).apply()

    // ---- Cấu hình Booster ----

    var boosterKillBackgroundApps: Boolean
        get() = prefs.getBoolean(KEY_BOOSTER_KILL_BG, true)
        set(value) = prefs.edit().putBoolean(KEY_BOOSTER_KILL_BG, value).apply()

    var boosterEnableDnd: Boolean
        get() = prefs.getBoolean(KEY_BOOSTER_DND, true)
        set(value) = prefs.edit().putBoolean(KEY_BOOSTER_DND, value).apply()

    var boosterKeepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_BOOSTER_KEEP_SCREEN_ON, true)
        set(value) = prefs.edit().putBoolean(KEY_BOOSTER_KEEP_SCREEN_ON, value).apply()

    var boosterPerformanceMode: Boolean
        get() = prefs.getBoolean(KEY_BOOSTER_PERFORMANCE_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_BOOSTER_PERFORMANCE_MODE, value).apply()
}
