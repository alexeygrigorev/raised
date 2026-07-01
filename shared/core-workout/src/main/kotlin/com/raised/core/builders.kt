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
 *  2. `WARMUP(warmupSecs)`
 *  3. For each round `1..rounds`: every exercise as
 *     `EXERCISE(exerciseSecs, index, label)`, with `BREAK(breakSecs)` between
 *     them (NOT after the last exercise of the round). After the round, if it is
 *     not the last round, `LONG_BREAK(longBreakSecs)`.
 *  4. `CHALLENGE(challengeSecs)` (cardio).
 *  5. `COOLDOWN(cooldownSecs)`.
 *
 * @throws IllegalArgumentException if [config] has no exercises or `rounds < 1`.
 */
fun buildHiitPlan(config: WorkoutConfig): WorkoutPlan {
    require(config.exercises.isNotEmpty()) { "HIIT requires at least one exercise" }
    require(config.rounds >= 1) { "HIIT requires at least one round (rounds=${config.rounds})" }

    val steps = mutableListOf<Step>()

    steps += Step(StepType.GET_READY, config.getReadySecs)
    steps += Step(StepType.WARMUP, config.warmupSecs)

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

    steps += Step(StepType.CHALLENGE, config.challengeSecs)
    steps += Step(StepType.COOLDOWN, config.cooldownSecs)

    return WorkoutPlan(steps)
}

/**
 * Builds the Raised plan from [config].
 *
 * Structure, in order:
 *  1. `GET_READY(getReadySecs)`
 *  2. `WARMUP(warmupSecs)`
 *  3. For each exercise index `e`: every set as
 *     `EXERCISE(exerciseSecs, index, label)`, with `BREAK(breakSecs)` between
 *     sets (NOT after the last set). After the exercise, if it is not the last
 *     exercise, `LONG_BREAK(longBreakSecs)`.
 *  4. `CHALLENGE(challengeSecs)` (core).
 *  5. `COOLDOWN(cooldownSecs)`.
 *
 * @throws IllegalArgumentException if [config] has no exercises or `sets < 1`.
 */
fun buildRaisedPlan(config: WorkoutConfig): WorkoutPlan {
    require(config.exercises.isNotEmpty()) { "Raised requires at least one exercise" }
    require(config.sets >= 1) { "Raised requires at least one set (sets=${config.sets})" }

    val steps = mutableListOf<Step>()

    steps += Step(StepType.GET_READY, config.getReadySecs)
    steps += Step(StepType.WARMUP, config.warmupSecs)

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

    steps += Step(StepType.CHALLENGE, config.challengeSecs)
    steps += Step(StepType.COOLDOWN, config.cooldownSecs)

    return WorkoutPlan(steps)
}

/**
 * Builds the plan for [config] by dispatching on its [WorkoutType].
 */
fun buildPlan(config: WorkoutConfig): WorkoutPlan = when (config.type) {
    WorkoutType.HIIT -> buildHiitPlan(config)
    WorkoutType.RAISED -> buildRaisedPlan(config)
}
