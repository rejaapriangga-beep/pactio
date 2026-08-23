package com.keluargakendali.service

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sumber kebenaran untuk "apakah Activity Pactio (MainActivity) sedang di depan layar" -
 * dipakai DeviceLockService untuk memutuskan kapan overlay kunci harus disembunyikan.
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

    fun setResumed(value: Boolean) {
        resumed.set(value)
    }

    val isForeground: Boolean
        get() = resumed.get()
}
