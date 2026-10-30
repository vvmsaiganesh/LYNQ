package com.flux.relay.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val RelayScheme = darkColorScheme(
    primary = MeshGreen,
    onPrimary = Ink,
    primaryContainer = MeshGreen.copy(alpha = 0.16f),
    onPrimaryContainer = MeshGreen,
    secondary = RelayBlue,
    onSecondary = Ink,
    secondaryContainer = RelayBlue.copy(alpha = 0.16f),
    onSecondaryContainer = RelayBlue,
    tertiary = AlertAmber,
    onTertiary = Ink,
    error = SosRed,
    onError = Ink,
    errorContainer = SosRedDeep,
    onErrorContainer = TextPrimary,
    background = Ink,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = TextSecondary,
    outline = Hairline,
    outlineVariant = Hairline,
)

@Composable
fun RelayTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Ink.toArgb()
            window.navigationBarColor = Ink.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    // Always dark: an emergency console should not blind you at 3am in a shelter.
    MaterialTheme(
        colorScheme = RelayScheme,
        typography = RelayTypography,
        content = content,
    )
}
