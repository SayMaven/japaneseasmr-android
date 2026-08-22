package com.saymaven.downloader.japaneseasmr.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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
import com.saymaven.downloader.japaneseasmr.data.model.ColorPalette
import com.saymaven.downloader.japaneseasmr.data.model.ThemeMode

private val DefaultDarkScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    tertiary = DarkTertiary,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = DarkText,
    onSurface = DarkText,
    onSurfaceVariant = DarkTextMuted,
    outline = DarkOutline,
    error = DarkError
)

private val DefaultLightScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    tertiary = LightTertiary,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = LightText,
    onSurface = LightText,
    onSurfaceVariant = LightTextMuted,
    outline = LightOutline,
    error = LightError
)

private fun getPaletteColorScheme(palette: ColorPalette, darkTheme: Boolean): ColorScheme {
    return when (palette) {
        ColorPalette.SAKURA -> if (darkTheme) {
            DefaultDarkScheme.copy(primary = SakuraPrimary, background = SakuraDarkBackground, surface = SakuraDarkSurface)
        } else {
            DefaultLightScheme.copy(primary = SakuraPrimary)
        }
        ColorPalette.OCEAN -> if (darkTheme) {
            DefaultDarkScheme.copy(primary = OceanPrimary, background = OceanDarkBackground, surface = OceanDarkSurface)
        } else {
            DefaultLightScheme.copy(primary = OceanPrimary)
        }
        ColorPalette.PURPLE -> if (darkTheme) {
            DefaultDarkScheme.copy(primary = PurplePrimary, background = PurpleDarkBackground, surface = PurpleDarkSurface)
        } else {
            DefaultLightScheme.copy(primary = PurplePrimary)
        }
        ColorPalette.EMERALD -> if (darkTheme) {
            DefaultDarkScheme.copy(primary = EmeraldPrimary, background = EmeraldDarkBackground, surface = EmeraldDarkSurface)
        } else {
            DefaultLightScheme.copy(primary = EmeraldPrimary)
        }
        ColorPalette.DEFAULT -> if (darkTheme) DefaultDarkScheme else DefaultLightScheme
    }
}

@Composable
fun JapaneseASMRTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    colorPalette: ColorPalette = ColorPalette.DEFAULT,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> getPaletteColorScheme(colorPalette, darkTheme)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
