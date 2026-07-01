package com.raised.app.data

import com.raised.core.WorkoutConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A stored preset in domain terms: a stable row id, a display name, and the
 * full [WorkoutConfig] (timing + ordered exercise list). This is what the UI
 * consumes; the Room [PresetEntity]/[ExerciseEntity] rows never escape the data
 * layer (decision D3).
 */
data class Preset(
    val id: Long,
    val name: String,
    val config: WorkoutConfig,
    val isDefault: Boolean,
)

/**
 * Thin wrapper over [PresetDao] + [PresetMapper] (decision D8). Keeps ViewModels
 * free of Room/SQL: it exposes presets reactively as a [Flow] of domain
 * [Preset]s and offers [save] / [delete] / [get] as [suspend] functions.
 *
 * A Hilt [Singleton] so the (cheap) Flow mapping is shared across screens. The
 * class and its members are `open` so plain-JVM ViewModel tests can substitute
 * a fake without pulling in Room/Robolectric.
 */
@Singleton
open class PresetRepository @Inject constructor(
    private val presetDao: PresetDao,
) {
    /** All presets as domain values, ordered by row id, reactive. */
    open fun observeAll(): Flow<List<Preset>> =
        presetDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** One preset by row id, or null. */
    open suspend fun get(id: Long): Preset? = presetDao.getById(id)?.toDomain()

    /**
     * Insert (or, if [id] > 0, treat as a fresh insert under that name) a preset
     * from a domain [WorkoutConfig]. Returns the new row id.
     *
     * Presets are always inserted as new rows — there is no in-place update
     * path in v1 (decision D5, single user). "Editing a preset" is save-again
     * under a name; the caller may [delete] the old row if it wants.
     */
    open suspend fun save(name: String, config: WorkoutConfig, isDefault: Boolean = false): Long {
        val (entity, exerciseNames) = PresetMapper.toEntity(name, config, isDefault)
        return presetDao.insertPresetWithExercises(entity, exerciseNames)
    }

    /** Delete a preset by row id; its exercise rows cascade-delete. */
    open suspend fun delete(id: Long) = presetDao.deleteById(id)

    private fun PresetWithExercises.toDomain(): Preset = Preset(
        id = preset.id,
        name = preset.name,
        config = PresetMapper.toConfig(this),
        isDefault = preset.isDefault,
    )
}
