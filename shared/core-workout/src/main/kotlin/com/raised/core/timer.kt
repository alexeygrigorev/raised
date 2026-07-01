package com.raised.core

/**
 * Pure session-timer state machine (decision D3/D4). No coroutines, no real
 * clock: the ViewModel drives [tick] with the elapsed wall-clock delta, and tests
 * drive deterministic deltas. The machine walks [SessionState.plan.steps],
 * decrementing [SessionState.remainingSec] on each step and advancing across
 * step boundaries with carry-over — a single large [tick] can cross several
 * short steps.
 */

/**
 * Immutable snapshot of a session at one instant.
 *
 * @property plan           the workout being played.
 * @property index          index of the current step within [plan], or the last
 *                          valid index once finished.
 * @property remainingSec   seconds left in the current step; 0 once finished.
 * @property totalElapsedSec total seconds of workout consumed so far (sum of
 *                          durations fully consumed plus partial consumption of
 *                          the current step). Monotonically non-decreasing.
 * @property phase          the [StepType] of the current step, or [StepType.DONE]
 *                          once the plan is finished.
 * @property isFinished     true once the final step's time is consumed.
 */
data class SessionState(
    val plan: WorkoutPlan,
    val index: Int,
    val remainingSec: Int,
    val totalElapsedSec: Int,
    val phase: StepType,
    val isFinished: Boolean,
)

/** Thrown when a timer operation is applied to an empty plan (no steps). */
class EmptyPlanException(message: String) : IllegalStateException(message)

/**
 * Starts a session at the first step of [plan].
 *
 * `index = 0`, `remainingSec = steps[0].durationSec`, `phase = steps[0].type`,
 * `totalElapsedSec = 0`, `isFinished = false`. A zero-length single-step plan is
 * finished immediately via the first [tick] (or [skipForward]).
 *
 * @throws EmptyPlanException if [plan] has no steps.
 */
fun start(plan: WorkoutPlan): SessionState {
    if (plan.steps.isEmpty()) throw EmptyPlanException("Cannot start an empty plan")
    val first = plan.steps.first()
    return SessionState(
        plan = plan,
        index = 0,
        remainingSec = first.durationSec,
        totalElapsedSec = 0,
        phase = first.type,
        isFinished = false,
    )
}

/**
 * Advances the session by [deltaSec] seconds of real time, consuming the delta
 * across step boundaries with carry-over. A single call can cross several short
 * steps. As each step is fully consumed its full duration is credited to
 * [SessionState.totalElapsedSec]; the partial remainder of the current step is
 * credited too.
 *
 * Once the final step's time is consumed the session becomes finished:
 * `isFinished = true`, `phase = DONE`, `remainingSec = 0`, `totalElapsedSec =
 * plan.totalDurationSec`.
 *
 * A [deltaSec] of 0 returns an equivalent state (no time consumed). Negative
 * deltas are rejected.
 *
 * @throws IllegalArgumentException if [deltaSec] < 0.
 */
fun tick(state: SessionState, deltaSec: Int): SessionState {
    require(deltaSec >= 0) { "deltaSec must be non-negative (got $deltaSec)" }

    if (state.isFinished || deltaSec == 0) {
        return state
    }

    val steps = state.plan.steps
    var index = state.index
    var remaining = state.remainingSec
    var elapsed = state.totalElapsedSec
    var toConsume = deltaSec

    while (toConsume > 0 && index <= steps.lastIndex) {
        if (toConsume < remaining) {
            // Partial consumption of the current step — still inside it.
            remaining -= toConsume
            elapsed += toConsume
            toConsume = 0
        } else {
            // Finish the current step and carry the remainder forward.
            toConsume -= remaining
            elapsed += remaining
            index += 1
            remaining = if (index <= steps.lastIndex) steps[index].durationSec else 0
        }
    }

    return if (index > steps.lastIndex) {
        // Consumed through the final step.
        SessionState(
            plan = state.plan,
            index = steps.lastIndex,
            remainingSec = 0,
            totalElapsedSec = state.plan.totalDurationSec,
            phase = StepType.DONE,
            isFinished = true,
        )
    } else {
        SessionState(
            plan = state.plan,
            index = index,
            remainingSec = remaining,
            totalElapsedSec = elapsed,
            phase = steps[index].type,
            isFinished = false,
        )
    }
}

/**
 * Jumps forward to the next step at full duration. The skipped remainder of the
 * current step is credited to [SessionState.totalElapsedSec] (it counts as
 * elapsed workout time). If already on the last step the session finishes.
 *
 * Semantics:
 *  - On a finished state: returns it unchanged.
 *  - On the last step: finishes (`phase = DONE`, `remainingSec = 0`,
 *    `totalElapsedSec = plan.totalDurationSec`).
 *  - Otherwise: `index += 1`, `remainingSec = steps[index].durationSec`,
 *    `totalElapsedSec` increases by the previous step's `remainingSec`.
 */
fun skipForward(state: SessionState): SessionState {
    if (state.isFinished) return state

    val steps = state.plan.steps
    val skipped = state.remainingSec
    val newIndex = state.index + 1

    return if (newIndex > steps.lastIndex) {
        SessionState(
            plan = state.plan,
            index = steps.lastIndex,
            remainingSec = 0,
            totalElapsedSec = state.plan.totalDurationSec,
            phase = StepType.DONE,
            isFinished = true,
        )
    } else {
        SessionState(
            plan = state.plan,
            index = newIndex,
            remainingSec = steps[newIndex].durationSec,
            totalElapsedSec = state.totalElapsedSec + skipped,
            phase = steps[newIndex].type,
            isFinished = false,
        )
    }
}

/**
 * Jumps back to the previous step at full duration, without un-crediting elapsed
 * time (skipped-back time is not "un-played" — [SessionState.totalElapsedSec]
 * is monotonic and unchanged). If already on the first step, it resets that step
 * to its full duration (a no-op if it is already full).
 *
 * Semantics:
 *  - On a finished state: returns to the LAST step (the cursor's `index` already
 *    points at the final step) at full duration, unfinished — so the user can
 *    replay the final cooldown.
 *  - On the first step (index 0): `remainingSec = steps[0].durationSec`,
 *    `isFinished = false`, elapsed unchanged.
 *  - Otherwise: `index -= 1`, `remainingSec = steps[index].durationSec`,
 *    elapsed unchanged.
 */
fun skipBack(state: SessionState): SessionState {
    val steps = state.plan.steps
    // When finished the cursor sits at the last real step already; "back" from
    // DONE replays that last step rather than the one before it.
    val newIndex = if (state.isFinished) {
        steps.lastIndex
    } else {
        (state.index - 1).coerceAtLeast(0)
    }
    return SessionState(
        plan = state.plan,
        index = newIndex,
        remainingSec = steps[newIndex].durationSec,
        totalElapsedSec = state.totalElapsedSec,
        phase = steps[newIndex].type,
        isFinished = false,
    )
}

/**
 * Restarts the session from the first step, as if [start] were called fresh on
 * the same plan (resets elapsed time to 0, unfinished, at step 0 full duration).
 */
fun restart(state: SessionState): SessionState = start(state.plan)
