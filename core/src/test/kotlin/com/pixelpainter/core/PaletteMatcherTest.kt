package com.pixelpainter.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PaletteMatcherTest {

    private val fixed = SamplePalettes.arknights40

    @Test
    fun colorfulPixelsStillUseTheFullPalette() {
        val red = ColorMath.argb(255, 0, 0)
        val index = PaletteMatcher.nearestIndex(red, fixed.colors)
        assertEquals(ColorMath.argb(211, 47, 54), fixed.colors[index])
    }

    @Test
    fun neutralPixelsUsePlainOklabDistance() {
        // Restored legacy behavior from the backup APK: no neutral chroma bias.
        for (gray in listOf(80, 110, 140, 170, 200, 230)) {
            val color = ColorMath.argb(gray, gray, gray)
            val curve = fixed.colors.map { ColorMath.srgbToOklab(it) }
            val target = ColorMath.srgbToOklab(color)
            val expected = curve.indices.minByOrNull { index ->
                ColorMath.oklabDeltaE(target, curve[index])
            }!!
            assertEquals(
                "gray $gray should use the plain OKLab nearest index",
                expected,
                PaletteMatcher.nearestIndex(color, fixed.colors)
            )
        }
    }
}
