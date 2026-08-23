# Pactio — Android

Aplikasi parental-control berbasis tugas. Orang tua membuat tugas → anak mengerjakan &
mengirim bukti → orang tua menyetujui/menolak → anak memperoleh hadiah berupa menit akses.

Aplikasi ini terhubung ke backend nyata di `https://api.patio.my.id`. Tidak ada lagi data
tugas yang disimulasikan secara lokal — semua data (keluarga, anak, tugas, saldo) berasal
dari API.

## Yang sudah berfungsi

- Pendaftaran & login orang tua (`/auth/register-parent`, `/auth/login-parent`).
- Login anak memakai kode keluarga + PIN (`/auth/login-child`).
- Token disimpan aman memakai `EncryptedSharedPreferences` (kunci di Android Keystore),
  **bukan** `SharedPreferences` biasa.
- Orang tua: melihat info keluarga & kode keluarga, menambah profil anak, membuat tugas,
  melihat semua tugas, menyetujui/menolak tugas yang dikirim anak.
- Anak: melihat daftar tugas miliknya, mengirim bukti selesai (teks), melihat saldo menit
  akses yang telah disetujui.
- Sesi otomatis dipulihkan saat aplikasi dibuka ulang (token tersimpan), dan otomatis
  kembali ke layar login kalau server menolak token (HTTP 401) — mis. karena server
  di-restart (token backend masih in-memory, lihat catatan keamanan backend).
- Logout menghapus token dari penyimpanan lokal.

## Yang sengaja BELUM dikerjakan (lihat PRD)

- Kontrol perangkat/aplikasi nyata (App/Device policy). Saldo menit saat ini murni data
  dari backend — belum menegakkan apa pun di perangkat.
- Unggah bukti berupa foto/file — backend saat ini hanya menerima `evidence` berupa teks.
- Refresh token / auto-renew — backend belum punya mekanisme ini (token in-memory tanpa
  expiry), jadi Android hanya menangani penolakan token dengan kembali ke layar login.

## Arsitektur singkat

```
app/src/main/java/com/keluargakendali/
├── MainActivity.kt          # entry point, merutekan Auth/Parent/Child berdasarkan state
├── data/
│   ├── Models.kt             # DTO — field mengikuti persis JSON dari server.js
│   ├── PactioApi.kt          # klien HTTP (HttpsURLConnection + org.json, tanpa Retrofit/OkHttp)
│   ├── ApiException.kt       # error terklasifikasi (Unauthorized/Http/Network/InvalidResponse)
│   └── SecureTokenStore.kt   # penyimpanan token via Android Keystore
└── ui/
    ├── AppViewModel.kt       # satu ViewModel, StateFlow<UiState>, semua panggilan API lewat sini
    ├── AuthScreen.kt         # daftar ortu / masuk ortu / masuk anak
    ├── ParentScreen.kt       # dashboard orang tua
    ├── ChildScreen.kt        # dashboard anak
    └── Components.kt         # StatusChip, ErrorBanner
```

Sengaja **tidak** menambah Retrofit/OkHttp/Gson/Hilt/Navigation-Compose — backend tanpa
dependency eksternal, jadi Android juga dijaga minimal. Dua dependency baru yang ditambah:
`androidx.security:security-crypto` (wajib untuk penyimpanan token aman) dan
`kotlinx-coroutines-android` (dispatcher IO/Main standar).

## Menjalankan / build

Lihat [`BUILD_ANDROID.md`](../BUILD_ANDROID.md) di root repo untuk langkah build APK debug
dan alasan kenapa build tidak bisa dijalankan otomatis di lingkungan Claude Code ini.

## Tahap berikutnya (belum dikerjakan)

1. Unggah bukti foto dengan pemindaian aman (perlu perubahan backend `/tasks/:id/submit`).
2. Refresh token / session expiry di backend, lalu Android mengikuti.
3. Rancang kontrol perangkat yang transparan & butuh persetujuan orang tua (App/Device
   Policy resmi Android), baru dipasang setelah alur Task→Approval→Reward stabil di
   penggunaan nyata.
