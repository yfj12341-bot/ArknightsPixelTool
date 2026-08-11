package com.pixelpainter.app.autofill

/**
 * Two frame targets the user has to mark on the game screen.
 */
enum class AutoFillTool {
    CANVAS,
    PALETTE,
}

/**
 * Built-in fill speed presets. Canvas cell taps are the ones that can cause
 * accidental pinch-zoom/scroll on some devices, so presets vary the canvas tap
 * delay most. Palette taps stay fast, while swipes carry a longer pause so the
 * page-flip bounce settles before the next color selection.
 */
enum class FillSpeedPreset(
    val label: String,
    val tapDelayMs: Long,
    val paletteDelayMs: Long,
    val swipeDelayMs: Long,
) {
    VERY_FAST("非常快", 20L, 40L, 300L),
    FAST("快", 35L, 60L, 360L),
    MEDIUM("中等", 60L, 90L, 420L),
    SLOW("慢", 90L, 120L, 500L),
}

/**
 * All user-adjustable parameters for the experimental auto fill.
 *
 * Rect coordinates live in the setup screenshot space; the accessibility service
 * scales them back to physical screen coordinates before dispatching gestures.
 */
data class AutoFillSettings(
    val canvasRect: FloatArray = floatArrayOf(0f, 0f, 0f, 0f),
    val paletteRect: FloatArray = floatArrayOf(0f, 0f, 0f, 0f),
    /** ARGB colors sampled from the game's visible palette slots, row-major. */
    val paletteColors: IntArray = IntArray(0),
    val gridSize: Int = 24,
    val paletteColumns: Int = 4,
    val paletteRows: Int = 6,
    /** How many swatches are visible at once on the palette page. */
    val visibleColors: Int = 24,
    /**
     * How many swatches at the top of each later page repeat the previous
     * page's tail. The game's second page shows colors 17..40, so the first
     * 8 slots of page 2 duplicate the last 8 of page 1.
     */
    val pageOverlapColors: Int = 8,
    val swipeUpToNextPage: Boolean = true,
    val tapDelayMs: Long = 60L,
    val paletteDelayMs: Long = 90L,
    val swipeDelayMs: Long = 420L,
    val countdownSeconds: Int = 3,
) {
    fun copyWithRects(canvas: FloatArray, palette: FloatArray): AutoFillSettings =
        copy(canvasRect = canvas.copyOf(), paletteRect = palette.copyOf())

    fun copyWithPaletteColors(colors: IntArray): AutoFillSettings =
        copy(paletteColors = colors.copyOf())

    companion object {
        val defaults = AutoFillSettings()
    }
}

/**
 * A single low-level gesture to dispatch on the accessibility service.
 * Pure Kotlin so the sequence generator can be unit tested on the JVM.
 */
sealed interface AutoFillAction {
    /** The action is followed by this many milliseconds of pause. */
    val waitMs: Long

    data class Tap(val x: Float, val y: Float, override val waitMs: Long) : AutoFillAction

    data class Swipe(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        override val waitMs: Long
    ) : AutoFillAction

    data object Stop : AutoFillAction {
        override val waitMs: Long = 0L
    }
}

sealed interface AutoFillResult {
    data class Success(
        val totalTaps: Int,
        val paletteSwitches: Int,
        val paletteTaps: Int,
        val estimatedDurationMs: Long
    ) : AutoFillResult

    data class Failure(val reason: String) : AutoFillResult
}
