// Pure-JVM domain module (decision D3). The plan builders + session timer live
// here, free of any `android.*` type, so they are fully unit-testable on the
// host JVM with no emulator / Robolectric. The UI layer consumes these from
// :app; this module never depends on Android.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // The engine is intentionally dependency-free and synchronous — no
    // coroutines, no real clock. The ViewModel drives ticks; tests drive
    // deterministic deltas.
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
