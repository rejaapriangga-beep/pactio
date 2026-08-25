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

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Default "id" - aplikasi ini dibuat untuk keluarga Indonesia, Inggris opsional. */
    fun getLanguage(context: Context): String = prefs(context).getString(KEY_LANGUAGE, "id") ?: "id"

    fun setLanguage(context: Context, language: String) {
        prefs(context).edit().putString(KEY_LANGUAGE, language).apply()
    }

    fun isDarkMode(context: Context): Boolean = prefs(context).getBoolean(KEY_DARK_MODE, false)

    fun setDarkMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }
}
