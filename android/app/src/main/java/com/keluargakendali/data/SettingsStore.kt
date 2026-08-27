package com.keluargakendali.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Preferensi tampilan lokal per-perangkat: bahasa dan mode gelap. SharedPreferences biasa
 * (BUKAN EncryptedSharedPreferences seperti token sesi) karena ini bukan data sensitif -
 * cuma pilihan tampilan, sama seperti pengaturan sistem biasa.
 */
object SettingsStore {
    private const val PREFS_NAME = "timecraft_settings"
    private const val KEY_LANGUAGE = "language" // "id" atau "en"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_SEEN_PARENT_TUTORIAL = "seen_parent_tutorial"

    // TIDAK pakai context.applicationContext di sini dengan sengaja - fungsi ini dipanggil dari
    // Application.attachBaseContext() (lewat LocaleHelper), dan pada tahap SEDINI itu
    // applicationContext BELUM tersedia (masih null) di banyak versi Android, bikin
    // NullPointerException saat aplikasi baru dibuka. context itu sendiri (Application/Activity/
    // Service apa pun yang memanggil) sudah valid dipakai langsung untuk getSharedPreferences.
    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Default "id" - aplikasi ini dibuat untuk keluarga Indonesia, Inggris opsional. */
    fun getLanguage(context: Context): String = prefs(context).getString(KEY_LANGUAGE, "id") ?: "id"

    fun setLanguage(context: Context, language: String) {
        prefs(context).edit().putString(KEY_LANGUAGE, language).apply()
    }

    fun isDarkMode(context: Context): Boolean = prefs(context).getBoolean(KEY_DARK_MODE, false)

    fun setDarkMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    /** Tur coach-mark Dashboard orang tua (lihat TutorialOverlay.kt) - tampil otomatis sekali per perangkat, bisa diulang manual lewat Pengaturan. */
    fun hasSeenParentTutorial(context: Context): Boolean = prefs(context).getBoolean(KEY_SEEN_PARENT_TUTORIAL, false)

    fun setSeenParentTutorial(context: Context, seen: Boolean) {
        prefs(context).edit().putBoolean(KEY_SEEN_PARENT_TUTORIAL, seen).apply()
    }
}
