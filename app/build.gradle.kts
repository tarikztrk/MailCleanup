plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.tarik.mailcleanup"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tarik.mailcleanup"
        minSdk = 27
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
    packaging {
        resources {
            pickFirsts.add("META-INF/DEPENDENCIES")
        }
    }
}

dependencies {

    // Mevcut kütüphaneler...
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // === MVVM & Lifecycle Kütüphaneleri ===

    implementation("androidx.activity:activity-ktx:1.10.1")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    // LiveData
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    // Lifecycle (Activity/Fragment yaşam döngüsü için)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // === Asenkron İşlemler için Coroutines ===
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // === Google API Kütüphaneleri ===
    // Google ile Güvenli Giriş (OAuth 2.0)
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    // Gmail API
    implementation("com.google.api-client:google-api-client-android:2.2.0")
    implementation("com.google.apis:google-api-services-gmail:v1-rev20220404-2.0.0")

    // (Not: Gmail API versiyonları değişebilir, en güncellerini kullanmak iyi bir pratiktir.)
}