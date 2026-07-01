package com.raised.app.di

import android.content.Context
import androidx.room.Room
import com.raised.app.data.AppDatabase
import com.raised.app.data.PresetDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing the Room [AppDatabase] (decision D8) and its DAOs as
 * singletons. The database file is [AppDatabase.DB_NAME] (`raised.db`).
 *
 * v1 only — no migrations registered yet (decision D5: migrations arrive with
 * the first schema change).
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DB_NAME,
        ).build()

    @Provides
    fun providePresetDao(db: AppDatabase): PresetDao = db.presetDao()
}
