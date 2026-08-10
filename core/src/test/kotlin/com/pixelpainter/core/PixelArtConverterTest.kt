package com.pixelpainter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelArtConverterTest {

    @Test
    fun convertProduces24x24GridWithAtMost40Colors() {
        val source = gradientImage(400, 400)
        val result = PixelArtConverter.convert(
            source,
            PixelArtOptions(paletteMode = PaletteMode.AUTO)
        )
        assertEquals(24, result.gridSize)
        assertEquals(24 * 24, result.indices.size)
        assertTrue(result.palette.size in 1..40)
        for (index in result.indices) {
            assertTrue(index in 0 until result.palette.size)
        }
    }

    @Test
    fun fixedPaletteIsHonored() {
        val source = gradientImage(200, 200)
        val fixed = SamplePalettes.arknights40
        val result = PixelArtConverter.convert(
            source,
            PixelArtOptions(paletteMode = PaletteMode.FIXED),
            fixedPalette = fixed
        )
        assertEquals(40, result.palette.size)
        for (index in result.indices) {
            assertEquals(fixed.colors[index], result.palette.colors[index])
        }
    }

    @Test
    fun boxModeStaysValidOnHighResolutionImage() {
        val fixed = SamplePalettes.arknights40
        val light = ColorMath.argb(251, 246, 232)
        val dark = fixed.colors[0]
        val source = verticalStripeImage(
            size = 1024,
            stripe = 8,
            background = light,
            line = dark
        )

        val result = PixelArtConverter.convert(
            source,
            PixelArtOptions(
                paletteMode = PaletteMode.FIXED,
                downscaleMode = DownsampleMode.BOX
            ),
            fixedPalette = fixed
        )
        assertEquals(24 * 24, result.indices.size)
        for (index in result.indices) {
            assertTrue(index in 0 until result.palette.size)
        }
    }

    @Test
    fun ditherStillOnlyUsesPaletteColors() {
        val source = gradientImage(120, 120)
        val fixed = SamplePalettes.arknights40
        val result = PixelArtConverter.convert(
            source,
            PixelArtOptions(
                paletteMode = PaletteMode.FIXED,
                dither = true,
                downscaleMode = DownsampleMode.AVERAGE
            ),
            fixedPalette = fixed
        )
        val allowed = fixed.colors.toSet()
        for (pixel in result.preview.pixels) {
            assertTrue("$pixel is not in palette", pixel in allowed)
        }
    }

    @Test
    fun customModeUsesRequestedGridSizeAndColorLimit() {
        val source = gradientImage(300, 300)
        val result = PixelArtConverter.convert(
            source,
            PixelArtOptions(
                gridSize = 48,
                maxColors = 8,
                paletteMode = PaletteMode.CUSTOM
            )
        )
        assertEquals(48, result.gridSize)
        assertEquals(48 * 48, result.indices.size)
        assertTrue(result.palette.size in 1..8)
        for (index in result.indices) {
            assertTrue(index in 0 until result.palette.size)
        }
    }

    @Test
    fun manualSquareCropIsApplied() {
        val red = ColorMath.argb(255, 0, 0)
        val blue = ColorMath.argb(0, 0, 255)
        val source = quadrantImage(
            size = 100,
            topLeft = red,
            topRight = blue,
            bottomLeft = red,
            bottomRight = blue
        )
        val fixed = Palette(listOf(red, blue))

        val topLeft = PixelArtConverter.convert(
            source,
            PixelArtOptions(
                gridSize = 2,
                maxColors = 2,
                paletteMode = PaletteMode.FIXED,
                downscaleMode = DownsampleMode.AVERAGE
            ),
            fixedPalette = fixed,
            crop = CropBounds(startX = 0, startY = 0, sidePixels = 50)
        )
        assertTrue(topLeft.preview.pixels.all { it == red })

        val topRight = PixelArtConverter.convert(
            source,
            PixelArtOptions(
                gridSize = 2,
                maxColors = 2,
                paletteMode = PaletteMode.FIXED,
                downscaleMode = DownsampleMode.AVERAGE
            ),
            fixedPalette = fixed,
            crop = CropBounds(startX = 50, startY = 0, sidePixels = 50)
        )
        assertTrue(topRight.preview.pixels.all { it == blue })
    }

    @Test
    fun fixedPaletteKeepsNeutralRampVariedAndNotCollapsedToWhite() {
        val fixed = SamplePalettes.arknights40
        val source = grayRampImage(256, 256)
        val result = PixelArtConverter.convert(
            source,
            PixelArtOptions(
                paletteMode = PaletteMode.FIXED,
                downscaleMode = DownsampleMode.BOX
            ),
            fixedPalette = fixed
        )
        val white = ColorMath.argb(255, 255, 255)
        assertTrue(
            "gray image should not collapse to pure white",
            result.preview.pixels.count { it == white } < result.indices.size / 4
        )
        assertTrue(
            "gray image should keep several gray levels",
            result.preview.pixels.toSet().size >= 3
        )
    }

    private fun gradientImage(width: Int, height: Int): RgbImage {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = (x * 255 / width).coerceIn(0, 255)
                val g = (y * 255 / height).coerceIn(0, 255)
                val b = ((x + y) * 255 / (width + height)).coerceIn(0, 255)
                pixels[y * width + x] = ColorMath.argb(r, g, b)
            }
        }
        return RgbImage(width, height, pixels)
    }

    private fun grayRampImage(width: Int, height: Int): RgbImage {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val gray = (64 + x * 160 / width).coerceIn(0, 255)
                pixels[y * width + x] = ColorMath.argb(gray, gray, gray)
            }
        }
        return RgbImage(width, height, pixels)
    }

    private fun quadrantImage(
        size: Int,
        topLeft: Int,
        topRight: Int,
        bottomLeft: Int,
        bottomRight: Int
    ): RgbImage {
        val half = size / 2
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val color = when {
                    y < half && x < half -> topLeft
                    y < half -> topRight
                    x < half -> bottomLeft
                    else -> bottomRight
                }
                pixels[y * size + x] = color
            }
        }
        return RgbImage(size, size, pixels)
    }

    private fun verticalStripeImage(
        size: Int,
        stripe: Int,
        background: Int,
        line: Int
    ): RgbImage {
        val startX = (size - stripe) / 2
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (x in startX until startX + stripe) line else background
            }
        }
        return RgbImage(size, size, pixels)
    }
}
