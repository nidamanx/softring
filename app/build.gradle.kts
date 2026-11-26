// SPDX-License-Identifier: AGPL-3.0-or-later

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The release APK is always built unsigned (as F-Droid needs for its reproducibility
// check); the Release workflow signs it afterwards with scripts/sign-apk.sh.

// Debug signing: the fixed debug key only where its environment variables are set (the
// Build workflow of this repository), so test builds install over each other. Elsewhere
// (forks, local builds) the Android SDK's own debug key of that machine is used.
val debugKeystoreFile: String? = System.getenv("SOFTRING_DEBUG_KEYSTORE_FILE")
val debugKeystorePassword: String? = System.getenv("SOFTRING_DEBUG_KEYSTORE_PASSWORD")
val hasDebugSigning = !debugKeystoreFile.isNullOrBlank() && !debugKeystorePassword.isNullOrBlank()

android {
    namespace = "io.github.nidamanx.softring"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.nidamanx.softring"
        minSdk = 26
        targetSdk = 36
        // versionCode must increase with every release (F-Droid and Android rely on it)
        versionCode = 1
        versionName = "1.0.0"
        // Name shown in the launcher and in the system settings (see the debug build type)
        manifestPlaceholders["appLabel"] = "SoftRing"
    }

    signingConfigs {
        if (hasDebugSigning) {
            getByName("debug") {
                storeFile = file(debugKeystoreFile!!)
                storeType = "pkcs12"
                storePassword = debugKeystorePassword
                keyAlias = "softring-debug"
                keyPassword = debugKeystorePassword
            }
        }
    }

    buildTypes {
        // Test builds are a separate app, "SoftRing-D", installable next to the released
        // one. The release keeps applicationId unchanged. Use only one of the two at a
        // time: both switched on would fight over the same volumes.
        debug {
            applicationIdSuffix = ".dev"
            manifestPlaceholders["appLabel"] = "SoftRing-D"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Do not embed git metadata: keeps the APK identical regardless of
            // how the source was checked out (reproducible builds).
            vcsInfo.include = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Required by F-Droid: don't embed the Google-encrypted dependency blob
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // Missing translations must fail the build: all 5 languages are mandatory
        error += "MissingTranslation"
        // targetSdk must stay at 36: with 37, Android 17 would also require
        // while-in-use capability for VolumeService (see VolumeService)
        disable += "OldTargetApi"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // No runtime dependencies on purpose. Test-only dependencies below.
    testImplementation("junit:junit:4.13.2")
}
