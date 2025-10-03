plugins {
    // Core Android plugins
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")

    // Dependency Injection
    id("com.google.dagger.hilt.android")

    // Jetpack Compose (to be removed later)
    alias(libs.plugins.kotlin.compose)

    // Navigation
    id("androidx.navigation.safeargs.kotlin")

    // Google Services
    id("com.google.gms.google-services")
}

val placesApiKey: String = project.findProperty("PLACES_API_KEY") as? String ?: "ERROR_NO_API_KEY_SET"
if (placesApiKey == "ERROR_NO_API_KEY_SET") {
    println("WARNING: PLACES_API_KEY is not set! Please check your gradle.properties or environment variables.")
}

android {
    namespace = "com.synaptix.capetowncoffees"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.synaptix.capetowncoffees"
        // minSdk increased from 24 to 26 to support features/libraries that require Android 8.0 (API 26) or higher.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true // Will be removed after fixing usages
        viewBinding = true
        buildConfig = true
        dataBinding = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "PLACES_API_KEY", "\"$placesApiKey\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "PLACES_API_KEY", "\"$placesApiKey\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        @Suppress("DEPRECATION")
        jvmTarget = "11"
    }
}

dependencies {
    // --- Core Android ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.core.splashscreen)

    // --- Dependency Injection (Hilt) ---
    implementation(libs.hilt.android)
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.core.animation)
    kapt(libs.hilt.android.compiler)

    // --- Places API (SDK and Web) ---
    implementation(libs.places)
    implementation(libs.places.ktx)

    // Play Services - location & maps
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("com.google.android.gms:play-services-maps:18.2.0")

    // --- Material Design ---
    implementation(libs.androidx.material)
    implementation(libs.androidx.material3.android)
    implementation("com.google.android.material:material:1.8.0")

    // --- Kotlin Coroutines ---
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // --- Lifecycle & ViewModel ---
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    // --- Navigation Component ---
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // --- Firebase ---
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // Storage for profile photo uploads
    implementation("com.google.firebase:firebase-storage")

    implementation(libs.androidx.ui.graphics.android)
    implementation(libs.androidx.foundation.android)


    // --- Optional helpers ---
    implementation("de.hdodenhof:circleimageview:3.1.0") // Circle Image View

    // --- Image loading ---
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // --- Testing & Logging ---
    implementation(libs.timber)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}