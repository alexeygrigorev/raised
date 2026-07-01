plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.raised.app"
    compileSdk = 35

    // Pin both debug and release APKs to a single committed keystore so upgrading
    // an existing install never trips the "signatures do not match" path. The
    // password is the public Android debug password — the file has no real
    // security value, it just gives every machine (laptop, CI, release tag build)
    // the same signing identity. Mirrors pocketshell.
    signingConfigs {
        create("debugKeystore") {
            storeFile = file("../debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.raised.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debugKeystore")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("debugKeystore")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // Hand the Room KSP compiler a schema export dir (decision D5 — ship a
    // migration for later schema changes). The committed v1 JSON under
    // app/schemas/ is the baseline future migrations diff against.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    testOptions {
        // returnDefaultValues lets calls to Android framework stubs (Log, etc.)
        // return Java defaults instead of throwing in host-JVM unit tests.
        unitTests {
            isReturnDefaultValues = true
            // Robolectric needs the merged manifest + resources on the host JVM
            // to stand up the Room/SQLite-backed DAO tests.
            isIncludeAndroidResources = true
            all { test ->
                test.testLogging {
                    events("passed", "skipped", "failed")
                    showStandardStreams = true
                }
            }
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.material)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room persistence (D8). v1 schema only — migrations arrive with later
    // schema changes (D5). The generated DAO implementations come from the KSP
    // room-compiler.
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // The design language (colour, typography, shapes) lives in :shared:ui-kit
    // so every screen consumes the same source of truth.
    implementation(project(":shared:ui-kit"))
    // The persistence layer maps entities to/from :shared:core-workout's
    // WorkoutConfig so the UI/engine consume domain types, not entities.
    implementation(project(":shared:core-workout"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Robolectric in-memory Room tests on the host JVM.
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
