package com.keluargakendali.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.keluargakendali.R

/**
 * Penerima Device Admin KHUSUS untuk proteksi anti-uninstall - BUKAN kontrol perangkat umum
 * (itu tetap lewat DeviceLockService/SYSTEM_ALERT_WINDOW seperti sebelumnya, lihat komentar di
 * AndroidManifest.xml). Satu-satunya efek nyata mengaktifkan Device Admin di sini: Android
 * TIDAK MENGIZINKAN aplikasi ini di-uninstall langsung selama admin ini masih aktif - anak
 * harus tahu caranya masuk ke Settings > Security > Device admin apps dan menonaktifkannya
 * dulu, langkah yang jauh lebih sulit ditemukan tanpa sengaja dibanding tombol "Uninstall"
 * biasa dari layar utama/App info.
 *
 * TETAP TRANSPARAN & TIDAK PERNAH mencegah pencabutan sepenuhnya - siapa pun yang punya akses
 * ke Settings sistem perangkat tetap BISA menonaktifkannya kapan saja (itu pengaman anti-
 * penyalahgunaan bawaan platform Android sendiri, bukan celah). Ini murni "speed bump" resmi
 * lewat API platform, sesuai batasan PRD: kontrol perangkat harus transparan & memakai API
 * resmi, bukan mekanisme tersembunyi.
 *
 * Tidak memakai satu pun kemampuan device-admin sungguhan (lihat res/xml/device_admin_receiver.xml -
 * uses-policies-nya sengaja kosong): tidak wipe data, tidak paksa kebijakan PIN, tidak nonaktifkan
 * kamera, dst. Registrasi ini murni untuk efek samping resmi "tidak bisa langsung di-uninstall".
 */
class UninstallProtectionAdminReceiver : DeviceAdminReceiver() {

    /**
     * Pesan peringatan yang ditampilkan SISTEM (bukan dialog TimeCraft) tepat sebelum admin
     * ini benar-benar dinonaktifkan - satu-satunya "penghalang" tambahan yang bisa kita berikan
     * lewat API resmi ini (bukan blokir sungguhan, Android tidak mengizinkan aplikasi pihak
     * ketiga mencegah pencabutan device admin oleh pemegang perangkat).
     */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return context.getString(R.string.warning_device_admin_disable)
    }
}
