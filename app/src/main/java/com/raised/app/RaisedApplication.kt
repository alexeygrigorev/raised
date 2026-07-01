package com.raised.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. [HiltAndroidApp] triggers KSP to generate the
 * dependency-injection graph at compile time.
 */
@HiltAndroidApp
class RaisedApplication : Application()
