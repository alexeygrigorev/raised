package com.raised.app.ui.session

import androidx.lifecycle.SavedStateHandle
import com.raised.app.data.Preset
import com.raised.app.ui.FakePresetRepository
import com.raised.core.StepType
import com.raised.core.WorkoutConfig
import com.raised.core.WorkoutType
import com.raised.core.buildHiitPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Plain-JVM tests for [SessionViewModel] using a [FakePresetRepository] (no
 * Room, no Robolectric). The per-second advance is driven directly through the
 * [SessionViewModel.onTickSecond] seam — one call == one `tick(state, 1)` of the
 * pure engine — so the engine stepping is verified deterministically with no
 * wall clock and no virtual-clock `delay` interaction. `Dispatchers.Main` is an
 * UNCONFINED test dispatcher so stateIn/repo collectors emit eagerly.
 *
 * Proves: the engine steps through correctly per second, skip-forward/skip-back
 * follow the engine transitions, pause halts progress (no state change across a
 * tick while paused), and ticking to the end sets `isFinished`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Latest observed UI state, mirrored by the warm background collector. */
    private val latestState = kotlinx.coroutines.flow.MutableStateFlow<SessionUiState?>(null)

    /**
     * A tiny plan whose steps are easy to reason about. The HIIT builder always
     * emits GET_READY then WARMUP (even at 0s), so the resulting steps are:
     *   GET_READY(10), WARMUP(0), EXERCISE(45 "Push-ups"), BREAK(15),
     *   EXERCISE(45 "Squats"), CHALLENGE(0), COOLDOWN(5)
     * total = 120s.
     */
    private fun tinyConfig(): WorkoutConfig = WorkoutConfig(
        type = WorkoutType.HIIT,
        exercises = listOf("Push-ups", "Squats"),
        exerciseSecs = 45,
        breakSecs = 15,
        longBreakSecs = 60,
        rounds = 1,
        sets = 0,
        warmupSecs = 0,
        challengeSecs = 0,
        cooldownSecs = 5,
        getReadySecs = 10,
    )

    private fun TestScope.buildVm(
        type: WorkoutType = WorkoutType.HIIT,
        presets: List<Preset> = emptyList(),
    ): SessionViewModel {
        val repo = FakePresetRepository(presets)
        val savedState = SavedStateHandle(mapOf("type" to type.name))
        val vm = SessionViewModel(savedState, repo, mainDispatcher)
        // Keep a warm subscriber so WhileSubscribed stays active.
        backgroundScope.launch { vm.uiState.collect { latestState.value = it } }
        return vm
    }

    private suspend fun loaded(): SessionUiState = latestState.first { it != null && !it.loading }!!

    @Test
    fun `starts at the first step with full remaining and running`() = runTest(mainDispatcher) {
        val vm = buildVm(presets = listOf(Preset(1, "p", tinyConfig(), isDefault = true)))
        val state = loaded()
        assertEquals(StepType.GET_READY, state.step?.phase)
        assertEquals(10, state.remainingSec)
        assertTrue(state.running)
        assertFalse(state.isFinished)
    }

    @Test
    fun `ticking reduces remaining one second at a time`() = runTest(mainDispatcher) {
        val vm = buildVm(presets = listOf(Preset(1, "p", tinyConfig(), isDefault = true)))
        loaded()
        repeat(1) { vm.onTickSecond() }
        assertEquals(9, latestState.value!!.remainingSec)
        repeat(3) { vm.onTickSecond() }
        assertEquals(6, latestState.value!!.remainingSec)
    }

    @Test
    fun `ticking across a step boundary carries over into the next step`() = runTest(mainDispatcher) {
        val config = tinyConfig()
        val vm = buildVm(presets = listOf(Preset(1, "p", config, isDefault = true)))
        loaded()
        // GET_READY is 10s: 10 ticks finishes it, the 11th lands in EXERCISE.
        repeat(11) { vm.onTickSecond() }
        val state = latestState.value!!
        assertEquals(StepType.EXERCISE, state.step?.phase)
        assertEquals("Push-ups", state.step?.label)
        // 10s of get-ready consumed; 1s into the 45s exercise ⇒ remaining 44.
        assertEquals(44, state.remainingSec)
        assertEquals(11, state.progress?.totalElapsedSec)
    }

    @Test
    fun `skipForward advances to the next step`() = runTest(mainDispatcher) {
        val vm = buildVm(presets = listOf(Preset(1, "p", tinyConfig(), isDefault = true)))
        loaded()
        // GET_READY -> WARMUP -> EXERCISE (Push-ups). WARMUP is present even at 0s.
        vm.skipForward()
        assertEquals(StepType.WARMUP, latestState.value!!.step?.phase)
        vm.skipForward()
        val state = latestState.value!!
        assertEquals(StepType.EXERCISE, state.step?.phase)
        assertEquals("Push-ups", state.step?.label)
        assertEquals(45, state.remainingSec)
    }

    @Test
    fun `skipBack from later step returns to previous step at full duration`() = runTest(mainDispatcher) {
        val vm = buildVm(presets = listOf(Preset(1, "p", tinyConfig(), isDefault = true)))
        loaded()
        // GET_READY -> WARMUP -> EXERCISE (Push-ups) -> BREAK
        vm.skipForward()
        vm.skipForward()
        vm.skipForward()
        assertEquals(StepType.BREAK, latestState.value!!.step?.phase)
        vm.skipBack() // BREAK -> EXERCISE (Push-ups)
        val state = latestState.value!!
        assertEquals(StepType.EXERCISE, state.step?.phase)
        assertEquals("Push-ups", state.step?.label)
        assertEquals(45, state.remainingSec)
    }

    @Test
    fun `pause halts progress across ticks then resume continues`() = runTest(mainDispatcher) {
        val vm = buildVm(presets = listOf(Preset(1, "p", tinyConfig(), isDefault = true)))
        loaded()
        vm.onTickSecond()
        val beforePause = latestState.value!!.remainingSec
        vm.pause()
        repeat(5) { vm.onTickSecond() } // 5 ticks while paused: must not advance
        assertEquals(beforePause, latestState.value!!.remainingSec)
        assertFalse(latestState.value!!.running)
        vm.play()
        vm.onTickSecond()
        assertEquals(beforePause - 1, latestState.value!!.remainingSec)
        assertTrue(latestState.value!!.running)
    }

    @Test
    fun `ticking to the end sets isFinished and stops running`() = runTest(mainDispatcher) {
        val config = tinyConfig() // 120s total
        val vm = buildVm(presets = listOf(Preset(1, "p", config, isDefault = true)))
        loaded()
        repeat(buildHiitPlan(config).totalDurationSec) { vm.onTickSecond() }
        val state = latestState.value!!
        assertTrue(state.isFinished)
        assertFalse(state.running)
        assertEquals(0, state.remainingSec)
        assertEquals(buildHiitPlan(config).totalDurationSec, state.totalElapsedSecOnFinish)
        assertEquals(buildHiitPlan(config).steps.size, state.completedStepsOnFinish)
    }

    @Test
    fun `restart returns to step 0 and resumes`() = runTest(mainDispatcher) {
        val vm = buildVm(presets = listOf(Preset(1, "p", tinyConfig(), isDefault = true)))
        loaded()
        vm.skipForward()
        vm.skipForward()
        vm.restart()
        val state = latestState.value!!
        assertEquals(StepType.GET_READY, state.step?.phase)
        assertEquals(10, state.remainingSec)
        assertTrue(state.running)
    }

    @Test
    fun `uses the spec default when no preset of the type is stored`() = runTest(mainDispatcher) {
        val vm = buildVm(type = WorkoutType.HIIT, presets = emptyList())
        val state = loaded()
        assertNotNull(state.step)
        // hiitDefault builds GET_READY(10) first.
        assertEquals(10, state.remainingSec)
    }
}
