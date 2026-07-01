package com.raised.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for Raised (v1 schema, decision D8). Single database, single
 * user, single source of truth for stored configs/presets/exercises.
 *
 * Version 1 is the initial schema; migrations arrive with later schema changes
 * (decision D5 — hard cuts only, ship a migration for normal APK updates).
 *
 * Provided to the DI graph by [com.raised.app.di.DatabaseModule]; tests build an
 * in-memory instance directly via [Builder].
 */
@Database(
    entities = [PresetEntity::class, ExerciseEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    /** Access to presets + their exercise lists. */
    abstract fun presetDao(): PresetDao

    companion object {
        /** Production database file name. */
        const val DB_NAME = "raised.db"
    }
}
