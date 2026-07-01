package com.raised.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.raised.core.WorkoutConfig
import com.raised.core.WorkoutType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric in-memory Room tests for [SeedDefaults]. Proves:
 *  - first run inserts both default presets (HIIT + Raised) with their default
 *    exercise lists, derived from [WorkoutConfig.hiitDefault] / [raisedDefault];
 *  - a second run is a no-op (idempotent — the emptiness guard holds);
 *  - the seeded rows round-trip back to the exact domain defaults, so the stored
 *    seed and the engine agree.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SeedDefaultsTest {

    private lateinit var db: AppDatabase
    private lateinit var seed: SeedDefaults

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seed = SeedDefaults(db.presetDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun firstRunSeedsBothDefaultsWithExerciseLists() = runTest {
        val inserted = seed.seedIfEmpty()

        assertEquals(2, inserted)
        val all = db.presetDao().observeAll().first()
        assertEquals(2, all.size)

        val byName = all.associateBy { it.preset.name }
        assertTrue("HIIT default seeded", "HIIT" in byName)
        assertTrue("Raised default seeded", "Raised" in byName)

        // Both seeded rows are flagged as defaults.
        assertTrue(byName.getValue("HIIT").preset.isDefault)
        assertTrue(byName.getValue("Raised").preset.isDefault)

        // The seeded configs round-trip to the exact domain defaults.
        assertEquals(
            WorkoutConfig.hiitDefault(),
            PresetMapper.toConfig(byName.getValue("HIIT")),
        )
        assertEquals(
            WorkoutConfig.raisedDefault(),
            PresetMapper.toConfig(byName.getValue("Raised")),
        )
    }

    @Test
    fun secondRunIsANoop() = runTest {
        // First run seeds.
        assertEquals(2, seed.seedIfEmpty())
        val afterFirst = db.presetDao().observeAll().first()

        // Second run must not duplicate or mutate anything.
        assertEquals(0, seed.seedIfEmpty())

        val afterSecond = db.presetDao().observeAll().first()
        assertEquals(afterFirst.size, afterSecond.size)
        assertEquals(afterFirst.map { it.preset.id }, afterSecond.map { it.preset.id })
        assertEquals(afterFirst.map { it.exercises.size }, afterSecond.map { it.exercises.size })
    }

    @Test
    fun seedIsSkippedWhenPresetsAlreadyExist() = runTest {
        // Pre-populate with a single non-default preset.
        val (preset, exercises) = PresetMapper.toEntity(
            name = "Custom",
            config = WorkoutConfig.hiitDefault(),
            isDefault = false,
        )
        db.presetDao().insertPresetWithExercises(preset, exercises)

        // Seed must observe the non-empty table and refuse to seed.
        assertEquals(0, seed.seedIfEmpty())
        val all = db.presetDao().observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Custom", all.first().preset.name)
    }

    @Test
    fun seededPresetsCoverBothWorkoutTypes() = runTest {
        seed.seedIfEmpty()
        val types = db.presetDao().observeAll().first()
            .map { it.preset.type }
            .toSet()
        assertTrue(WorkoutType.HIIT.name in types)
        assertTrue(WorkoutType.RAISED.name in types)
    }
}
