package com.pixelpainter.app.autofill

import com.pixelpainter.core.Palette
import com.pixelpainter.core.PixelArtResult
import com.pixelpainter.core.RgbImage
import com.pixelpainter.core.SamplePalettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorCalibrationTest {

    private val canonical = SamplePalettes.arknights40.colors

    private fun artWith(indices: IntArray): PixelArtResult {
        val preview = IntArray(indices.size) { canonical[indices[it]] }
        return PixelArtResult(
            gridSize = 24,
            palette = Palette(canonical, "test-40"),
            indices = indices,
            preview = RgbImage(24, 24, preview)
        )
    }

    @Test
    fun keepsAll40SlotsWhenOnlyFirstPageWasSampled() {
        val indices = IntArray(24 * 24)
        indices[0] = 0
        indices[23] = 23
        indices[24] = 24
        indices[24 * 24 - 1] = 39
        val art = artWith(indices)
        val sampled = canonical.take(24).toIntArray()

        val calibrated = ColorCalibration.calibrateArt(art, sampled, canonical, 24)

        assertEquals(40, calibrated.palette.colors.size)
        assertEquals(0, calibrated.indices[0])
        assertEquals(23, calibrated.indices[23])
        assertEquals(24, calibrated.indices[24])
        assertEquals(39, calibrated.indices[24 * 24 - 1])
    }

    @Test
    fun sampledFirstPageDoesNotRemapSecondPageColors() {
        val sampled = canonical.take(24).toIntArray()
        val indices = IntArray(24 * 24)
        indices[31] = 29
        val art = artWith(indices)

        val calibrated = ColorCalibration.calibrateArt(art, sampled, canonical, 24)

        assertEquals(29, calibrated.indices[31])
        assertTrue(calibrated.indices[31] >= 24)
    }

    @Test
    fun screenshotNoiseStillPrefersCanonicalSlot() {
        val sampled = listOf(
            0xFF212222.toInt(), 0xFFB5B5B5.toInt(), 0xFFEBE6E0.toInt(), 0xFFFFFFFF.toInt(),
            0xFFC13F3F.toInt(), 0xFF901E13.toInt(), 0xFFC42D4E.toInt(), 0xFFDA9A90.toInt(),
            0xFFF09D7B.toInt(), 0xFFF2D0C4.toInt(), 0xFFFAEFEB.toInt(), 0xFFFAF6EA.toInt(),
            0xFFDAD1C8.toInt(), 0xFFDFCFAE.toInt(), 0xFFC76935.toInt(), 0xFFCA8E50.toInt(),
            0xFFE59D38.toInt(), 0xFFF2CB54.toInt(), 0xFFF8E5A1.toInt(), 0xFFB4B480.toInt(),
            0xFFC7D87F.toInt(), 0xFF6C6E20.toInt(), 0xFFAD915F.toInt(), 0xFFA58F77.toInt()
        ).toIntArray()
        val indices = IntArray(24 * 24)
        indices[4] = 4
        indices[16] = 16
        indices[20] = 20
        val art = artWith(indices)

        val calibrated = ColorCalibration.calibrateArt(art, sampled, canonical, 24)

        assertEquals(4, calibrated.indices[4])
        assertEquals(16, calibrated.indices[16])
        assertEquals(20, calibrated.indices[20])
    }

    @Test
    fun emptySamplingReturnsArtUnchanged() {
        val art = artWith(IntArray(24 * 24) { it % 40 })
        val calibrated = ColorCalibration.calibrateArt(art, IntArray(0), canonical, 24)

        assertEquals(art.indices.toList(), calibrated.indices.toList())
        assertEquals(40, calibrated.palette.colors.size)
    }
}
