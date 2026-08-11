package com.pixelpainter.app.autofill

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.pixelpainter.core.PixelArtResult
import com.pixelpainter.core.SamplePalettes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

class AutoFillAccessibilityService : AccessibilityService(), IDispatcher {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preSetupCountdownSeconds = 5

    private var root: FrameLayout? = null
    private var overlay: FrameLayout? = null
    private var overlayView: AutoFillOverlayView? = null
    private var fillJob: Job? = null
    private var countdownRunnable: Runnable? = null
    private var screenshotRunnable: Runnable? = null
    private var setupRelayoutRunnable: Runnable? = null
    private var screenshotGeneration = 0L
    private var setupScreenshotPending = false

    private val ignoredWindowPackages = setOf(
        "com.android.settings",
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.miui.securitycenter",
        "com.miui.home",
        "com.android.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.oppo.launcher",
        "com.coloros.launcher",
        "com.vivo.launcher",
        "com.huawei.android.launcher"
    )

    @Volatile
    private var fillCancelled = false

    @Volatile
    private var fillActive = false

    /**
     * True while the service itself is injecting a synthetic tap/swipe. Touch
     * interaction events that arrive during this window belong to our own
     * gesture and must not abort the fill.
     */
    @Volatile
    private var syntheticTouchActive = false

    /** Small touchable "abort" button shown while the fill is running. */
    private var abortWindow: FrameLayout? = null

    /** True while the user is pressing the abort button (skip auto-abort). */
    @Volatile
    private var abortButtonPressed = false

    @Volatile
    private var pendingArt: PixelArtResult? = null

    private val setupReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AutoFillSupport.ACTION_START_SETUP) {
                AutoFillStateHolder.applyIntent(intent)
                startPreSetupCountdown()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        AutoFillStateHolder.init(this)
        if (AutoFillStateHolder.settings == AutoFillSettings.defaults) {
            AutoFillStateHolder.settings = AutoFillStateHolder.load(this)
        }
        ContextCompat.registerReceiver(
            this,
            setupReceiver,
            IntentFilter(AutoFillSupport.ACTION_START_SETUP),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AutoFillSupport.serviceConnected = true
    }

    override fun onDestroy() {
        AutoFillSupport.serviceConnected = false
        invalidatePendingScreenshot()
        cancelFill()
        dismissOverlay()
        runCatching { unregisterReceiver(setupReceiver) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
            // The user touched the screen while the fill is running. Abort
            // immediately so a second touch point can never trigger a pinch
            // zoom or canvas scroll that would misalign the remaining taps.
            if (fillActive && !syntheticTouchActive && !abortButtonPressed) {
                abortFill("检测到屏幕被触碰，已中止填充，请勿在填充时触碰屏幕")
            }
            return
        }
        val pkg = event.packageName?.toString()
        if (pkg != null && pkg.startsWith("com.pixelpainter") && setupScreenshotPending) {
            invalidatePendingScreenshot()
            return
        }
        if (overlay != null) return
        if (!AutoFillStateHolder.openSetupRequested.get()) return
        val accepted = when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
                pkg != null && shouldAcceptSetupWindow(pkg)
            AccessibilityEvent.TYPE_WINDOWS_CHANGED ->
                pkg == null || shouldAcceptSetupWindow(pkg)
            else -> false
        }
        if (accepted) {
            scheduleSetupScreenshot()
        }
    }

    private fun shouldAcceptSetupWindow(packageName: String): Boolean {
        if (packageName == "android" || packageName.startsWith("android.")) return false
        if (packageName.startsWith("com.pixelpainter")) return false
        return packageName !in ignoredWindowPackages
    }

    override fun onInterrupt() {
        if (fillActive) {
            abortFill("自动填充被系统中断")
        }
    }

    private fun scheduleSetupScreenshot() {
        cancelFill()
        if (overlay != null) dismissOverlay()
        invalidatePendingScreenshot()
        setupScreenshotPending = true
        screenshotRunnable = Runnable {
            screenshotRunnable = null
            takeScreenshot()
        }.also {
            mainHandler.postDelayed(it, 600L)
        }
    }

    private fun invalidatePendingScreenshot() {
        screenshotGeneration++
        setupScreenshotPending = false
        screenshotRunnable?.let { mainHandler.removeCallbacks(it) }
        screenshotRunnable = null
        setupRelayoutRunnable?.let { mainHandler.removeCallbacks(it) }
        setupRelayoutRunnable = null
    }

    private fun takeScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            showSetupOverlay(null)
            toast("请在 Android 11+ 上使用自动填充")
            return
        }
        val generation = screenshotGeneration
        try {
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        if (generation != screenshotGeneration) return
                        setupScreenshotPending = false
                        screenshotRunnable = null
                        val hardware = Bitmap.wrapHardwareBuffer(
                            screenshot.hardwareBuffer,
                            ColorSpace.get(ColorSpace.Named.SRGB)
                        )
                        val copy = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                        showSetupOverlay(copy)
                    }

                    override fun onFailure(errorCode: Int) {
                        if (generation != screenshotGeneration) return
                        setupScreenshotPending = false
                        screenshotRunnable = null
                        showSetupOverlay(null)
                        toast("截图失败，请重新框选")
                    }
                }
            )
        } catch (e: Exception) {
            if (generation != screenshotGeneration) return
            setupScreenshotPending = false
            screenshotRunnable = null
            showSetupOverlay(null)
            toast("截图失败，请重新框选")
        }
    }

    private fun startPreSetupCountdown() {
        mainHandler.post {
            fillCancelled = false
            fillActive = false
            fillJob?.cancel()
            fillJob = null
            countdownRunnable?.let { mainHandler.removeCallbacks(it) }
            countdownRunnable = null
            hideAbortButton()
            AutoFillStateHolder.openSetupRequested.set(true)
            ensureWindow()
            val view = overlayView ?: return@post
            val seconds = preSetupCountdownSeconds
            view.beginPreSetupCountdown(seconds)
            view.setMessage("")
            applyFullScreenWindow()
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val params = root?.layoutParams as? WindowManager.LayoutParams
            if (params != null) {
                params.flags = baseWindowFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                runCatching { wm.updateViewLayout(root!!, params) }
            }
            val runnable = object : Runnable {
                var remaining = seconds
                override fun run() {
                    if (remaining > 0) {
                        view.updateCountdown(remaining)
                        remaining--
                        mainHandler.postDelayed(this, 1000L)
                    } else {
                        countdownRunnable = null
                        scheduleSetupScreenshot()
                    }
                }
            }
            countdownRunnable = runnable
            mainHandler.post(runnable)
        }
    }
    private fun showSetupOverlay(bitmap: Bitmap?) {
        cancelFill()
        AutoFillStateHolder.openSetupRequested.set(false)
        ensureWindow()
        val view = overlayView ?: AutoFillOverlayView(this).also {
            overlayView = it
            it.listener = object : AutoFillOverlayView.Listener {
                override fun onConfirmSetup(canvas: RectF, palette: RectF) {
                    confirmAndRun(canvas, palette)
                }

                override fun onCancelFill() {
                    dismissOverlay()
                }

                override fun onReopenSetup() {
                    scheduleSetupScreenshot()
                }

                override fun onDismissOverlay() {
                    dismissOverlay()
                }
            }
            overlay?.addView(
                it,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        view.gridSize = AutoFillStateHolder.settings.gridSize
        view.paletteColumns = AutoFillStateHolder.settings.paletteColumns
        view.paletteRows = AutoFillStateHolder.settings.paletteRows
        view.openSetupState()
        view.setMessage("")
        view.setScreenshotBitmap(bitmap)
        applyFullScreenWindow()
        restoreRememberedFrames(view)
        setupRelayoutRunnable = Runnable {
            setupRelayoutRunnable = null
            view.recomputeSetupLayout()
            restoreRememberedFrames(view)
        }.also { mainHandler.postDelayed(it, 250L) }
    }

    /**
     * Re-applies the last confirmed frame positions (screen coordinates) onto
     * the freshly laid-out setup overlay so the user does not need to re-frame.
     */
    private fun restoreRememberedFrames(view: AutoFillOverlayView) {
        val shot = view.screenshot ?: return
        val stored = AutoFillStateHolder.loadRememberedRects(this) ?: return
        val canvas = stored.first
        val palette = stored.second
        if (isValidScreenRect(canvas, shot.width, shot.height) &&
            isValidScreenRect(palette, shot.width, shot.height)
        ) {
            view.setFramesFromScreen(
                RectF(canvas[0], canvas[1], canvas[2], canvas[3]),
                RectF(palette[0], palette[1], palette[2], palette[3])
            )
        }
    }

    private fun isValidScreenRect(rect: FloatArray, width: Int, height: Int): Boolean {
        if (rect.size < 4) return false
        if (rect[2] <= rect[0] || rect[3] <= rect[1]) return false
        return rect[0] >= 0f && rect[1] >= 0f && rect[2] <= width && rect[3] <= height
    }

    private fun confirmAndRun(canvas: RectF, palette: RectF) {
        val view = overlayView ?: return
        if (fillActive) return
        val settings = AutoFillStateHolder.settings
        val screenCanvas = view.mapViewRectToScreen(canvas)
        val screenPalette = view.mapViewRectToScreen(palette)
        val sampledColors = samplePaletteColors(view.screenshot, view.mapViewRectToScreenshot(palette))
        val screenSettings = settings.copyWithRects(
            floatArrayOf(screenCanvas.left, screenCanvas.top, screenCanvas.right, screenCanvas.bottom),
            floatArrayOf(screenPalette.left, screenPalette.top, screenPalette.right, screenPalette.bottom)
        ).copyWithPaletteColors(sampledColors)
        AutoFillStateHolder.settings = screenSettings
        AutoFillStateHolder.rememberRects(
            this,
            screenSettings.canvasRect,
            screenSettings.paletteRect
        )

        val originalArt = AutoFillStateHolder.pendingArt ?: pendingArt
        val art = if (originalArt != null && sampledColors.isNotEmpty()) {
            runCatching {
                ColorCalibration.calibrateArt(
                    art = originalArt,
                    sampledColors = sampledColors,
                    canonicalColors = SamplePalettes.arknights40.colors,
                    visibleColors = settings.visibleColors
                )
            }.getOrElse { originalArt }
        } else {
            originalArt
        }
        if (art == null) {
            view.showDone("缺少像素画数据，请先在应用内生成结果")
            return
        }

        fillCancelled = false
        fillActive = true
        view.beginFill(settings.countdownSeconds)
        applyFillWindow()
        showAbortButton()
        beginCountdownThenRun(art, screenSettings)
    }

    private fun beginCountdownThenRun(art: PixelArtResult, settings: AutoFillSettings) {
        val view = overlayView ?: return
        val seconds = settings.countdownSeconds.coerceIn(1, 10)
        val runnable = object : Runnable {
            var remaining = seconds

            override fun run() {
                if (fillCancelled || !fillActive) {
                    abortFill("已取消自动填充")
                    return
                }
                if (remaining > 0) {
                    view.updateCountdown(remaining)
                    remaining--
                    mainHandler.postDelayed(this, 1000L)
                } else {
                    countdownRunnable = null
                    launchRunner(art, settings)
                }
            }
        }
        countdownRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun launchRunner(art: PixelArtResult, settings: AutoFillSettings) {
        val view = overlayView ?: return
        fillJob?.cancel()
        view.openProgressState()
        view.setMessage("")
        applyFillWindow()

        val sequence = runCatching {
            AutoFillActionEngine.buildSequence(art, settings)
        }.getOrElse {
            fillActive = false
            hideAbortButton()
            view.showDone("无法生成动作，${it.message}")
            return
        }

        val actions = sequence.actions
        val totalWait = (actions.sumOf { it.waitMs }).coerceAtLeast(1L)
        var completedWait = 0L

        fillJob = scope.launch {
            delay(150L)
            if (!verifyScreenUnchanged()) {
                fillActive = false
                hideAbortButton()
                view.openDoneState()
                applyDoneWindow()
                view.setMessage("画面发生变动（可能误触缩放），已中止填充，请重新框选后重试")
                return@launch
            }
            var step = 0
            var cancelled = false
            var failure: String? = null

            for (action in actions) {
                step++
                if (fillCancelled) {
                    cancelled = true
                    break
                }
                val ok = dispatchGestureSuspending(action)
                if (!ok) {
                    failure = "手势被系统拒绝（第 $step 步）"
                    break
                }
                val percent = completedWait.toFloat() / totalWait
                view.updateProgress(percent, "第 $step/${actions.size} 步")
                if (action.waitMs > 0 && !fillCancelled) {
                    delay(action.waitMs)
                    completedWait += action.waitMs
                }
            }

            if (!fillActive) return@launch
            fillActive = false
            hideAbortButton()
            view.openDoneState()
            applyDoneWindow()
            view.setMessage(
                when {
                    cancelled -> "已取消自动填充"
                    failure != null -> "填充失败：$failure"
                    else -> "填充完成：${sequence.totalTaps} 次点击"
                }
            )
        }
    }

    private fun abortFill(reason: String) {
        fillCancelled = true
        fillActive = false
        fillJob?.cancel()
        fillJob = null
        countdownRunnable?.let { mainHandler.removeCallbacks(it) }
        countdownRunnable = null
        hideAbortButton()
        val view = overlayView ?: return
        view.openDoneState()
        applyDoneWindow()
        view.setMessage(reason)
    }

    private fun cancelFill() {
        fillCancelled = true
        fillActive = false
        fillJob?.cancel()
        fillJob = null
        countdownRunnable?.let { mainHandler.removeCallbacks(it) }
        countdownRunnable = null
        hideAbortButton()
    }

    private suspend fun dispatchGestureSuspending(action: AutoFillAction): Boolean {
        if (action is AutoFillAction.Stop) return true
        return suspendCancellableCoroutine { continuation ->
            val path = Path()
            val duration: Long
            when (action) {
                is AutoFillAction.Tap -> {
                    path.moveTo(action.x, action.y)
                    duration = 40L
                }
                is AutoFillAction.Swipe -> {
                    path.moveTo(action.startX, action.startY)
                    path.lineTo(action.endX, action.endY)
                    duration = 160L
                }
                AutoFillAction.Stop -> {
                    continuation.resume(true)
                    return@suspendCancellableCoroutine
                }
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, duration))
                .build()
            syntheticTouchActive = true
            val dispatched = try {
                dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) {
                        syntheticTouchActive = false
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription) {
                        syntheticTouchActive = false
                        if (continuation.isActive) continuation.resume(false)
                    }
                }, mainHandler)
            } catch (e: Exception) {
                syntheticTouchActive = false
                false
            }
            if (!dispatched && continuation.isActive) {
                syntheticTouchActive = false
                continuation.resume(false)
            }
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    override fun dispatch(action: AutoFillAction): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        scope.launch {
            future.complete(dispatchGestureSuspending(action))
        }
        return future
    }

    private fun ensureWindow() {
        if (overlay != null && root != null && root?.parent != null) return
        if (overlay != null || root != null) dismissOverlay()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            baseWindowFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        val newRoot = FrameLayout(this)
        root = newRoot
        overlay = newRoot
        newRoot.keepScreenOn = true
        wm.addView(newRoot, params)
    }

    private fun baseWindowFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

    private fun applyFullScreenWindow() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = root?.layoutParams as? WindowManager.LayoutParams ?: return
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.MATCH_PARENT
        params.x = 0
        params.y = 0
        params.gravity = Gravity.TOP or Gravity.START
        params.flags = baseWindowFlags()
        runCatching { wm.updateViewLayout(root!!, params) }
    }

    private fun applyFillWindow() = updateFillWindow(touchable = false)

    private fun applyDoneWindow() = updateFillWindow(touchable = true)

    private fun updateFillWindow(touchable: Boolean) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = root?.layoutParams as? WindowManager.LayoutParams ?: return
        val density = resources.displayMetrics.density
        // Taller card so title / progress bar / info line / DONE buttons do not
        // overlap; pinned to the top-right corner so it never covers the
        // palette (right side) or the canvas center while the fill is running.
        params.width = (density * 240f).roundToInt()
        params.height = (density * 110f).roundToInt()
        val margin = (density * 12f).roundToInt()
        params.x = resources.displayMetrics.widthPixels - params.width - margin
        params.y = margin
        params.gravity = Gravity.TOP or Gravity.START
        params.flags = if (touchable) {
            baseWindowFlags()
        } else {
            baseWindowFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        runCatching { wm.updateViewLayout(root!!, params) }
    }

    private fun samplePaletteColors(bitmap: Bitmap?, paletteRect: RectF): IntArray {
        if (bitmap == null || paletteRect.width() <= 0f || paletteRect.height() <= 0f) {
            return IntArray(0)
        }
        val columns = AutoFillStateHolder.settings.paletteColumns.coerceAtLeast(1)
        val rows = AutoFillStateHolder.settings.paletteRows.coerceAtLeast(1)
        val slotWidth = paletteRect.width() / columns
        val slotHeight = paletteRect.height() / rows
        val colors = IntArray(columns * rows)
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val cx = (paletteRect.left + slotWidth * (column + 0.5f)).roundToInt()
                val cy = (paletteRect.top + slotHeight * (row + 0.5f)).roundToInt()
                var r = 0L
                var g = 0L
                var b = 0L
                var count = 0L
                for (dy in -3..3) {
                    for (dx in -3..3) {
                        val px = (cx + dx).coerceIn(0, bitmap.width - 1)
                        val py = (cy + dy).coerceIn(0, bitmap.height - 1)
                        val pixel = bitmap.getPixel(px, py)
                        r += (pixel shr 16) and 0xFF
                        g += (pixel shr 8) and 0xFF
                        b += pixel and 0xFF
                        count++
                    }
                }
                val argb = 0xFF000000.toInt() or
                    (((r / count).toInt() and 0xFF) shl 16) or
                    (((g / count).toInt() and 0xFF) shl 8) or
                    ((b / count).toInt() and 0xFF)
                colors[row * columns + column] = argb
            }
        }
        return colors
    }

    /**
     * Takes a fresh screenshot right before the first fill gesture and compares
     * the canvas + palette regions against the setup screenshot. If the screen
     * changed (pinch-zoom, palette scroll, dialog, ...) we abort instead of
     * filling the wrong cells.
     */
    private suspend fun verifyScreenUnchanged(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        val view = overlayView ?: return true
        val setup = view.screenshot ?: return true
        val current = takeScreenshotBitmap() ?: return true
        val excludes = listOfNotNull(
            currentFillWindowScreenRect(),
            currentAbortButtonScreenRect()
        )
        val regions = listOf(
            AutoFillStateHolder.settings.canvasRect,
            AutoFillStateHolder.settings.paletteRect
        )
        for (region in regions) {
            if (region.size < 4 || region[2] <= region[0] || region[3] <= region[1]) continue
            val diff = meanAbsDiff(
                setup,
                current,
                RectF(region[0], region[1], region[2], region[3]),
                excludes
            )
            if (diff > 12f) return false
        }
        return true
    }

    private suspend fun takeScreenshotBitmap(): Bitmap? = suspendCancellableCoroutine { cont ->
        try {
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        if (!cont.isActive) return
                        val hardware = Bitmap.wrapHardwareBuffer(
                            screenshot.hardwareBuffer,
                            ColorSpace.get(ColorSpace.Named.SRGB)
                        )
                        cont.resume(hardware?.copy(Bitmap.Config.ARGB_8888, false))
                    }

                    override fun onFailure(errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            )
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(null)
        }
    }

    private fun currentFillWindowScreenRect(): RectF? {
        val params = root?.layoutParams as? WindowManager.LayoutParams ?: return null
        return RectF(
            params.x.toFloat(),
            params.y.toFloat(),
            (params.x + params.width).toFloat(),
            (params.y + params.height).toFloat()
        )
    }

    private fun meanAbsDiff(a: Bitmap, b: Bitmap, region: RectF, excludes: List<RectF>): Float {
        val limitW = min(a.width, b.width)
        val limitH = min(a.height, b.height)
        val left = region.left.roundToInt().coerceIn(0, limitW - 1)
        val top = region.top.roundToInt().coerceIn(0, limitH - 1)
        val right = region.right.roundToInt().coerceIn(left + 1, limitW)
        val bottom = region.bottom.roundToInt().coerceIn(top + 1, limitH)
        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0) return 0f
        val pa = IntArray(w * h)
        val pb = IntArray(w * h)
        a.getPixels(pa, 0, w, left, top, w, h)
        b.getPixels(pb, 0, w, left, top, w, h)
        var sum = 0L
        var count = 0L
        for (i in pa.indices) {
            val x = left + (i % w)
            val y = top + (i / w)
            if (excludes.any { x >= it.left && x < it.right && y >= it.top && y < it.bottom }) {
                continue
            }
            val c1 = pa[i]
            val c2 = pb[i]
            sum += abs(((c1 shr 16) and 0xFF) - ((c2 shr 16) and 0xFF))
            sum += abs(((c1 shr 8) and 0xFF) - ((c2 shr 8) and 0xFF))
            sum += abs((c1 and 0xFF) - (c2 and 0xFF))
            count += 3
        }
        return if (count == 0L) 0f else sum.toFloat() / count.toFloat()
    }

    private fun showAbortButton() {
        if (abortWindow != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val width = (density * 128f).roundToInt()
        val height = (density * 52f).roundToInt()
        val margin = (density * 12f).roundToInt()
        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = margin
            y = margin
        }
        val frame = FrameLayout(this)
        val button = TextView(this).apply {
            text = "中止填充"
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            setBackgroundColor(0xCCB3261E.toInt())
            setOnClickListener { abortFill("已手动中止填充") }
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> abortButtonPressed = true
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> abortButtonPressed = false
                }
                false
            }
        }
        frame.addView(
            button,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        abortWindow = frame
        runCatching { wm.addView(frame, params) }
    }

    private fun hideAbortButton() {
        abortButtonPressed = false
        abortWindow?.let { w ->
            runCatching {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(w)
            }
        }
        abortWindow = null
    }

    private fun currentAbortButtonScreenRect(): RectF? {
        val params = abortWindow?.layoutParams as? WindowManager.LayoutParams ?: return null
        return RectF(
            params.x.toFloat(),
            params.y.toFloat(),
            (params.x + params.width).toFloat(),
            (params.y + params.height).toFloat()
        )
    }

    private fun dismissOverlay() {
        cancelFill()
        AutoFillStateHolder.openSetupRequested.set(false)
        setupRelayoutRunnable?.let { mainHandler.removeCallbacks(it) }
        setupRelayoutRunnable = null
        runCatching {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            root?.let { wm.removeView(it) }
        }
        overlay = null
        overlayView = null
        root = null
    }
}
