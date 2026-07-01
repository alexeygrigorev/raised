package com.raised.app.ui

import com.raised.app.data.ExerciseEntity
import com.raised.app.data.Preset
import com.raised.app.data.PresetDao
import com.raised.app.data.PresetEntity
import com.raised.app.data.PresetRepository
import com.raised.app.data.PresetWithExercises
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake [PresetRepository] for plain-JVM ViewModel tests. Keeps a
 * mutable list of presets behind a [MutableStateFlow] so the ViewModels' real
 * Flow plumbing (combine/stateIn) runs exactly as in production. No Room, no
 * Robolectric — fast host-JVM tests only.
 *
 * The parent constructor demands a [PresetDao]; we hand it a stub that throws
 * on any real call, because every repository method is overridden and the dao
 * is never reached.
 */
class FakePresetRepository(initial: List<Preset> = emptyList()) : PresetRepository(
    presetDao = stubDao(),
) {
    private val _presets = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Preset>> = _presets

    override suspend fun get(id: Long): Preset? = _presets.value.firstOrNull { it.id == id }

    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L

    override suspend fun save(name: String, config: com.raised.core.WorkoutConfig, isDefault: Boolean): Long {
        val id = nextId++
        _presets.value = _presets.value + Preset(id, name, config, isDefault)
        return id
    }

    override suspend fun delete(id: Long) {
        _presets.value = _presets.value.filterNot { it.id == id }
    }

    /** Test helper: replace the preset list and emit. */
    fun setPresets(values: List<Preset>) {
        _presets.value = values
    }

    companion object {
        /** A [PresetDao] that fails if any method is actually invoked. */
        private fun stubDao(): PresetDao = object : PresetDao {
            override fun observeAll(): Flow<List<PresetWithExercises>> = error("stub")
            override suspend fun getById(id: Long): PresetWithExercises? = error("stub")
            override suspend fun count(): Int = error("stub")
            override suspend fun insertPreset(preset: PresetEntity): Long = error("stub")
            override suspend fun insertExercises(exercises: List<ExerciseEntity>) = error("stub")
            override suspend fun deleteById(id: Long) = error("stub")
        }
    }
}
