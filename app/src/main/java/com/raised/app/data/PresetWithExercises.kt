package com.raised.app.data

import androidx.room.Embedded
import androidx.room.Relation

/**
 * A preset plus its ordered exercise list, produced by a `@Transaction` DAO
 * query that pairs [PresetEntity] with the child [ExerciseEntity] rows.
 *
 * A transient value — never persisted, never cached. The data layer maps it to a
 * domain [com.raised.core.WorkoutConfig] (via [PresetMapper]) before handing it
 * to the UI/engine.
 */
data class PresetWithExercises(
    @Embedded val preset: PresetEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "presetId",
    )
    val exercises: List<ExerciseEntity>,
)
