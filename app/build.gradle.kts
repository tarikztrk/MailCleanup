plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.devtools.ksp") version "1.9.23-1.0.19" // Versiyonu kontrol et
}

android {
    namespace = "com.tarik.mailcleanup"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tarik.mailcleanup"
        minSdk = 27
        targetSdk = 35
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
    implementation("com.google.apis:google-api-services-gmail:v1-rev20220404-2.0.0")
    
    // Google API Client Core ve Android Extensions
    implementation("com.google.api-client:google-api-client-android:2.2.0")
    implementation("com.google.api-client:google-api-client-gson:2.2.0")
    
    // HTTP Client kütüphaneleri
    implementation("com.google.http-client:google-http-client-gson:1.44.1")
    implementation("com.google.http-client:google-http-client-android:1.44.1")
    
    // Google OAuth2 ve Auth kütüphaneleri
    implementation("com.google.auth:google-auth-library-oauth2-http:1.19.0")

    // (Not: Gmail API versiyonları değişebilir, en güncellerini kullanmak iyi bir pratiktir.)

    implementation("androidx.fragment:fragment-ktx:1.6.2")

    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    ksp("androidx.room:room-compiler:$room_version") // Annotation processor
    // Coroutine desteği için
    implementation("androidx.room:room-ktx:$room_version")


}