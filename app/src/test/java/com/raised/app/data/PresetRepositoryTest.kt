package com.raised.app.data

import android.content.Context
import androidx.room.Room
import com.raised.core.WorkoutConfig
import com.raised.core.WorkoutType
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
 * Robolectric integration test of [PresetRepository] against an in-memory
 * [AppDatabase] (host JVM, no emulator). Confirms the repository maps entities
 * to/from domain configs and that save/get/delete round-trip through real Room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PresetRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: PresetRepository

    @Before
    fun setUp() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = PresetRepository(db.presetDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun saveThenGetRoundTripsConfig() = runTest {
        val config = WorkoutConfig.hiitDefault().copy(rounds = 5)
        val id = repo.save("Hard HIIT", config)

        val loaded = repo.get(id)
        assertNotNull(loaded)
        assertEquals("Hard HIIT", loaded!!.name)
        assertEquals(WorkoutType.HIIT, loaded.config.type)
        assertEquals(5, loaded.config.rounds)
        assertEquals(config.exercises, loaded.config.exercises)
    }

    @Test
    fun observeAllEmitsSavedPresetsInDomainForm() = runTest {
        repo.save("HIIT", WorkoutConfig.hiitDefault())
        repo.save("Raised", WorkoutConfig.raisedDefault())

        val all = repo.observeAll().first()
        assertEquals(2, all.size)
        assertEquals("HIIT", all[0].name)
        assertEquals(WorkoutType.RAISED, all[1].config.type)
        assertTrue(all[0].config.exercises.isNotEmpty())
    }

    @Test
    fun deleteRemovesPreset() = runTest {
        val id = repo.save("Temp", WorkoutConfig.hiitDefault())
        assertEquals(1, repo.observeAll().first().size)
        repo.delete(id)
        assertNull(repo.get(id))
        assertTrue(repo.observeAll().first().isEmpty())
    }

    @Test
    fun getReturnsNullForUnknownId() = runTest {
        assertNull(repo.get(9999L))
    }
}
