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

    buildFeatures {
        compose = true
    }

    defaultConfig {
        applicationId = "com.keluargakendali"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
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
