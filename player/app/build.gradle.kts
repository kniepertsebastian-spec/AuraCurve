import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.auracurve.app.player"
    compileSdk = 37

    defaultConfig {
        // Same as :app, which is the only consumer of this module.
        minSdk = 28
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            // Robolectric (ResolvingTrackMediaSourceTest runs a real ExoPlayer) needs the resources.
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        // Matches the :app module - the JDK running Gradle isn't the bytecode target.
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ktlint {
    // Same style as :app.
    android.set(true)
}

dependencies {
    // Pure-Kotlin DSP (BassExciterPcm16) used by the virtual-bass audio processor.
    implementation(project(":core"))

    // The player's screens are Compose and live in :app; this module has no XML views, so no
    // appcompat, material, constraintlayout or media3-ui (Teil-Roadmap 4, M-6).
    implementation(libs.core.ktx)

    // Media3 / ExoPlayer
    implementation(libs.media3.exoplayer)
    // MediaLibraryService: notification, lock screen, Bluetooth, Wear and Android Auto from one session.
    implementation(libs.media3.session)

    // Async
    implementation(libs.kotlinx.coroutines.android)
    // The player's remembered settings (PlayerDspState, PlaybackFadeState), off the main thread.
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
    // Android ships org.json; the JVM unit tests run against stubs, so they need a real one.
    testImplementation(libs.org.json)
    testImplementation(libs.mockito.core)
    // A real ExoPlayer with fake renderers and clock for the queue's stream lookup; Robolectric
    // in the version media3-test-utils-robolectric 1.11.1 is built against.
    testImplementation(libs.media3.test.utils)
    testImplementation(libs.media3.test.utils.robolectric)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
