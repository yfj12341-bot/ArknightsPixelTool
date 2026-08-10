package com.pixelpainter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageAdjustmentTest {

    @Test
    fun identityAdjustmentsPreserveImage() {
        val source = gradient(4, 3)
        val result = ImageAdjustments.apply(source)
        assertTrue(source.pixels.contentEquals(result.pixels))
    }

    @Test
    fun rotation90FlipsDimensionsAndMovesCorners() {
        val red = ColorMath.argb(255, 0, 0)
        val green = ColorMath.argb(0, 255, 0)
        val blue = ColorMath.argb(0, 0, 255)
        val black = ColorMath.argb(0, 0, 0)
        val source = RgbImage(
            2,
            3,
            intArrayOf(red, green, blue, black, black, blue)
        )

        val rotated = ImageAdjustments.rotate(source, 90)

        assertEquals(3, rotated.width)
        assertEquals(2, rotated.height)
        assertEquals(black, rotated[0, 0])
        assertEquals(blue, rotated[1, 0])
        assertEquals(red, rotated[2, 0])
        assertEquals(blue, rotated[0, 1])
        assertEquals(black, rotated[1, 1])
        assertEquals(green, rotated[2, 1])
    }

    @Test
    fun rotation270ReturnsToOriginalAfter90() {
        val source = gradient(3, 2)
        val rotated = ImageAdjustments.rotate(ImageAdjustments.rotate(source, 90), 270)
        assertTrue(source.pixels.contentEquals(rotated.pixels))
    }

    @Test
    fun arbitraryRotationKeepsCenterPixelAndFillsOutside() {
        val black = ColorMath.argb(0, 0, 0)
        val red = ColorMath.argb(255, 0, 0)
        val source = RgbImage(
            3,
            3,
            intArrayOf(
                black, black, black,
                black, red, black,
                black, black, black
            )
        )

        val rotated = ImageAdjustments.rotate(source, 45)

        assertEquals(red, rotated[1, 1])
        assertEquals(black, rotated[0, 0])
    }

    @Test
    fun fullRotationReturnsOriginal() {
        val source = gradient(4, 3)
        val rotated = ImageAdjustments.rotate(source, 360)
        assertTrue(source.pixels.contentEquals(rotated.pixels))
    }

    @Test
    fun brightnessAndContrastShiftNeutralPixels() {
        val source = RgbImage(
            3,
            1,
            intArrayOf(
                ColorMath.argb(64, 64, 64),
                ColorMath.argb(128, 128, 128),
                ColorMath.argb(192, 192, 192)
            )
        )

        val brightened = ImageAdjustments.apply(source, brightness = -100)
        assertEquals(0, ColorMath.red(brightened[0, 0]))
        assertEquals(28, ColorMath.red(brightened[1, 0]))
        assertEquals(92, ColorMath.red(brightened[2, 0]))

        val contrasted = ImageAdjustments.apply(source, contrast = 100)
        assertEquals(0, ColorMath.red(contrasted[0, 0]))
        assertEquals(128, ColorMath.red(contrasted[1, 0]))
        assertEquals(255, ColorMath.red(contrasted[2, 0]))
    }

    @Test
    fun hueRotationTurnsRedToGreen() {
        val source = onePixel(ColorMath.argb(255, 0, 0))
        val result = ImageAdjustments.apply(source, hueDegrees = 120)
        assertEquals(ColorMath.argb(0, 255, 0), result[0, 0])
    }

    @Test
    fun zeroSaturationKeepsOnlyValueChannel() {
        val source = RgbImage(
            3,
            1,
            intArrayOf(
                ColorMath.argb(255, 40, 40),
                ColorMath.argb(20, 255, 80),
                ColorMath.argb(30, 50, 255)
            )
        )
        val result = ImageAdjustments.apply(source, saturation = -100)
        for (x in 0 until 3) {
            val color = result[x, 0]
            assertEquals(ColorMath.red(color), ColorMath.green(color))
            assertEquals(ColorMath.green(color), ColorMath.blue(color))
        }
    }

    private fun gradient(width: Int, height: Int): RgbImage {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = ColorMath.argb(
                    x * 40,
                    y * 50,
                    (x + y) * 30
                )
            }
        }
        return RgbImage(width, height, pixels)
    }

    private fun onePixel(color: Int): RgbImage =
        RgbImage(1, 1, intArrayOf(color))
}
