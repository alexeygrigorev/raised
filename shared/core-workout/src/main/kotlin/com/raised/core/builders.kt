package com.raised.core

/**
 * Pure plan builders (decision D4). Each turns a [WorkoutConfig] into a flat
 * [WorkoutPlan] (an ordered [Step] list) following a fixed interval structure.
 * No I/O, no clock, no state — trivially testable.
 */

/**
 * Builds the HIIT plan from [config].
 *
 * Structure, in order:
 *  1. `GET_READY(getReadySecs)`
 *  2. `WARMUP(warmupSecs)` — only when `warmupSecs > 0`.
 *  3. For each round `1..rounds`: every exercise as
 *     `EXERCISE(exerciseSecs, index, label)`, with `BREAK(breakSecs)` between
 *     them (NOT after the last exercise of the round). After the round, if it is
 *     not the last round, `LONG_BREAK(longBreakSecs)`.
 *  4. `CHALLENGE(challengeSecs)` (cardio) — only when `challengeSecs > 0`.
 *  5. `COOLDOWN(cooldownSecs)` — only when `cooldownSecs > 0`.
 *
 * A configurable block with a `0` duration is omitted entirely (it would flash
 * a 0s step in the session UI); the total duration is unchanged since a 0s step
 * contributes nothing.
 *
 * @throws IllegalArgumentException if [config] has no exercises or `rounds < 1`.
 */
fun buildHiitPlan(config: WorkoutConfig): WorkoutPlan {
    require(config.exercises.isNotEmpty()) { "HIIT requires at least one exercise" }
    require(config.rounds >= 1) { "HIIT requires at least one round (rounds=${config.rounds})" }

    val steps = mutableListOf<Step>()

    steps += Step(StepType.GET_READY, config.getReadySecs)
    if (config.warmupSecs > 0) {
        steps += Step(StepType.WARMUP, config.warmupSecs)
    }

    val lastIndex = config.exercises.lastIndex
    for (round in 1..config.rounds) {
        for (i in config.exercises.indices) {
            steps += Step(
                type = StepType.EXERCISE,
                durationSec = config.exerciseSecs,
                exerciseIndex = i,
                label = config.exercises[i],
            )
            if (i < lastIndex) {
                steps += Step(StepType.BREAK, config.breakSecs)
            }
        }
        if (round < config.rounds) {
            steps += Step(StepType.LONG_BREAK, config.longBreakSecs)
        }
    }

    if (config.challengeSecs > 0) {
        steps += Step(StepType.CHALLENGE, config.challengeSecs)
    }
    if (config.cooldownSecs > 0) {
        steps += Step(StepType.COOLDOWN, config.cooldownSecs)
    }

    return WorkoutPlan(steps)
}

/**
 * Builds the Raised plan from [config].
 *
 * Structure, in order:
 *  1. `GET_READY(getReadySecs)`
 *  2. `WARMUP(warmupSecs)` — only when `warmupSecs > 0`.
 *  3. For each exercise index `e`: every set as
 *     `EXERCISE(exerciseSecs, index, label)`, with `BREAK(breakSecs)` between
 *     sets (NOT after the last set). After the exercise, if it is not the last
 *     exercise, `LONG_BREAK(longBreakSecs)`.
 *  4. `CHALLENGE(challengeSecs)` (core) — only when `challengeSecs > 0`.
 *  5. `COOLDOWN(cooldownSecs)` — only when `cooldownSecs > 0`.
 *
 * A configurable block with a `0` duration is omitted entirely (it would flash
 * a 0s step in the session UI); the total duration is unchanged since a 0s step
 * contributes nothing.
 *
 * @throws IllegalArgumentException if [config] has no exercises or `sets < 1`.
 */
fun buildRaisedPlan(config: WorkoutConfig): WorkoutPlan {
    require(config.exercises.isNotEmpty()) { "Raised requires at least one exercise" }
    require(config.sets >= 1) { "Raised requires at least one set (sets=${config.sets})" }

    val steps = mutableListOf<Step>()

    steps += Step(StepType.GET_READY, config.getReadySecs)
    if (config.warmupSecs > 0) {
        steps += Step(StepType.WARMUP, config.warmupSecs)
    }

    val lastIndex = config.exercises.lastIndex
    for (e in config.exercises.indices) {
        for (set in 1..config.sets) {
            steps += Step(
                type = StepType.EXERCISE,
                durationSec = config.exerciseSecs,
                exerciseIndex = e,
                label = config.exercises[e],
            )
            if (set < config.sets) {
                steps += Step(StepType.BREAK, config.breakSecs)
            }
        }
        if (e < lastIndex) {
            steps += Step(StepType.LONG_BREAK, config.longBreakSecs)
        }
    }

    if (config.challengeSecs > 0) {
        steps += Step(StepType.CHALLENGE, config.challengeSecs)
    }
    if (config.cooldownSecs > 0) {
        steps += Step(StepType.COOLDOWN, config.cooldownSecs)
    }

    return WorkoutPlan(steps)
}

/**
 * Builds the plan for [config] by dispatching on its [WorkoutType].
 */
fun buildPlan(config: WorkoutConfig): WorkoutPlan = when (config.type) {
    WorkoutType.HIIT -> buildHiitPlan(config)
    WorkoutType.RAISED -> buildRaisedPlan(config)
}
