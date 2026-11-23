import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ktlint)
}

// Teil-Roadmap 4, M-7: the release key's location and passwords come from environment variables,
// never from -P arguments (those end up in the process list and in CI logs). Locally, export them
// in the shell (or keep them in an ignored .env / keystore.properties file and source that).
val releaseStoreFile = providers.environmentVariable("AURACURVE_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("AURACURVE_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("AURACURVE_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("AURACURVE_RELEASE_KEY_PASSWORD").orNull
val hasReleaseSigning =
    listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

// An unsigned release APK/AAB is only useful to check that R8 shrinking works (the CI does that);
// it must be asked for explicitly, so a release build never silently comes out unsigned.
val allowUnsignedRelease =
    providers.gradleProperty("auracurve.allowUnsignedRelease").map(String::toBoolean).getOrElse(false)

android {
    // Roadmap M1: single source of truth is gradle.properties, not this file.
    namespace = providers.gradleProperty("auracurve.namespace").get()
    compileSdk = 37

    defaultConfig {
        applicationId = providers.gradleProperty("auracurve.applicationId").get()
        minSdk = 28
        targetSdk = 36
        versionCode = providers.gradleProperty("auracurve.versionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("auracurve.versionName").getOrElse("0.2.0-m1")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // English-only app: also drops translations bundled with libraries.
    androidResources {
        localeFilters += "en"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("auracurveRelease") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // AuraCurve roadmap 01, step 3: R8 shrinks, optimizes and obfuscates the store build and
            // drops unused resources. An earlier attempt (roadmap 06, P6-6) was switched off because
            // the release APK did not start; the cause turned out to be a NullPointerException in
            // MainViewModel's init, not R8 (builds without R8 failed the same way), fixed since.
            // Keep rules: proguard-rules.pro. R8's mapping.txt is attached to every release by
            // .github/workflows/release.yml (stack traces from the Play Console are deobfuscated
            // with it); before a store upload the signed bundle is still checked on a device.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("auracurveRelease")
        }
    }

    testOptions {
        unitTests {
            // android.jar's real classes are stubbed out (throw
            // "not mocked") under plain testDebugUnitTest by default - this repo
            // has code (e.g. RoomPresetRepository's corrupted-row logging) that
            // legitimately calls android.util.Log from a path unit tests exercise,
            // with no Robolectric/instrumentation set up to provide a real
            // implementation. Falling back to harmless defaults (Log.w() no-ops
            // and returns 0) instead of throwing is the standard fix.
            isReturnDefaultValues = true
            // Compose UI tests of the player run under Robolectric and need the resources.
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        // The exported Room schemas are what MigrationTestHelper opens the old database from.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Fails assembleRelease/bundleRelease (and anything that includes them, such as `build`) before
// any packaging when the signing variables are missing. Lint and unit tests of the release variant
// do not need the key and are not affected.
val requireReleaseSigning =
    tasks.register("requireReleaseSigning") {
        description = "Fails the release build when the AURACURVE_RELEASE_* signing variables are missing."
        val signed = hasReleaseSigning
        val unsignedAllowed = allowUnsignedRelease
        doLast {
            if (!signed && !unsignedAllowed) {
                throw GradleException(
                    "Release signing is not configured: set AURACURVE_RELEASE_STORE_FILE, " +
                        "AURACURVE_RELEASE_STORE_PASSWORD, AURACURVE_RELEASE_KEY_ALIAS and " +
                        "AURACURVE_RELEASE_KEY_PASSWORD, or pass -Pauracurve.allowUnsignedRelease=true " +
                        "for an unsigned build that is never uploaded.",
                )
            }
        }
    }
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn(requireReleaseSigning)
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    mustRunAfter(requireReleaseSigning)
}

kotlin {
    compilerOptions {
        // With built-in Kotlin disabled, match Java's target explicitly.
        // The JDK running Gradle (21 in CI) is not the bytecode target.
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ktlint {
    // Matches the ktlint_official style already implied by
    // kotlin.code.style=official in gradle.properties.
    android.set(true)
}

ksp {
    // M2: exports the DB schema to JSON on every build, the baseline
    // MigrationTestHelper tests need. The `presets` table has never had a real
    // write path before this change (verified: nothing referenced PresetDao),
    // so there's no real prior schema to migrate away from yet - this starts
    // the practice from the first schema that's actually reachable. No
    // MigrationTestHelper test exists yet either (needs Robolectric or
    // instrumentation, neither set up in this repo - see AppDatabase.kt).
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Shared preset/DSP domain logic (plain Kotlin/JVM, no Android dependency).
    implementation(project(":core"))
    // The built-in player (Media3, files from the device), a library module merged into this
    // app's manifest/APK.
    implementation(project(":player"))

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.service)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.car.app)
    // Covers in the player's lists and the large cover, cached in memory and on disk. No network
    // module: the store build has no online source, so covers only ever come from the device.
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Compose UI tests of the player (mini player, Now playing, queue) on the JVM.
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)

    // Tests on a device or emulator (roadmap 06, P6-3): ./gradlew :app:connectedDebugAndroidTest
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.room.ktx)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
