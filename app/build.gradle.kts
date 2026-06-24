plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tiramisu.deepseekwidget"
    compileSdk = 34



    defaultConfig {
        applicationId = "com.tiramisu.deepseekwidget"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")

    // AppCompat (for Config Activity)
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Activity (for lifecycleScope)
    implementation("androidx.activity:activity-ktx:1.8.2")

    // AppWidget
    implementation("androidx.glance:glance-appwidget:1.0.0")

    // WorkManager for periodic updates
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // HTTP client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Encrypted storage for API key
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
