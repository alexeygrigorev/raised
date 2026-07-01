package com.raised.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Timer engine tests (acceptance criteria): tick-by-tick advance, transitions at
 * boundaries, a single tick crossing multiple steps, reaching DONE, and
 * skip-forward / skip-back / restart.
 */
class TimerEngineTest {

    // A small, hand-verifiable plan: GET_READY(2), EXERCISE(3), BREAK(1),
    // COOLDOWN(4). total = 10.
    private fun smallPlan() = WorkoutPlan(
        listOf(
            Step(StepType.GET_READY, 2),
            Step(StepType.EXERCISE, 3, 0, "Squat"),
            Step(StepType.BREAK, 1),
            Step(StepType.COOLDOWN, 4),
        ),
    )

    private val types = listOf(StepType.GET_READY, StepType.EXERCISE, StepType.BREAK, StepType.COOLDOWN)

    // ---- start ----

    @Test fun `start places cursor at step 0 full duration`() {
        val s = start(smallPlan())
        assertEquals(0, s.index)
        assertEquals(2, s.remainingSec)
        assertEquals(0, s.totalElapsedSec)
        assertEquals(StepType.GET_READY, s.phase)
        assertFalse(s.isFinished)
    }

    @Test fun `start rejects an empty plan`() {
        assertThrows(EmptyPlanException::class.java) { start(WorkoutPlan(emptyList())) }
    }

    // ---- tick: advance within a step ----

    @Test fun `tick decrements remaining and credits elapsed`() {
        val s0 = start(smallPlan())
        val s1 = tick(s0, 1)
        assertEquals(1, s1.remainingSec)
        assertEquals(1, s1.totalElapsedSec)
        assertEquals(StepType.GET_READY, s1.phase)
        assertEquals(0, s1.index)
    }

    @Test fun `tick of zero is a no-op`() {
        val s0 = start(smallPlan())
        val s1 = tick(s0, 0)
        assertEquals(s0, s1)
    }

    @Test fun `tick rejects negative delta`() {
        val s0 = start(smallPlan())
        assertThrows(IllegalArgumentException::class.java) { tick(s0, -1) }
    }

    // ---- tick: transitions at boundaries ----

    @Test fun `tick exactly to a boundary advances to the next step at full duration`() {
        // GET_READY is 2s. Tick exactly 2s consumes it fully and the cursor
        // advances to EXERCISE at full duration (3s). A step whose time is fully
        // consumed is over; there is no lingering "0s" frame.
        val s0 = start(smallPlan())
        val s1 = tick(s0, 2)
        assertEquals(1, s1.index)
        assertEquals(3, s1.remainingSec)
        assertEquals(2, s1.totalElapsedSec)
        assertEquals(StepType.EXERCISE, s1.phase)
        assertFalse(s1.isFinished)
    }

    @Test fun `tick one past a boundary advances to the next step with carry-over`() {
        val s1 = tick(start(smallPlan()), 3)
        // 2s finishes GET_READY, 1s into EXERCISE -> remaining 2, elapsed 3.
        assertEquals(1, s1.index)
        assertEquals(2, s1.remainingSec)
        assertEquals(3, s1.totalElapsedSec)
        assertEquals(StepType.EXERCISE, s1.phase)
    }

    // ---- tick: a single tick crossing multiple steps ----

    @Test fun `single tick crosses multiple steps with carry-over`() {
        // From the start, tick 6s: GET_READY(2) + EXERCISE(3) + BREAK(1) all
        // consumed (2+3+1 = 6) -> lands on COOLDOWN(4) with none consumed yet
        // (remaining 4), elapsed 6.
        val s = tick(start(smallPlan()), 6)
        assertEquals(3, s.index)
        assertEquals(4, s.remainingSec)
        assertEquals(6, s.totalElapsedSec)
        assertEquals(StepType.COOLDOWN, s.phase)
        assertFalse(s.isFinished)
    }

    @Test fun `tick mid-stream also crosses multiple steps`() {
        // 1s into GET_READY (remaining 1), then tick 5s: 1 finishes GET_READY,
        // 3 finishes EXERCISE, 1 finishes BREAK -> lands on COOLDOWN(4) with none
        // consumed (remaining 4), elapsed 6.
        val s0 = tick(start(smallPlan()), 1) // remaining 1 on GET_READY
        val s1 = tick(s0, 5)
        assertEquals(3, s1.index)
        assertEquals(4, s1.remainingSec)
        assertEquals(6, s1.totalElapsedSec)
        assertEquals(StepType.COOLDOWN, s1.phase)
    }

    // ---- tick: reaching DONE ----

    @Test fun `tick that consumes the final step finishes the session`() {
        // Tick exactly the plan total: lands DONE.
        val s = tick(start(smallPlan()), smallPlan().totalDurationSec)
        assertTrue(s.isFinished)
        assertEquals(StepType.DONE, s.phase)
        assertEquals(0, s.remainingSec)
        assertEquals(smallPlan().totalDurationSec, s.totalElapsedSec)
        assertEquals(smallPlan().steps.lastIndex, s.index)
    }

    @Test fun `tick past the end is clamped to finished`() {
        val s = tick(start(smallPlan()), smallPlan().totalDurationSec + 5)
        assertTrue(s.isFinished)
        assertEquals(smallPlan().totalDurationSec, s.totalElapsedSec)
    }

    @Test fun `tick on a finished state is a no-op`() {
        val done = tick(start(smallPlan()), smallPlan().totalDurationSec)
        val again = tick(done, 10)
        assertEquals(done, again)
    }

    // ---- whole-plan walk, one second at a time ----

    @Test fun `walking the whole plan one second at a time reaches DONE with full elapsed`() {
        var state = start(smallPlan())
        repeat(smallPlan().totalDurationSec) { state = tick(state, 1) }
        assertTrue(state.isFinished)
        assertEquals(StepType.DONE, state.phase)
        assertEquals(smallPlan().totalDurationSec, state.totalElapsedSec)
    }

    @Test fun `phase visits every step type in order during a one-second walk`() {
        val seen = mutableListOf<StepType>()
        var state = start(smallPlan())
        seen += state.phase
        repeat(smallPlan().totalDurationSec) {
            state = tick(state, 1)
            if (state.phase != seen.last()) seen += state.phase
        }
        // GET_READY observed during its step, EXERCISE, BREAK, COOLDOWN, DONE.
        assertEquals(types + StepType.DONE, seen)
    }

    // ---- skipForward ----

    @Test fun `skipForward jumps to next step at full duration and credits the skipped remainder`() {
        val s0 = start(smallPlan())           // GET_READY remaining 2
        val s1 = tick(s0, 1)                  // GET_READY remaining 1, elapsed 1
        val skipped = skipForward(s1)         // -> EXERCISE, remaining 3, elapsed 1+1=2
        assertEquals(1, skipped.index)
        assertEquals(3, skipped.remainingSec)
        assertEquals(2, skipped.totalElapsedSec)
        assertEquals(StepType.EXERCISE, skipped.phase)
        assertFalse(skipped.isFinished)
    }

    @Test fun `skipForward from the last step finishes`() {
        val last = tick(start(smallPlan()), 6) // on COOLDOWN (index 3), remaining 4
        val done = skipForward(last)
        assertTrue(done.isFinished)
        assertEquals(StepType.DONE, done.phase)
        assertEquals(smallPlan().totalDurationSec, done.totalElapsedSec)
    }

    @Test fun `skipForward on a finished state is a no-op`() {
        val done = tick(start(smallPlan()), smallPlan().totalDurationSec)
        assertEquals(done, skipForward(done))
    }

    // ---- skipBack ----

    @Test fun `skipBack from a later step returns to the previous step at full duration`() {
        // On COOLDOWN (index 3); skip back -> BREAK (index 2) at full 1s.
        val s = tick(start(smallPlan()), 6) // COOLDOWN (index 3)
        val back = skipBack(s)
        assertEquals(StepType.BREAK, back.phase)
        assertEquals(2, back.index)
        assertEquals(1, back.remainingSec)
    }

    @Test fun `skipBack is clamped to the first step and resets it to full duration`() {
        val s0 = start(smallPlan()) // GET_READY index 0, remaining 2
        val s1 = tick(s0, 1)        // remaining 1
        val back = skipBack(s1)
        assertEquals(0, back.index)
        assertEquals(2, back.remainingSec)
        assertEquals(StepType.GET_READY, back.phase)
    }

    @Test fun `skipBack from finished state un-finishes onto the last step`() {
        val done = tick(start(smallPlan()), smallPlan().totalDurationSec)
        val back = skipBack(done)
        assertFalse(back.isFinished)
        assertEquals(smallPlan().steps.lastIndex, back.index)
        assertEquals(StepType.COOLDOWN, back.phase)
        assertEquals(smallPlan().steps.last().durationSec, back.remainingSec)
    }

    @Test fun `skipBack does not un-credit elapsed time`() {
        val s0 = tick(start(smallPlan()), 3) // elapsed 3, on EXERCISE
        val back = skipBack(s0)
        assertEquals(3, back.totalElapsedSec) // monotonic: unchanged
    }

    // ---- restart ----

    @Test fun `restart returns to step 0 full duration and zero elapsed`() {
        val mid = tick(start(smallPlan()), 4)
        val r = restart(mid)
        assertEquals(0, r.index)
        assertEquals(2, r.remainingSec)
        assertEquals(0, r.totalElapsedSec)
        assertEquals(StepType.GET_READY, r.phase)
        assertFalse(r.isFinished)
    }

    @Test fun `restart of a finished state yields a fresh unfinished state`() {
        val done = tick(start(smallPlan()), smallPlan().totalDurationSec)
        val r = restart(done)
        assertFalse(r.isFinished)
        assertEquals(0, r.totalElapsedSec)
    }

    // ---- defaults drive a full real plan ----

    @Test fun `timer walks the full default HIIT plan to DONE`() {
        val plan = buildHiitPlan(WorkoutConfig.hiitDefault())
        var state = start(plan)
        state = tick(state, plan.totalDurationSec)
        assertTrue(state.isFinished)
        assertEquals(plan.totalDurationSec, state.totalElapsedSec)
    }

    @Test fun `timer walks the full default Raised plan to DONE`() {
        val plan = buildRaisedPlan(WorkoutConfig.raisedDefault())
        var state = start(plan)
        state = tick(state, plan.totalDurationSec)
        assertTrue(state.isFinished)
        assertEquals(plan.totalDurationSec, state.totalElapsedSec)
    }
}
