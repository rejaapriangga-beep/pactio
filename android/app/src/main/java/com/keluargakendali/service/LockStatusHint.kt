package com.keluargakendali.service

import java.util.concurrent.atomic.AtomicLong

/**
 * Info "unlockUntil" versi lokal, di-set INSTAN begitu anak berhasil menukar saldo lewat
 * POST /access-balance/redeem (lihat AppViewModel.redeemAccessBalance) - supaya
 * DeviceLockService tidak perlu menunggu siklus poll status berikutnya (sampai
 * STATUS_POLL_MS, ~15 detik) untuk tahu kuncinya sudah harus terbuka.
 *
 * Server tetap sumber kebenaran jangka panjang (tetap dipoll berkala seperti biasa via
 * GET /lock-status) - ini cuma percepatan untuk kasus perangkat yang sama baru saja
 * melakukan aksinya sendiri, supaya tidak ada jeda yang terasa seperti bug.
 */
object LockStatusHint {
    private val unlockUntilMs = AtomicLong(0L)

    fun setUnlockUntil(value: Long) {
        unlockUntilMs.set(value)
    }

    val isActive: Boolean
        get() = System.currentTimeMillis() < unlockUntilMs.get()
}
