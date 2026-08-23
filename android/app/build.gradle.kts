plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.keluargakendali"
    compileSdk = 37

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
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
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
