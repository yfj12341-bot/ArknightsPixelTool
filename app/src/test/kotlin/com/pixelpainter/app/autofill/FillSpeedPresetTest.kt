package com.pixelpainter.app.autofill

import org.junit.Assert.assertTrue
import org.junit.Test

class FillSpeedPresetTest {

    @Test
    fun presetsGetProgressivelySlower() {
        val presets = FillSpeedPreset.values()
        assertTrue(presets.size >= 3)
        for (i in 1 until presets.size) {
            assertTrue(
                "canvas tap delay must increase with preset index",
                presets[i].tapDelayMs > presets[i - 1].tapDelayMs
            )
            assertTrue(
                "palette tap delay must increase with preset index",
                presets[i].paletteDelayMs > presets[i - 1].paletteDelayMs
            )
            assertTrue(
                "swipe delay must increase with preset index",
                presets[i].swipeDelayMs > presets[i - 1].swipeDelayMs
            )
        }
    }

    @Test
    fun paletteStaysFastAndSwipeAllowsBounceSettle() {
        // Palette taps do not cause mis-touch, so they stay close to the canvas
        // tap delay. Swipes carry a longer pause because the game's page-flip
        // bounce must settle before the next color selection.
        for (preset in FillSpeedPreset.values()) {
            assertTrue(
                "${preset.label}: palette delay should not exceed 2x canvas tap delay",
                preset.paletteDelayMs <= preset.tapDelayMs * 2
            )
            assertTrue(
                "${preset.label}: swipe delay must allow page bounce to settle",
                preset.swipeDelayMs >= 250
            )
            assertTrue(
                "${preset.label}: swipe delay should not be excessive",
                preset.swipeDelayMs <= 800
            )
        }
    }
}
