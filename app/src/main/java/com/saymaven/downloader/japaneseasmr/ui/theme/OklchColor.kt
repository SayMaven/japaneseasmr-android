package com.saymaven.downloader.japaneseasmr.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

data class Oklch(val l: Float, val c: Float, val h: Float)

/**
 * Pure-Kotlin perceptual OKLCH <-> sRGB converter with binary-search sRGB gamut clamping
 * and WCAG 2.1 contrast verification for Material 3 Light & Dark color schemes.
 */
object OklchColor {

    private fun srgbToLinear(c: Float): Float {
        return if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun linearToSrgb(c: Float): Float {
        val clamped = c.coerceIn(0f, 1f)
        return if (clamped <= 0.0031308f) {
            12.92f * clamped
        } else {
            1.055f * clamped.pow(1f / 2.4f) - 0.055f
        }
    }

    fun fromColor(color: Color): Oklch {
        val r = srgbToLinear(color.red)
        val g = srgbToLinear(color.green)
        val b = srgbToLinear(color.blue)

        val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
        val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
        val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)

        val okL = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s
        val okA = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
        val okB = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s

        val chroma = hypot(okA, okB)
        var hue = (atan2(okB, okA) * 180f / PI.toFloat()) % 360f
        if (hue < 0f) hue += 360f
        return Oklch(okL.coerceIn(0f, 1f), chroma, hue)
    }

    private fun oklchToLinearRgb(l: Float, c: Float, h: Float): Triple<Float, Float, Float> {
        val rad = h * (PI.toFloat() / 180f)
        val a = c * cos(rad)
        val b = c * sin(rad)

        val l_ = l + 0.3963377774f * a + 0.2158037573f * b
        val m_ = l - 0.1055613458f * a - 0.0638541728f * b
        val s_ = l - 0.0894841775f * a - 1.2914855480f * b

        val l3 = l_ * l_ * l_
        val m3 = m_ * m_ * m_
        val s3 = s_ * s_ * s_

        val r = +4.0767416621f * l3 - 3.3077115913f * m3 + 0.2309699292f * s3
        val g = -1.2684380046f * l3 + 2.6097574011f * m3 - 0.3413193965f * s3
        val bl = -0.0041960863f * l3 - 0.7034186147f * m3 + 1.7076147010f * s3
        return Triple(r, g, bl)
    }

    private fun isInGamut(r: Float, g: Float, b: Float): Boolean {
        val eps = -0.0005f
        val maxEps = 1.0005f
        return r in eps..maxEps && g in eps..maxEps && b in eps..maxEps
    }

    fun toColor(l: Float, c: Float, h: Float): Color {
        val clampedL = l.coerceIn(0f, 1f)
        val (r0, g0, b0) = oklchToLinearRgb(clampedL, c.coerceAtLeast(0f), h)
        if (isInGamut(r0, g0, b0)) {
            return Color(linearToSrgb(r0), linearToSrgb(g0), linearToSrgb(b0), 1f)
        }
        var lo = 0f
        var hi = c.coerceAtLeast(0f)
        repeat(16) {
            val mid = (lo + hi) * 0.5f
            val (rm, gm, bm) = oklchToLinearRgb(clampedL, mid, h)
            if (isInGamut(rm, gm, bm)) lo = mid else hi = mid
        }
        val (rf, gf, bf) = oklchToLinearRgb(clampedL, lo, h)
        return Color(linearToSrgb(rf), linearToSrgb(gf), linearToSrgb(bf), 1f)
    }

    fun relativeLuminance(color: Color): Float {
        val r = srgbToLinear(color.red)
        val g = srgbToLinear(color.green)
        val b = srgbToLinear(color.blue)
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    fun contrastRatio(fg: Color, bg: Color): Float {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun darkenForWhiteContrast(seed: Oklch, targetL: Float, maxChroma: Float, minRatio: Float = 4.6f): Color {
        var l = targetL
        val c = min(seed.c, maxChroma)
        repeat(12) {
            val candidate = toColor(l, c, seed.h)
            if (contrastRatio(Color.White, candidate) >= minRatio) {
                return candidate
            }
            l = (l - 0.03f).coerceAtLeast(0.18f)
        }
        return toColor(l, c, seed.h)
    }

    private fun lightenForDarkOnContrast(seed: Oklch, minL: Float, minRatio: Float = 4.6f): Pair<Color, Color> {
        val l = max(seed.l, minL).coerceAtMost(0.88f)
        val accent = toColor(l, seed.c, seed.h)
        val onAccent = toColor(0.16f, min(seed.c * 0.35f, 0.06f), seed.h)
        if (contrastRatio(accent, onAccent) >= minRatio) {
            return accent to onAccent
        }
        val darkerOn = toColor(0.08f, min(seed.c * 0.2f, 0.03f), seed.h)
        return accent to darkerOn
    }

    fun buildLightColorScheme(
        primarySeed: Color,
        secondarySeed: Color,
        tertiarySeed: Color,
        lightBgOverride: Color? = null,
        lightSurfOverride: Color? = null
    ): ColorScheme {
        val p = fromColor(primarySeed)
        val s = fromColor(secondarySeed)
        val t = fromColor(tertiarySeed)
        val baseH = p.h

        val bg = lightBgOverride ?: toColor(0.974f, 0.004f, baseH)
        val surf = lightSurfOverride ?: toColor(0.992f, 0.002f, baseH)
        val surfLowest = Color.White
        val surfLow = Color.White
        val surfContainer = toColor(0.954f, 0.005f, baseH)
        val surfHigh = toColor(0.924f, 0.006f, baseH)
        val surfHighest = toColor(0.888f, 0.008f, baseH)

        val primary = darkenForWhiteContrast(p, targetL = 0.47f, maxChroma = 0.22f)
        val secondary = darkenForWhiteContrast(s, targetL = 0.48f, maxChroma = 0.14f)
        val tertiary = darkenForWhiteContrast(t, targetL = 0.46f, maxChroma = 0.16f)

        val primContainer = toColor(0.91f, min(p.c * 0.55f, 0.065f), p.h)
        val onPrimContainer = toColor(0.22f, min(p.c * 0.8f, 0.14f), p.h)
        val secContainer = toColor(0.91f, min(s.c * 0.45f, 0.055f), s.h)
        val onSecContainer = toColor(0.22f, min(s.c * 0.7f, 0.12f), s.h)
        val tertContainer = toColor(0.91f, min(t.c * 0.50f, 0.060f), t.h)
        val onTertContainer = toColor(0.22f, min(t.c * 0.75f, 0.12f), t.h)

        val onText = toColor(0.18f, 0.012f, baseH)
        val onTextMuted = toColor(0.42f, 0.015f, baseH)
        val outline = toColor(0.56f, 0.015f, baseH)
        val outlineVariant = toColor(0.84f, 0.010f, baseH)

        return lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primContainer,
            onPrimaryContainer = onPrimContainer,
            inversePrimary = toColor(0.78f, min(p.c, 0.16f), p.h),
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = secContainer,
            onSecondaryContainer = onSecContainer,
            tertiary = tertiary,
            onTertiary = Color.White,
            tertiaryContainer = tertContainer,
            onTertiaryContainer = onTertContainer,
            background = bg,
            onBackground = onText,
            surface = surf,
            onSurface = onText,
            surfaceVariant = surfHigh,
            onSurfaceVariant = onTextMuted,
            surfaceTint = primary,
            surfaceContainerLowest = surfLowest,
            surfaceContainerLow = surfLow,
            surfaceContainer = surfContainer,
            surfaceContainerHigh = surfHigh,
            surfaceContainerHighest = surfHighest,
            outline = outline,
            outlineVariant = outlineVariant,
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002)
        )
    }

    fun buildDarkColorScheme(
        primarySeed: Color,
        secondarySeed: Color,
        tertiarySeed: Color,
        darkBackground: Color,
        darkSurface: Color
    ): ColorScheme {
        val p = fromColor(primarySeed)
        val s = fromColor(secondarySeed)
        val t = fromColor(tertiarySeed)
        val baseH = p.h

        val (primary, onPrimary) = lightenForDarkOnContrast(p, minL = 0.72f)
        val (secondary, onSecondary) = lightenForDarkOnContrast(s, minL = 0.72f)
        val (tertiary, onTertiary) = lightenForDarkOnContrast(t, minL = 0.72f)

        val primContainer = toColor(0.28f, min(p.c * 0.65f, 0.10f), p.h)
        val onPrimContainer = toColor(0.92f, min(p.c * 0.45f, 0.07f), p.h)
        val secContainer = toColor(0.27f, min(s.c * 0.55f, 0.08f), s.h)
        val onSecContainer = toColor(0.92f, min(s.c * 0.40f, 0.06f), s.h)
        val tertContainer = toColor(0.27f, min(t.c * 0.55f, 0.09f), t.h)
        val onTertContainer = toColor(0.92f, min(t.c * 0.40f, 0.06f), t.h)

        return darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primContainer,
            onPrimaryContainer = onPrimContainer,
            inversePrimary = toColor(0.47f, min(p.c, 0.18f), p.h),
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secContainer,
            onSecondaryContainer = onSecContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertContainer,
            onTertiaryContainer = onTertContainer,
            background = darkBackground,
            onBackground = toColor(0.91f, 0.010f, baseH),
            surface = darkSurface,
            onSurface = toColor(0.91f, 0.010f, baseH),
            surfaceVariant = toColor(0.26f, 0.016f, baseH),
            onSurfaceVariant = toColor(0.72f, 0.014f, baseH),
            surfaceTint = primary,
            surfaceContainerLowest = toColor(0.11f, 0.012f, baseH),
            surfaceContainerLow = darkSurface,
            surfaceContainer = toColor(0.21f, 0.014f, baseH),
            surfaceContainerHigh = toColor(0.25f, 0.016f, baseH),
            surfaceContainerHighest = toColor(0.29f, 0.018f, baseH),
            outline = toColor(0.45f, 0.018f, baseH),
            outlineVariant = toColor(0.30f, 0.014f, baseH),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6)
        )
    }
}
