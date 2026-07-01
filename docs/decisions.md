# Locked Decisions

Design decisions locked for Raised. Open questions and rejected alternatives get
added here as they arise. Mirrors pocketshell's decisions-doc discipline.

- **D1 — Toolchain.** Kotlin + Jetpack Compose (Material 3), Gradle Kotlin DSL +
  version catalog, Hilt DI, Room persistence, MVVM (ViewModel + StateFlow).
  Versions mirror pocketshell / ssh-auto-forward-android (AGP 8.9.2, Kotlin
  2.1.21, Compose BOM 2025.05.00, Hilt 2.56.1, Room 2.7.1). minSdk 26,
  target/compile 35.
- **D2 — Composition, not fork.** Multi-module Gradle: `:app` + shared library
  modules (`:shared:ui-kit`, `:shared:core-workout`). Each concern is its own
  module declared in `settings.gradle.kts`.
- **D3 — Pure-JVM domain module.** The plan builders + session timer live in
  `:shared:core-workout` (Kotlin/JVM, no `android.*` types) so they are fully
  unit-testable on the host JVM with no emulator/Robolectric.
- **D4 — Flat-step plan model.** A workout = an ordered `List<Step>` (interval).
  HIIT and Raised are builders: `WorkoutConfig` → `List<Step>`. The timer is a
  pure state machine over that list. Keeps timing correctness trivial to test
  and independent of UI.
- **D5 — Single user, hard cuts only.** No install base, no backwards-compat.
  Superseded features are deleted in the same PR; Room schema changes ship a
  migration (destructive reset is only an explicit user-confirmed recovery).
- **D6 — Foreground-only session.** The timer runs only while the session screen
  is foregrounded, with `FLAG_KEEP_SCREEN_ON`. No WorkManager / ForegroundService
  / AlarmManager background timer in v1; backgrounding pauses and resume
  re-syncs.
- **D7 — Spec defaults.** Default configs match the maintainer's exact spec:
  HIIT 8×45/15/60, 3 rounds + cardio challenge + stretch; Raised 6×45/45/60,
  3 sets + core challenge + stretch.
- **D8 — MVVM + Hilt + Room.** ViewModels expose `StateFlow`; Hilt provides
  singletons; Room stores configs/presets/exercises with migrations.
- **D9 — Audio cues opt-in.** A short beep on the countdown + TTS announcing the
  next exercise, behind a setting (beeps default on, TTS default off). Not
  required for v1 timer correctness.
