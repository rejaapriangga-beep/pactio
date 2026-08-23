# Build APK Debug — Pactio Android

## Kenapa build tidak dijalankan otomatis di sesi Claude Code ini

Sudah dicoba secara nyata (bukan asumsi): sesi ini punya JDK 21 dan Gradle 8.14.3, tapi
**tidak punya Android SDK**, dan jaringan sesi ini **memblokir `dl.google.com` /
`maven.google.com`** (403 di level proxy) — yaitu sumber wajib untuk mengunduh Android
Gradle Plugin (AGP) dan seluruh library AndroidX/Jetpack Compose. Percobaan build asli:

```
$ ./gradlew :app:assembleDebug
...
* What went wrong:
Plugin [id: 'com.android.application', version: '9.0.1', apply: false] was not found in
any of the following sources:
  ...
  Searched in the following repositories:
    Google
    MavenRepo
    Gradle Central Plugin Repository
BUILD FAILED in 7s
```

`repo.maven.apache.org` dan `services.gradle.org` bisa diakses (itu sebabnya Gradle
wrapper berhasil dibuat), tapi itu tidak cukup untuk project Android. Karena itu APK debug
**belum bisa dibuktikan berhasil dibangun dari sesi ini** — bukan karena kode salah,
tapi karena sandbox ini tidak punya akses ke server Google.

## Cara build di komputer kamu (Android Studio) — direkomendasikan

1. Install [Android Studio](https://developer.android.com/studio) terbaru (sudah termasuk
   JDK & Android SDK).
2. `git clone https://github.com/rejaapriangga-beep/pactio.git`
3. Buka folder `android/` sebagai project di Android Studio (**File → Open**, pilih folder
   `android`, bukan root repo).
4. Biarkan Android Studio melakukan Gradle sync pertama kali (mengunduh AGP + AndroxX +
   Compose — perlu koneksi internet normal, tidak lewat proxy terbatas seperti sesi ini).
5. Sambungkan HP Android (Developer Options + USB debugging aktif) atau siapkan emulator
   API 26+.
6. Klik **Run ▶** pada konfigurasi `app`, atau lewat terminal:
   ```
   cd android
   ./gradlew :app:assembleDebug
   ```
7. APK debug akan ada di:
   ```
   android/app/build/outputs/apk/debug/app-debug.apk
   ```

## Cara build lewat command line saja (tanpa Android Studio)

Perlu Android SDK command-line tools terpasang dan `ANDROID_HOME` diarahkan ke situ (lihat
[panduan resmi](https://developer.android.com/tools/sdkmanager)), lalu jalankan perintah
`./gradlew :app:assembleDebug` di atas dari folder `android/`.

## Setelah APK terinstal di HP — langkah uji sederhana

1. **Sebagai orang tua**: buka aplikasi → tab **Daftar Ortu** → isi nama keluarga, nama,
   email, kata sandi (≥8 karakter) → **Daftarkan Keluarga**.
2. Setelah masuk, catat **kode keluarga** yang tampil di kartu ringkasan keluarga.
3. Klik **Tambah Anak** → isi nama & PIN 4-8 digit.
4. Klik **Buat Tugas** → pilih anak, isi judul & menit hadiah → **Buat Tugas**.
5. **Keluar** (tombol di kanan atas), lalu masuk lagi lewat tab **Masuk Anak** memakai kode
   keluarga + PIN yang tadi dibuat (bisa di HP yang sama untuk uji cepat, atau HP anak yang
   berbeda).
6. Sebagai anak: buka tugas yang muncul → **Kirim sebagai selesai** → isi bukti teks →
   **Kirim**.
7. **Keluar**, masuk lagi sebagai orang tua (email/kata sandi tadi) → lihat tugas di bagian
   **Menunggu persetujuan** → **Setujui**.
8. Masuk lagi sebagai anak → kartu **Saldo akses hadiah** harus bertambah sesuai menit
   hadiah tugas tadi.
9. Coba juga skenario gagal: masukkan kata sandi salah, PIN salah, atau matikan Wi-Fi/data
   seluler saat memuat — pesan error dari `ErrorBanner` harus muncul, aplikasi tidak boleh
   crash.

Kalau semua langkah di atas berhasil, alur inti **Task → Submission → Parent Approval →
Reward Balance** sudah terbukti berjalan nyata lewat `https://api.patio.my.id`, sesuai
prioritas PRD sebelum kontrol perangkat mulai dirancang.
