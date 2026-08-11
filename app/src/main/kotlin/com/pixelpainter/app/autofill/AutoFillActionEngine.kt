package com.pixelpainter.app.autofill

import com.pixelpainter.core.PixelArtResult
import kotlin.math.ceil

/**
 * Converts a pixel-art result into a scripted sequence of palette swipes/taps
 * and canvas cell taps. Pure Kotlin and side-effect free so it can be tested
 * without an Android device.
 */
object AutoFillActionEngine {

    data class Sequence(
        val actions: List<AutoFillAction>,
        val neededColors: Int,
        val totalTaps: Int,
        val paletteSwipes: Int,
        val estimatedDurationMs: Long,
    )

    fun buildSequence(art: PixelArtResult, settings: AutoFillSettings): Sequence {
        require(art.gridSize > 0) { "gridSize must be positive" }
        require(art.palette.colors.isNotEmpty()) { "palette is empty" }
        require(settings.paletteColumns > 0 && settings.paletteRows > 0) { "palette columns/rows must be positive" }
        require(settings.visibleColors > 0) { "visibleColors must be positive" }

        val gridSize = art.gridSize
        val canvasLeft = settings.canvasRect[0]
        val canvasTop = settings.canvasRect[1]
        val canvasRight = settings.canvasRect[2]
        val canvasBottom = settings.canvasRect[3]
        val cellWidth = (canvasRight - canvasLeft) / gridSize
        val cellHeight = (canvasBottom - canvasTop) / gridSize

        val perColorCells = LinkedHashMap<Int, MutableList<Pair<Int, Int>>>()
        for (y in 0 until gridSize) {
            for (x in 0 until gridSize) {
                val index = art.indices[y * gridSize + x].coerceIn(0, art.palette.colors.size - 1)
                if (!isBackgroundColor(art.palette.colors[index])) {
                    perColorCells.getOrPut(index) { mutableListOf() }.add(x to y)
                }
            }
        }

        val paletteLeft = settings.paletteRect[0]
        val paletteTop = settings.paletteRect[1]
        val paletteRight = settings.paletteRect[2]
        val paletteBottom = settings.paletteRect[3]
        val slotWidth = (paletteRight - paletteLeft) / settings.paletteColumns
        val slotHeight = (paletteBottom - paletteTop) / settings.paletteRows

        val actions = mutableListOf<AutoFillAction>()
        var currentPage = 0
        var totalTaps = 0
        var paletteSwipes = 0
        var estimatedDurationMs = 0L
        val paletteMidX = (paletteLeft + paletteRight) / 2f

        fun add(action: AutoFillAction) {
            estimatedDurationMs += action.waitMs
            actions.add(action)
            when (action) {
                is AutoFillAction.Swipe -> paletteSwipes++
                is AutoFillAction.Tap -> totalTaps++
                AutoFillAction.Stop -> Unit
            }
        }

        fun swipePage(pageIncrement: Int) {
            val swipeUp = if (pageIncrement > 0) {
                settings.swipeUpToNextPage
            } else {
                !settings.swipeUpToNextPage
            }
            add(
                AutoFillAction.Swipe(
                    startX = paletteMidX,
                    startY = if (swipeUp) paletteBottom - slotHeight * 0.7f else paletteTop + slotHeight * 0.7f,
                    endX = paletteMidX,
                    endY = if (swipeUp) paletteTop + slotHeight * 0.7f else paletteBottom - slotHeight * 0.7f,
                    waitMs = settings.swipeDelayMs
                )
            )
        }

        val pageStep = (settings.visibleColors - settings.pageOverlapColors).coerceAtLeast(1)
        val requiredPageCount = requiredPageCountFor(
            art.palette.colors.size,
            settings.visibleColors,
            pageStep
        )
        if (requiredPageCount > 1) {
            // Make sure the visible palette page is the first one before filling.
            swipePage(-1)
        }

        for ((index, cells) in perColorCells) {
            val page: Int
            val slot: Int
            if (index < settings.visibleColors) {
                // First page shows colors 1..visibleColors at slots 0..n-1.
                page = 0
                slot = index
            } else {
                // Later pages repeat the previous page's tail: the game's page 2
                // shows colors 17..40, i.e. slot 0 is color 17 (index pageOverlap).
                val rel = index - settings.visibleColors
                page = 1 + rel / pageStep
                slot = settings.pageOverlapColors + (rel % pageStep)
            }
            val slotRow = slot / settings.paletteColumns
            val slotColumn = slot % settings.paletteColumns

            if (page != currentPage) {
                val delta = page - currentPage
                val step = if (delta > 0) 1 else -1
                repeat(kotlin.math.abs(delta)) { swipePage(step) }
                currentPage = page
            }

            add(
                AutoFillAction.Tap(
                    x = paletteLeft + slotWidth * (slotColumn + 0.5f),
                    y = paletteTop + slotHeight * (slotRow + 0.5f),
                    waitMs = settings.paletteDelayMs
                )
            )
            for ((x, y) in cells) {
                add(
                    AutoFillAction.Tap(
                        x = canvasLeft + cellWidth * (x + 0.5f),
                        y = canvasTop + cellHeight * (y + 0.5f),
                        waitMs = settings.tapDelayMs
                    )
                )
            }
        }
        repeat(currentPage) { swipePage(-1) }
        add(AutoFillAction.Stop)
        estimatedDurationMs += (settings.countdownSeconds * 1000L)

        val requiredVisibleCount = requiredPageCount
        val maxPage = kotlin.math.max(0, requiredVisibleCount - 1)
        return Sequence(
            actions = actions,
            neededColors = perColorCells.size,
            totalTaps = totalTaps,
            paletteSwipes = paletteSwipes,
            estimatedDurationMs = estimatedDurationMs,
        )
    }

    /**
     * Number of palette pages needed to cover [colorCount] colors when every
     * page shows [visibleColors] swatches and later pages overlap the previous
     * tail by [pageOverlap] colors.
     */
    private fun requiredPageCountFor(colorCount: Int, visibleColors: Int, pageStep: Int): Int {
        if (colorCount <= visibleColors) return 1
        return 1 + ceil((colorCount - visibleColors).toDouble() / pageStep).toInt()
    }

    /**
     * The pixel-art canvas starts white, so pure-white cells do not need to be
     * tapped. This saves one palette switch plus up to 576 canvas taps.
     *
     * A small tolerance keeps the optimization working even when the sampled
     * palette swatch is a few steps away from pure white (screenshot noise),
     * while still never matching the lightest non-white palette colors.
     */
    private fun isBackgroundColor(color: Int): Boolean {
        val rgb = color and 0xFFFFFF
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return r >= 250 && g >= 250 && b >= 250
    }
}
