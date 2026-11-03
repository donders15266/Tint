plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "nl.jesse.tint"
    compileSdk = 35

    defaultConfig {
        applicationId = "nl.jesse.tint"
        // Systeemkleuren (Material You) en de tegel-API's bestaan vanaf Android 12
        minSdk = 31
        targetSdk = 35
        // GitHub Actions geeft elke build een hoger versienummer; lokaal blijft het 1
        versionCode = (System.getenv("TINT_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("TINT_VERSION_NAME") ?: "1.0-lokaal"
        // Waar de app naar updates zoekt (zie gradle.properties)
        buildConfigField("String", "UPDATE_REPO", "\"${project.property("tint.updateRepo")}\"")
    }

    // Altijd dezelfde sleutel: anders weigert Android een update over de geïnstalleerde app.
    // Op GitHub Actions wijst TINT_KEYSTORE naar de sleutel uit het secret; lokaal is het de debug-sleutel van deze pc.
    signingConfigs {
        create("tint") {
            storeFile = file(System.getenv("TINT_KEYSTORE") ?: "${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // R8 maakt Compose merkbaar sneller dan in de debug-build
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("tint")
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

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    // Laat de meegeleverde baseline-profielen van Compose bij installatie voorcompileren
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
}
