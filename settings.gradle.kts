pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

rootProject.name = "callspy"

include(
    ":app",
    ":module1",
    ":module2"
)