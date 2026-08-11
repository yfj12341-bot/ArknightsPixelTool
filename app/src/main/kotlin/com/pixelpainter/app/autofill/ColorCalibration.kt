package com.pixelpainter.app.autofill

import com.pixelpainter.core.Palette
import com.pixelpainter.core.PixelArtResult
import com.pixelpainter.core.RgbImage
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Calibrates a generated pixel-art result against the colors visible in the
 * game's palette panel at setup time.
 *
 * The app's fixed 40-color order stays authoritative. Screenshot sampling only
 * refines the colors of the visible first page; later pages keep the canonical
 * colors so 40-color results are never collapsed onto the 24 visible slots.
 */
object ColorCalibration {

    private const val CANONICAL_BONUS = 40.0

    fun calibrateArt(
        art: PixelArtResult,
        sampledColors: IntArray,
        canonicalColors: List<Int>,
        visibleColors: Int
    ): PixelArtResult {
        require(visibleColors > 0) { "visibleColors must be positive" }
        if (sampledColors.isEmpty()) return art

        val artColors = art.palette.colors
        val sampledCount = minOf(sampledColors.size, visibleColors)
        val slotColors = IntArray(artColors.size) { slot ->
            val rgb = when {
                slot < sampledCount -> sampledColors[slot]
                slot < canonicalColors.size -> canonicalColors[slot]
                else -> artColors[slot]
            }
            0xFF000000.toInt() or (rgb and 0xFFFFFF)
        }

        val canonicalByColor = HashMap<Int, Int>(artColors.size)
        for ((index, color) in artColors.withIndex()) {
            canonicalByColor[color and 0xFFFFFF] = index % slotColors.size
        }

        val chosen = IntArray(artColors.size) { old ->
            val color = artColors[old] and 0xFFFFFF
            var bestSlot = 0
            var bestCost = Double.POSITIVE_INFINITY
            for (slot in slotColors.indices) {
                val distance = colorDistance(rgbOf(color), rgbOf(slotColors[slot]))
                val canonical = if (canonicalByColor[color] == slot) CANONICAL_BONUS else 0.0
                val cost = distance - canonical
                if (cost < bestCost) {
                    bestCost = cost
                    bestSlot = slot
                }
            }
            bestSlot
        }

        val newIndices = IntArray(art.indices.size) { i ->
            val old = art.indices[i].coerceIn(0, artColors.size - 1)
            chosen[old]
        }
        val preview = IntArray(newIndices.size) { slotColors[newIndices[it]] }
        return art.copy(
            palette = Palette(slotColors.toList(), art.palette.name),
            indices = newIndices,
            preview = RgbImage(art.gridSize, art.gridSize, preview)
        )
    }

    private fun rgbOf(color: Int): IntArray {
        return intArrayOf(
            (color shr 16) and 0xFF,
            (color shr 8) and 0xFF,
            color and 0xFF
        )
    }

    private fun colorDistance(a: IntArray, b: IntArray): Double {
        var sum = 0.0
        for (i in 0..2) {
            val d = a[i] - b[i]
            sum += d * d
        }
        return sqrt(sum)
    }
}
