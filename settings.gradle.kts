pluginManagement {
    repositories {
        google()
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
        mavenCentral()
    }
}

rootProject.name = "AuraCurve"

include(":app")
include(":core")

// The built-in player (Media3/ExoPlayer, files from the device), a library module merged into the app.
include(":player")
project(":player").projectDir = file("player/app")
