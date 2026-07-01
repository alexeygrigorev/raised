package com.raised.app.ui.config

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raised.app.data.Preset
import com.raised.app.data.PresetRepository
import com.raised.core.Step
import com.raised.core.WorkoutConfig
import com.raised.core.WorkoutType
import com.raised.core.buildPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Read-only projection of the working config for the UI: the live total
 * duration (rebuilt from the plan on every edit), a short step preview, and the
 * editable values mirrored from the working [WorkoutConfig]. Edits go through
 * the ViewModel's mutator functions; composables never mutate state directly.
 */
data class ConfigUiState(
    val type: WorkoutType,
    val exerciseSecs: Int = 0,
    val breakSecs: Int = 0,
    val longBreakSecs: Int = 0,
    val rounds: Int = 0,
    val sets: Int = 0,
    val warmupSecs: Int = 0,
    val challengeSecs: Int = 0,
    val cooldownSecs: Int = 0,
    val getReadySecs: Int = 0,
    val exercises: List<String> = emptyList(),
    val totalDurationSec: Int = 0,
    val stepCount: Int = 0,
    /** The first few step labels, for a quick preview under the total. */
    val preview: List<Step> = emptyList(),
    val presets: List<Preset> = emptyList(),
    /** A transient message (e.g. "Saved") the UI can show and clear. */
    val message: String? = null,
)

/**
 * Config screen ViewModel (decision D8). Owns the working [WorkoutConfig] for
 * the selected [WorkoutType] (passed as the `type` nav arg), recomputes the
 * plan + total on every edit, and round-trips presets through
 * [PresetRepository].
 *
 * The working config is seeded from the current stored config of that type (the
 * same default/latest logic Home uses) and then lives only in memory until the
 * user taps "Save as preset".
 */
@HiltViewModel
class ConfigViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PresetRepository,
) : ViewModel() {

    private val type: WorkoutType = WorkoutType.valueOf(
        savedStateHandle.get<String>("type") ?: WorkoutType.HIIT.name,
    )

    // Mutable working config, seeded lazily from the repo.
    private val workingConfig = kotlinx.coroutines.flow.MutableStateFlow(seedConfig())

    // Transient UI messages (save confirmation, load errors).
    private val messages = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    val uiState: StateFlow<ConfigUiState> = combine(
        workingConfig,
        repository.observeAll(),
        messages,
    ) { config, presets, message ->
        project(config, presets, message)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = project(workingConfig.value, emptyList(), null),
    )

    // ---- editors (each updates the working config; total recomputes via combine) ----

    fun setExerciseSecs(value: Int) = update { it.copy(exerciseSecs = value.coerceAtLeast(0)) }
    fun setBreakSecs(value: Int) = update { it.copy(breakSecs = value.coerceAtLeast(0)) }
    fun setLongBreakSecs(value: Int) = update { it.copy(longBreakSecs = value.coerceAtLeast(0)) }
    fun setRounds(value: Int) = update { it.copy(rounds = value.coerceAtLeast(1)) }
    fun setSets(value: Int) = update { it.copy(sets = value.coerceAtLeast(1)) }
    fun setWarmupSecs(value: Int) = update { it.copy(warmupSecs = value.coerceAtLeast(0)) }
    fun setChallengeSecs(value: Int) = update { it.copy(challengeSecs = value.coerceAtLeast(0)) }
    fun setCooldownSecs(value: Int) = update { it.copy(cooldownSecs = value.coerceAtLeast(0)) }
    fun setGetReadySecs(value: Int) = update { it.copy(getReadySecs = value.coerceAtLeast(0)) }

    fun setExerciseName(index: Int, name: String) = update {
        if (index !in it.exercises.indices) return@update it
        it.copy(exercises = it.exercises.toMutableList().also { list -> list[index] = name })
    }

    fun addExercise(name: String = "") = update {
        it.copy(exercises = it.exercises + name)
    }

    fun removeExercise(index: Int) = update {
        if (index !in it.exercises.indices) return@update it
        it.copy(exercises = it.exercises.toMutableList().also { list -> list.removeAt(index) })
    }

    /** Move the exercise at [from] to [to], shifting the intervening items. */
    fun moveExercise(from: Int, to: Int) = update {
        if (from !in it.exercises.indices || to !in it.exercises.indices) return@update it
        val list = it.exercises.toMutableList()
        list.add(to, list.removeAt(from))
        it.copy(exercises = list)
    }

    // ---- preset persistence ----

    /** Save the current working config as a new preset. */
    fun saveAsPreset(name: String) {
        val config = workingConfig.value
        viewModelScope.launch {
            repository.save(name, config)
            messages.value = "Saved \"$name\""
        }
    }

    /** Replace the working config with a stored preset's config. */
    fun loadPreset(id: Long) {
        viewModelScope.launch {
            val preset = repository.get(id)
            if (preset != null) {
                workingConfig.value = preset.config
                messages.value = "Loaded \"${preset.name}\""
            } else {
                messages.value = "Preset not found"
            }
        }
    }

    fun consumeMessage() {
        messages.value = null
    }

    // ---- internals ----

    private fun update(transform: (WorkoutConfig) -> WorkoutConfig) {
        workingConfig.value = transform(workingConfig.value)
    }

    /** Seed the working config from the current stored config, or the spec default. */
    private fun seedConfig(): WorkoutConfig {
        // Synchronous best-effort seed: the default. The combine above will
        // re-emit with the stored config once the repo Flow loads (see refresh
        // below); for the very first frame the spec default is correct enough.
        return when (type) {
            WorkoutType.HIIT -> WorkoutConfig.hiitDefault()
            WorkoutType.RAISED -> WorkoutConfig.raisedDefault()
        }
    }

    init {
        // On first non-empty preset load, seed the working config from the
        // stored current-of-type so the editor opens on the user's actual
        // config rather than the spec default. Only fires once; later edits
        // are never clobbered by repo changes.
        viewModelScope.launch {
            repository.observeAll().collect { presets ->
                if (!seededFromRepo && presets.isNotEmpty()) {
                    seededFromRepo = true
                    PresetRepository.currentConfig(presets, type)?.let { workingConfig.value = it }
                }
            }
        }
    }

    @Volatile
    private var seededFromRepo = false

    private fun project(
        config: WorkoutConfig,
        presets: List<Preset>,
        message: String?,
    ): ConfigUiState {
        val plan = runCatching { buildPlan(config) }
        val total = plan.getOrDefault(WorkoutPlanEmpty).totalDurationSec
        val steps = plan.map { it.steps }.getOrDefault(emptyList())
        return ConfigUiState(
            type = config.type,
            exerciseSecs = config.exerciseSecs,
            breakSecs = config.breakSecs,
            longBreakSecs = config.longBreakSecs,
            rounds = config.rounds,
            sets = config.sets,
            warmupSecs = config.warmupSecs,
            challengeSecs = config.challengeSecs,
            cooldownSecs = config.cooldownSecs,
            getReadySecs = config.getReadySecs,
            exercises = config.exercises,
            totalDurationSec = total,
            stepCount = steps.size,
            preview = steps.take(PREVIEW_SIZE),
            presets = presets,
            message = message,
        )
    }

    private companion object {
        const val PREVIEW_SIZE = 6
        // Sentinel plan used only when buildPlan throws (e.g. zero exercises
        // mid-edit) so the UI shows a 0 total instead of crashing.
        val WorkoutPlanEmpty = com.raised.core.WorkoutPlan(emptyList())
    }
}
