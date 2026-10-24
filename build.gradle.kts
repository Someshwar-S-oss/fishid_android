// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    id("com.facebook.react.rootproject")
}

// Read by React Native libraries (react-native-svg, safe-area-context) via rootProject.ext, as in the RN template.
extra.apply {
    set("compileSdkVersion", 37)
    set("targetSdkVersion", 37)
    set("minSdkVersion", 24)
    set("buildToolsVersion", "37.0.0")
    set("ndkVersion", "27.1.12297006")
}
