package com.keluargakendali.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Penyimpanan token & data sesi memakai EncryptedSharedPreferences, yang mengunci kunci
 * enkripsinya di Android Keystore. Sesuai aturan proyek: token TIDAK PERNAH disimpan
 * sebagai teks biasa di SharedPreferences biasa, dan tidak pernah dicetak ke log.
 */
class SecureTokenStore(context: Context) {

    private val prefs = run {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "pactio_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(token: String, user: UserDto) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, user.id)
            .putString(KEY_USER_ROLE, user.role)
            .putString(KEY_USER_NAME, user.name)
            .putString(KEY_USER_FAMILY_ID, user.familyId)
            .apply()
    }

    fun loadToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun loadUser(): UserDto? {
        val id = prefs.getString(KEY_USER_ID, null) ?: return null
        val role = prefs.getString(KEY_USER_ROLE, null) ?: return null
        val name = prefs.getString(KEY_USER_NAME, null) ?: return null
        val familyId = prefs.getString(KEY_USER_FAMILY_ID, null) ?: return null
        return UserDto(id = id, role = role, name = name, familyId = familyId)
    }

    /** Dipanggil saat logout ATAU saat server menolak token (HTTP 401). */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_USER_ID = "user_id"
        const val KEY_USER_ROLE = "user_role"
        const val KEY_USER_NAME = "user_name"
        const val KEY_USER_FAMILY_ID = "user_family_id"
    }
}
