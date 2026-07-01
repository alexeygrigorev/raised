# Raised

An Android interval-workout app with two configurable workouts:

- **HIIT** — move around a circuit of N exercises (default 8), a work interval
  (default 45s) with a short break (default 15s) between them, a long break
  (default 60s) between rounds, for R rounds (default 3). Ends with a cardio
  challenge, then a stretching/breathing cool-down.
- **Raised** — N exercises (default 6), S sets per exercise (default 3), each set
  a work interval (default 45s) + break (default 45s), with a long rest
  (default 60s) between exercises. Starts with a warm-up and ends with a core
  challenge + stretching cool-down.

Every duration, every exercise, the round/set counts, and the warm-up /
challenge / cool-down blocks are configurable. Named presets store a full
configuration. Built for one user (no install base, no backwards-compat — hard
cuts only).

## Status

Bootstrapping via the [process](process.md) — orchestrator + implementer +
reviewer, issue-driven. The repo currently holds the process docs + build
tooling (the "factory"). The app scaffold, the session engine, persistence, and
the screens are each their own GitHub issue, built one reviewed slice at a time.

## Tech

Kotlin + Jetpack Compose (Material 3), Gradle Kotlin DSL + version catalog, Hilt
for DI, Room for persistence, MVVM (ViewModel + StateFlow). Pure-JVM domain
(`:shared:core-workout`) keeps the plan builders + timer engine fully unit-
testable off-device.

## Key docs

- [process.md](process.md) — orchestrator + implementer + reviewer loop
- [agents.md](agents.md) — agent role pointers + project knowledge
- [docs/decisions.md](docs/decisions.md) — locked design decisions
- [CLAUDE.md](CLAUDE.md) — top-level guide for Claude

## Build

```bash
./gradlew assembleDebug      # build the debug APK
./gradlew test               # JVM unit tests
```

Requires JDK 17+ and the Android SDK (`local.properties` → `sdk.dir=...`, or a
set `ANDROID_HOME`).
