package com.keluargakendali

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.keluargakendali.service.AppForegroundState

/**
 * Mendaftarkan callback lifecycle Activity resmi Android sekali di level aplikasi, supaya
 * DeviceLockService tahu SECARA INSTAN kapan Activity Pactio (MainActivity) sedang di depan
 * layar - lihat AppForegroundState untuk alasan kenapa ini dipakai (bukan UsageStatsManager)
 * untuk mendeteksi aplikasi kita sendiri.
 */
class PactioApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) = AppForegroundState.setResumed(true)
            override fun onActivityPaused(activity: Activity) = AppForegroundState.setResumed(false)
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
