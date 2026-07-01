package com.raised.uikit.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = Flame,
    onPrimary = Slate,
    primaryContainer = Ember,
    secondary = Mint,
    onSecondary = Slate,
    background = Slate,
    onBackground = OnSurfaceHigh,
    surface = Slate,
    onSurface = OnSurfaceHigh,
    surfaceVariant = SlateElevated,
    onSurfaceVariant = OnSurfaceMed,
)

private val LightColors = lightColorScheme(
    primary = Pulse,
    onPrimary = OnSurfaceHigh,
    secondary = Mint,
    onSecondary = Slate,
)

/**
 * App theme. Raised is a dark-first fitness app; light is supported but dark is
 * the brand direction.
 */
@Composable
fun RaisedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = RaisedTypography,
        shapes = RaisedShapes,
        content = content,
    )
}
