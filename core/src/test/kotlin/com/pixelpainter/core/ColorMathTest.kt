package com.pixelpainter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMathTest {

    @Test
    fun whiteMapsToLightLab() {
        val lab = ColorMath.srgbToLab(0xFFFFFFFF.toInt())
        assertEquals(100f, lab[0], 1.5f)
        assertEquals(0f, lab[1], 1.5f)
        assertEquals(0f, lab[2], 1.5f)
    }

    @Test
    fun blackMapsToDarkLab() {
        val lab = ColorMath.srgbToLab(0xFF000000.toInt())
        assertTrue("L should be near 0, was ${lab[0]}", lab[0] < 1f)
    }

    @Test
    fun identicalColorsHaveZeroDistance() {
        val c = ColorMath.argb(120, 40, 200)
        assertEquals(0f, ColorMath.labDeltaE(ColorMath.srgbToLab(c), ColorMath.srgbToLab(c)), 0.001f)
    }

    @Test
    fun distinguishableColorsHavePositiveDistance() {
        val red = ColorMath.argb(255, 0, 0)
        val blue = ColorMath.argb(0, 0, 255)
        val distance = ColorMath.labDeltaE(ColorMath.srgbToLab(red), ColorMath.srgbToLab(blue))
        assertTrue("red/blue distance should be large, was $distance", distance > 50f)
    }

    @Test
    fun hexRoundTrip() {
        val color = ColorMath.argb(12, 34, 56)
        assertEquals("#0C2238", ColorMath.toHex(color))
        assertEquals(color, ColorMath.fromHex("#0C2238"))
    }
}

