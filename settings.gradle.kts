pluginManagement {
    // React Native's Gradle plugins ship inside node_modules (run `npm install` first).
    includeBuild("node_modules/@react-native/gradle-plugin")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.facebook.react.settings")
}
// The JS project (package.json) lives at the repo root next to this file, not one level up as in the RN template.
extensions.configure<com.facebook.react.ReactSettingsExtension> {
    autolinkLibrariesFromCommand(
        workingDirectory = settings.layout.rootDirectory.asFile,
        lockFiles = settings.layout.rootDirectory.files("package-lock.json", "package.json", "react-native.config.js"),
    )
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FishID"
include(":app")
