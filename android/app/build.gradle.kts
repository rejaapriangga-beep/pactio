plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.keluargakendali"
    // Sempat 37 di source awal, tapi API level itu belum dipublikasikan Google
    // (dibuktikan gagal di CI: "Failed to find package 'platforms;android-37'").
    // Diturunkan ke 36 agar sama dengan targetSdk — cukup dan valid untuk semua
    // fitur yang dipakai project ini.
    compileSdk = 36

    // Nama file APK/AAB hasil build - default AGP pakai nama modul ("app"), jadi tanpa ini
    // hasilnya selalu app-debug.apk/app-release.aab. Diganti supaya nama filenya sendiri
    // juga "TimeCraft", bukan cuma android:label di dalam aplikasi.
    base {
        archivesName = "TimeCraft"
    }

    buildFeatures {
        compose = true
    }

    defaultConfig {
        // applicationId (identitas unik di Play Store) SENGAJA dibedakan dari namespace di atas
        // (com.keluargakendali, tetap dipakai sebagai nama package Kotlin/R class - AGP modern
        // memisahkan keduanya, jadi ini TIDAK mengharuskan rename package di semua berkas .kt).
        // Ini keputusan permanen - applicationId tidak bisa diubah lagi setelah upload pertama
        // ke Play Console. "com.timecraft" ternyata sudah dipakai developer lain di Play Store
        // (nama package unik secara global) - diganti ke kebalikan domain yang BENAR-BENAR
        // dimiliki pengguna (timecraft.my.id), supaya hampir pasti tidak bentrok lagi.
        applicationId = "id.my.timecraft"
        minSdk = 26
        targetSdk = 36
        // Naik dari 1 -> 2 -> 5 -> 6 -> 7 -> 8 -> 9 -> 10 -> 11 -> sekarang 12 - build ini
        // memperluas tur coach-mark Dashboard (TutorialOverlay.kt) dari 4 jadi 12 langkah:
        // tiap tab disorot satu-satu (bukan cuma baris tab-nya), ditambah 4 ikon TopAppBar
        // (bahasa, tema, backup, Pengaturan).
        versionCode = 12
        versionName = "0.1.0"
    }

    // Debug keystore TETAP (disimpan di repo, bukan di-generate ulang tiap build).
    // Tanpa ini, setiap build APK debug di CI (runner GitHub selalu fresh/sekali pakai)
    // otomatis pakai keystore acak baru -> APK baru tidak bisa "update" menimpa yang lama
    // di HP, harus uninstall dulu (terbukti nyata: INSTALL_FAILED_UPDATE_INCOMPATIBLE).
    // Ini debug key biasa (bukan release/production), aman disimpan di repo publik —
    // sama seperti debug.keystore default Android Studio yang juga dibagi banyak developer.
    //
    // Keystore rilis (upload key Google Play) SENGAJA TIDAK disimpan di repo maupun
    // di-hardcode di sini — kredensialnya dibaca dari environment variable saat build.
    // Di CI, keystore-nya sendiri didekode dari secret base64 langsung ke file sementara
    // (lihat android-build.yml), passwordnya juga dari secret. Kalau env var ini tidak
    // ada (mis. build lokal biasa tanpa niat rilis), build type "release" otomatis jatuh
    // ke signing config debug supaya tetap bisa di-build untuk testing.
    val releaseKeystorePath = System.getenv("TIMECRAFT_RELEASE_KEYSTORE_PATH")

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("TIMECRAFT_RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TIMECRAFT_RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("TIMECRAFT_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    // Compose BOM 2026.08.00 (Compose 1.12.0) mensyaratkan compileSdk 37 + AGP >=9.1.0
    // secara internal (bukan aturan project ini) - dibuktikan gagal nyata di CI:
    //   "requires ... compile against version 37 or later"
    //   "requires Android Gradle plugin 9.1.0 or higher"
    // Diturunkan ke 2026.04.01 (rilis April 2026, sebelum syarat compileSdk 37 berlaku)
    // agar cocok dengan compileSdk 36 / AGP 9.0.1 yang sudah terbukti jalan di CI.
    val composeBom = platform("androidx.compose:compose-bom:2026.04.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    // Satu-satunya dua dependency baru untuk integrasi backend:
    // - security-crypto: penyimpanan token terenkripsi berbasis Android Keystore (wajib, sesuai PRD).
    // - coroutines-android: dispatcher IO/Main standar untuk panggilan jaringan di ViewModel.
    // Networking sendiri sengaja TIDAK memakai Retrofit/OkHttp/Gson (lihat PactioApi.kt) —
    // cukup HttpsURLConnection + org.json bawaan platform, konsisten dengan backend yang
    // juga tanpa dependency eksternal.
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
}
