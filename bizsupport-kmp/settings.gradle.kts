pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "bizsupport-kmp"
include(":shared")
include(":desktopApp")
include(":androidApp")
include(":desktopApp")
