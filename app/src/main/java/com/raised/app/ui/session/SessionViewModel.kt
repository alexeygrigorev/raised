package com.raised.app.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raised.app.data.PresetRepository
import com.raised.app.di.DefaultDispatcher
import com.raised.core.SessionState
import com.raised.core.Step
import com.raised.core.StepType
import com.raised.core.WorkoutConfig
import com.raised.core.WorkoutType
import com.raised.core.buildPlan
import com.raised.core.restart
import com.raised.core.skipBack
import com.raised.core.skipForward
import com.raised.core.start
import com.raised.core.tick
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the screen renders for the current step. The phase drives the
 * background colour; [label] is the human name ("Warm-up", the exercise name,
 * "Break", ...); [nextLabel] is the "next up" preview, or null on the last step
 * / when finished.
 */
data class SessionStepView(
    val phase: StepType,
    val label: String,
    val nextLabel: String?,
)

/** Counts for the round/set + overall progress readout. */
data class SessionProgress(
    val stepIndex: Int,       // 0-based, of the current step
    val stepCount: Int,       // plan.steps.size
    val totalElapsedSec: Int,
    val totalDurationSec: Int,
)

/**
 * Immutable Session screen state projected from the pure [SessionState]. The
 * engine ([start]/[tick]/[skipForward]/[skipBack]/[restart]) is the ONLY source
 * of truth for time; this class only formats it for Compose.
 *
 * @property loading      true until the current config has resolved.
 * @property running      whether the per-second ticker is advancing.
 * @property isFinished   mirrors [SessionState.isFinished].
 * @property remainingSec mirrors [SessionState.remainingSec] — the big countdown.
 * @property step         the current step view (phase/label/next up), or null
 *                        while loading.
 * @property progress     round/overall progress counts, or null while loading.
 * @property totalElapsedSecOnFinish captured total once finished (for the
 *                        summary); equals [SessionProgress.totalElapsedSec].
 */
data class SessionUiState(
    val loading: Boolean = true,
    val running: Boolean = false,
    val isFinished: Boolean = false,
    val remainingSec: Int = 0,
    val step: SessionStepView? = null,
    val progress: SessionProgress? = null,
    val totalElapsedSecOnFinish: Int = 0,
    val completedStepsOnFinish: Int = 0,
)

private const val TICK_PERIOD_MS = 1_000L
private const val TICK_DELTA_SEC = 1

/**
 * Session screen ViewModel. Resolves the current stored config for the workout
 * `type` nav arg, builds the [com.raised.core.WorkoutPlan], and drives the pure
 * timer engine with a 1-second ticker.
 *
 * Foreground-only (D6): the ticker is a coroutine in [viewModelScope] launched
 * only while the UI is collecting [uiState] ([SharingStarted.WhileSubscribed]).
 * Pause ([toggleRunning]) stops the advance; resume continues. The engine is
 * the single source of truth — the ViewModel never decrements time itself.
 *
 * Testability: the ticker coroutine runs on the injected [DefaultDispatcher].
 * Tests inject a [kotlinx.coroutines.test.TestDispatcher] and drive ticks with
 * `advanceTimeBy(TICK_PERIOD_MS)` instead of the real wall clock, so behaviour
 * is fully deterministic. Pause is verified by asserting no state change while
 * paused across an `advanceTimeBy`.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PresetRepository,
    @DefaultDispatcher private val tickerDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val type: WorkoutType = WorkoutType.valueOf(
        savedStateHandle.get<String>("type") ?: WorkoutType.HIIT.name,
    )

    // The live engine state. Seeded with a placeholder until the plan resolves;
    // once the repo emits, [seedPlan] swaps in `start(plan)`.
    private val engineState = MutableStateFlow<SessionState?>(null)

    // Whether the ticker should be advancing. Toggled by play/pause; cleared by
    // finish. Separate from the subscription so "paused but still on screen" is
    // representable.
    private val running = MutableStateFlow(false)

    val uiState: StateFlow<SessionUiState> = combine(
        engineState,
        running,
    ) { state, isRunning ->
        if (state == null) {
            SessionUiState(loading = true)
        } else {
            project(state, isRunning)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SessionUiState(loading = true),
    )

    init {
        // Resolve the current config of `type` and start the engine. WhileSubscribed
        // means the UI only collects while foregrounded, so this collector too only
        // runs while the screen is up — consistent with D6.
        viewModelScope.launch {
            repository.observeAll().collect { presets ->
                if (engineState.value == null) {
                    val config = PresetRepository.currentConfig(presets, type)
                        ?: fallbackDefault(type)
                    engineState.value = start(buildPlan(config))
                    // Auto-start running once the plan is ready.
                    running.value = true
                }
            }
        }
    }

    // ---- controls ----

    /** Toggle play/pause. No-op once finished. */
    fun toggleRunning() {
        val state = engineState.value ?: return
        if (state.isFinished) return
        running.value = !running.value
    }

    /** Explicit play (resume). */
    fun play() {
        val state = engineState.value ?: return
        if (state.isFinished) return
        running.value = true
    }

    /** Explicit pause — stops the ticker advancing. */
    fun pause() {
        running.value = false
    }

    /** Skip to the next step at full duration. */
    fun skipForward() {
        mutate { skipForward(it) }
    }

    /** Jump back to the previous step at full duration. */
    fun skipBack() {
        mutate { skipBack(it) }
    }

    /** Restart the whole session from step 0 (and resume play). */
    fun restart() {
        val current = engineState.value ?: return
        engineState.value = restart(current)
        running.value = true
    }

    // ---- ticker ----

    private var tickerJob: Job? = null

    /**
     * Launch the per-second ticker. Started from the screen composition (tied to
     * its lifecycle) so the timer advances ONLY while the screen is foregrounded
     * — the foreground-only rule (D6). Cancelling the job (screen leaves the
     * composition / process death) stops advancing. Idempotent: a second call
     * while running is a no-op.
     */
    fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch(tickerDispatcher) {
            while (isActive) {
                delay(TICK_PERIOD_MS)
                onTickSecond()
            }
        }
    }

    /**
     * Advance the engine by exactly one second — the single tick seam. Called by
     * the [startTicker] loop in production, and directly by unit tests so the
     * engine stepping is verified deterministically without any wall clock.
     * Honours pause/finish: a no-op when paused or already finished.
     */
    fun onTickSecond() {
        val current = engineState.value ?: return
        if (current.isFinished || !running.value) return
        engineState.value = tick(current, TICK_DELTA_SEC)
        if (engineState.value?.isFinished == true) {
            running.value = false
        }
    }

    /** Cancel the ticker (screen leaving the composition). Idempotent. */
    fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    // ---- internals ----

    /** Apply an engine transition and clear `running` if it finishes. */
    private fun mutate(transform: (SessionState) -> SessionState) {
        val current = engineState.value ?: return
        val next = transform(current)
        engineState.value = next
        if (next.isFinished) running.value = false
    }

    private fun project(state: SessionState, isRunning: Boolean): SessionUiState {
        val steps = state.plan.steps
        val current = steps[state.index]
        val next = steps.getOrNull(state.index + 1)
        return SessionUiState(
            loading = false,
            running = isRunning && !state.isFinished,
            isFinished = state.isFinished,
            remainingSec = state.remainingSec,
            step = SessionStepView(
                phase = state.phase,
                label = labelFor(current),
                nextLabel = next?.let(::labelFor),
            ),
            progress = SessionProgress(
                stepIndex = state.index,
                stepCount = steps.size,
                totalElapsedSec = state.totalElapsedSec,
                totalDurationSec = state.plan.totalDurationSec,
            ),
            // On finish, every step is "completed"; before that the count is the
            // number of fully-consumed steps (the cursor index).
            totalElapsedSecOnFinish = if (state.isFinished) state.plan.totalDurationSec else 0,
            completedStepsOnFinish = if (state.isFinished) steps.size else state.index,
        )
    }

    /** Human label for a step: its exercise name if it has one, else the phase. */
    private fun labelFor(step: Step): String = step.label ?: phaseLabel(step.type)

    private fun phaseLabel(type: StepType): String = when (type) {
        StepType.GET_READY -> "Get Ready"
        StepType.WARMUP -> "Warm-up"
        StepType.EXERCISE -> "Exercise"
        StepType.BREAK -> "Break"
        StepType.LONG_BREAK -> "Long Break"
        StepType.CHALLENGE -> "Challenge"
        StepType.COOLDOWN -> "Cool-down"
        StepType.DONE -> "Done"
    }

    private fun fallbackDefault(type: WorkoutType): WorkoutConfig = when (type) {
        WorkoutType.HIIT -> WorkoutConfig.hiitDefault()
        WorkoutType.RAISED -> WorkoutConfig.raisedDefault()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
