package com.raised.app.ui.home

import com.raised.app.data.Preset
import com.raised.app.ui.FakePresetRepository
import com.raised.core.WorkoutConfig
import com.raised.core.WorkoutType
import com.raised.core.buildHiitPlan
import com.raised.core.buildRaisedPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
 * Plain-JVM tests for [HomeViewModel] using a [FakePresetRepository] (no Room,
 * no Robolectric). Covers: loading both workout summaries, totals computed from
 * the plan builders, and the fallback to spec defaults when nothing is stored.
 *
 * Uses an [UnconfinedTestDispatcher] as `Dispatchers.Main` and reads state by
 * subscribing to the StateFlow ([first]) — `WhileSubscribed` only emits while a
 * collector is active, so a bare `.value` read would see only the initial state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Collect the first non-loading state by subscribing to the StateFlow. */
    private suspend fun HomeViewModel.loaded(): HomeUiState =
        uiState.first { !it.loading }

    @Test
    fun `loads both workout summaries with totals from the plan builder`() = runTest {
        val hiit = WorkoutConfig.hiitDefault()
        val raised = WorkoutConfig.raisedDefault()
        val repo = FakePresetRepository(
            listOf(
                Preset(1, "HIIT", hiit, isDefault = true),
                Preset(2, "Raised", raised, isDefault = true),
            ),
        )
        val vm = HomeViewModel(repo)

        val state = vm.loaded()
        assertFalse(state.loading)
        assertEquals(buildHiitPlan(hiit).totalDurationSec, state.hiit?.totalDurationSec)
        assertEquals(buildRaisedPlan(raised).totalDurationSec, state.raised?.totalDurationSec)
        assertEquals(WorkoutType.HIIT, state.hiit?.type)
        assertEquals(WorkoutType.RAISED, state.raised?.type)
    }

    @Test
    fun `falls back to spec defaults when no presets are stored`() = runTest {
        val repo = FakePresetRepository(emptyList())
        val vm = HomeViewModel(repo)

        val state = vm.loaded()
        assertNotNull(state.hiit)
        assertNotNull(state.raised)
        assertEquals(
            buildHiitPlan(WorkoutConfig.hiitDefault()).totalDurationSec,
            state.hiit?.totalDurationSec,
        )
    }

    @Test
    fun `non-default saved preset of a type is used when no seeded default exists`() = runTest {
        val edited = WorkoutConfig.hiitDefault().copy(rounds = 5)
        val repo = FakePresetRepository(
            listOf(Preset(10, "My HIIT", edited, isDefault = false)),
        )
        val vm = HomeViewModel(repo)

        val state = vm.loaded()
        val hiit = state.hiit!!
        assertEquals(5, hiit.config.rounds)
        assertEquals(buildHiitPlan(edited).totalDurationSec, hiit.totalDurationSec)
    }

    @Test
    fun `seeded default takes priority over later saved preset of same type`() = runTest {
        val default = WorkoutConfig.hiitDefault()
        val custom = WorkoutConfig.hiitDefault().copy(rounds = 2)
        val repo = FakePresetRepository(
            listOf(
                Preset(1, "HIIT", default, isDefault = true),
                Preset(2, "Hard", custom, isDefault = false),
            ),
        )
        val vm = HomeViewModel(repo)

        assertEquals(default.rounds, vm.loaded().hiit?.config?.rounds)
    }

    @Test
    fun `total duration is non-zero for both default workouts`() = runTest {
        val repo = FakePresetRepository(
            listOf(
                Preset(1, "HIIT", WorkoutConfig.hiitDefault(), isDefault = true),
                Preset(2, "Raised", WorkoutConfig.raisedDefault(), isDefault = true),
            ),
        )
        val vm = HomeViewModel(repo)
        val state = vm.loaded()
        assertTrue("HIIT total > 0", (state.hiit?.totalDurationSec ?: 0) > 0)
        assertTrue("Raised total > 0", (state.raised?.totalDurationSec ?: 0) > 0)
    }
}
