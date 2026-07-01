package com.raised.app.data

import com.raised.core.WorkoutConfig
import com.raised.core.WorkoutType

/**
 * Maps between the Room [PresetEntity] / [ExerciseEntity] rows and the
 * :shared:core-workout domain [WorkoutConfig] (decision D3 — the engine and UI
 * consume domain types, not entities).
 *
 * The exercise list is mapped to/from [WorkoutConfig.exercises], preserving
 * [ExerciseEntity.orderIndex] order. Timing fields map 1:1.
 */
object PresetMapper {

    /**
     * Entity rows → domain config. Exercises are sorted by [ExerciseEntity.orderIndex]
     * so the returned list is in playback order regardless of row-insert order.
     *
     * An unknown [PresetEntity.type] string falls back to [WorkoutType.HIIT] rather
     * than throwing — the stored schema is the source of truth, and a corrupt row
     * should never crash a read.
     */
    fun toConfig(preset: PresetWithExercises): WorkoutConfig {
        val type = runCatching { WorkoutType.valueOf(preset.preset.type) }
            .getOrDefault(WorkoutType.HIIT)
        val exercises = preset.exercises
            .sortedBy { it.orderIndex }
            .map { it.name }
        return WorkoutConfig(
            type = type,
            exercises = exercises,
            exerciseSecs = preset.preset.exerciseSecs,
            breakSecs = preset.preset.breakSecs,
            longBreakSecs = preset.preset.longBreakSecs,
            rounds = preset.preset.rounds,
            sets = preset.preset.sets,
            warmupSecs = preset.preset.warmupSecs,
            challengeSecs = preset.preset.challengeSecs,
            cooldownSecs = preset.preset.cooldownSecs,
            getReadySecs = preset.preset.getReadySecs,
        )
    }

    /**
     * Domain config → entity rows (preset not yet inserted, exercises to be
     * inserted after the preset's row id is known). The returned [PresetEntity]
     * has [PresetEntity.id] = 0 so Room auto-generates it.
     *
     * @param name      preset display name.
     * @param config    the source domain config.
     * @param isDefault true for the seeded default presets.
     */
    fun toEntity(
        name: String,
        config: WorkoutConfig,
        isDefault: Boolean = false,
    ): Pair<PresetEntity, List<String>> {
        val preset = PresetEntity(
            id = 0,
            name = name,
            type = config.type.name,
            exerciseSecs = config.exerciseSecs,
            breakSecs = config.breakSecs,
            longBreakSecs = config.longBreakSecs,
            rounds = config.rounds,
            sets = config.sets,
            warmupSecs = config.warmupSecs,
            challengeSecs = config.challengeSecs,
            cooldownSecs = config.cooldownSecs,
            getReadySecs = config.getReadySecs,
            isDefault = isDefault,
        )
        return preset to config.exercises
    }
}
