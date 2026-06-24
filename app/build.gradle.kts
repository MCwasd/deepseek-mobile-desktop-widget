plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 签名配置（CI 中生成 keystore.properties，本地构建可手动创建）
val keystorePropertiesFile = file("keystore.properties")
val keystoreProperties = java.util.Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.tiramisu.deepseekwidget"
    compileSdk = 34

    signingConfigs {
        create("release") {
            storeFile = keystorePropertiesFile.parentFile.resolve(
                keystoreProperties.getProperty("storeFile") ?: "release.jks"
            )
            storePassword = keystoreProperties.getProperty("storePassword") ?: "android"
            keyAlias = keystoreProperties.getProperty("keyAlias") ?: "release"
            keyPassword = keystoreProperties.getProperty("keyPassword") ?: "android"
        }
    }




    defaultConfig {
        applicationId = "com.tiramisu.deepseekwidget"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
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

    // Fix: security-crypto 的 Tink 依赖需要 error_prone_annotations
    implementation("com.google.errorprone:error_prone_annotations:2.26.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
