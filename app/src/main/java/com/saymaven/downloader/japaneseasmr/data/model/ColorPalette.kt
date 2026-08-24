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
    ),
    NORDIC_ICE(
        title = "Nordic Ice",
        description = "Biru es kutub utara sejuk",
        primary = Color(0xFF80DEEA),
        secondary = Color(0xFFB2EBF2),
        tertiary = Color(0xFF90CAF9),
        darkBackground = Color(0xFF0F171B),
        darkSurface = Color(0xFF152229)
    ),
    MATCHA_GREEN(
        title = "Matcha Tea",
        description = "Hijau teh matcha khas Jepang",
        primary = Color(0xFF81C784),
        secondary = Color(0xFFC8E6C9),
        tertiary = Color(0xFFAED581),
        darkBackground = Color(0xFF101711),
        darkSurface = Color(0xFF172319)
    ),
    MIDNIGHT_OBSIDIAN(
        title = "Midnight Obsidian",
        description = "Hitam amoled pekat beraksen perak",
        primary = Color(0xFFE0E0E0),
        secondary = Color(0xFF9E9E9E),
        tertiary = Color(0xFF616161),
        darkBackground = Color(0xFF080808),
        darkSurface = Color(0xFF121212)
    ),
    CORAL_PEACH(
        title = "Coral Peach",
        description = "Koral lembut & peach manis",
        primary = Color(0xFFFF8A65),
        secondary = Color(0xFFFFCCBC),
        tertiary = Color(0xFFFFAB91),
        darkBackground = Color(0xFF191210),
        darkSurface = Color(0xFF241916)
    ),
    CYBERPUNK(
        title = "Cyber Magenta",
        description = "Magenta neon gaya cyberpunk",
        primary = Color(0xFFFF007F),
        secondary = Color(0xFF00F0FF),
        tertiary = Color(0xFFFFE600),
        darkBackground = Color(0xFF120814),
        darkSurface = Color(0xFF1F0F23)
    ),
    ROYAL_INDIGO(
        title = "Royal Indigo",
        description = "Biru indigo kebangsawanan",
        primary = Color(0xFF5C6BC0),
        secondary = Color(0xFF9FA8DA),
        tertiary = Color(0xFF3F51B5),
        darkBackground = Color(0xFF0F111E),
        darkSurface = Color(0xFF16192B)
    ),
    HONEY_GOLD(
        title = "Honey Gold",
        description = "Kuning madu keemasan mewah",
        primary = Color(0xFFFFD54F),
        secondary = Color(0xFFFFF59D),
        tertiary = Color(0xFFFFB300),
        darkBackground = Color(0xFF18150B),
        darkSurface = Color(0xFF231F10)
    ),
    FOREST_MOSS(
        title = "Forest Moss",
        description = "Hijau lumut hutan lebat",
        primary = Color(0xFF558B2F),
        secondary = Color(0xFF9CCC65),
        tertiary = Color(0xFF33691E),
        darkBackground = Color(0xFF0D1409),
        darkSurface = Color(0xFF141F0E)
    ),
    BUBBLEGUM(
        title = "Bubblegum Sky",
        description = "Merah muda permen karet & biru muda",
        primary = Color(0xFFFF4081),
        secondary = Color(0xFF40C4FF),
        tertiary = Color(0xFF7C4DFF),
        darkBackground = Color(0xFF160F1A),
        darkSurface = Color(0xFF221627)
    ),
    ELECTRIC_VIOLET(
        title = "Electric Violet",
        description = "Violet elektrik menyala",
        primary = Color(0xFF7C4DFF),
        secondary = Color(0xFFB388FF),
        tertiary = Color(0xFF536DFE),
        darkBackground = Color(0xFF110E1C),
        darkSurface = Color(0xFF1A152B)
    ),
    AUTUMN_MAPLE(
        title = "Autumn Maple",
        description = "Merah daun maple musim gugur",
        primary = Color(0xFFD84315),
        secondary = Color(0xFFFF8A65),
        tertiary = Color(0xFFBF360C),
        darkBackground = Color(0xFF170E0B),
        darkSurface = Color(0xFF221410)
    ),
    CARAMEL_MOCHA(
        title = "Caramel Mocha",
        description = "Cokelat karamel & kopi hangat",
        primary = Color(0xFFA1887F),
        secondary = Color(0xFFD7CCC8),
        tertiary = Color(0xFF8D6E63),
        darkBackground = Color(0xFF141110),
        darkSurface = Color(0xFF1E1A18)
    )
}
