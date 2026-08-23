# Build APK Debug — Pactio Android

## ✅ Status: build APK debug BERHASIL (terverifikasi nyata di CI)

GitHub Actions run [#5](https://github.com/rejaapriangga-beep/pactio/actions/runs/32631827679)
selesai dengan sukses dan menghasilkan artifact `pactio-debug-apk` (18.6 MB). Perjalanan
sampai ke sana melewati 4 kegagalan nyata yang masing-masing diperbaiki berdasarkan pesan
error asli (bukan tebakan) — riwayatnya didokumentasikan di bagian bawah untuk referensi:

| Run | Masalah nyata | Perbaikan |
|---|---|---|
| #1 | `platforms;android-37` tidak ditemukan | `compileSdk` 37→36 |
| #2 | Gradle 8.14.3 < minimum AGP 9.0.1 (butuh 9.1.0) | Gradle wrapper 8.14.3→9.1.0 |
| #3 | `Unresolved reference 'ExposedDropdownMenu'` + OOM daemon | Ganti ke `DropdownMenu` biasa + `gradle.properties` batasi memori |
| #4 | Compose 1.12.0 (BOM 2026.08.00) minta `compileSdk 37`+AGP 9.1.0 lagi | Compose BOM diturunkan ke `2026.04.01` |
| **#5** | **—** | **✅ BUILD SUCCESSFUL** |

Cara ambil APK-nya: lihat bagian **GitHub Actions** di bawah.

## Kenapa build tidak bisa dijalankan langsung di sesi Claude Code ini

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

## Cara build tanpa Android Studio DAN tanpa install SDK apa pun — GitHub Actions (paling mudah)

Repo ini punya workflow otomatis di `.github/workflows/android-build.yml`. Setiap kali ada
push ke branch `main` yang menyentuh folder `android/`, GitHub akan membangun APK debug di
servernya sendiri (bukan di laptop kamu, jadi tidak perlu install SDK/JDK apa pun).

**Cara pakai:**

1. Buka repo di browser: https://github.com/rejaapriangga-beep/pactio/actions
2. Klik workflow **"Android Build"** di daftar sebelah kiri.
3. Kalau belum pernah jalan otomatis (misal belum ada perubahan baru di `android/`), klik
   tombol **"Run workflow"** di kanan atas → pilih branch `main` → **Run workflow**.
4. Tunggu sampai selesai (ikon kuning berputar → hijau ✅, biasanya beberapa menit).
5. Klik run yang sudah selesai → scroll ke bagian **Artifacts** di bawah → unduh
   **`pactio-debug-apk`** (berupa file `.zip` berisi `app-debug.apk`).
6. Extract zip-nya, pindahkan `app-debug.apk` ke HP, install seperti biasa (aktifkan
   **Instal aplikasi tidak dikenal** untuk aplikasi yang kamu pakai memindahkan file).

> Riwayat lengkap kegagalan-perbaikan workflow ini ada di tabel pada bagian atas dokumen ini.
> Sejak run #5, build berjalan sukses dan artifact selalu tersedia untuk diunduh.

## Cara build lewat command line di laptop sendiri (tanpa Android Studio)

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
