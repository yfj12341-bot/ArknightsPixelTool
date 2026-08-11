package com.pixelpainter.app.autofill

import android.content.Intent
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.pixelpainter.core.PixelArtResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt
import kotlin.math.roundToInt

/**
 * Bridges the Compose app and the accessibility process. A singleton is safe here
 * because both sides live in the same application process on Android; it is also
 * used as the default when the auto-fill broadcast intent cannot be delivered.
 */
object AutoFillStateHolder {

    private const val PREFS = "autofill"
    private const val KEY_CANVAS = "canvas_rect"
    private const val KEY_PALETTE = "palette_rect"
    private const val KEY_PALETTE_COLORS = "palette_colors"
    private const val KEY_GRID = "grid_size"
    private const val KEY_COLUMNS = "palette_columns"
    private const val KEY_ROWS = "palette_rows"
    private const val KEY_VISIBLE = "visible_colors"
    private const val KEY_PAGE_OVERLAP = "page_overlap"
    private const val KEY_SWIPE_NEXT = "swipe_up_next"
    private const val KEY_TAP_DELAY = "tap_delay"
    private const val KEY_PALETTE_DELAY = "palette_delay"
    private const val KEY_SWIPE_DELAY = "swipe_delay"
    private const val KEY_COUNTDOWN = "countdown"

    @Volatile
    var pendingArt: PixelArtResult? = null

    @Volatile
    var settings: AutoFillSettings = AutoFillSettings()

    val openSetupRequested = AtomicBoolean(false)

    fun setPending(art: PixelArtResult, newSettings: AutoFillSettings) {
        pendingArt = art
        settings = newSettings
        openSetupRequested.set(true)
    }

    fun consumeOpenSetupRequest(): Boolean =
        openSetupRequested.getAndSet(false)

    fun applyIntent(intent: Intent) {
        settings = readSettingsFrom(intent)
        readArtFrom(intent)?.let { pendingArt = it }
        openSetupRequested.set(true)
    }

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun persist(context: Context, value: AutoFillSettings) {
        init(context)
        prefs.edit {
            putString(KEY_CANVAS, encodeRect(value.canvasRect))
            putString(KEY_PALETTE, encodeRect(value.paletteRect))
            putString(KEY_PALETTE_COLORS, encodeColors(value.paletteColors))
            putInt(KEY_GRID, value.gridSize)
            putInt(KEY_COLUMNS, value.paletteColumns)
            putInt(KEY_ROWS, value.paletteRows)
            putInt(KEY_VISIBLE, value.visibleColors)
            putInt(KEY_PAGE_OVERLAP, value.pageOverlapColors)
            putBoolean(KEY_SWIPE_NEXT, value.swipeUpToNextPage)
            putLong(KEY_TAP_DELAY, value.tapDelayMs)
            putLong(KEY_PALETTE_DELAY, value.paletteDelayMs)
            putLong(KEY_SWIPE_DELAY, value.swipeDelayMs)
            putInt(KEY_COUNTDOWN, value.countdownSeconds)
            openSetupRequested.set(true)
        }
    }

    /**
     * Remembers the last confirmed frame positions (screen coordinates) without
     * triggering the setup flow, so the next auto-fill setup can restore them.
     */
    fun rememberRects(context: Context, canvas: FloatArray, palette: FloatArray) {
        init(context)
        prefs.edit {
            putString(KEY_CANVAS, encodeRect(canvas))
            putString(KEY_PALETTE, encodeRect(palette))
        }
    }

    fun loadRememberedRects(context: Context): Pair<FloatArray, FloatArray>? {
        init(context)
        val canvas = decodeRect(prefs.getString(KEY_CANVAS, null)) ?: return null
        val palette = decodeRect(prefs.getString(KEY_PALETTE, null)) ?: return null
        return canvas to palette
    }

    fun load(context: Context): AutoFillSettings {
        init(context)
        val stored = prefs
        val canvas = decodeRect(stored.getString(KEY_CANVAS, null))
        val palette = decodeRect(stored.getString(KEY_PALETTE, null))
        if (canvas == null || palette == null) return AutoFillSettings()
        return AutoFillSettings(
            canvasRect = canvas,
            paletteRect = palette,
            paletteColors = decodeColors(stored.getString(KEY_PALETTE_COLORS, null)),
            gridSize = stored.getInt(KEY_GRID, 24),
            paletteColumns = stored.getInt(KEY_COLUMNS, 4),
            paletteRows = stored.getInt(KEY_ROWS, 6),
            visibleColors = stored.getInt(KEY_VISIBLE, 24),
            pageOverlapColors = stored.getInt(KEY_PAGE_OVERLAP, 8),
            swipeUpToNextPage = stored.getBoolean(KEY_SWIPE_NEXT, true),
            tapDelayMs = stored.getLong(KEY_TAP_DELAY, 60L),
            paletteDelayMs = stored.getLong(KEY_PALETTE_DELAY, 90L),
            swipeDelayMs = stored.getLong(KEY_SWIPE_DELAY, 420L),
            countdownSeconds = stored.getInt(KEY_COUNTDOWN, 3),
        )
    }

    private fun encodeRect(rect: FloatArray): String {
        val parts = rect.takeIf { it.size >= 4 } ?: return ""
        return listOf(parts[0], parts[1], parts[2], parts[3]).joinToString(",") { it.roundToInt().toString() }
    }

    private fun decodeRect(raw: String?): FloatArray? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(',')
        if (parts.size != 4) return null
        return floatArrayOf(
            parts[0].toFloatOrNull() ?: return null,
            parts[1].toFloatOrNull() ?: return null,
            parts[2].toFloatOrNull() ?: return null,
            parts[3].toFloatOrNull() ?: return null,
        )
    }

    private fun encodeColors(colors: IntArray): String =
        colors.joinToString(",") { java.lang.Integer.toHexString(it and 0xFFFFFF) }

    private fun decodeColors(raw: String?): IntArray {
        if (raw.isNullOrBlank()) return IntArray(0)
        return raw.split(',')
            .mapNotNull { it.trim().toIntOrNull(16) }
            .map { 0xFF000000.toInt() or (it and 0xFFFFFF) }
            .toIntArray()
    }

    private fun readSettingsFrom(intent: Intent): AutoFillSettings {
        return AutoFillSettings(
            gridSize = intent.getIntExtra(AutoFillSupport.EXTRA_GRID_SIZE, AutoFillSettings.defaults.gridSize),
            paletteColumns = intent.getIntExtra(AutoFillSupport.EXTRA_PALETTE_COLUMNS, AutoFillSettings.defaults.paletteColumns),
            paletteRows = intent.getIntExtra(AutoFillSupport.EXTRA_PALETTE_ROWS, AutoFillSettings.defaults.paletteRows),
            visibleColors = intent.getIntExtra(AutoFillSupport.EXTRA_VISIBLE_COLORS, AutoFillSettings.defaults.visibleColors),
            pageOverlapColors = intent.getIntExtra(AutoFillSupport.EXTRA_PAGE_OVERLAP, AutoFillSettings.defaults.pageOverlapColors),
            swipeUpToNextPage = intent.getBooleanExtra(AutoFillSupport.EXTRA_SWIPE_UP_NEXT, AutoFillSettings.defaults.swipeUpToNextPage),
            tapDelayMs = intent.getLongExtra(AutoFillSupport.EXTRA_TAP_DELAY, AutoFillSettings.defaults.tapDelayMs),
            paletteDelayMs = intent.getLongExtra(AutoFillSupport.EXTRA_PALETTE_DELAY, AutoFillSettings.defaults.paletteDelayMs),
            swipeDelayMs = intent.getLongExtra(AutoFillSupport.EXTRA_SWIPE_DELAY, AutoFillSettings.defaults.swipeDelayMs),
            countdownSeconds = intent.getIntExtra(AutoFillSupport.EXTRA_COUNTDOWN, AutoFillSettings.defaults.countdownSeconds)
        )
    }

    private fun readArtFrom(intent: Intent): PixelArtResult? {
        if (!intent.getBooleanExtra(AutoFillSupport.EXTRA_HAS_ART, false)) return null
        val size = intent.getIntExtra(AutoFillSupport.EXTRA_PALETTE_SIZE, 0)
        if (size <= 0) return null
        val colors = intent.getIntArrayExtra(AutoFillSupport.EXTRA_PALETTE_COLORS) ?: return null
        val indices = intent.getIntArrayExtra(AutoFillSupport.EXTRA_INDICES) ?: return null
        if (colors.size != size || indices.isEmpty()) return null
        val grid = sqrt(indices.size.toDouble()).toInt()
        if (grid * grid != indices.size) return null
        val preview = IntArray(indices.size) { colors[indices[it]] }
        return PixelArtResult(
            gridSize = grid,
            palette = com.pixelpainter.core.Palette(colors.toList()),
            indices = indices,
            preview = com.pixelpainter.core.RgbImage(grid, grid, preview)
        )
    }
}
