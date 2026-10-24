plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    id("com.facebook.react")
}

// UI is React Native (JS at the repo root: index.js, src/). The model, data and scan logic stay in Kotlin.
react {
    root = file("..")
    reactNativeDir = file("../node_modules/react-native")
    codegenDir = file("../node_modules/@react-native/codegen")
    cliFile = file("../node_modules/react-native/cli.js")
    autolinkLibrariesWithApp()
}

android {
    namespace = "com.example.fishid"
    compileSdk {
        version = release(37)
    }
    ndkVersion = "27.1.12297006"

    defaultConfig {
        // TODO before Play Store: Play rejects com.example.* application ids.
        applicationId = "com.example.fishid"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        // Builds locales_config.xml from res/values-* so Android 13+ lists FishID in per-app language settings.
        generateLocaleConfig = true
        // The model is memory-mapped straight from the APK, which needs it stored uncompressed.
        noCompress += "tflite"
    }

    buildTypes {
        release {
            // ponytail: signed with the debug key so release builds install for testing; add a real keystore before Play.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 }
    }
}

dependencies {
    implementation("com.facebook.react:react-android")
    implementation("com.facebook.react:hermes-android")
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.exifinterface)
    implementation(libs.litert)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
