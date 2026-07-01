package com.raised.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.raised.app.nav.RaisedApp
import com.raised.uikit.theme.RaisedTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single activity host. Compose navigation drives the screens; this activity
 * only owns the window and the Hilt entry point.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RaisedTheme {
                RaisedApp()
            }
        }
    }
}
