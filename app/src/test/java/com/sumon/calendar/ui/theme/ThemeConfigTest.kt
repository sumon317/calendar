package com.sumon.calendar.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeConfigTest {

    @Test
    fun `all color palette presets have valid names and previews`() {
        assertEquals(7, ColorPalettePreset.entries.size)

        ColorPalettePreset.entries.forEach { preset ->
            assertNotNull(preset.displayName)
            assertNotNull(preset.subtitle)
            assertNotNull(preset.previewAccent)
            assertNotNull(preset.previewLightBackground)
            assertNotNull(preset.previewDarkBackground)
        }
    }

    @Test
    fun `fallback to dynamic preset on invalid name`() {
        assertEquals(ColorPalettePreset.DYNAMIC, ColorPalettePreset.fromNameOrDefault(null))
        assertEquals(ColorPalettePreset.DYNAMIC, ColorPalettePreset.fromNameOrDefault("unknown_preset"))
        assertEquals(ColorPalettePreset.CATPPUCCIN, ColorPalettePreset.fromNameOrDefault("CATPPUCCIN"))
        assertEquals(ColorPalettePreset.NORDIC, ColorPalettePreset.fromNameOrDefault("nordic"))
        assertEquals(ColorPalettePreset.EVERFOREST, ColorPalettePreset.fromNameOrDefault("everforest"))
        assertEquals(ColorPalettePreset.TOKYO_NIGHT, ColorPalettePreset.fromNameOrDefault("TOKYO_NIGHT"))
        assertEquals(ColorPalettePreset.ROSE_PINE, ColorPalettePreset.fromNameOrDefault("rose_pine"))
        assertEquals(ColorPalettePreset.WARM_PAPER, ColorPalettePreset.fromNameOrDefault("warm_paper"))
    }

    @Test
    fun `theme modes has expected 4 options`() {
        val modes = ThemeMode.entries.map { it.name }
        assertEquals(listOf("SYSTEM", "LIGHT", "DARK", "OLED"), modes)
        ThemeMode.entries.forEach { mode ->
            assertTrue(mode.titleRes != 0)
            assertNotNull(mode.displayName)
        }
    }
}
