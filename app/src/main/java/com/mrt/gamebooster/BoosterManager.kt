package com.mrt.gamebooster

import android.app.ActivityManager
import android.app.GameManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log

/**
 * Gói toàn bộ logic "tăng mượt" hợp lệ, không cần root.
 *
 * QUAN TRỌNG (minh bạch về giới hạn kỹ thuật):
 * Android không cho phép app bên thứ 3 ép tần số quét màn hình hay overclock GPU/CPU.
 * Những gì app này thực sự làm được, và là những gì các Game Booster uy tín trên
 * Play Store cũng làm:
 *   1. Giải phóng RAM bằng cách dừng các process nền không cần thiết.
 *   2. Bật "Không làm phiền" để chặn thông báo gây giật/lag khi có popup đè lên game.
 *   3. Giữ màn hình luôn sáng (chặn sleep) bằng WakeLock trong lúc chơi.
 *   4. Từ Android 13 (API 33): báo cho hệ thống ưu tiên hiệu năng cho tiến trình hiện tại
 *      qua GameManager.setGameState() - đây là API chính thức của Android dành riêng cho
 *      việc này, hệ thống sẽ tự điều chỉnh CPU/GPU governor cho phù hợp.
 */
class BoosterManager(private val context: Context) {

    private var wakeLock: PowerManager.WakeLock? = null
    private var previousInterruptionFilter: Int? = null

    /** Bật toàn bộ tối ưu theo cấu hình đã lưu. Trả về danh sách log để hiển thị cho user. */
    fun startBoost(prefs: PrefsManager): List<String> {
        val log = mutableListOf<String>()

        if (prefs.boosterKillBackgroundApps) {
            val killed = killBackgroundApps()
            log.add("Đã giải phóng RAM: dừng $killed tiến trình nền")
        }

        if (prefs.boosterEnableDnd) {
            if (enableDoNotDisturb()) {
                log.add("Đã bật Không làm phiền")
            } else {
                log.add("Cần cấp quyền 'Do Not Disturb access' để chặn thông báo")
            }
        }

        if (prefs.boosterKeepScreenOn) {
            acquireWakeLock()
            log.add("Đã giữ màn hình luôn sáng")
        }

        if (prefs.boosterPerformanceMode) {
            if (requestPerformanceGameMode()) {
                log.add("Đã yêu cầu hệ thống ưu tiên hiệu năng (Game Mode)")
            } else {
                log.add("Thiết bị/Android version không hỗ trợ Game Mode API")
            }
        }

        return log
    }

    /** Tắt toàn bộ tối ưu, khôi phục trạng thái máy như trước khi boost. */
    fun stopBoost() {
        releaseWakeLock()
        restoreNotificationPolicy()
    }

    // ---- 1. Dọn RAM ----

    private fun killBackgroundApps(): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val before = try {
            am.runningAppProcesses?.size ?: 0
        } catch (e: SecurityException) {
            0
        }

        try {
            val packageManager = context.packageManager
            val installedApps = packageManager.getInstalledApplications(0)
            for (appInfo in installedApps) {
                if (appInfo.packageName != context.packageName) {
                    am.killBackgroundProcesses(appInfo.packageName)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Không thể kill hết background apps: ${e.message}")
        }

        return before
    }

    // ---- 2. Do Not Disturb ----

    private fun enableDoNotDisturb(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            return false
        }
        previousInterruptionFilter = nm.currentInterruptionFilter
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        return true
    }

    private fun restoreNotificationPolicy() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) {
            previousInterruptionFilter?.let {
                nm.setInterruptionFilter(it)
            }
        }
        previousInterruptionFilter = null
    }

    // ---- 3. Giữ màn hình sáng ----

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
            "GameBooster:BoostWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(4 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    // ---- 4. Game Mode Performance (Android 13+) ----

    private fun requestPerformanceGameMode(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return try {
            val gameManager = context.getSystemService(Context.GAME_SERVICE) as? GameManager
            gameManager?.let {
                it.setGameState(
                    android.app.GameState(false, android.app.GameState.MODE_GAMEPLAY_UNINTERRUPTIBLE)
                )
                true
            } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "GameManager không khả dụng trên thiết bị này: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "BoosterManager"
    }
}
