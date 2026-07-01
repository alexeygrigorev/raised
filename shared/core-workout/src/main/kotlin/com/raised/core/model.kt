package com.raised.core

/**
 * Domain model for the Raised interval-workout app (decision D4 — flat-step plan
 * model). A workout is an ordered list of [Step]s; the HIIT and Raised workouts
 * are [buildHiitPlan] / [buildRaisedPlan] builders that turn a [WorkoutConfig]
 * into a [WorkoutPlan]. The session timer ([timer.kt]) walks that list.
 *
 * Pure Kotlin/JVM — no `android.*` types (decision D3), so fully unit-testable
 * on the host JVM.
 */

/**
 * The kind of interval a [Step] represents.
 *
 * [DONE] is not produced by any builder; it is the terminal [SessionState.phase]
 * once the timer consumes the final step of a plan.
 */
enum class StepType {
    WARMUP,
    GET_READY,
    EXERCISE,
    BREAK,
    LONG_BREAK,
    CHALLENGE,
    COOLDOWN,
    DONE,
}

/**
 * One interval in a [WorkoutPlan].
 *
 * @property type         the interval kind.
 * @property durationSec   length of the interval in seconds; always >= 0.
 * @property exerciseIndex index into [WorkoutConfig.exercises] for an
 *                         [StepType.EXERCISE] step, otherwise null.
 * @property label         human-readable label (typically the exercise name) for
 *                         display, otherwise null.
 */
data class Step(
    val type: StepType,
    val durationSec: Int,
    val exerciseIndex: Int? = null,
    val label: String? = null,
)

/** Which workout family a [WorkoutConfig] describes. */
enum class WorkoutType {
    HIIT,
    RAISED,
}

/**
 * Full configuration for one workout. The builders derive the exact step list
 * from this; the user-facing screens edit it; named presets store a full copy.
 *
 * `rounds` drives HIIT (exercises repeated across rounds); `sets` drives Raised
 * (each exercise repeated across sets before moving on). The unused count is
 * kept at 0 for clarity.
 *
 * @property type          the workout family (selects the builder).
 * @property exercises     ordered exercise names.
 * @property exerciseSecs  seconds of work per exercise.
 * @property breakSecs     seconds of the short break between exercises/sets.
 * @property longBreakSecs seconds of the long break between rounds/exercises.
 * @property rounds        HIIT round count (unused by Raised).
 * @property sets          Raised set count (unused by HIIT).
 * @property warmupSecs    seconds of the warm-up block.
 * @property challengeSecs seconds of the challenge block.
 * @property cooldownSecs  seconds of the cool-down block.
 * @property getReadySecs  seconds of the get-ready countdown before warm-up.
 */
data class WorkoutConfig(
    val type: WorkoutType,
    val exercises: List<String>,
    val exerciseSecs: Int,
    val breakSecs: Int,
    val longBreakSecs: Int,
    val rounds: Int,
    val sets: Int,
    val warmupSecs: Int,
    val challengeSecs: Int,
    val cooldownSecs: Int,
    val getReadySecs: Int,
) {
    companion object {
        /** Shared default durations, documented as constants (D7). */
        const val DEFAULT_GET_READY_SECS = 10
        const val DEFAULT_WARMUP_SECS = 60
        const val DEFAULT_CHALLENGE_SECS = 60
        const val DEFAULT_COOLDOWN_SECS = 120

        /** Default HIIT exercise list (8). */
        val HIIT_EXERCISES: List<String> = listOf(
            "Jumping Jacks",
            "Wall Sit",
            "Push-ups",
            "Crunches",
            "Step-ups",
            "Squats",
            "Plank",
            "Lunges",
        )

        /** Default Raised exercise list (6). */
        val RAISED_EXERCISES: List<String> = listOf(
            "Push-ups",
            "Squats",
            "Sit-ups",
            "Lunges",
            "Plank",
            "Burpees",
        )

        /**
         * Default HIIT config (D7): 8 exercises, 45s work / 15s break / 60s long
         * break, 3 rounds. Builds to 51 steps / 1765s.
         */
        fun hiitDefault(): WorkoutConfig = WorkoutConfig(
            type = WorkoutType.HIIT,
            exercises = HIIT_EXERCISES,
            exerciseSecs = 45,
            breakSecs = 15,
            longBreakSecs = 60,
            rounds = 3,
            sets = 0,
            warmupSecs = DEFAULT_WARMUP_SECS,
            challengeSecs = DEFAULT_CHALLENGE_SECS,
            cooldownSecs = DEFAULT_COOLDOWN_SECS,
            getReadySecs = DEFAULT_GET_READY_SECS,
        )

        /**
         * Default Raised config (D7): 6 exercises, 45s work / 45s break / 60s
         * long break, 3 sets. Builds to 39 steps / 1900s.
         */
        fun raisedDefault(): WorkoutConfig = WorkoutConfig(
            type = WorkoutType.RAISED,
            exercises = RAISED_EXERCISES,
            exerciseSecs = 45,
            breakSecs = 45,
            longBreakSecs = 60,
            rounds = 0,
            sets = 3,
            warmupSecs = DEFAULT_WARMUP_SECS,
            challengeSecs = DEFAULT_CHALLENGE_SECS,
            cooldownSecs = DEFAULT_COOLDOWN_SECS,
            getReadySecs = DEFAULT_GET_READY_SECS,
        )
    }
}

/**
 * A built workout: a flat, ordered list of [Step]s.
 *
 * @property steps the intervals, in playback order.
 */
data class WorkoutPlan(
    val steps: List<Step>,
) {
    /** Total playback length in seconds (sum of every step's duration). */
    val totalDurationSec: Int
        get() = steps.sumOf { it.durationSec }
}
