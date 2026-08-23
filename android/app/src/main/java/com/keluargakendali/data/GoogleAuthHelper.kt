package com.keluargakendali.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Login Google lewat Credential Manager — API resmi AndroidX yang menggantikan
 * GoogleSignInClient (deprecated). Menampilkan pemilih akun bawaan sistem Android;
 * tidak ada kredensial yang disimpan atau diambil di luar dialog resmi ini.
 * Token ID mentah yang dihasilkan HARUS diverifikasi ulang di backend (lihat
 * verifyGoogleIdToken di server.js) — Android tidak pernah mempercayai token ini sendiri.
 */
object GoogleAuthHelper {

    suspend fun requestIdToken(context: Context): String {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = CredentialManager.create(context).getCredential(context, request)

        val credential = response.credential
        require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Jenis kredensial dari Google tidak dikenali."
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
}
