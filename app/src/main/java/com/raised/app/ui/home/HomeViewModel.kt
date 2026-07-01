package com.raised.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raised.app.data.Preset
import com.raised.app.data.PresetRepository
import com.raised.core.WorkoutConfig
import com.raised.core.WorkoutType
import com.raised.core.buildPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * One workout summary on the Home screen. The config is the **current** stored
 * config for that workout type (the seeded default, or the most recently saved
 * preset of that type), and [totalDurationSec] is computed live from the plan
 * builder so the card always shows what the user will actually run.
 */
data class WorkoutSummary(
    val type: WorkoutType,
    val name: String,
    val config: WorkoutConfig,
    val totalDurationSec: Int,
)

/** Immutable Home screen state. */
data class HomeUiState(
    val loading: Boolean = true,
    val hiit: WorkoutSummary? = null,
    val raised: WorkoutSummary? = null,
    /** All stored presets, for quick "load" actions if Home ever exposes them. */
    val presets: List<Preset> = emptyList(),
)

/**
 * Home screen ViewModel (decision D8). Observes the stored presets, picks the
 * current config per workout type (the default preset, or the highest-id preset
 * of that type if the user has saved more), and exposes a [StateFlow] of
 * summaries whose totals come straight from the plan builders.
 *
 * No business logic lives in composables; the totals here are the source of
 * truth the cards render.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: PresetRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = repository.observeAll()
        .map { presets -> buildState(presets) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(loading = true),
        )

    /**
     * Build the Home state from the preset list. For each workout type the
     * "current" config is the seeded default if present, otherwise the
     * most-recently-inserted preset of that type. If nothing is stored yet
     * (e.g. seed still running), fall back to the spec default so the card
     * still renders a sensible summary.
     */
    private fun buildState(presets: List<Preset>): HomeUiState {
        val hiitConfig = currentConfig(presets, WorkoutType.HIIT) ?: WorkoutConfig.hiitDefault()
        val raisedConfig = currentConfig(presets, WorkoutType.RAISED) ?: WorkoutConfig.raisedDefault()
        return HomeUiState(
            loading = false,
            hiit = summary("HIIT", hiitConfig),
            raised = summary("Raised", raisedConfig),
            presets = presets,
        )
    }

    private fun currentConfig(presets: List<Preset>, type: WorkoutType): WorkoutConfig? {
        val oftype = presets.filter { it.config.type == type }
        // Prefer the seeded default; otherwise the latest saved preset of the type.
        return oftype.firstOrNull { it.isDefault }?.config
            ?: oftype.maxByOrNull { it.id }?.config
    }

    private fun summary(name: String, config: WorkoutConfig): WorkoutSummary {
        val total = runCatching { buildPlan(config).totalDurationSec }.getOrDefault(0)
        return WorkoutSummary(type = config.type, name = name, config = config, totalDurationSec = total)
    }
}
