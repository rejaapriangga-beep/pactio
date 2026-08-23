package com.keluargakendali.service

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Sumber kebenaran untuk "apakah Pactio sedang dianggap di depan layar" - dipakai
 * DeviceLockService untuk memutuskan kapan overlay kunci harus disembunyikan.
 *
 * Sengaja TIDAK memakai UsageStatsManager untuk mendeteksi status aplikasi kita sendiri:
 * versi awal fitur ini terbukti nyata dari log gagal - di perangkat tertentu (ColorOS/Oplus),
 * event MOVE_TO_FOREGROUND dari UsageStatsManager bisa telat sampai puluhan detik bahkan
 * untuk aplikasi yang baru saja dibuka sendiri, membuat overlay "Perangkat Terkunci" nyangkut
 * menutupi Pactio padahal usernya sudah benar-benar membukanya. Callback lifecycle Activity
 * resmi (didaftarkan di PactioApplication) instan dan tidak bergantung ke API/perangkat lain.
 */
object AppForegroundState {
    private val resumed = AtomicBoolean(false)
    private val suppressUntilMs = AtomicLong(0L)

    fun setResumed(value: Boolean) {
        resumed.set(value)
    }

    /**
     * Menahan sementara supaya DeviceLockService TIDAK ikut mengunci aplikasi lain,
     * walau Activity Pactio sedang tidak di depan - dipakai saat Pactio SENGAJA
     * mendelegasikan ke aplikasi sistem resmi lewat Activity Result API (contoh: kamera
     * bawaan sistem untuk ambil foto bukti tugas - lihat SubmitEvidenceDialog di
     * ChildScreen), supaya aplikasi delegasi itu tidak ikut ketutup overlay kunci.
     *
     * durationMs adalah jaring pengaman kalau hasilnya ternyata tidak pernah kembali
     * (mis. proses kamera dibunuh sistem) - pemanggil tetap harus memanggil
     * clearSuppression() begitu hasilnya kembali, jangan mengandalkan timeout ini saja.
     */
    fun suppressLockFor(durationMs: Long) {
        suppressUntilMs.set(System.currentTimeMillis() + durationMs)
    }

    fun clearSuppression() {
        suppressUntilMs.set(0L)
    }

    val isForeground: Boolean
        get() = resumed.get() || System.currentTimeMillis() < suppressUntilMs.get()
}
