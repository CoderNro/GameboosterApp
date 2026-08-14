package com.mrt.gamebooster

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class GameBoosterApp : Application() {

    companion object {
        const val CHANNEL_ID = "game_booster_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * Foreground Service bắt buộc phải có kênh thông báo từ Android 8.0 (API 26) trở lên.
     * Đặt IMPORTANCE_LOW để icon hiện nhưng không kêu/rung, tránh làm phiền khi chơi game.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
