package com.claudetracker.app

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = premium_light_primary,
    onPrimary = premium_light_onPrimary,
    primaryContainer = premium_light_primaryContainer,
    onPrimaryContainer = premium_light_onPrimaryContainer,
    background = premium_light_background,
    onBackground = premium_light_onBackground,
    surface = premium_light_surface,
    onSurface = premium_light_onSurface,
    surfaceVariant = premium_light_surfaceVariant,
    onSurfaceVariant = premium_light_onSurfaceVariant,
)

private val DarkColors = darkColorScheme(
    primary = premium_dark_primary,
    onPrimary = premium_dark_onPrimary,
    primaryContainer = premium_dark_primaryContainer,
    onPrimaryContainer = premium_dark_onPrimaryContainer,
    background = premium_dark_background,
    onBackground = premium_dark_onBackground,
    surface = premium_dark_surface,
    onSurface = premium_dark_onSurface,
    surfaceVariant = premium_dark_surfaceVariant,
    onSurfaceVariant = premium_dark_onSurfaceVariant,
)

@Composable
fun ClaudeTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Force premium colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
