package com.mrt.gamebooster

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlin.math.abs

/**
 * Foreground Service chịu trách nhiệm:
 *  1. Vẽ 1 vùng tròn nổi (fixed touch zone) đứng yên tại vị trí tương đối đã cấu hình,
 *     giữ nguyên vị trí đó kể cả khi xoay ngang/dọc màn hình.
 *  2. Bật/tắt các tối ưu hiệu năng (BoosterManager) khi service khởi động/dừng.
 *
 * Overlay dùng TYPE_APPLICATION_OVERLAY (Android 8+) với FLAG_NOT_TOUCH_MODAL để
 * KHÔNG chặn touch của game ở các vùng khác trên màn hình - chỉ vùng tròn mới bắt sự kiện.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: PrefsManager
    private lateinit var boosterManager: BoosterManager

    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    // Trạng thái kéo-thả tạm thời (chỉ dùng khi vùng chưa bị khóa)
    private var dragStartTouchX = 0f
    private var dragStartTouchY = 0f
    private var dragStartViewX = 0
    private var dragStartViewY = 0
    private var isDragging = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = PrefsManager(this)
        boosterManager = BoosterManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (prefs.zoneEnabled && overlayView == null) {
            showOverlay()
        }
        boosterManager.startBoost(prefs)

        return START_STICKY
    }

    override fun onDestroy() {
        removeOverlay()
        boosterManager.stopBoost()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Khi xoay màn hình, Android sẽ gọi callback này (Service luôn nhận được, không phụ
     * thuộc configChanges khai báo cho Activity). Ta tính lại tọa độ pixel tuyệt đối từ
     * tỉ lệ đã lưu để vùng cố định "đi theo" đúng vị trí tương đối trên màn hình mới.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayView?.post { repositionFromRatio() }
    }

    // ---------------- Overlay: vẽ + kéo-thả + khóa vị trí ----------------

    private fun showOverlay() {
        if (overlayView != null) return

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.overlay_zone, null)

        val sizePx = dpToPx(prefs.zoneSizeDp)
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        overlayParams = params
        overlayView = view

        view.setOnTouchListener { v, event -> handleTouch(v, event) }

        windowManager.addView(view, params)
        repositionFromRatio()
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: IllegalArgumentException) {
                // View đã bị gỡ trước đó (service bị hệ thống kill và restart) - bỏ qua an toàn.
            }
        }
        overlayView = null
        overlayParams = null
    }

    /** Tính lại tọa độ pixel (x,y) từ tỉ lệ đã lưu, áp dụng theo kích thước màn hình HIỆN TẠI. */
    private fun repositionFromRatio() {
        val params = overlayParams ?: return
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val maxX = (metrics.widthPixels - params.width).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - params.height).coerceAtLeast(0)

        params.x = (prefs.zoneXRatio * maxX).toInt()
        params.y = (prefs.zoneYRatio * maxY).toInt()

        updateViewSafely(params)
    }

    /** Lưu lại vị trí hiện tại (pixel) thành tỉ lệ, để dùng lại đúng vị trí tương đối sau khi xoay. */
    private fun saveCurrentPositionAsRatio() {
        val params = overlayParams ?: return
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val maxX = (metrics.widthPixels - params.width).coerceAtLeast(1)
        val maxY = (metrics.heightPixels - params.height).coerceAtLeast(1)

        prefs.zoneXRatio = params.x.toFloat() / maxX.toFloat()
        prefs.zoneYRatio = params.y.toFloat() / maxY.toFloat()
    }

    private fun updateViewSafely(params: WindowManager.LayoutParams) {
        val view = overlayView ?: return
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: IllegalArgumentException) {
            // View chưa được add hoặc đã gỡ - bỏ qua.
        }
    }

    /**
     * Xử lý chạm trên vùng cố định:
     *  - Nếu ĐÃ KHÓA (zoneLocked = true): vùng đứng yên tuyệt đối, chạm vào chỉ phát rung
     *    phản hồi (placeholder cho hành động tùy biến sau này: macro, auto-tap...).
     *  - Nếu CHƯA KHÓA: cho phép kéo-thả để định vị lại vị trí mong muốn.
     */
    private fun handleTouch(view: View, event: MotionEvent): Boolean {
        val params = overlayParams ?: return false

        if (prefs.zoneLocked) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                triggerHapticFeedback()
            }
            // Trả về true để vùng này "ăn" sự kiện chạm tại đúng vị trí của nó,
            // không để game phía dưới nhận nhầm thao tác.
            return true
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartTouchX = event.rawX
                dragStartTouchY = event.rawY
                dragStartViewX = params.x
                dragStartViewY = params.y
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - dragStartTouchX
                val dy = event.rawY - dragStartTouchY
                if (!isDragging && (abs(dx) > TOUCH_SLOP || abs(dy) > TOUCH_SLOP)) {
                    isDragging = true
                }
                if (isDragging) {
                    params.x = dragStartViewX + dx.toInt()
                    params.y = dragStartViewY + dy.toInt()
                    updateViewSafely(params)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    saveCurrentPositionAsRatio()
                } else {
                    triggerHapticFeedback()
                }
                isDragging = false
                return true
            }
        }
        return false
    }

    private fun triggerHapticFeedback() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20)
        }
    }

    // ---------------- Thông báo Foreground Service ----------------

    private fun buildNotification(): android.app.Notification {
        val stopIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, GameBoosterApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.notification_stop), stopPendingIntent)
            .build()
    }

    private fun dpToPx(dp: Float): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val TOUCH_SLOP = 12
        const val ACTION_STOP = "com.mrt.gamebooster.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }
}
