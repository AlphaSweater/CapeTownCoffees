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

android {
    namespace = "com.example.capetowncoffees"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.capetowncoffees"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true // Will be removed after fixing usages
        viewBinding = true
        buildConfig = true
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
}

dependencies {
    // --- Core Android ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.core.splashscreen)

    // --- Material Design ---
    implementation(libs.androidx.material)
    implementation(libs.androidx.material3.android)

    // --- Jetpack Lifecycle & ViewModel ---
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    // --- Navigation Component ---
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // --- Dependency Injection (Hilt) ---
    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)

    // --- Kotlin Coroutines ---
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // --- Firebase ---
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    implementation(libs.androidx.ui.graphics.android)
    implementation(libs.androidx.foundation.android)


    // --- Optional helpers ---
    implementation("de.hdodenhof:circleimageview:3.1.0") // Circle Image View

    // --- Testing ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}