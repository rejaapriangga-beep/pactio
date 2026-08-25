package com.keluargakendali.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Override bahasa aplikasi secara manual, terlepas dari bahasa sistem HP - dipakai supaya ikon
 * ganti bahasa di dalam aplikasi benar-benar berlaku instan, tanpa perlu pengguna ubah bahasa
 * sistem Android-nya. Pendekatan attachBaseContext + createConfigurationContext ini jalan di
 * semua versi Android dari minSdk 26 (BUKAN API AppCompatDelegate.setApplicationLocales() yang
 * baru resmi didukung penuh dari API 33 - aplikasi ini juga tidak memakai AppCompatActivity).
 */
object LocaleHelper {
    /** Dipanggil dari MainActivity.attachBaseContext, SEBELUM Activity selesai dibuat. */
    fun wrap(context: Context): Context {
        val language = SettingsStore.getLanguage(context)
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
