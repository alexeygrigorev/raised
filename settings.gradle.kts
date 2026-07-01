pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "raised"

// Module layout mirrors docs/decisions.md (D2 — composition, not fork).
//
// Approach for not-yet-existing modules: keep them commented out. Each later
// issue uncomments its own line as part of creating the module directory.
// An `include()` for a missing directory is a hard Gradle error, and an
// explicit comment list is more discoverable than a conditional exists() filter.
//
// Issue #1 uncomments :app and :shared:ui-kit.
// Issue #2 uncomments :shared:core-workout (pure-JVM domain: plan builders
// + timer engine, D3).

include(":app")
include(":shared:ui-kit")
// include(":shared:core-workout")
