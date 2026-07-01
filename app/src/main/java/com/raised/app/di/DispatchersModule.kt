package com.raised.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

/**
 * Hilt-provided coroutine dispatchers. The Session ticker runs on
 * [DefaultDispatcher] so it is injectable (and thus swappable in tests with a
 * [kotlinx.coroutines.test.TestDispatcher]) — the only reason a dispatcher is
 * injected at all is testability: a test substitutes a controlled dispatcher
 * and drives ticks with `advanceTimeBy` instead of the real wall clock.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
