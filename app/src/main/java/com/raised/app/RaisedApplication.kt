package com.raised.app

import android.app.Application
import android.util.Log
import com.raised.app.data.SeedDefaults
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point. [HiltAndroidApp] triggers KSP to generate the
 * dependency-injection graph at compile time.
 *
 * On first launch [onCreate] kicks off the idempotent [SeedDefaults] so the two
 * default presets (HIIT, Raised) are present before any screen reads them. The
 * seed runs on a background dispatcher; if it fails the app still starts — the
 * presets table simply stays empty and the user can add their own (issue #4).
 */
@HiltAndroidApp
class RaisedApplication : Application() {

    /** EntryPoint to reach [SeedDefaults] from the Application, which is not
     *  an @AndroidEntryPoint (field injection isn't available here). */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppEntryPoint {
        fun seedDefaults(): SeedDefaults
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val entryPoint = EntryPointAccessors.fromApplication(
            this,
            AppEntryPoint::class.java,
        )
        appScope.launch {
            runCatching { entryPoint.seedDefaults().seedIfEmpty() }
                .onFailure { Log.e(TAG, "Default seed failed", it) }
        }
    }

    private companion object {
        const val TAG = "RaisedApplication"
    }
}
