plugins {
    id("com.android.application") version "8.1.0" apply false
    id("com.android.library") version "8.1.0" apply false
    kotlin("android") version "1.9.10" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

// Root build.gradle.kts file for a modern Android project with clean architecture

// This file configures the Android Gradle plugin and Kotlin DSL
// It also sets up repositories for all projects

// You can add common dependencies or configurations here if needed

