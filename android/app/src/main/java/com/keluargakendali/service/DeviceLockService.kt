package com.keluargakendali.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.keluargakendali.data.PactioApi
import com.keluargakendali.data.SecureTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Menegakkan "Mode Kunci" yang dinyalakan orang tua: kalau aktif dan aplikasi yang sedang
 * dibuka BUKAN Pactio, tampilkan layar penuh "Terkunci" di atasnya.
 *
 * Cuma jalan kalau kedua izin khusus (Usage Access + Display over other apps) sudah
 * diberikan MANUAL oleh pengguna lewat Pengaturan - lihat DeviceLockPermissions &
 * ChildScreen. Notifikasi persisten di bawah ini sengaja SELALU tampil selama service
 * jalan, supaya transparan (anak/siapa pun yang pegang HP tahu persis kalau ini aktif).
 * TIDAK memakai accessibility service, root, atau device admin/owner.
 */
class DeviceLockService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, buildNotification())
        pollJob = scope.launch { pollLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        pollJob?.cancel()
        removeOverlay()
        super.onDestroy()
    }

    private suspend fun pollLoop() {
        val tokenStore = SecureTokenStore(applicationContext)
        var locked = false
        var lastStatusCheckAt = 0L

        while (true) {
            val token = tokenStore.loadToken()
            if (token == null) {
                // Tidak ada sesi login (sudah logout) - jangan mengunci apa pun.
                removeOverlay()
                delay(STATUS_POLL_MS)
                continue
            }

            val now = System.currentTimeMillis()
            if (now - lastStatusCheckAt >= STATUS_POLL_MS) {
                locked = runCatching { PactioApi.getLockStatus(token) }.getOrDefault(locked)
                lastStatusCheckAt = now
            }

            if (locked && !isPactioForeground()) showOverlay() else removeOverlay()

            delay(if (locked) FOREGROUND_POLL_MS else STATUS_POLL_MS)
        }
    }

    /** Dicek lewat UsageStatsManager (butuh izin Usage Access) - bukan accessibility service. */
    private fun isPactioForeground(): Boolean {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return false
        val end = System.currentTimeMillis()
        val begin = end - USAGE_LOOKBACK_MS
        val events = usageStatsManager.queryEvents(begin, end) ?: return false
        var lastForegroundPackage: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastForegroundPackage = event.packageName
            }
        }
        return lastForegroundPackage == packageName
    }

    private fun showOverlay() {
        if (overlayView != null) return
        val context = this

        val view = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#E61B1B1B"))
            setPadding(64, 64, 64, 64)

            addView(TextView(context).apply {
                text = "Perangkat Terkunci"
                setTextColor(Color.WHITE)
                textSize = 24f
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = "Orang tua sedang mengaktifkan mode kunci. Buka Pactio untuk melihat tugas dan saldo hadiahmu."
                setTextColor(Color.WHITE)
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(0, 32, 0, 32)
            })
            addView(Button(context).apply {
                text = "Buka Pactio"
                setOnClickListener {
                    packageManager.getLaunchIntentForPackage(packageName)?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(it)
                    }
                }
            })
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        runCatching { windowManager?.addView(view, params) }.onSuccess { overlayView = view }
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        runCatching { windowManager?.removeView(view) }
        overlayView = null
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(CHANNEL_ID, "Kontrol Perangkat", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Pactio - Kontrol Perangkat aktif")
            .setContentText("Memantau status kunci dari orang tua.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 4201
        private const val CHANNEL_ID = "device_lock"
        private const val STATUS_POLL_MS = 15_000L
        private const val FOREGROUND_POLL_MS = 1_000L
        private const val USAGE_LOOKBACK_MS = 10_000L
    }
}
