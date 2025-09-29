// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        // Hilt
        classpath(libs.hilt.android.gradle.plugin)
        // Navigation
        classpath(libs.androidx.navigation.safe.args.gradle.plugin)
        // Google services (Firebase)
        classpath(libs.google.services)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("androidx.navigation.safeargs.kotlin") version "2.9.5" apply false
    id("com.google.gms.google-services") version "4.4.3" apply false
}

