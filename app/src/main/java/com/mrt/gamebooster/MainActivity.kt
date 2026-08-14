package com.mrt.gamebooster

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.mrt.gamebooster.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            toast(getString(R.string.msg_notification_permission_needed))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)

        requestNotificationPermissionIfNeeded()
        setupZoneControls()
        setupBoosterControls()
        setupActionButtons()
    }

    override fun onResume() {
        super.onResume()
        // Cập nhật lại trạng thái nút cấp quyền mỗi khi quay lại app (sau khi user
        // vào Settings cấp quyền rồi bấm Back).
        refreshPermissionStatus()
    }

    // ---------------- Vùng cố định (Fixed Touch Zone) ----------------

    private fun setupZoneControls() {
        binding.switchZoneEnabled.isChecked = prefs.zoneEnabled
        binding.switchZoneLocked.isChecked = prefs.zoneLocked
        binding.sliderZoneSize.value = prefs.zoneSizeDp

        binding.switchZoneEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !hasOverlayPermission()) {
                binding.switchZoneEnabled.isChecked = false
                requestOverlayPermission()
                return@setOnCheckedChangeListener
            }
            prefs.zoneEnabled = isChecked
            restartServiceIfRunning()
        }

        binding.switchZoneLocked.setOnCheckedChangeListener { _, isChecked ->
            prefs.zoneLocked = isChecked
            toast(
                if (isChecked) getString(R.string.msg_zone_locked)
                else getString(R.string.msg_zone_unlocked)
            )
        }

        binding.sliderZoneSize.addOnChangeListener { _, value, _ ->
            prefs.zoneSizeDp = value
        }
    }

    // ---------------- Tối ưu hiệu năng (Booster) ----------------

    private fun setupBoosterControls() {
        binding.switchKillBg.isChecked = prefs.boosterKillBackgroundApps
        binding.switchDnd.isChecked = prefs.boosterEnableDnd
        binding.switchKeepScreenOn.isChecked = prefs.boosterKeepScreenOn
        binding.switchPerformanceMode.isChecked = prefs.boosterPerformanceMode

        binding.switchKillBg.setOnCheckedChangeListener { _, isChecked ->
            prefs.boosterKillBackgroundApps = isChecked
        }
        binding.switchDnd.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !hasDndPermission()) {
                binding.switchDnd.isChecked = false
                requestDndPermission()
                return@setOnCheckedChangeListener
            }
            prefs.boosterEnableDnd = isChecked
        }
        binding.switchKeepScreenOn.setOnCheckedChangeListener { _, isChecked ->
            prefs.boosterKeepScreenOn = isChecked
        }
        binding.switchPerformanceMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.boosterPerformanceMode = isChecked
        }
    }

    // ---------------- Nút Bắt đầu / Dừng ----------------

    private fun setupActionButtons() {
        binding.btnStart.setOnClickListener {
            if (!hasOverlayPermission() && prefs.zoneEnabled) {
                requestOverlayPermission()
                return@setOnClickListener
            }
            OverlayService.start(this)
            toast(getString(R.string.msg_service_started))
        }

        binding.btnStop.setOnClickListener {
            OverlayService.stop(this)
            toast(getString(R.string.msg_service_stopped))
        }

        binding.btnGrantOverlay.setOnClickListener { requestOverlayPermission() }
    }

    private fun restartServiceIfRunning() {
        // Khởi động lại service để overlay ẩn/hiện ngay lập tức phản ánh thay đổi switch,
        // thay vì phải chờ user tự bấm Start lại.
        OverlayService.start(this)
    }

    // ---------------- Quyền: Overlay ----------------

    private fun hasOverlayPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            toast(getString(R.string.msg_grant_overlay_instruction))
        }
    }

    // ---------------- Quyền: Do Not Disturb access ----------------

    private fun hasDndPermission(): Boolean {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    private fun requestDndPermission() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        startActivity(intent)
        toast(getString(R.string.msg_grant_dnd_instruction))
    }

    // ---------------- Quyền: Thông báo (Android 13+) ----------------

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun refreshPermissionStatus() {
        binding.tvOverlayStatus.text = if (hasOverlayPermission()) {
            getString(R.string.status_granted)
        } else {
            getString(R.string.status_not_granted)
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
