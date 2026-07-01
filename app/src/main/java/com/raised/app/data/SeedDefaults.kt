package com.raised.app.data

import com.raised.core.WorkoutConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Idempotently seeds the two default presets (HIIT, Raised) on first run so the
 * app is usable out of the box (decision D7 — spec defaults).
 *
 * The seed is guarded on the `presets` table being empty: a fresh install has
 * zero rows and gets both defaults; every later run is a no-op. The exercise
 * lists come straight from [WorkoutConfig.hiitDefault] / [raisedDefault], so the
 * stored seed and the engine agree exactly — there is no second copy of the
 * default exercise list to drift out of sync.
 *
 * Call once at app start (e.g. from a Hilt-injected startup hook). Safe to call
 * repeatedly: the emptiness guard makes it idempotent.
 */
@Singleton
class SeedDefaults @Inject constructor(
    private val presetDao: PresetDao,
) {
    /**
     * Seed the defaults if the `presets` table is empty. Returns the number of
     * presets inserted (2 on a real first run, 0 once already seeded).
     */
    suspend fun seedIfEmpty(): Int {
        if (presetDao.count() > 0) return 0
        return insertDefault(WorkoutConfig.hiitDefault(), name = "HIIT") +
            insertDefault(WorkoutConfig.raisedDefault(), name = "Raised")
    }

    private suspend fun insertDefault(config: WorkoutConfig, name: String): Int {
        val (preset, exerciseNames) = PresetMapper.toEntity(
            name = name,
            config = config,
            isDefault = true,
        )
        presetDao.insertPresetWithExercises(preset, exerciseNames)
        return 1
    }
}
