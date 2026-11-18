package com.sumon.calendar.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.sumon.calendar.R

enum class ThemeMode(
    @StringRes val titleRes: Int,
    val displayName: String,
) {
    SYSTEM(R.string.theme_mode_system, "System"),
    LIGHT(R.string.theme_mode_light, "Light"),
    DARK(R.string.theme_mode_dark, "Dark"),
    OLED(R.string.theme_mode_oled, "OLED"),
}

enum class ColorPalettePreset(
    val displayName: String,
    val subtitle: String,
    val previewAccent: Color,
    val previewLightBackground: Color,
    val previewDarkBackground: Color,
) {
    DYNAMIC(
        displayName = "Dynamic Material You",
        subtitle = "System wallpaper colors (Android 12+)",
        previewAccent = Color(0xFF4458C7),
        previewLightBackground = Color(0xFFF4F5FB),
        previewDarkBackground = Color(0xFF1B1B1F),
    ),
    NORDIC(
        displayName = "Nordic Frost",
        subtitle = "Arctic cool blues & slate",
        previewAccent = Color(0xFF88C0D0),
        previewLightBackground = Color(0xFFECEFF4),
        previewDarkBackground = Color(0xFF2E3440),
    ),
    CATPPUCCIN(
        displayName = "Catppuccin",
        subtitle = "Soothing pastel mauve & latte",
        previewAccent = Color(0xFFCBA6F7),
        previewLightBackground = Color(0xFFEFF1F5),
        previewDarkBackground = Color(0xFF1E1E2E),
    ),
    EVERFOREST(
        displayName = "Everforest",
        subtitle = "Natural earthy forest & amber",
        previewAccent = Color(0xFFA7C080),
        previewLightBackground = Color(0xFFFDF6E3),
        previewDarkBackground = Color(0xFF272E33),
    ),
    TOKYO_NIGHT(
        displayName = "Tokyo Night",
        subtitle = "Vibrant neon dusk & blue",
        previewAccent = Color(0xFF7AA2F7),
        previewLightBackground = Color(0xFFF2F3F7),
        previewDarkBackground = Color(0xFF1A1B26),
    ),
    ROSE_PINE(
        displayName = "Rosé Pine",
        subtitle = "Vintage pine, rose & soho vibes",
        previewAccent = Color(0xFFEBBCBA),
        previewLightBackground = Color(0xFFFAF4ED),
        previewDarkBackground = Color(0xFF191724),
    ),
    WARM_PAPER(
        displayName = "Warm Paper",
        subtitle = "Bookish sepia & terracotta",
        previewAccent = Color(0xFFE28766),
        previewLightBackground = Color(0xFFF7F5F0),
        previewDarkBackground = Color(0xFF1F1D1B),
    );

    companion object {
        fun fromNameOrDefault(name: String?): ColorPalettePreset {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DYNAMIC
        }
    }
}
