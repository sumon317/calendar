package com.sumon.calendar.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// --- Dynamic Fallback (for Android < 12) ---
private val DynamicFallbackLightColors = lightColorScheme(
    primary = Color(0xFF3F51B5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE1F9),
    onPrimaryContainer = Color(0xFF00115A),
    secondary = Color(0xFF5B5D72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E1F9),
    onSecondaryContainer = Color(0xFF171A2C),
    tertiary = Color(0xFF75546F),
    onTertiary = Color.White,
    background = Color(0xFFFEFBFF),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFEFBFF),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    outline = Color(0xFF767680),
)

private val DynamicFallbackDarkColors = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    onPrimary = Color(0xFF08218A),
    primaryContainer = Color(0xFF263B9B),
    onPrimaryContainer = Color(0xFFDEE1F9),
    secondary = Color(0xFFC4C5DD),
    onSecondary = Color(0xFF2C2F42),
    secondaryContainer = Color(0xFF434559),
    onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFFE4BADB),
    onTertiary = Color(0xFF43273F),
    background = Color(0xFF1B1B1F),
    onBackground = Color(0xFFE4E1E6),
    surface = Color(0xFF1B1B1F),
    onSurface = Color(0xFFE4E1E6),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    outline = Color(0xFF90909A),
)

// --- Nordic Frost Palette ---
private val NordicLightColors = lightColorScheme(
    primary = Color(0xFF5E81AC), // Nord10
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E2EC),
    onPrimaryContainer = Color(0xFF1E3654),
    secondary = Color(0xFF81A1C1), // Nord9
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E9F0), // Nord5
    onSecondaryContainer = Color(0xFF2E3440),
    tertiary = Color(0xFF88C0D0), // Nord8
    onTertiary = Color(0xFF2E3440),
    background = Color(0xFFECEFF4), // Nord6
    onBackground = Color(0xFF2E3440),
    surface = Color(0xFFF4F6F9),
    onSurface = Color(0xFF2E3440),
    surfaceVariant = Color(0xFFE5E9F0),
    onSurfaceVariant = Color(0xFF4C566A), // Nord3
    outline = Color(0xFFD8DEE9), // Nord4
)

private val NordicDarkColors = darkColorScheme(
    primary = Color(0xFF88C0D0), // Nord8
    onPrimary = Color(0xFF1E2836),
    primaryContainer = Color(0xFF3B4A5E),
    onPrimaryContainer = Color(0xFFECEFF4),
    secondary = Color(0xFF81A1C1), // Nord9
    onSecondary = Color(0xFF1E2836),
    secondaryContainer = Color(0xFF434C5E), // Nord2
    onSecondaryContainer = Color(0xFFD8DEE9),
    tertiary = Color(0xFFA3BE8C), // Nord14 (Aurora Green)
    onTertiary = Color(0xFF1E2836),
    background = Color(0xFF242933),
    onBackground = Color(0xFFECEFF4),
    surface = Color(0xFF2E3440), // Nord0
    onSurface = Color(0xFFECEFF4),
    surfaceVariant = Color(0xFF3B4252), // Nord1
    onSurfaceVariant = Color(0xFFD8DEE9),
    outline = Color(0xFF4C566A),
)

// --- Catppuccin Palette (Latte & Mocha) ---
private val CatppuccinLightColors = lightColorScheme(
    primary = Color(0xFF8839EF), // Mauve
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF1D4F9),
    onPrimaryContainer = Color(0xFF470C8F),
    secondary = Color(0xFF1E66F5), // Blue
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCD0DA), // Surface0
    onSecondaryContainer = Color(0xFF4C4F69),
    tertiary = Color(0xFF179299), // Teal
    onTertiary = Color.White,
    background = Color(0xFFEFF1F5), // Base
    onBackground = Color(0xFF4C4F69), // Text
    surface = Color(0xFFE6E9EF), // Mantle
    onSurface = Color(0xFF4C4F69),
    surfaceVariant = Color(0xFFDCE0E8), // Crust
    onSurfaceVariant = Color(0xFF5C5F77),
    outline = Color(0xFFBCC0CC),
)

private val CatppuccinDarkColors = darkColorScheme(
    primary = Color(0xFFCBA6F7), // Mauve
    onPrimary = Color(0xFF1E1E2E), // Base
    primaryContainer = Color(0xFF4E376E),
    onPrimaryContainer = Color(0xFFF5E0DC),
    secondary = Color(0xFF89B4FA), // Blue
    onSecondary = Color(0xFF1E1E2E),
    secondaryContainer = Color(0xFF313244), // Surface0
    onSecondaryContainer = Color(0xFFCDD6F4),
    tertiary = Color(0xFF94E2D5), // Teal
    onTertiary = Color(0xFF1E1E2E),
    background = Color(0xFF181825), // Mantle
    onBackground = Color(0xFFCDD6F4), // Text
    surface = Color(0xFF1E1E2E), // Base
    onSurface = Color(0xFFCDD6F4),
    surfaceVariant = Color(0xFF313244),
    onSurfaceVariant = Color(0xFFA6ADC8),
    outline = Color(0xFF45475A),
)

// --- Everforest Palette ---
private val EverforestLightColors = lightColorScheme(
    primary = Color(0xFF4F7335), // Forest Green
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4C8),
    onPrimaryContainer = Color(0xFF18280D),
    secondary = Color(0xFFD07D4C), // Terracotta
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBD8BE),
    onSecondaryContainer = Color(0xFF442409),
    tertiary = Color(0xFF3A7D78), // Pine Cyan
    onTertiary = Color.White,
    background = Color(0xFFFDF6E3), // Cream
    onBackground = Color(0xFF5C6A72),
    surface = Color(0xFFF4EED9),
    onSurface = Color(0xFF5C6A72),
    surfaceVariant = Color(0xFFEAE2C8),
    onSurfaceVariant = Color(0xFF708089),
    outline = Color(0xFFBDC3C3),
)

private val EverforestDarkColors = darkColorScheme(
    primary = Color(0xFFA7C080), // Soft Sage Green
    onPrimary = Color(0xFF232A2E),
    primaryContainer = Color(0xFF3A4B3A),
    onPrimaryContainer = Color(0xFFD3E6BE),
    secondary = Color(0xFFE69875), // Terracotta
    onSecondary = Color(0xFF2D353B),
    secondaryContainer = Color(0xFF4F3F35),
    onSecondaryContainer = Color(0xFFFAD1BD),
    tertiary = Color(0xFF7FBBB3), // Soft Cyan
    onTertiary = Color(0xFF232A2E),
    background = Color(0xFF1E2326), // Forest Deep
    onBackground = Color(0xFFD3C6AA),
    surface = Color(0xFF272E33),
    onSurface = Color(0xFFD3C6AA),
    surfaceVariant = Color(0xFF2D353B),
    onSurfaceVariant = Color(0xFF9DA9A0),
    outline = Color(0xFF4F5B58),
)

// --- Tokyo Night Palette ---
private val TokyoNightLightColors = lightColorScheme(
    primary = Color(0xFF3D59A1), // Deep Blue
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1DCFA),
    onPrimaryContainer = Color(0xFF111E44),
    secondary = Color(0xFF8F5E99), // Soft Violet
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1DDF8),
    onSecondaryContainer = Color(0xFF3B1E45),
    tertiary = Color(0xFF2E7DE9),
    onTertiary = Color.White,
    background = Color(0xFFF4F5F9),
    onBackground = Color(0xFF343B58),
    surface = Color(0xFFEAEBF3),
    onSurface = Color(0xFF343B58),
    surfaceVariant = Color(0xFFDEE0EA),
    onSurfaceVariant = Color(0xFF565A6E),
    outline = Color(0xFFC0CAF5),
)

private val TokyoNightDarkColors = darkColorScheme(
    primary = Color(0xFF7AA2F7), // Neon Blue
    onPrimary = Color(0xFF15161E),
    primaryContainer = Color(0xFF2B3B66),
    onPrimaryContainer = Color(0xFFC4D5FF),
    secondary = Color(0xFFBB9AF7), // Neon Purple
    onSecondary = Color(0xFF1A1B26),
    secondaryContainer = Color(0xFF3D325E),
    onSecondaryContainer = Color(0xFFE7D8FF),
    tertiary = Color(0xFF7DCFFF), // Neon Cyan
    onTertiary = Color(0xFF15161E),
    background = Color(0xFF16161E),
    onBackground = Color(0xFFC0CAF5),
    surface = Color(0xFF1A1B26),
    onSurface = Color(0xFFC0CAF5),
    surfaceVariant = Color(0xFF24283B),
    onSurfaceVariant = Color(0xFFA9B1D6),
    outline = Color(0xFF414868),
)

// --- Rosé Pine Palette ---
private val RosePineLightColors = lightColorScheme(
    primary = Color(0xFFB4637A), // Love (Rose)
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF4D1D8),
    onPrimaryContainer = Color(0xFF471723),
    secondary = Color(0xFF907AA9), // Iris
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF0),
    onSecondaryContainer = Color(0xFF322343),
    tertiary = Color(0xFFEA9D34), // Gold
    onTertiary = Color.White,
    background = Color(0xFFFAF4ED), // Dawn Base
    onBackground = Color(0xFF575279), // Text
    surface = Color(0xFFF2E9E1), // Surface
    onSurface = Color(0xFF575279),
    surfaceVariant = Color(0xFFE5DDD5),
    onSurfaceVariant = Color(0xFF797593),
    outline = Color(0xFFCECAC3),
)

private val RosePineDarkColors = darkColorScheme(
    primary = Color(0xFFEBBCBA), // Rose
    onPrimary = Color(0xFF191724),
    primaryContainer = Color(0xFF513840),
    onPrimaryContainer = Color(0xFFF9DADB),
    secondary = Color(0xFFC4A7E7), // Iris
    onSecondary = Color(0xFF191724),
    secondaryContainer = Color(0xFF3E3253),
    onSecondaryContainer = Color(0xFFEEDEFF),
    tertiary = Color(0xFFF6C177), // Gold
    onTertiary = Color(0xFF191724),
    background = Color(0xFF14121E),
    onBackground = Color(0xFFE0DEF4),
    surface = Color(0xFF191724), // Base
    onSurface = Color(0xFFE0DEF4),
    surfaceVariant = Color(0xFF1F1D2E), // Surface
    onSurfaceVariant = Color(0xFF908CAA),
    outline = Color(0xFF403D52),
)

// --- Warm Paper Palette ---
private val WarmPaperLightColors = lightColorScheme(
    primary = Color(0xFF9E4828), // Deep Terracotta
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF9DFD5),
    onPrimaryContainer = Color(0xFF3A150A),
    secondary = Color(0xFF705D4A), // Warm Walnut
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECE4D9),
    onSecondaryContainer = Color(0xFF282015),
    tertiary = Color(0xFF886A2D), // Raw Umber
    onTertiary = Color.White,
    background = Color(0xFFF9F7F1), // Cream Parchment
    onBackground = Color(0xFF282520), // Charcoal Ink
    surface = Color(0xFFF1EEE5),
    onSurface = Color(0xFF282520),
    surfaceVariant = Color(0xFFE7E2D6),
    onSurfaceVariant = Color(0xFF5D584F),
    outline = Color(0xFFCEC8BA),
)

private val WarmPaperDarkColors = darkColorScheme(
    primary = Color(0xFFE58F70), // Warm Clay
    onPrimary = Color(0xFF2E170F),
    primaryContainer = Color(0xFF4E2B1E),
    onPrimaryContainer = Color(0xFFFAD7CC),
    secondary = Color(0xFFD6C3AF), // Warm Sand
    onSecondary = Color(0xFF2A231C),
    secondaryContainer = Color(0xFF41382E),
    onSecondaryContainer = Color(0xFFECE2D4),
    tertiary = Color(0xFFD9B973), // Muted Amber
    onTertiary = Color(0xFF2B210B),
    background = Color(0xFF171513), // Deep Espresso
    onBackground = Color(0xFFEBE6DC),
    surface = Color(0xFF201D1A),
    onSurface = Color(0xFFEBE6DC),
    surfaceVariant = Color(0xFF2B2723),
    onSurfaceVariant = Color(0xFFAFA99E),
    outline = Color(0xFF4C453E),
)

/**
 * Transforms a dark Material 3 color scheme into an ultra-dark OLED pitch black scheme,
 * optimizing battery consumption on AMOLED displays while maintaining accent vibrancy.
 */
fun ColorScheme.toOledScheme(): ColorScheme = this.copy(
    background = Color.Black,
    surface = Color(0xFF0C0C0E),
    surfaceVariant = Color(0xFF151518),
    surfaceContainer = Color(0xFF101014),
    surfaceContainerHigh = Color(0xFF18181D),
    surfaceContainerHighest = Color(0xFF202026),
    surfaceContainerLow = Color(0xFF070709),
    surfaceContainerLowest = Color.Black,
)

@Composable
fun CalendarTheme(
    palette: ColorPalettePreset = ColorPalettePreset.DYNAMIC,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemInDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.OLED -> true
    }
    val isOled = themeMode == ThemeMode.OLED

    val baseScheme = when (palette) {
        ColorPalettePreset.DYNAMIC -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (isDark) DynamicFallbackDarkColors else DynamicFallbackLightColors
            }
        }
        ColorPalettePreset.NORDIC -> if (isDark) NordicDarkColors else NordicLightColors
        ColorPalettePreset.CATPPUCCIN -> if (isDark) CatppuccinDarkColors else CatppuccinLightColors
        ColorPalettePreset.EVERFOREST -> if (isDark) EverforestDarkColors else EverforestLightColors
        ColorPalettePreset.TOKYO_NIGHT -> if (isDark) TokyoNightDarkColors else TokyoNightLightColors
        ColorPalettePreset.ROSE_PINE -> if (isDark) RosePineDarkColors else RosePineLightColors
        ColorPalettePreset.WARM_PAPER -> if (isDark) WarmPaperDarkColors else WarmPaperLightColors
    }

    val finalScheme = if (isOled) baseScheme.toOledScheme() else baseScheme

    MaterialTheme(
        colorScheme = finalScheme,
        typography = Typography,
        content = content,
    )
}
