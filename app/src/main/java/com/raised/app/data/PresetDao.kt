package com.raised.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to the `presets` table and its child `exercises` rows
 * (Room v1 schema, decision D8). Listing observers get a reactive [Flow]; single
 * fetches and mutations are [suspend] so callers stay off the main thread.
 */
@Dao
interface PresetDao {
    /** All presets as domain-ready configs + exercise lists, ordered by row id. */
    @Transaction
    @Query("SELECT * FROM presets ORDER BY id ASC")
    fun observeAll(): Flow<List<PresetWithExercises>>

    /** One preset by row id, or null. */
    @Transaction
    @Query("SELECT * FROM presets WHERE id = :id")
    suspend fun getById(id: Long): PresetWithExercises?

    /** Total preset row count — used to gate the idempotent seed. */
    @Query("SELECT COUNT(*) FROM presets")
    suspend fun count(): Int

    /**
     * Insert a preset row, returning its new row id. Callers then insert the
     * child [ExerciseEntity] rows with this id as [ExerciseEntity.presetId].
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPreset(preset: PresetEntity): Long

    /** Insert the exercise rows for a preset. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExercises(exercises: List<ExerciseEntity>)

    /**
     * Insert a full preset + its exercise list in one call, returning the new
     * preset row id. Convenience wrapper around [insertPreset] + [insertExercises].
     */
    suspend fun insertPresetWithExercises(
        preset: PresetEntity,
        exerciseNames: List<String>,
    ): Long {
        val presetId = insertPreset(preset)
        if (exerciseNames.isNotEmpty()) {
            insertExercises(
                exerciseNames.mapIndexed { index, name ->
                    ExerciseEntity(presetId = presetId, orderIndex = index, name = name)
                },
            )
        }
        return presetId
    }

    /** Delete a preset by row id. Exercises cascade-delete via the FK. */
    @Query("DELETE FROM presets WHERE id = :id")
    suspend fun deleteById(id: Long)
}
