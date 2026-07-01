package com.raised.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One stored workout config / named preset (Room v1 schema, decision D8).
 *
 * A row is a flattened [com.raised.core.WorkoutConfig]: every timing field the
 * engine needs is denormalised onto the row, and the ordered exercise list lives
 * in the child [ExerciseEntity] rows keyed by [presetId]. The UI and engine
 * consume domain [com.raised.core.WorkoutConfig] values produced by
 * [PresetMapper]; this entity never leaks past the data layer.
 *
 * @property id            stable row id (0 = not yet inserted / auto-generated).
 * @property name          user-visible preset name (e.g. "HIIT", "Raised").
 * @property type          [com.raised.core.WorkoutType] name — `HIIT` or `RAISED`.
 *                        Stored as a String so a schema bump is not required if
 *                        the enum grows, and reads never need an enum lookup that
 *                        could throw on unknown values.
 * @property exerciseSecs  seconds of work per exercise.
 * @property breakSecs     seconds of the short break between exercises/sets.
 * @property longBreakSecs seconds of the long break between rounds/exercises.
 * @property rounds        HIIT round count (unused by Raised, kept at 0).
 * @property sets          Raised set count (unused by HIIT, kept at 0).
 * @property warmupSecs    seconds of the warm-up block.
 * @property challengeSecs seconds of the challenge block.
 * @property cooldownSecs  seconds of the cool-down block.
 * @property getReadySecs  seconds of the get-ready countdown before warm-up.
 * @property isDefault     true for the two seeded default presets (HIIT, Raised).
 */
@Entity(
    tableName = "presets",
    indices = [Index(value = ["type"])],
)
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val exerciseSecs: Int,
    val breakSecs: Int,
    val longBreakSecs: Int,
    val rounds: Int,
    val sets: Int,
    val warmupSecs: Int,
    val challengeSecs: Int,
    val cooldownSecs: Int,
    val getReadySecs: Int,
    val isDefault: Boolean = false,
)
