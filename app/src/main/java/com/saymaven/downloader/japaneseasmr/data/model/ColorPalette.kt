package com.saymaven.downloader.japaneseasmr.data.model

import androidx.compose.ui.graphics.Color

enum class ColorPalette(
    val title: String,
    val description: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val darkBackground: Color,
    val darkSurface: Color,
    val lightBackground: Color = Color(0xFFFCF5F4),
    val lightSurface: Color = Color(0xFFFFFFFF)
) {
    DEFAULT(
        title = "Default Rose",
        description = "Tema standar bawaan aplikasi",
        primary = Color(0xFFFFB4AB),
        secondary = Color(0xFFE7BDB7),
        tertiary = Color(0xFFDFBFAF),
        darkBackground = Color(0xFF141212),
        darkSurface = Color(0xFF1F1B1B)
    ),
    CYAN_WAVE(
        title = "Cyan Wave",
        description = "Biru toska cerah nan segar",
        primary = Color(0xFF4DD0E1),
        secondary = Color(0xFF80DEEA),
        tertiary = Color(0xFF7986CB),
        darkBackground = Color(0xFF0E1618),
        darkSurface = Color(0xFF142125)
    ),
    SLATE_PLATINUM(
        title = "Slate Platinum",
        description = "Nuansa abu-abu minimalis elegan",
        primary = Color(0xFFCFD8DC),
        secondary = Color(0xFFECEFF1),
        tertiary = Color(0xFF78909C),
        darkBackground = Color(0xFF121518),
        darkSurface = Color(0xFF1A1F24)
    ),
    MINT_EMERALD(
        title = "Mint Emerald",
        description = "Hijau mint segar dan teduh",
        primary = Color(0xFF69F0AE),
        secondary = Color(0xFFA7F3D0),
        tertiary = Color(0xFF26A69A),
        darkBackground = Color(0xFF0D1712),
        darkSurface = Color(0xFF132219)
    ),
    GOLDEN_SAND(
        title = "Golden Sand",
        description = "Kombinasi biru langit dan pasir emas",
        primary = Color(0xFF40C4FF),
        secondary = Color(0xFFFFE0B2),
        tertiary = Color(0xFFFFA726),
        darkBackground = Color(0xFF181510),
        darkSurface = Color(0xFF241F16)
    ),
    SAKURA(
        title = "Sakura Blossom",
        description = "Merah muda bunga sakura anggun",
        primary = Color(0xFFFF80AB),
        secondary = Color(0xFFFF80DF),
        tertiary = Color(0xFFEA80FC),
        darkBackground = Color(0xFF1A1116),
        darkSurface = Color(0xFF25161F)
    ),
    OCEAN(
        title = "Deep Sapphire",
        description = "Biru laut dalam yang tenang",
        primary = Color(0xFF448AFF),
        secondary = Color(0xFF82B1FF),
        tertiary = Color(0xFF00E5FF),
        darkBackground = Color(0xFF0D141E),
        darkSurface = Color(0xFF121D2C)
    ),
    DRACULA(
        title = "Dracula Purple",
        description = "Ungu neon misterius",
        primary = Color(0xFFBD93F9),
        secondary = Color(0xFFFF79C6),
        tertiary = Color(0xFF8BE9FD),
        darkBackground = Color(0xFF191622),
        darkSurface = Color(0xFF211D30)
    ),
    SUNSET(
        title = "Sunset Amber",
        description = "Jingga hangat mentari senja",
        primary = Color(0xFFFF6E40),
        secondary = Color(0xFFFFD180),
        tertiary = Color(0xFFFFAB40),
        darkBackground = Color(0xFF1B120E),
        darkSurface = Color(0xFF271A13)
    ),
    NEON_LIME(
        title = "Neon Lime",
        description = "Hijau lemon futuristik",
        primary = Color(0xFFAEEA00),
        secondary = Color(0xFFE6EE9C),
        tertiary = Color(0xFF00B0FF),
        darkBackground = Color(0xFF14180A),
        darkSurface = Color(0xFF1D240E)
    ),
    LAVENDER(
        title = "Lavender Mist",
        description = "Ungu lavender lembut menenangkan",
        primary = Color(0xFFD1C4E9),
        secondary = Color(0xFFEDE7F6),
        tertiary = Color(0xFF9575CD),
        darkBackground = Color(0xFF15121B),
        darkSurface = Color(0xFF201B29)
    ),
    CRIMSON(
        title = "Crimson Ruby",
        description = "Merah delima yang berani",
        primary = Color(0xFFFF5252),
        secondary = Color(0xFFFF8A80),
        tertiary = Color(0xFFFF4081),
        darkBackground = Color(0xFF1A0F11),
        darkSurface = Color(0xFF261518)
    )
}
