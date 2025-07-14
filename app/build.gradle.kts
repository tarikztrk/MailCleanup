plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.tarik.mailcleanup"
    compileSdk = 34 // 35 yerine stabil 34 kullanalım

    defaultConfig {
        applicationId = "com.tarik.mailcleanup"
        minSdk = 27
        targetSdk = 34 // compileSdk ile aynı olmalı
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // Çoğu kütüphane ile en uyumlu versiyonlar
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
    }

    // Tek ve doğru paketleme bloğu
    packaging {
        resources {
            excludes += "/META-INF/**"
        }
    }
}

dependencies {

    // TEMEL ANDROIDX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity)
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.browser:browser:1.8.0")

    // LIFECYCLE & VIEWMODEL
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // COROUTINES
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ROOM
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // --- GOOGLE & GMAIL KÜTÜPHANELERİ (SADELEŞTİRİLMİŞ) ---
    // Sadece bu 3 satır gerekli. Diğerleri (http, gson, auth) bunlar tarafından dolaylı olarak eklenir.
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.api-client:google-api-client-android:2.4.0")
    implementation("com.google.apis:google-api-services-gmail:v1-rev20220404-2.0.0")
    // ----------------------------------------------------

    // E-POSTA GÖNDERME (javax.mail)
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")

    // TEST
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}