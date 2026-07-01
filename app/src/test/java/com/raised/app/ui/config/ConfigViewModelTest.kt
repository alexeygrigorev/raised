package com.raised.app.ui.config

import androidx.lifecycle.SavedStateHandle
import com.raised.app.data.Preset
import com.raised.app.ui.FakePresetRepository
import com.raised.core.WorkoutConfig
import com.raised.core.WorkoutType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Plain-JVM tests for [ConfigViewModel] using a [FakePresetRepository] (no Room,
 * no Robolectric). Covers: total recomputes on edit, save/load round-trip, and
 * exercise-list edits (rename/add/remove/move).
 *
 * The ViewModel publishes state via `stateIn(WhileSubscribed)`, so the test
 * subscribes with [first] to read the current state rather than touching
 * `.value` directly (no subscriber ⇒ no emission).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfigViewModelTest {

    private val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @org.junit.Before
    fun resetLatestState() {
        latestState.value = null
    }

    /** Latest observed UI state, mirrored by the warm background collector. */
    private val latestState = MutableStateFlow<ConfigUiState?>(null)

    private fun kotlinx.coroutines.test.TestScope.vm(
        type: WorkoutType = WorkoutType.HIIT,
        presets: List<Preset> = emptyList(),
    ): Pair<ConfigViewModel, FakePresetRepository> {
        val repo = FakePresetRepository(presets)
        val savedState = SavedStateHandle(mapOf("type" to type.name))
        val viewModel = ConfigViewModel(savedState, repo)
        warmSubscribe(viewModel)
        return viewModel to repo
    }

    /** Build a ConfigViewModel directly with a warm subscriber (for cases that
     *  need custom repo setup). */
    private fun kotlinx.coroutines.test.TestScope.configVm(
        repo: FakePresetRepository,
        type: WorkoutType = WorkoutType.HIIT,
    ): ConfigViewModel {
        val savedState = SavedStateHandle(mapOf("type" to type.name))
        val viewModel = ConfigViewModel(savedState, repo)
        warmSubscribe(viewModel)
        return viewModel
    }

    private fun kotlinx.coroutines.test.TestScope.warmSubscribe(viewModel: ConfigViewModel) {
        // Keep a warm subscriber for the whole test so WhileSubscribed stays
        // active and the StateFlow always reflects the latest edits.
        backgroundScope.launch { viewModel.uiState.collect { latestState.value = it } }
    }

    /** Read the current UI state from the warm collector (waits for first emit). */
    private suspend fun state(): ConfigUiState = latestState.first { it != null }!!

    @Test
    fun `bumping exerciseSecs raises the total duration`() = runTest(testDispatcher) {
        val (vm, _) = vm()
        advanceUntilIdle()

        val before = state().totalDurationSec
        val beforeWork = state().exerciseSecs

        // Each HIIT exercise step is repeated `rounds * exercises` times (24).
        vm.setExerciseSecs(beforeWork + 10)
        advanceUntilIdle()

        val after = state().totalDurationSec
        assertTrue("total went up: before=$before after=$after", after > before)
        // 24 exercise steps * 10s each = +240s.
        assertEquals(before + 240, after)
    }

    @Test
    fun `changing rounds changes total`() = runTest(testDispatcher) {
        val (vm, _) = vm()
        advanceUntilIdle()

        val before = state().totalDurationSec
        vm.setRounds(5)
        advanceUntilIdle()

        assertNotEquals(before, state().totalDurationSec)
        assertEquals(5, state().rounds)
    }

    @Test
    fun `save then load round-trips through the repository`() = runTest(testDispatcher) {
        val (vm, repo) = vm()
        advanceUntilIdle()

        // Edit something distinctive, then save.
        vm.setExerciseSecs(99)
        advanceUntilIdle()
        vm.saveAsPreset("My HIIT")
        advanceUntilIdle()

        val presets = state().presets
        val savedPreset = presets.first { it.name == "My HIIT" }
        val saved = repo.get(savedPreset.id)!!
        assertEquals(99, saved.config.exerciseSecs)

        // Mutate the working config, then load the preset back.
        vm.setExerciseSecs(20)
        advanceUntilIdle()
        assertEquals(20, state().exerciseSecs)

        vm.loadPreset(saved.id)
        advanceUntilIdle()
        assertEquals(99, state().exerciseSecs)
        assertEquals("Loaded \"My HIIT\"", state().message)
    }

    @Test
    fun `loadPreset with unknown id shows a not-found message`() = runTest(testDispatcher) {
        val (vm, _) = vm()
        advanceUntilIdle()

        vm.loadPreset(424242L)
        advanceUntilIdle()

        assertEquals("Preset not found", state().message)
    }

    @Test
    fun `rename exercise updates the exercise list`() = runTest(testDispatcher) {
        val (vm, _) = vm()
        advanceUntilIdle()

        vm.setExerciseName(0, "Burpees")
        advanceUntilIdle()

        assertEquals("Burpees", state().exercises.first())
    }

    @Test
    fun `add then remove exercise round-trips the list size`() = runTest(testDispatcher) {
        val (vm, _) = vm()
        advanceUntilIdle()
        val initialSize = state().exercises.size

        vm.addExercise("New")
        advanceUntilIdle()
        assertEquals(initialSize + 1, state().exercises.size)
        assertEquals("New", state().exercises.last())

        vm.removeExercise(state().exercises.lastIndex)
        advanceUntilIdle()
        assertEquals(initialSize, state().exercises.size)
    }

    @Test
    fun `move exercise reorders the list`() = runTest(testDispatcher) {
        val (vm, _) = vm(type = WorkoutType.HIIT)
        advanceUntilIdle()

        val original = state().exercises
        vm.moveExercise(0, 2)
        advanceUntilIdle()

        val moved = state().exercises
        // item originally at 0 is now at 2; items at 1,2 shifted left.
        assertEquals(original[0], moved[2])
        assertEquals(original[1], moved[0])
        assertEquals(original[2], moved[1])
    }

    @Test
    fun `consumeMessage clears the transient message`() = runTest(testDispatcher) {
        val (vm, _) = vm()
        advanceUntilIdle()

        vm.saveAsPreset("x")
        advanceUntilIdle()
        assertTrue(state().message != null)

        vm.consumeMessage()
        advanceUntilIdle()
        assertNull(state().message)
    }

    @Test
    fun `seeds working config from stored default preset`() = runTest(testDispatcher) {
        val stored = WorkoutConfig.hiitDefault().copy(breakSecs = 30)
        val repo = FakePresetRepository(listOf(Preset(1, "HIIT", stored, isDefault = true)))
        val vm = configVm(repo, type = WorkoutType.HIIT)
        advanceUntilIdle()

        assertEquals(30, state().breakSecs)
    }

    @Test
    fun `raised type uses sets not rounds`() = runTest(testDispatcher) {
        val (vm, _) = vm(type = WorkoutType.RAISED)
        advanceUntilIdle()

        val s = state()
        assertEquals(WorkoutType.RAISED, s.type)
        // Raised default total matches the plan builder exactly.
        assertEquals(
            com.raised.core.buildRaisedPlan(WorkoutConfig.raisedDefault()).totalDurationSec,
            s.totalDurationSec,
        )
    }
}
