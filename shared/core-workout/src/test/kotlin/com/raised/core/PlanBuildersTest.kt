package com.raised.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Builder tests. Asserts the EXACT step sequence + durations for the default
 * HIIT and Raised configs (acceptance criteria: counts, types, order, per-step
 * durations, total). Expected values are derived from the algorithm, not
 * hard-coded magic.
 */
class PlanBuildersTest {

    // ---- HIIT ----

    @Test fun `hiit default has 51 steps totalling 1765 seconds`() {
        val plan = buildHiitPlan(WorkoutConfig.hiitDefault())
        // 2 (get-ready + warmup)
        // + rounds * (exercises + breaks) = 3 * (8 + 7) = 45
        // + (rounds - 1) long breaks       = 2
        // + 2 (challenge + cooldown)
        // = 2 + 45 + 2 + 2 = 51
        val expectedSteps = 2 + 3 * (8 + 7) + 2 + 2
        assertEquals(expectedSteps, plan.steps.size)
        assertEquals(1765, plan.totalDurationSec)
    }

    @Test fun `hiit default step sequence is exactly as specified`() {
        val cfg = WorkoutConfig.hiitDefault()
        val plan = buildHiitPlan(cfg)

        val expected = expectedHiitSteps(cfg)
        assertEquals(expected.size, plan.steps.size)
        for (i in plan.steps.indices) {
            assertEquals("step $i type", expected[i].type, plan.steps[i].type)
            assertEquals("step $i duration", expected[i].durationSec, plan.steps[i].durationSec)
            assertEquals("step $i exerciseIndex", expected[i].exerciseIndex, plan.steps[i].exerciseIndex)
            assertEquals("step $i label", expected[i].label, plan.steps[i].label)
        }
    }

    @Test fun `hiit default per-type counts are correct`() {
        val plan = buildHiitPlan(WorkoutConfig.hiitDefault())
        // GET_READY x1, WARMUP x1, EXERCISE x(8*3)=24, BREAK x(7*3)=21,
        // LONG_BREAK x2, CHALLENGE x1, COOLDOWN x1.
        assertEquals(1, plan.steps.count { it.type == StepType.GET_READY })
        assertEquals(1, plan.steps.count { it.type == StepType.WARMUP })
        assertEquals(24, plan.steps.count { it.type == StepType.EXERCISE })
        assertEquals(21, plan.steps.count { it.type == StepType.BREAK })
        assertEquals(2, plan.steps.count { it.type == StepType.LONG_BREAK })
        assertEquals(1, plan.steps.count { it.type == StepType.CHALLENGE })
        assertEquals(1, plan.steps.count { it.type == StepType.COOLDOWN })
        assertEquals(0, plan.steps.count { it.type == StepType.DONE })
    }

    @Test fun `hiit first and last steps are get-ready and cooldown`() {
        val plan = buildHiitPlan(WorkoutConfig.hiitDefault())
        assertEquals(StepType.GET_READY, plan.steps.first().type)
        assertEquals(10, plan.steps.first().durationSec)
        assertEquals(StepType.COOLDOWN, plan.steps.last().type)
        assertEquals(120, plan.steps.last().durationSec)
    }

    @Test fun `hiit exercise steps carry index and label, others are null`() {
        val plan = buildHiitPlan(WorkoutConfig.hiitDefault())
        val exercises = plan.steps.filter { it.type == StepType.EXERCISE }
        assertTrue(exercises.all { it.exerciseIndex != null && it.label != null })
        // First exercise is index 0 = "Jumping Jacks".
        assertEquals(0, exercises.first().exerciseIndex)
        assertEquals("Jumping Jacks", exercises.first().label)
        // Index cycles 0..7 across all three rounds.
        val indices = exercises.map { it.exerciseIndex }
        assertEquals((0..7).toList() + (0..7).toList() + (0..7).toList(), indices)
        // Non-exercise steps have no index/label.
        plan.steps.filter { it.type != StepType.EXERCISE }.forEach {
            assertNull(it.exerciseIndex)
            assertNull(it.label)
        }
    }

    @Test fun `hiit long break only appears between rounds, never after the last`() {
        val plan = buildHiitPlan(WorkoutConfig.hiitDefault())
        // Layout: [0]GET_READY [1]WARMUP, then each round = 8 EXERCISE + 7 BREAK
        // = 15 steps. Round 1 spans 2..16 (last BREAK at 16), so LONG_BREAK after
        // round 1 is at 17. Round 2 spans 18..32 (last BREAK at 32), LONG_BREAK
        // at 33. Round 3 spans 34..48, then CHALLENGE at 49, COOLDOWN at 50.
        val longBreakPositions = plan.steps.mapIndexed { i, s -> if (s.type == StepType.LONG_BREAK) i else -1 }
            .filter { it >= 0 }
        assertEquals(listOf(17, 33), longBreakPositions)
        // Step before CHALLENGE is the last exercise of the final round.
        val challengeAt = plan.steps.indexOfFirst { it.type == StepType.CHALLENGE }
        assertEquals(49, challengeAt)
        assertEquals(StepType.EXERCISE, plan.steps[challengeAt - 1].type)
        assertEquals(7, plan.steps[challengeAt - 1].exerciseIndex)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `hiit rejects zero exercises`() {
        buildHiitPlan(WorkoutConfig.hiitDefault().copy(exercises = emptyList()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `hiit rejects zero rounds`() {
        buildHiitPlan(WorkoutConfig.hiitDefault().copy(rounds = 0))
    }

    @Test fun `hiit single round single exercise has no breaks`() {
        val cfg = WorkoutConfig.hiitDefault().copy(
            exercises = listOf("Only"),
            rounds = 1,
        )
        val plan = buildHiitPlan(cfg)
        // GET_READY, WARMUP, EXERCISE, CHALLENGE, COOLDOWN — no breaks at all.
        assertEquals(
            listOf(StepType.GET_READY, StepType.WARMUP, StepType.EXERCISE, StepType.CHALLENGE, StepType.COOLDOWN),
            plan.steps.map { it.type },
        )
    }

    @Test fun `hiit omits zero-duration warmup, challenge and cooldown`() {
        val cfg = WorkoutConfig.hiitDefault().copy(
            warmupSecs = 0,
            challengeSecs = 0,
            cooldownSecs = 0,
        )
        val plan = buildHiitPlan(cfg)

        // None of the collapsed blocks appear.
        assertEquals(0, plan.steps.count { it.type == StepType.WARMUP })
        assertEquals(0, plan.steps.count { it.type == StepType.CHALLENGE })
        assertEquals(0, plan.steps.count { it.type == StepType.COOLDOWN })
        // GET_READY and the exercise sequence are untouched.
        assertEquals(1, plan.steps.count { it.type == StepType.GET_READY })
        assertEquals(24, plan.steps.count { it.type == StepType.EXERCISE })
        // Plan now starts at GET_READY and ends on the final exercise.
        assertEquals(StepType.GET_READY, plan.steps.first().type)
        assertEquals(StepType.EXERCISE, plan.steps.last().type)
    }

    @Test fun `hiit collapsing a zero-duration block is total-neutral`() {
        // A 0s block contributes 0 either way, so omitting it must not change the
        // total vs. a plan that still carries the 0s step (modelled by the
        // expected-sequence helper, which mirrors the old unconditional emit).
        val zeroed = WorkoutConfig.hiitDefault().copy(warmupSecs = 0, challengeSecs = 0, cooldownSecs = 0)
        val collapsed = buildHiitPlan(zeroed).totalDurationSec
        // Default total minus the three zeroed block values (60 + 60 + 120).
        assertEquals(1765 - 60 - 60 - 120, collapsed)
        // And the kept sequence (get-ready + exercises/rounds + long breaks) sums
        // to exactly the same number — the dropped steps added nothing.
        assertEquals(
            WorkoutConfig.hiitDefault().let {
                it.getReadySecs + it.rounds * (8 * it.exerciseSecs + 7 * it.breakSecs) +
                    (it.rounds - 1) * it.longBreakSecs
            },
            collapsed,
        )
    }

    @Test fun `hiit collapses only the blocks set to zero, keeping the rest`() {
        // Only challenge zeroed; warmup and cooldown remain in place and ordered.
        val cfg = WorkoutConfig.hiitDefault().copy(challengeSecs = 0)
        val plan = buildHiitPlan(cfg)
        assertEquals(1, plan.steps.count { it.type == StepType.WARMUP })
        assertEquals(0, plan.steps.count { it.type == StepType.CHALLENGE })
        assertEquals(1, plan.steps.count { it.type == StepType.COOLDOWN })
        // Order of the kept tail blocks: last exercise directly into COOLDOWN.
        val cooldownAt = plan.steps.indexOfLast { it.type == StepType.COOLDOWN }
        assertEquals(StepType.EXERCISE, plan.steps[cooldownAt - 1].type)
    }

    // ---- Raised ----

    @Test fun `raised default has 39 steps totalling 1900 seconds`() {
        val plan = buildRaisedPlan(WorkoutConfig.raisedDefault())
        // 2 (get-ready + warmup)
        // + exercises * (sets + breaks) = 6 * (3 + 2) = 30
        // + (exercises - 1) long breaks   = 5
        // + 2 (challenge + cooldown)
        // = 2 + 30 + 5 + 2 = 39
        val expectedSteps = 2 + 6 * (3 + 2) + 5 + 2
        assertEquals(expectedSteps, plan.steps.size)
        assertEquals(1900, plan.totalDurationSec)
    }

    @Test fun `raised default step sequence is exactly as specified`() {
        val cfg = WorkoutConfig.raisedDefault()
        val plan = buildRaisedPlan(cfg)

        val expected = expectedRaisedSteps(cfg)
        assertEquals(expected.size, plan.steps.size)
        for (i in plan.steps.indices) {
            assertEquals("step $i type", expected[i].type, plan.steps[i].type)
            assertEquals("step $i duration", expected[i].durationSec, plan.steps[i].durationSec)
            assertEquals("step $i exerciseIndex", expected[i].exerciseIndex, plan.steps[i].exerciseIndex)
            assertEquals("step $i label", expected[i].label, plan.steps[i].label)
        }
    }

    @Test fun `raised default per-type counts are correct`() {
        val plan = buildRaisedPlan(WorkoutConfig.raisedDefault())
        // GET_READY x1, WARMUP x1, EXERCISE x(6*3)=18, BREAK x(6*2)=12,
        // LONG_BREAK x5, CHALLENGE x1, COOLDOWN x1.
        assertEquals(1, plan.steps.count { it.type == StepType.GET_READY })
        assertEquals(1, plan.steps.count { it.type == StepType.WARMUP })
        assertEquals(18, plan.steps.count { it.type == StepType.EXERCISE })
        assertEquals(12, plan.steps.count { it.type == StepType.BREAK })
        assertEquals(5, plan.steps.count { it.type == StepType.LONG_BREAK })
        assertEquals(1, plan.steps.count { it.type == StepType.CHALLENGE })
        assertEquals(1, plan.steps.count { it.type == StepType.COOLDOWN })
    }

    @Test fun `raised exercise index repeats each exercise sets times then advances`() {
        val plan = buildRaisedPlan(WorkoutConfig.raisedDefault())
        val indices = plan.steps.filter { it.type == StepType.EXERCISE }.map { it.exerciseIndex }
        // 0 0 0 1 1 1 2 2 2 3 3 3 4 4 4 5 5 5
        val expected = (0..5).flatMap { e -> List(3) { e } }
        assertEquals(expected, indices)
    }

    @Test fun `raised omits zero-duration warmup, challenge and cooldown`() {
        val cfg = WorkoutConfig.raisedDefault().copy(
            warmupSecs = 0,
            challengeSecs = 0,
            cooldownSecs = 0,
        )
        val plan = buildRaisedPlan(cfg)

        assertEquals(0, plan.steps.count { it.type == StepType.WARMUP })
        assertEquals(0, plan.steps.count { it.type == StepType.CHALLENGE })
        assertEquals(0, plan.steps.count { it.type == StepType.COOLDOWN })
        assertEquals(1, plan.steps.count { it.type == StepType.GET_READY })
        assertEquals(18, plan.steps.count { it.type == StepType.EXERCISE })
        assertEquals(StepType.GET_READY, plan.steps.first().type)
        assertEquals(StepType.EXERCISE, plan.steps.last().type)
    }

    @Test fun `raised collapsing a zero-duration block is total-neutral`() {
        val zeroed = WorkoutConfig.raisedDefault().copy(warmupSecs = 0, challengeSecs = 0, cooldownSecs = 0)
        val collapsed = buildRaisedPlan(zeroed).totalDurationSec
        // Default total minus the three zeroed block values (60 + 60 + 120).
        assertEquals(1900 - 60 - 60 - 120, collapsed)
        // The kept sequence (get-ready + sets per exercise + long breaks) sums to
        // the same number — the dropped 0s steps added nothing.
        assertEquals(
            WorkoutConfig.raisedDefault().let {
                it.getReadySecs + 6 * (3 * it.exerciseSecs + 2 * it.breakSecs) +
                    (6 - 1) * it.longBreakSecs
            },
            collapsed,
        )
    }

    @Test fun `raised collapses only the blocks set to zero, keeping the rest`() {
        val cfg = WorkoutConfig.raisedDefault().copy(cooldownSecs = 0)
        val plan = buildRaisedPlan(cfg)
        assertEquals(1, plan.steps.count { it.type == StepType.WARMUP })
        assertEquals(1, plan.steps.count { it.type == StepType.CHALLENGE })
        assertEquals(0, plan.steps.count { it.type == StepType.COOLDOWN })
        // With cooldown gone the plan ends on CHALLENGE.
        assertEquals(StepType.CHALLENGE, plan.steps.last().type)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `raised rejects zero exercises`() {
        buildRaisedPlan(WorkoutConfig.raisedDefault().copy(exercises = emptyList()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `raised rejects zero sets`() {
        buildRaisedPlan(WorkoutConfig.raisedDefault().copy(sets = 0))
    }

    // ---- dispatch ----

    @Test fun `buildPlan dispatches on type`() {
        assertEquals(
            buildHiitPlan(WorkoutConfig.hiitDefault()).steps.size,
            buildPlan(WorkoutConfig.hiitDefault()).steps.size,
        )
        assertEquals(
            buildRaisedPlan(WorkoutConfig.raisedDefault()).steps.size,
            buildPlan(WorkoutConfig.raisedDefault()).steps.size,
        )
    }

    // ---- helpers: re-derive the expected sequence straight from the algorithm ----

    private fun expectedHiitSteps(cfg: WorkoutConfig): List<Step> {
        val out = mutableListOf<Step>()
        out += Step(StepType.GET_READY, cfg.getReadySecs)
        if (cfg.warmupSecs > 0) out += Step(StepType.WARMUP, cfg.warmupSecs)
        val last = cfg.exercises.lastIndex
        for (round in 1..cfg.rounds) {
            for (i in cfg.exercises.indices) {
                out += Step(StepType.EXERCISE, cfg.exerciseSecs, i, cfg.exercises[i])
                if (i < last) out += Step(StepType.BREAK, cfg.breakSecs)
            }
            if (round < cfg.rounds) out += Step(StepType.LONG_BREAK, cfg.longBreakSecs)
        }
        if (cfg.challengeSecs > 0) out += Step(StepType.CHALLENGE, cfg.challengeSecs)
        if (cfg.cooldownSecs > 0) out += Step(StepType.COOLDOWN, cfg.cooldownSecs)
        return out
    }

    private fun expectedRaisedSteps(cfg: WorkoutConfig): List<Step> {
        val out = mutableListOf<Step>()
        out += Step(StepType.GET_READY, cfg.getReadySecs)
        if (cfg.warmupSecs > 0) out += Step(StepType.WARMUP, cfg.warmupSecs)
        val last = cfg.exercises.lastIndex
        for (e in cfg.exercises.indices) {
            for (set in 1..cfg.sets) {
                out += Step(StepType.EXERCISE, cfg.exerciseSecs, e, cfg.exercises[e])
                if (set < cfg.sets) out += Step(StepType.BREAK, cfg.breakSecs)
            }
            if (e < last) out += Step(StepType.LONG_BREAK, cfg.longBreakSecs)
        }
        if (cfg.challengeSecs > 0) out += Step(StepType.CHALLENGE, cfg.challengeSecs)
        if (cfg.cooldownSecs > 0) out += Step(StepType.COOLDOWN, cfg.cooldownSecs)
        return out
    }
}
