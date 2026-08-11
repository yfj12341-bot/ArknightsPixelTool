package com.pixelpainter.app.autofill

import com.pixelpainter.core.Palette
import com.pixelpainter.core.PixelArtResult
import com.pixelpainter.core.RgbImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoFillActionEngineTest {

    private val palette = Palette(
        name = "test-40",
        colors = (0 until 40).map { 0xFF000000.toInt() or (it shl 8) }
    )

    private fun artWith(indices: IntArray): PixelArtResult {
        val preview = IntArray(indices.size) { palette.colors[indices[it]] }
        return PixelArtResult(
            gridSize = 24,
            palette = palette,
            indices = indices,
            preview = RgbImage(24, 24, preview)
        )
    }

    @Test
    fun groupsCellsByColorAndTapsEveryCanvasCell() {
        val indices = IntArray(24 * 24)
        indices[0] = 0
        indices[1] = 5
        indices[24 * 24 - 1] = 39
        val art = artWith(indices)

        val settings = AutoFillSettings(
            canvasRect = floatArrayOf(100f, 200f, 1108f, 1304f),
            paletteRect = floatArrayOf(1150f, 150f, 1500f, 1000f),
            tapDelayMs = 20L,
            paletteDelayMs = 100L,
            swipeDelayMs = 250L
        )
        val sequence = AutoFillActionEngine.buildSequence(art, settings)

        val taps = sequence.actions.filterIsInstance<AutoFillAction.Tap>()
        val swipes = sequence.actions.filterIsInstance<AutoFillAction.Swipe>()
        assertEquals(24 * 24 + 3, taps.size)
        assertEquals(3, swipes.size)
        assertEquals(3, sequence.neededColors)

        // Palette taps come before their canvas cells.
        val canvasTap = taps.last { it.x > 500f && it.x < 1200f }
        assertTrue(canvasTap.y > 200f)

        // Reset to page 1, then open page 2 for color 39.
        val firstSwipe = swipes.first()
        assertTrue("first swipe should move back to page 1", firstSwipe.startY < firstSwipe.endY)
        assertTrue("next swipe should open page 2", swipes[1].startY > swipes[1].endY)
        assertEquals(settings.swipeDelayMs, swipes.first().waitMs)
    }

    @Test
    fun respectsSwipeDownAsNextPageSetting() {
        val indices = IntArray(24 * 24)
        indices[10] = 25
        val art = artWith(indices)
        val settings = AutoFillSettings(
            canvasRect = floatArrayOf(0f, 0f, 720f, 720f),
            paletteRect = floatArrayOf(760f, 0f, 1080f, 900f),
            swipeUpToNextPage = false,
            tapDelayMs = 1L,
            paletteDelayMs = 1L,
            swipeDelayMs = 1L
        )
        val sequence = AutoFillActionEngine.buildSequence(art, settings)
        val swipes = sequence.actions.filterIsInstance<AutoFillAction.Swipe>()
        assertEquals(3, swipes.size)
        val reset = swipes.first()
        assertTrue("reset should swipe up when next page is down", reset.startY > reset.endY)
        val forward = swipes[1]
        assertTrue("swipe down expected when swipeUpToNextPage=false", forward.startY < forward.endY)
    }

    @Test
    fun resetsToFirstPageBeforeFillingPageTwo() {
        val indices = IntArray(24 * 24)
        indices[7] = 25
        val art = artWith(indices)
        val settings = AutoFillSettings(
            canvasRect = floatArrayOf(0f, 0f, 720f, 720f),
            paletteRect = floatArrayOf(760f, 0f, 1080f, 900f),
            tapDelayMs = 1L,
            paletteDelayMs = 1L,
            swipeDelayMs = 1L
        )
        val sequence = AutoFillActionEngine.buildSequence(art, settings)
        val swipes = sequence.actions.filterIsInstance<AutoFillAction.Swipe>()
        assertEquals(3, swipes.size)
        assertTrue(
            "multi-page fill must reset to the first palette page",
            swipes.first().startY < swipes.first().endY
        )
    }

    @Test
    fun singlePagePaletteNeedsNoSwipes() {
        val indices = IntArray(24 * 24) { it % 8 }
        val art = artWith(indices)
        val settings = AutoFillSettings(
            canvasRect = floatArrayOf(0f, 0f, 600f, 600f),
            paletteRect = floatArrayOf(700f, 0f, 1080f, 900f),
            visibleColors = 40
        )
        val sequence = AutoFillActionEngine.buildSequence(art, settings)
        assertEquals(0, sequence.paletteSwipes)
        assertEquals(24 * 24 + 8, sequence.totalTaps)
    }

    @Test
    fun skipsPureWhiteBackgroundCells() {
        val withWhite = Palette(
            name = "white-background",
            colors = listOf(0xFF202020.toInt(), 0xFFFFFFFF.toInt(), 0xFFE41F1F.toInt())
        )
        val indices = IntArray(24 * 24) { if (it % 2 == 0) 1 else 2 }
        val preview = IntArray(indices.size) { withWhite.colors[indices[it]] }
        val art = PixelArtResult(
            gridSize = 24,
            palette = withWhite,
            indices = indices,
            preview = RgbImage(24, 24, preview)
        )
        val settings = AutoFillSettings(
            canvasRect = floatArrayOf(100f, 100f, 700f, 700f),
            paletteRect = floatArrayOf(760f, 100f, 1080f, 900f),
            tapDelayMs = 1L,
            paletteDelayMs = 1L,
            swipeDelayMs = 1L
        )
        val sequence = AutoFillActionEngine.buildSequence(art, settings)
        val canvasTaps = sequence.actions.filterIsInstance<AutoFillAction.Tap>().count { it.x < 700f }
        assertEquals(24 * 24 / 2, canvasTaps)
        assertEquals(1, sequence.neededColors)
    }

    @Test
    fun skipsNearWhiteBackgroundCells() {
        val withNearWhite = Palette(
            name = "near-white-background",
            colors = listOf(
                0xFF202020.toInt(),
                0xFFFDFDFD.toInt(), // sampled white with tiny screenshot noise
                0xFFEFEFEF.toInt(), // light gray must still be filled
                0xFFE41F1F.toInt()
            )
        )
        val indices = IntArray(24 * 24) { if (it % 3 == 0) 1 else if (it % 3 == 1) 2 else 3 }
        val preview = IntArray(indices.size) { withNearWhite.colors[indices[it]] }
        val art = PixelArtResult(
            gridSize = 24,
            palette = withNearWhite,
            indices = indices,
            preview = RgbImage(24, 24, preview)
        )
        val settings = AutoFillSettings(
            canvasRect = floatArrayOf(100f, 100f, 700f, 700f),
            paletteRect = floatArrayOf(760f, 100f, 1080f, 900f),
            tapDelayMs = 1L,
            paletteDelayMs = 1L,
            swipeDelayMs = 1L
        )
        val sequence = AutoFillActionEngine.buildSequence(art, settings)
        val canvasTaps = sequence.actions.filterIsInstance<AutoFillAction.Tap>().count { it.x < 700f }
        // 1/3 near-white cells skipped, 1/3 light gray + 1/3 red still filled.
        assertEquals(24 * 24 * 2 / 3, canvasTaps)
        assertEquals(2, sequence.neededColors)
    }

    @Test
    fun secondPageUsesEightSwatchOverlapForSlotPositions() {
        // Page 2 in the game shows colors 17..40 (slot 0 = color 17), so
        // color 25 -> slot 8, color 28 -> slot 11, color 40 -> slot 23.
        val indices = IntArray(24 * 24) { i ->
            when (i % 3) {
                0 -> 24 // color 25
                1 -> 27 // color 28
                else -> 39 // color 40
            }
        }
        val art = artWith(indices)
        val settings = AutoFillSettings(
            canvasRect = floatArrayOf(0f, 0f, 960f, 960f),
            paletteRect = floatArrayOf(1000f, 0f, 2000f, 1500f),
            tapDelayMs = 1L,
            paletteDelayMs = 1L,
            swipeDelayMs = 1L
        )
        val sequence = AutoFillActionEngine.buildSequence(art, settings)
        val paletteTaps = sequence.actions
            .filterIsInstance<AutoFillAction.Tap>()
            .filter { it.x >= 1000f }

        assertEquals(3, paletteTaps.size)
        // color 25 -> slot 8 -> row 2, col 0 -> (1125, 625)
        assertEquals(1125f, paletteTaps[0].x, 0.01f)
        assertEquals(625f, paletteTaps[0].y, 0.01f)
        // color 28 -> slot 11 -> row 2, col 3 -> (1875, 625)
        assertEquals(1875f, paletteTaps[1].x, 0.01f)
        assertEquals(625f, paletteTaps[1].y, 0.01f)
        // color 40 -> slot 23 -> row 5, col 3 -> (1875, 1375)
        assertEquals(1875f, paletteTaps[2].x, 0.01f)
        assertEquals(1375f, paletteTaps[2].y, 0.01f)
    }
}
