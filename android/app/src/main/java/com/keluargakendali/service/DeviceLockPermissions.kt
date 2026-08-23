package com.keluargakendali.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Satu izin khusus yang dibutuhkan Kontrol Perangkat: "Tampil di atas aplikasi lain"
 * (SYSTEM_ALERT_WINDOW), API resmi Android - TIDAK memicu dialog runtime biasa, pengguna
 * wajib membukanya sendiri lewat Pengaturan sistem (transparan, terlihat jelas di "Akses
 * khusus aplikasi", bisa dicabut kapan saja tanpa perlu uninstall).
 */
object DeviceLockPermissions {

    fun hasOverlayPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun overlaySettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
}
