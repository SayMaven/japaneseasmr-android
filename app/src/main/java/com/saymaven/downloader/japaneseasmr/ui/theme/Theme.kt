package com.saymaven.downloader.japaneseasmr.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.saymaven.downloader.japaneseasmr.data.model.ColorPalette
import com.saymaven.downloader.japaneseasmr.data.model.ThemeMode

private fun getPaletteColorScheme(palette: ColorPalette, darkTheme: Boolean): ColorScheme {
    return if (darkTheme) {
        OklchColor.buildDarkColorScheme(
            primarySeed = palette.primary,
            secondarySeed = palette.secondary,
            tertiarySeed = palette.tertiary,
            darkBackground = palette.darkBackground,
            darkSurface = palette.darkSurface
        )
    } else {
        OklchColor.buildLightColorScheme(
            primarySeed = palette.primary,
            secondarySeed = palette.secondary,
            tertiarySeed = palette.tertiary,
            lightBgOverride = palette.lightBackground,
            lightSurfOverride = palette.lightSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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

    val rippleConfig = RippleConfiguration(color = colorScheme.primary)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography
    ) {
        CompositionLocalProvider(
            LocalRippleConfiguration provides rippleConfig,
            content = content
        )
    }
}
