package com.pixelpainter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownsampleTest {

    @Test
    fun areaAveragePicksBlendedCorner() {
        val source = halfSplitImage(48, ColorMath.argb(255, 0, 0), ColorMath.argb(0, 0, 255))
        val out = Downsample.resize(source, 24, DownsampleMode.AVERAGE)
        val corner = out[0, 0]
        assertEquals(255, ColorMath.red(corner))
        assertEquals(0, ColorMath.blue(corner))

        val opposite = out[23, 23]
        assertEquals(0, ColorMath.red(opposite))
        assertEquals(255, ColorMath.blue(opposite))
    }

    @Test
    fun dominantKeepsHardEdges() {
        val red = ColorMath.argb(255, 0, 0)
        val blue = ColorMath.argb(0, 0, 255)
        val source = halfSplitImage(48, red, blue)
        val palette = Palette(listOf(red, blue))
        val result = Downsample.resize(source, 24, DownsampleMode.DOMINANT, palette)
        assertEquals(red, result[0, 0])
        assertEquals(blue, result[23, 23])
    }

    @Test
    fun dominantTieBreaksWithAverageColor() {
        val black = ColorMath.argb(0, 0, 0)
        val white = ColorMath.argb(255, 255, 255)
        val gray = ColorMath.argb(180, 180, 180)
        val palette = Palette(listOf(black, white, gray))
        val source = trueCheckerboardImage(48)

        val result = Downsample.resize(source, 24, DownsampleMode.DOMINANT, palette)

        for (pixel in result.pixels) {
            assertEquals("tie should fall back to the block average color", gray, pixel)
        }
    }

    @Test
    fun boxModeStaysInPaletteCoverage() {
        val source = halfSplitImage(48, ColorMath.argb(255, 80, 30), ColorMath.argb(20, 40, 200))
        val options = PixelArtOptions(downscaleMode = DownsampleMode.BOX)
        val result = PixelArtConverter.convert(source, options)
        assertEquals(24, result.preview.width)
        assertEquals(24, result.preview.height)
        for (index in result.indices) {
            assertEquals(result.preview.pixels[index], result.palette.colors[result.indices[index]])
        }
    }

    @Test
    fun boxAverageMatchesAreaAverageOnEvenScaling() {
        val source = trueCheckerboardImage(48)
        val box = Downsample.resize(source, 24, DownsampleMode.BOX)
        val average = Downsample.areaAverage(source, 24, 24)
        assertTrue(
            "BOX should equal plain area average when source maps 2:1",
            box.pixels.contentEquals(average.pixels)
        )
    }

    @Test
    fun boxAverageIsSmoothOnScatteredPattern() {
        val source = trueCheckerboardImage(48)
        val expected = ColorMath.argb(127, 127, 127)
        val result = Downsample.resize(source, 24, DownsampleMode.BOX)

        for (pixel in result.pixels) {
            assertEquals(
                "BOX should blend scattered pixels into a neutral gray",
                expected,
                pixel
            )
        }
    }

    private fun halfSplitImage(
        size: Int,
        left: Int,
        right: Int
    ): RgbImage {
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (x < size / 2) left else right
            }
        }
        return RgbImage(size, size, pixels)
    }

    private fun trueCheckerboardImage(size: Int): RgbImage {
        val black = ColorMath.argb(0, 0, 0)
        val white = ColorMath.argb(255, 255, 255)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if ((x + y) % 2 == 0) white else black
            }
        }
        return RgbImage(size, size, pixels)
    }
}
