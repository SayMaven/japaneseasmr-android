package com.saymaven.downloader.japaneseasmr.ui.theme

import com.saymaven.downloader.japaneseasmr.data.model.ColorPalette
import org.junit.Assert.assertTrue
import org.junit.Test

class OklchColorContrastTest {

    @Test
    fun allPalettes_meetWcagAaContrastInLightAndDarkModes() {
        for (palette in ColorPalette.entries) {
            val light = OklchColor.buildLightColorScheme(
                primarySeed = palette.primary,
                secondarySeed = palette.secondary,
                tertiarySeed = palette.tertiary,
                lightBgOverride = palette.lightBackground,
                lightSurfOverride = palette.lightSurface
            )
            assertTrue(
                "${palette.name} light primary/onPrimary contrast < 4.5",
                OklchColor.contrastRatio(light.onPrimary, light.primary) >= 4.5f
            )
            assertTrue(
                "${palette.name} light onSurface/surfaceContainerLow contrast < 4.5",
                OklchColor.contrastRatio(light.onSurface, light.surfaceContainerLow) >= 4.5f
            )
            assertTrue(
                "${palette.name} light onSurfaceVariant/surfaceContainerHighest contrast < 4.5",
                OklchColor.contrastRatio(light.onSurfaceVariant, light.surfaceContainerHighest) >= 4.5f
            )

            val dark = OklchColor.buildDarkColorScheme(
                primarySeed = palette.primary,
                secondarySeed = palette.secondary,
                tertiarySeed = palette.tertiary,
                darkBackground = palette.darkBackground,
                darkSurface = palette.darkSurface
            )
            assertTrue(
                "${palette.name} dark primary/onPrimary contrast < 4.5",
                OklchColor.contrastRatio(dark.onPrimary, dark.primary) >= 4.5f
            )
            assertTrue(
                "${palette.name} dark onSurface/surfaceContainerLow contrast < 4.5",
                OklchColor.contrastRatio(dark.onSurface, dark.surfaceContainerLow) >= 4.5f
            )
        }
    }
}
