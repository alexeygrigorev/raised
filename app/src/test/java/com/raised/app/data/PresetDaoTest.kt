package com.raised.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric in-memory Room tests for [PresetDao] (host JVM, no emulator).
 * Builds a fresh in-memory [AppDatabase] per test so each is isolated.
 *
 * Covers insert/list (Flow emission via [first]), get-by-id, cascade delete of
 * the child exercise rows, and the emptiness count used by the seed guard.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PresetDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PresetDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.presetDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun emptyDatabaseHasNoPresets() = runTest {
        assertEquals(0, dao.count())
        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun insertPresetPersistsPresetAndExercises() = runTest {
        val (preset, exercises) = PresetMapper.toEntity(
            name = "HIIT",
            config = com.raised.core.WorkoutConfig.hiitDefault(),
        )
        val id = dao.insertPresetWithExercises(preset, exercises)

        assertTrue(id > 0)
        assertEquals(1, dao.count())

        val all = dao.observeAll().first()
        assertEquals(1, all.size)

        val stored = all.first()
        assertEquals(id, stored.preset.id)
        assertEquals("HIIT", stored.preset.name)
        assertEquals(com.raised.core.WorkoutType.HIIT.name, stored.preset.type)
        // HIIT default exercise list has 8 entries, preserved in order.
        assertEquals(
            com.raised.core.WorkoutConfig.HIIT_EXERCISES,
            stored.exercises.sortedBy { it.orderIndex }.map { it.name },
        )
    }

    @Test
    fun getByIdReturnsPresetWithExercisesOrNull() = runTest {
        val (preset, exercises) = PresetMapper.toEntity(
            name = "Raised",
            config = com.raised.core.WorkoutConfig.raisedDefault(),
        )
        val id = dao.insertPresetWithExercises(preset, exercises)

        val fetched = dao.getById(id)
        assertNotNull(fetched)
        assertEquals("Raised", fetched!!.preset.name)
        assertEquals(
            com.raised.core.WorkoutConfig.RAISED_EXERCISES.size,
            fetched.exercises.size,
        )

        assertNull(dao.getById(9999L))
    }

    @Test
    fun deletePresetCascadesToExercises() = runTest {
        val (preset, exercises) = PresetMapper.toEntity(
            name = "HIIT",
            config = com.raised.core.WorkoutConfig.hiitDefault(),
        )
        val id = dao.insertPresetWithExercises(preset, exercises)
        assertEquals(1, dao.count())

        dao.deleteById(id)

        assertEquals(0, dao.count())
        assertTrue(dao.observeAll().first().isEmpty())
        assertNull(dao.getById(id))
    }

    @Test
    fun observeAllEmitsMultiplePresetsInInsertionOrder() = runTest {
        val hiit = PresetMapper.toEntity(
            "HIIT",
            com.raised.core.WorkoutConfig.hiitDefault(),
        )
        val raised = PresetMapper.toEntity(
            "Raised",
            com.raised.core.WorkoutConfig.raisedDefault(),
        )
        dao.insertPresetWithExercises(hiit.first, hiit.second)
        dao.insertPresetWithExercises(raised.first, raised.second)

        val all = dao.observeAll().first()
        assertEquals(2, all.size)
        assertEquals("HIIT", all[0].preset.name)
        assertEquals("Raised", all[1].preset.name)
    }
}
