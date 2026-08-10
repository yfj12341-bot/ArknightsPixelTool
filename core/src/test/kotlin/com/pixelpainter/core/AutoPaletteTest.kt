package com.pixelpainter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPaletteTest {

    @Test
    fun fewDistinctColorsAreReturnedVerbatum() {
        val colors = listOf(
            ColorMath.argb(255, 0, 0),
            ColorMath.argb(0, 255, 0),
            ColorMath.argb(0, 0, 255),
            ColorMath.argb(10, 10, 10)
        )
        val image = solidImage(8, colors)
        val palette = AutoPalette.extract(image, 40)
        assertEquals(colors.toSet(), palette.colors.toSet())
    }

    @Test
    fun paletteNeverExceedsRequestedSize() {
        val image = RgbImage(
            width = 64,
            height = 64,
            pixels = IntArray(64 * 64) { i ->
                ColorMath.argb(i * 3 % 256, (i * 7) % 256, (i * 11) % 256)
            }
        )
        val palette = AutoPalette.extract(image, 40)
        assertTrue("palette size was ${palette.size}", palette.size in 1..40)
    }

    private fun solidImage(size: Int, colors: List<Int>): RgbImage {
        val pixels = IntArray(size * size)
        for (i in pixels.indices) {
            pixels[i] = colors[i % colors.size]
        }
        return RgbImage(size, size, pixels)
    }
}

