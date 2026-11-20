package com.saymaven.downloader.japaneseasmr.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.saymaven.downloader.japaneseasmr.data.model.ColorPalette
import com.saymaven.downloader.japaneseasmr.data.model.ThemeMode

private val BaseDarkScheme = darkColorScheme(
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

private val BaseLightScheme = lightColorScheme(
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
    return if (darkTheme) {
        val bg = palette.darkBackground
        val surf = palette.darkSurface
        val primContainer = lerp(surf, palette.primary, 0.28f)
        val secContainer = lerp(surf, palette.secondary, 0.22f)
        val tertContainer = lerp(surf, palette.tertiary, 0.22f)
        BaseDarkScheme.copy(
            primary = palette.primary,
            onPrimary = lerp(palette.primary, Color.Black, 0.82f),
            primaryContainer = primContainer,
            onPrimaryContainer = lerp(palette.primary, Color.White, 0.85f),
            inversePrimary = lerp(palette.primary, Color.Black, 0.45f),
            secondary = palette.secondary,
            onSecondary = lerp(palette.secondary, Color.Black, 0.82f),
            secondaryContainer = secContainer,
            onSecondaryContainer = lerp(palette.secondary, Color.White, 0.85f),
            tertiary = palette.tertiary,
            onTertiary = lerp(palette.tertiary, Color.Black, 0.82f),
            tertiaryContainer = tertContainer,
            onTertiaryContainer = lerp(palette.tertiary, Color.White, 0.85f),
            background = bg,
            surface = surf,
            surfaceVariant = lerp(surf, palette.primary, 0.12f),
            surfaceTint = palette.primary,
            surfaceContainerLowest = bg,
            surfaceContainerLow = lerp(bg, surf, 0.5f),
            surfaceContainer = surf,
            surfaceContainerHigh = lerp(surf, Color.White, 0.05f),
            surfaceContainerHighest = lerp(surf, Color.White, 0.09f),
            outline = lerp(surf, Color.White, 0.24f),
            outlineVariant = lerp(surf, Color.White, 0.14f)
        )
    } else {
        val bg = palette.lightBackground
        val surf = palette.lightSurface
        val prim = lerp(palette.primary, Color.Black, 0.25f)
        val sec = lerp(palette.secondary, Color.Black, 0.25f)
        val tert = lerp(palette.tertiary, Color.Black, 0.25f)
        BaseLightScheme.copy(
            primary = prim,
            onPrimary = Color.White,
            primaryContainer = lerp(Color.White, palette.primary, 0.28f),
            onPrimaryContainer = lerp(palette.primary, Color.Black, 0.72f),
            inversePrimary = palette.primary,
            secondary = sec,
            onSecondary = Color.White,
            secondaryContainer = lerp(Color.White, palette.secondary, 0.25f),
            onSecondaryContainer = lerp(palette.secondary, Color.Black, 0.72f),
            tertiary = tert,
            onTertiary = Color.White,
            tertiaryContainer = lerp(Color.White, palette.tertiary, 0.25f),
            onTertiaryContainer = lerp(palette.tertiary, Color.Black, 0.72f),
            background = bg,
            surface = surf,
            surfaceVariant = lerp(surf, palette.primary, 0.10f),
            surfaceTint = prim,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = bg,
            surfaceContainer = lerp(bg, palette.primary, 0.04f),
            surfaceContainerHigh = lerp(bg, palette.primary, 0.07f),
            surfaceContainerHighest = lerp(bg, palette.primary, 0.11f)
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
