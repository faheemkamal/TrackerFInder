plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}

android {
    namespace = "com.example.trackerfinder"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.trackerfinder"
        minSdk = 26
        targetSdk = 35
        versionCode = 18
        versionName = "4.8"
        // The Find Hub engine ships native Python libraries; real phones are 64-bit ARM.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildTypes {
        release {
            // Shrinks the Kotlin/Compose code a lot (the Python engine is unaffected).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Personal sideloaded app: sign with the same debug key so it installs over earlier builds.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Embedded Python running the Find Hub engine (GoogleFindMyTools + Tag Tracker Open, GPL-3.0).
chaquopy {
    defaultConfig {
        version = "3.13"
        pip { install("-r", "requirements-android.txt") }
        extractPackages("certifi")
    }
    sourceSets {
        getByName("main") {
            srcDir("src/main/python")
            srcDir("../third_party/GoogleFindMyTools")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Map engine: MapLibre (vector + raster tiles). Replaced osmdroid in 4.6 so OpenFreeMap vector maps work.
    implementation("org.maplibre.gl:android-sdk:12.3.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0") // same version MapLibre uses; for the tile User-Agent

    testImplementation("junit:junit:4.13.2")
}
