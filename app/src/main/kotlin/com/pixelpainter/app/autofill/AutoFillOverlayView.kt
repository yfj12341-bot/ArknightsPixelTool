package com.pixelpainter.app.autofill

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.text.TextUtils
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One accessibility overlay surface used for both setup (dragging the canvas /
 * palette frames over a live screenshot) and the fill progress/countdown UI.
 *
 * The view itself never stacks multiple layers: the service reuses a single
 * window and simply switches this view between setup and fill states.
 */
class AutoFillOverlayView(context: Context) : View(context) {

    enum class UiState { PRE_SETUP, SETUP, COUNTDOWN, PROGRESS, DONE }

    private val density = resources.displayMetrics.density
    private val px = { value: Float -> value * density }

    private val dimPaint = Paint().apply {
        color = 0x99000000.toInt()
        style = Paint.Style.FILL
    }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF202428.toInt()
        style = Paint.Style.FILL
    }
    private val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9AAAB8.toInt()
        style = Paint.Style.STROKE
        strokeWidth = px(1f)
    }
    private val canvasPaint = Paint().apply {
        color = 0xFF6FE66F.toInt()
        style = Paint.Style.STROKE
        strokeWidth = px(2.5f)
    }
    private val palettePaint = Paint().apply {
        color = 0xFF4DD8FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = px(2.5f)
    }
    private val gridPaint = Paint().apply {
        color = 0x556FE66F
        style = Paint.Style.STROKE
        strokeWidth = px(0.75f)
    }
    private val paletteGridPaint = Paint().apply {
        color = 0x554DD8FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = px(0.75f)
    }
    private val buttonStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9AAAB8.toInt()
        style = Paint.Style.STROKE
        strokeWidth = px(1f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = px(15f)
    }
    private val smallTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFCCD8E2.toInt()
        textSize = px(12f)
    }
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = px(13f)
        isFakeBoldText = true
    }
    private val greenTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6FE66F.toInt()
        textSize = px(14f)
        isFakeBoldText = true
    }
    private val cyanTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4DD8FF.toInt()
        textSize = px(14f)
        isFakeBoldText = true
    }
    private val fillTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = px(13f)
        isFakeBoldText = true
    }
    private val fillInfoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFCCD8E2.toInt()
        textSize = px(11f)
    }
    private val fillButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = px(12f)
        isFakeBoldText = true
    }
    private val bigCountdownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = px(84f)
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    var screenshot: Bitmap? = null
        private set

    var canvasFrame: RectF? = null
    var paletteFrame: RectF? = null
    var mode = AutoFillTool.CANVAS
    var gridSize = AutoFillSettings.defaults.gridSize
    var paletteColumns = 4
    var paletteRows = 6

    var uiState = UiState.SETUP
        private set
    var message: String = ""
        private set

    interface Listener {
        fun onConfirmSetup(canvas: RectF, palette: RectF)
        fun onCancelFill()
        fun onReopenSetup()
        fun onDismissOverlay()
        fun onFillDragStart()
        fun onFillDragMove(dx: Float, dy: Float)
        fun onFillDragEnd()
    }

    var listener: Listener? = null

    private val screenshotSource = RectF()
    private val screenshotMatrix = Matrix()
    private var displayedRect = RectF()
    private var imageScale = 1f

    private var fillProgress = 0f
    private var fillLabel = ""
    private var fillStatus = ""

    private var dragHandle = 0
    private var dragCorner = 0
    private var dragOrigin = RectF()
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var clickX = 0f
    private var clickY = 0f
    private var clickMoved = false

    private val controlBarWidth: Float
        get() = px(78f)
    private val controlBarPadding: Float
        get() = px(8f)
    private val frameLeftBound: Float
        get() = controlBarWidth + controlBarPadding * 2f

    private val confirmRect = RectF()
    private val cancelRect = RectF()
    private val reopenRect = RectF()
    private val modeCanvasRect = RectF()
    private val modePaletteRect = RectF()

    private val fillCardRect = RectF()
    private var fillDragging = false
    private var fillDragStartX = 0f
    private var fillDragStartY = 0f
    private val doneReopenRect = RectF()
    private val doneDismissRect = RectF()

    private companion object {
        const val DRAG_NONE = -1
        const val DRAG_MOVE = 0
        const val DRAG_LEFT_TOP = 1
        const val DRAG_RIGHT_TOP = 2
        const val DRAG_LEFT_BOTTOM = 3
        const val DRAG_RIGHT_BOTTOM = 4
        const val DRAG_LEFT = 5
        const val DRAG_RIGHT = 6
        const val DRAG_TOP = 7
        const val DRAG_BOTTOM = 8
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        setWillNotDraw(false)
    }

    fun openSetupState() {
        uiState = UiState.SETUP
        fillProgress = 0f
        fillLabel = ""
        fillStatus = ""
        message = ""
        layoutControls()
        invalidate()
    }

    fun beginPreSetupCountdown(seconds: Int) {
        uiState = UiState.PRE_SETUP
        fillProgress = 0f
        fillLabel = seconds.coerceAtLeast(1).toString()
        fillStatus = "presetup"
        message = "请切换到明日方舟，$seconds 秒后开始框选"
        layoutControls()
        invalidate()
    }
    fun beginFill(countdownSeconds: Int) {
        uiState = UiState.COUNTDOWN
        fillStatus = "countdown"
        fillLabel = countdownSeconds.coerceAtLeast(1).toString()
        message = "准备填充，$countdownSeconds 秒后开始"
        fillProgress = 0f
        layoutControls()
        invalidate()
    }

    fun openProgressState() {
        uiState = UiState.PROGRESS
        fillStatus = "running"
        layoutControls()
        invalidate()
    }

    fun openDoneState() {
        uiState = UiState.DONE
        fillStatus = "done"
        fillLabel = ""
        layoutControls()
        invalidate()
    }

    fun updateCountdown(secondsRemaining: Int) {
        fillLabel = secondsRemaining.toString()
        if (uiState == UiState.PRE_SETUP) {
            message = "请切换到明日方舟，$secondsRemaining 秒后开始框选"
        } else {
            fillStatus = "countdown"
            message = "准备填充，$secondsRemaining 秒后开始"
        }
        invalidate()
    }

    fun updateProgress(percent: Float, status: String) {
        if (uiState == UiState.DONE) return
        fillProgress = percent.coerceIn(0f, 1f)
        fillLabel = status
        fillStatus = "running"
        message = status
        invalidate()
    }

    fun showDone(text: String) {
        uiState = UiState.DONE
        fillStatus = "done"
        fillLabel = ""
        message = text
        layoutControls()
        invalidate()
    }

    fun setMessage(value: String) {
        message = value
        invalidate()
    }

    fun imageScale(): Float = imageScale

    /**
     * Converts a frame drawn on the setup overlay into physical screen
     * coordinates, undoing the letterboxing caused by fitting the screenshot
     * into the overlay window.
     */
    fun mapViewRectToScreen(rect: RectF): RectF {
        val shot = screenshot
        val scale = imageScale
        if (shot == null || scale <= 0f || shot.width <= 0 || shot.height <= 0) {
            return RectF(rect)
        }
        val d = displayedRect
        if (d.width() <= 0f || d.height() <= 0f) return RectF(rect)
        return RectF(
            (rect.left - d.left) / scale,
            (rect.top - d.top) / scale,
            (rect.right - d.left) / scale,
            (rect.bottom - d.top) / scale
        )
    }

    /**
     * Maps a frame drawn on the setup overlay back into original screenshot
     * pixel coordinates, so the service can sample the swatch colors directly
     * from the bitmap underneath the frames.
     */
    fun mapViewRectToScreenshot(rect: RectF): RectF {
        val shot = screenshot
        val scale = imageScale
        if (shot == null || scale <= 0f || shot.width <= 0 || shot.height <= 0) {
            return RectF(rect)
        }
        val d = displayedRect
        if (d.width() <= 0f || d.height() <= 0f) return RectF(rect)
        return RectF(
            (rect.left - d.left) / scale,
            (rect.top - d.top) / scale,
            (rect.right - d.left) / scale,
            (rect.bottom - d.top) / scale
        )
    }

    fun setScreenshotBitmap(bitmap: Bitmap?) {
        screenshot = bitmap
        if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
            computeImageRect()
            resetFramesToScreenshot()
        } else {
            imageScale = 1f
            displayedRect.setEmpty()
            canvasFrame = null
            paletteFrame = null
        }
        invalidate()
    }

    /**
     * Restores previously remembered frames (in screenshot/screen pixel
     * coordinates) onto the current overlay, converting them back to view
     * coordinates with the current image transform.
     */
    fun setFramesFromScreen(canvas: RectF, palette: RectF) {
        val shot = screenshot
        val scale = imageScale
        if (shot == null || scale <= 0f || shot.width <= 0 || shot.height <= 0) return
        val d = displayedRect
        if (d.width() <= 0f || d.height() <= 0f) return
        val canvasView = RectF(
            canvas.left * scale + d.left,
            canvas.top * scale + d.top,
            canvas.right * scale + d.left,
            canvas.bottom * scale + d.top
        )
        val paletteView = RectF(
            palette.left * scale + d.left,
            palette.top * scale + d.top,
            palette.right * scale + d.left,
            palette.bottom * scale + d.top
        )
        clampFrame(canvasView)
        clampFrame(paletteView)
        canvasFrame = canvasView
        paletteFrame = paletteView
        invalidate()
    }

    fun recomputeSetupLayout() {
        if (uiState != UiState.SETUP) return
        val shot = screenshot
        if (shot != null && width > 0 && height > 0) {
            computeImageRect()
            resetFramesToScreenshot()
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutControls()
        if (uiState != UiState.SETUP) return
        if (canvasFrame == null && paletteFrame == null) {
            val shot = screenshot
            if (shot != null) {
                computeImageRect()
                resetFramesToScreenshot()
            } else {
                val side = min(w * 0.56f, h * 0.56f).coerceAtLeast(px(80f))
                val paletteWidth = (w * 0.2f).coerceAtLeast(px(96f))
                val paletteLeft = w * 0.98f - paletteWidth
                val zoneLeft = frameLeftBound + px(8f)
                val zoneRight = (paletteLeft - px(16f)).coerceAtLeast(zoneLeft + px(80f))
                val zoneSide = min(side, zoneRight - zoneLeft)
                val centerX = (zoneLeft + zoneRight) / 2f
                val top = (h - zoneSide) / 2f
                canvasFrame = RectF(
                    centerX - zoneSide / 2f,
                    top,
                    centerX + zoneSide / 2f,
                    top + zoneSide
                )
                paletteFrame = RectF(paletteLeft, h * 0.12f, w * 0.98f, h * 0.62f)
            }
            invalidate()
        }
    }

    private fun computeImageRect() {
        val shot = screenshot ?: return
        if (width <= 0 || height <= 0) return
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val scale = min(viewW / shot.width, viewH / shot.height)
        val w = shot.width * scale
        val hgt = shot.height * scale
        screenshotSource.set(0f, 0f, shot.width.toFloat(), shot.height.toFloat())
        screenshotMatrix.setScale(scale, scale)
        screenshotMatrix.postTranslate((viewW - w) / 2f, (viewH - hgt) / 2f)
        displayedRect.set(
            (viewW - w) / 2f,
            (viewH - hgt) / 2f,
            (viewW + w) / 2f,
            (viewH + hgt) / 2f
        )
        imageScale = scale
    }

    private fun resetFramesToScreenshot() {
        if (displayedRect.width() <= 0f || displayedRect.height() <= 0f) return
        val d = displayedRect
        val paletteWidth = (d.width() * 0.20f).coerceAtLeast(px(96f))
        val rightInset = d.width() * 0.01f
        val paletteLeft = d.right - paletteWidth - rightInset
        paletteFrame = RectF(
            paletteLeft,
            d.top + d.height() * 0.08f,
            d.right - rightInset,
            d.bottom - d.height() * 0.08f
        )
        val zoneLeft = frameLeftBound + px(8f)
        val zoneRight = (paletteLeft - px(16f)).coerceAtLeast(zoneLeft + px(80f))
        val side = min(d.height() * 0.64f, zoneRight - zoneLeft).coerceAtLeast(px(80f))
        val centerX = (zoneLeft + zoneRight) / 2f
        val top = d.top + (d.height() - side) / 2f
        canvasFrame = RectF(
            centerX - side / 2f,
            top,
            centerX + side / 2f,
            top + side
        )
    }

    private fun layoutControls() {
        if (width <= 0 || height <= 0) return
        if (uiState == UiState.SETUP) {
            val barWidth = controlBarWidth
            val btnH = px(46f)
            val gap = px(10f)
            val top = px(12f)
            modeCanvasRect.set(0f, top, barWidth, top + btnH)
            modePaletteRect.set(0f, top + btnH + gap, barWidth, top + btnH * 2f + gap)
            confirmRect.set(0f, top + btnH * 2f + gap * 2.5f, barWidth, top + btnH * 3f + gap * 2.5f)
            cancelRect.set(0f, top + btnH * 3f + gap * 4f, barWidth, top + btnH * 4f + gap * 4f)
            reopenRect.set(0f, top, barWidth, top + btnH)
            return
        }

        fillCardRect.set(px(3f), px(3f), width - px(3f), height - px(3f))
        val innerPad = px(10f)
        val gap = px(6f)
        val btnH = px(30f)
        val bottomPad = px(4f)
        doneReopenRect.set(
            fillCardRect.left + innerPad,
            fillCardRect.bottom - btnH - bottomPad,
            fillCardRect.right - innerPad,
            fillCardRect.bottom - bottomPad
        )
        doneDismissRect.set(
            fillCardRect.left + innerPad,
            doneReopenRect.top - gap - btnH,
            fillCardRect.right - innerPad,
            doneReopenRect.top - gap
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (uiState) {
            UiState.PRE_SETUP -> drawPreSetupCountdown(canvas)
            UiState.SETUP -> drawSetup(canvas)
            UiState.COUNTDOWN, UiState.PROGRESS, UiState.DONE -> drawFill(canvas)
        }
    }

    private fun drawPreSetupCountdown(canvas: Canvas) {
        canvas.drawColor(0x88000000)
        val centerX = width / 2f
        val centerY = height / 2f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("请切换到明日方舟", centerX, centerY - px(84f), textPaint)
        smallTextPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(message, centerX, centerY - px(54f), smallTextPaint)
        canvas.drawText(fillLabel, centerX, height - px(150f), bigCountdownPaint)
        canvas.drawText("秒后开始框选", centerX, height - px(118f), smallTextPaint)
        textPaint.textAlign = Paint.Align.LEFT
        smallTextPaint.textAlign = Paint.Align.LEFT
    }
    private fun drawSetup(canvas: Canvas) {
        val shot = screenshot
        if (shot != null) {
            val bg = BitmapDrawable(context.resources, shot)
            bg.setBounds(
                displayedRect.left.roundToInt(),
                displayedRect.top.roundToInt(),
                displayedRect.right.roundToInt(),
                displayedRect.bottom.roundToInt()
            )
            bg.draw(canvas)
        } else {
            canvas.drawColor(0x88000000)
        }

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        canvasFrame?.let { frame ->
            canvas.drawRect(frame, canvasPaint)
            drawSquareHandles(canvas, frame)
            if (gridSize > 0) {
                val cellW = frame.width() / gridSize
                val cellH = frame.height() / gridSize
                for (i in 1 until gridSize) {
                    val x = frame.left + cellW * i
                    val y = frame.top + cellH * i
                    canvas.drawLine(x, frame.top, x, frame.bottom, gridPaint)
                    canvas.drawLine(frame.left, y, frame.right, y, gridPaint)
                }
            }
            drawLabel(canvas, "${gridSize}x${gridSize} 画布", frame, 0xFF6FE66F.toInt())
        }
        paletteFrame?.let { frame ->
            canvas.drawRect(frame, palettePaint)
            drawPaletteHandles(canvas, frame)
            if (paletteColumns > 0 && paletteRows > 0) {
                val cellW = frame.width() / paletteColumns
                val cellH = frame.height() / paletteRows
                for (i in 1 until paletteColumns) {
                    val x = frame.left + cellW * i
                    canvas.drawLine(x, frame.top, x, frame.bottom, paletteGridPaint)
                }
                for (i in 1 until paletteRows) {
                    val y = frame.top + cellH * i
                    canvas.drawLine(frame.left, y, frame.right, y, paletteGridPaint)
                }
            }
            drawLabel(canvas, "调色盘 ${paletteColumns}×${paletteRows}", frame, 0xFF4DD8FF.toInt())
        }
        drawControlSidebar(canvas)
        drawHelp(canvas)
    }

    private fun drawLabel(canvas: Canvas, label: String, frame: RectF, color: Int) {
        labelTextPaint.color = color
        val y = if (frame.top > px(24f)) frame.top - px(6f) else frame.top + px(16f)
        canvas.drawText(label, frame.left + px(4f), y, labelTextPaint)
    }

    private fun drawSquareHandles(canvas: Canvas, frame: RectF) {
        val size = px(14f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCC6FE66F.toInt()
            style = Paint.Style.FILL
        }
        val half = size / 2f
        canvas.drawRect(frame.left - half, frame.top - half, frame.left + half, frame.top + half, paint)
        canvas.drawRect(frame.right - half, frame.top - half, frame.right + half, frame.top + half, paint)
        canvas.drawRect(frame.left - half, frame.bottom - half, frame.left + half, frame.bottom + half, paint)
        canvas.drawRect(frame.right - half, frame.bottom - half, frame.right + half, frame.bottom + half, paint)
    }

    private fun drawPaletteHandles(canvas: Canvas, frame: RectF) {
        val size = px(10f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCC4DD8FF.toInt()
            style = Paint.Style.FILL
        }
        val half = size / 2f
        canvas.drawCircle(frame.left, frame.top, half, paint)
        canvas.drawCircle(frame.right, frame.top, half, paint)
        canvas.drawCircle(frame.left, frame.bottom, half, paint)
        canvas.drawCircle(frame.right, frame.bottom, half, paint)

        val edgeSize = px(8f)
        val edgeHalf = edgeSize / 2f
        val midX = (frame.left + frame.right) / 2f
        val midY = (frame.top + frame.bottom) / 2f
        canvas.drawRect(midX - edgeHalf, frame.top - edgeHalf, midX + edgeHalf, frame.top + edgeHalf, paint)
        canvas.drawRect(midX - edgeHalf, frame.bottom - edgeHalf, midX + edgeHalf, frame.bottom + edgeHalf, paint)
        canvas.drawRect(frame.left - edgeHalf, midY - edgeHalf, frame.left + edgeHalf, midY + edgeHalf, paint)
        canvas.drawRect(frame.right - edgeHalf, midY - edgeHalf, frame.right + edgeHalf, midY + edgeHalf, paint)
    }

    private fun drawControlSidebar(canvas: Canvas) {
        canvas.drawRect(0f, 0f, controlBarWidth, height.toFloat(), cardPaint)
        canvas.drawLine(controlBarWidth, 0f, controlBarWidth, height.toFloat(), cardStrokePaint)

        textPaint.textAlign = Paint.Align.CENTER
        greenTextPaint.textAlign = Paint.Align.CENTER
        cyanTextPaint.textAlign = Paint.Align.CENTER
        canvas.drawRect(modeCanvasRect, buttonStrokePaint)
        canvas.drawText(
            "画布",
            modeCanvasRect.centerX(),
            modeCanvasRect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f,
            if (mode == AutoFillTool.CANVAS) greenTextPaint else textPaint
        )
        canvas.drawRect(modePaletteRect, buttonStrokePaint)
        canvas.drawText(
            "调色盘",
            modePaletteRect.centerX(),
            modePaletteRect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f,
            if (mode == AutoFillTool.PALETTE) cyanTextPaint else textPaint
        )
        canvas.drawRect(confirmRect, buttonStrokePaint)
        canvas.drawText(
            "开始",
            confirmRect.centerX(),
            confirmRect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f,
            greenTextPaint
        )
        canvas.drawRect(cancelRect, buttonStrokePaint)
        canvas.drawText(
            "取消",
            cancelRect.centerX(),
            cancelRect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f,
            textPaint
        )
        textPaint.textAlign = Paint.Align.LEFT
        greenTextPaint.textAlign = Paint.Align.LEFT
        cyanTextPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawHelp(canvas: Canvas) {
        smallTextPaint.color = 0xFFCCD8E2.toInt()
        smallTextPaint.textAlign = Paint.Align.CENTER
        val centerX = controlBarWidth / 2f
        val helpTop = cancelRect.bottom + px(20f)
        val lineSpacing = smallTextPaint.fontSpacing
        val (firstLine, secondLine) = if (mode == AutoFillTool.CANVAS) {
            "拖动角点" to "保持正方形"
        } else {
            "拖动边缘" to "调整调色盘"
        }
        canvas.drawText(firstLine, centerX, helpTop, smallTextPaint)
        canvas.drawText(secondLine, centerX, helpTop + lineSpacing, smallTextPaint)
        canvas.drawText("点「开始」", centerX, helpTop + lineSpacing * 2f, smallTextPaint)
        smallTextPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawFill(canvas: Canvas) {
        canvas.drawColor(Color.TRANSPARENT)
        val radius = px(8f)
        canvas.drawRoundRect(fillCardRect, radius, radius, cardPaint)
        canvas.drawRoundRect(fillCardRect, radius, radius, cardStrokePaint)

        val pad = px(8f)
        val maxTextWidth = fillCardRect.width() - pad * 2f

        if (uiState == UiState.DONE) {
            // Done: message + stacked buttons (收起 above 重新框选)
            val msg = if (fillInfoPaint.measureText(message) > maxTextWidth) {
                TextUtils.ellipsize(message, fillInfoPaint, maxTextWidth, TextUtils.TruncateAt.MIDDLE).toString()
            } else {
                message
            }
            canvas.drawText(
                msg,
                fillCardRect.left + pad,
                fillCardRect.top + px(15f),
                fillInfoPaint.apply { textAlign = Paint.Align.LEFT }
            )
            fillInfoPaint.textAlign = Paint.Align.LEFT
            canvas.drawRect(doneReopenRect, buttonStrokePaint)
            canvas.drawText(
                "重新框选",
                doneReopenRect.centerX(),
                doneReopenRect.centerY() - (fillButtonPaint.ascent() + fillButtonPaint.descent()) / 2f,
                fillButtonPaint.apply { textAlign = Paint.Align.CENTER }
            )
            canvas.drawRect(doneDismissRect, buttonStrokePaint)
            canvas.drawText(
                "收起",
                doneDismissRect.centerX(),
                doneDismissRect.centerY() - (fillButtonPaint.ascent() + fillButtonPaint.descent()) / 2f,
                fillButtonPaint.apply { textAlign = Paint.Align.CENTER }
            )
            fillButtonPaint.textAlign = Paint.Align.LEFT
            return
        }

        val title = if (uiState == UiState.COUNTDOWN) "自动填充准备中" else "自动填充执行中"
        canvas.drawText(
            title,
            fillCardRect.left + pad,
            fillCardRect.top + px(15f),
            fillTitlePaint.apply { textAlign = Paint.Align.LEFT }
        )
        fillTitlePaint.textAlign = Paint.Align.LEFT

        val barLeft = fillCardRect.left + pad
        val barRight = fillCardRect.right - pad
        val barTop = fillCardRect.top + px(24f)
        val barBottom = barTop + px(5f)
        val fillRight = barLeft + (barRight - barLeft) * fillProgress
        canvas.drawRect(barLeft, barTop, barRight, barBottom, cardStrokePaint)
        canvas.drawRect(barLeft, barTop, fillRight, barBottom, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF6FE66F.toInt()
            style = Paint.Style.FILL
        })

        val info = if (uiState == UiState.COUNTDOWN) {
            "${fillLabel} 秒后开始，请勿触碰屏幕"
        } else {
            message.ifBlank { fillLabel }
        }
        val shown = if (fillInfoPaint.measureText(info) > maxTextWidth) {
            TextUtils.ellipsize(info, fillInfoPaint, maxTextWidth, TextUtils.TruncateAt.MIDDLE).toString()
        } else {
            info
        }
        canvas.drawText(
            shown,
            fillCardRect.left + pad,
            barBottom + px(14f),
            fillInfoPaint.apply { textAlign = Paint.Align.LEFT }
        )
        fillInfoPaint.textAlign = Paint.Align.LEFT
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val x = event.x
        val y = event.y

        return when (uiState) {
            UiState.SETUP -> {
                if (x <= controlBarWidth) {
                    handleControlTouch(action, x, y)
                } else {
                    handleFrameTouch(action, x, y)
                }
            }
            UiState.PRE_SETUP -> false
            UiState.COUNTDOWN, UiState.PROGRESS -> handleFillTouch(event)
            UiState.DONE -> handleDoneTouch(action, x, y)
        }
    }

    private fun handleControlTouch(action: Int, x: Float, y: Float): Boolean {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                clickX = x
                clickY = y
                clickMoved = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(x - clickX) > 12f || abs(y - clickY) > 12f) clickMoved = true
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (clickMoved) return true
                if (modeCanvasRect.contains(x, y)) {
                    mode = AutoFillTool.CANVAS
                    invalidate()
                } else if (modePaletteRect.contains(x, y)) {
                    mode = AutoFillTool.PALETTE
                    invalidate()
                } else if (confirmRect.contains(x, y)) {
                    val canvas = canvasFrame ?: RectF()
                    val palette = paletteFrame ?: RectF()
                    if (canvas.width() > 1f && palette.width() > 1f) {
                        listener?.onConfirmSetup(RectF(canvas), RectF(palette))
                    }
                } else if (cancelRect.contains(x, y)) {
                    listener?.onDismissOverlay()
                }
                clickMoved = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                clickMoved = false
                return true
            }
        }
        return true
    }

    private fun handleFillTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (fillCardRect.contains(event.x, event.y)) {
                    fillDragging = true
                    fillDragStartX = event.rawX
                    fillDragStartY = event.rawY
                    listener?.onFillDragStart()
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (fillDragging) {
                    listener?.onFillDragMove(event.rawX - fillDragStartX, event.rawY - fillDragStartY)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (fillDragging) {
                    fillDragging = false
                    listener?.onFillDragEnd()
                    return true
                }
                return false
            }
        }
        return true
    }

    private fun handleDoneTouch(action: Int, x: Float, y: Float): Boolean {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                clickX = x
                clickY = y
                clickMoved = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(x - clickX) > 12f || abs(y - clickY) > 12f) clickMoved = true
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!clickMoved) {
                    if (doneDismissRect.contains(x, y)) {
                        listener?.onDismissOverlay()
                    } else if (doneReopenRect.contains(x, y)) {
                        listener?.onReopenSetup()
                    }
                }
                clickMoved = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                clickMoved = false
                return true
            }
        }
        return true
    }

    private fun handleFrameTouch(action: Int, x: Float, y: Float): Boolean {
        return if (mode == AutoFillTool.CANVAS) {
            handleSquareDrag(action, x, y)
        } else {
            handlePaletteDrag(action, x, y)
        }
    }

    private fun handleSquareDrag(action: Int, x: Float, y: Float): Boolean {
        val frame = canvasFrame
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = x
                dragStartY = y
                dragHandle = -1
                dragCorner = 0
                if (frame != null) {
                    dragOrigin.set(frame)
                    dragCorner = hitSquareCorner(frame, x, y)
                    dragHandle = when {
                        dragCorner != 0 -> 1
                        frame.contains(x, y) -> 0
                        else -> -1
                    }
                }
                return dragHandle >= 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (frame == null || dragHandle < 0) return false
                val dx = x - dragStartX
                val dy = y - dragStartY
                val original = dragOrigin
                val next = if (dragCorner == 0) {
                    RectF(
                        original.left + dx,
                        original.top + dy,
                        original.right + dx,
                        original.bottom + dy
                    )
                } else {
                    buildSquareFrame(original, dragCorner, x, y)
                }
                if (dragCorner == 0) {
                    clampMovedFrame(next)
                } else {
                    clampSquareFrame(next, dragCorner)
                }
                canvasFrame = next
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragHandle = -1
                dragCorner = 0
                clickMoved = false
                return true
            }
        }
        return true
    }

    private fun buildSquareFrame(original: RectF, corner: Int, x: Float, y: Float): RectF {
        return when (corner) {
            1 -> {
                val newSide = max(abs(x - original.left), abs(y - original.top))
                RectF(original.left, original.top, original.left + newSide, original.top + newSide)
            }
            2 -> {
                val newSide = max(abs(x - original.right), abs(y - original.top))
                RectF(original.right - newSide, original.top, original.right, original.top + newSide)
            }
            3 -> {
                val newSide = max(abs(x - original.right), abs(y - original.bottom))
                RectF(original.right - newSide, original.bottom - newSide, original.right, original.bottom)
            }
            4 -> {
                val newSide = max(abs(x - original.left), abs(y - original.bottom))
                RectF(original.left, original.bottom - newSide, original.left + newSide, original.bottom)
            }
            else -> RectF(original)
        }
    }

    private fun clampSquareFrame(frame: RectF, corner: Int) {
        if (corner !in 1..4) return
        val shot = screenshot
        val leftBound = max(frameLeftBound, if (shot != null) displayedRect.left else 0f)
        val topBound = if (shot != null) displayedRect.top else 0f
        val rightBound = if (shot != null) displayedRect.right else width.toFloat()
        val bottomBound = if (shot != null) displayedRect.bottom else height.toFloat()
        val minSide = px(60f)
        val fx: Float
        val fy: Float
        var maxSide: Float
        when (corner) {
            1 -> {
                fx = frame.left
                fy = frame.top
                maxSide = min(rightBound - fx, bottomBound - fy)
            }
            2 -> {
                fx = frame.right
                fy = frame.top
                maxSide = min(fx - leftBound, bottomBound - fy)
            }
            3 -> {
                fx = frame.right
                fy = frame.bottom
                maxSide = min(fx - leftBound, fy - topBound)
            }
            else -> {
                fx = frame.left
                fy = frame.bottom
                maxSide = min(rightBound - fx, fy - topBound)
            }
        }
        if (maxSide < minSide) maxSide = minSide
        val side = frame.width().coerceIn(minSide, maxSide)
        when (corner) {
            1 -> frame.set(fx, fy, fx + side, fy + side)
            2 -> frame.set(fx - side, fy, fx, fy + side)
            3 -> frame.set(fx - side, fy - side, fx, fy)
            else -> frame.set(fx, fy - side, fx + side, fy)
        }
    }

    private fun clampMovedFrame(frame: RectF) {
        val shot = screenshot
        val leftBound = max(frameLeftBound, if (shot != null) displayedRect.left else 0f)
        val topBound = if (shot != null) displayedRect.top else 0f
        val rightBound = if (shot != null) displayedRect.right else width.toFloat()
        val bottomBound = if (shot != null) displayedRect.bottom else height.toFloat()
        val shiftX = when {
            frame.left < leftBound -> leftBound - frame.left
            frame.right > rightBound -> rightBound - frame.right
            else -> 0f
        }
        val shiftY = when {
            frame.top < topBound -> topBound - frame.top
            frame.bottom > bottomBound -> bottomBound - frame.bottom
            else -> 0f
        }
        frame.offset(shiftX, shiftY)
    }

    private fun handlePaletteDrag(action: Int, x: Float, y: Float): Boolean {
        val frame = paletteFrame
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = x
                dragStartY = y
                dragHandle = DRAG_NONE
                if (frame != null) {
                    dragOrigin.set(frame)
                    val handle = hitPaletteHandle(frame, x, y)
                    dragHandle = if (handle != DRAG_NONE) {
                        handle
                    } else if (frame.contains(x, y)) {
                        DRAG_MOVE
                    } else {
                        DRAG_NONE
                    }
                }
                return dragHandle != DRAG_NONE
            }
            MotionEvent.ACTION_MOVE -> {
                if (frame == null || dragHandle == DRAG_NONE) return false
                val dx = x - dragStartX
                val dy = y - dragStartY
                val original = dragOrigin
                val next = when (dragHandle) {
                    DRAG_MOVE -> RectF(
                        original.left + dx,
                        original.top + dy,
                        original.right + dx,
                        original.bottom + dy
                    )
                    DRAG_LEFT_TOP -> RectF(original.left + dx, original.top + dy, original.right, original.bottom)
                    DRAG_RIGHT_TOP -> RectF(original.left, original.top + dy, original.right + dx, original.bottom)
                    DRAG_LEFT_BOTTOM -> RectF(original.left + dx, original.top, original.right, original.bottom + dy)
                    DRAG_RIGHT_BOTTOM -> RectF(original.left, original.top, original.right + dx, original.bottom + dy)
                    DRAG_LEFT -> RectF(original.left + dx, original.top, original.right, original.bottom)
                    DRAG_RIGHT -> RectF(original.left, original.top, original.right + dx, original.bottom)
                    DRAG_TOP -> RectF(original.left, original.top + dy, original.right, original.bottom)
                    DRAG_BOTTOM -> RectF(original.left, original.top, original.right, original.bottom + dy)
                    else -> RectF(original)
                }
                clampFrame(next)
                paletteFrame = next
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragHandle = DRAG_NONE
                clickMoved = false
                return true
            }
        }
        return true
    }

    private fun clampFrame(frame: RectF) {
        val shot = screenshot
        val leftBound = max(frameLeftBound, if (shot != null) displayedRect.left else 0f)
        val topBound = if (shot != null) displayedRect.top else 0f
        val rightBound = if (shot != null) displayedRect.right else width.toFloat()
        val bottomBound = if (shot != null) displayedRect.bottom else height.toFloat()
        val minW = px(60f)
        val minH = px(60f)
        frame.left = frame.left.coerceIn(leftBound, rightBound - minW)
        frame.right = frame.right.coerceIn(frame.left + minW, rightBound)
        frame.top = frame.top.coerceIn(topBound, bottomBound - minH)
        frame.bottom = frame.bottom.coerceIn(frame.top + minH, bottomBound)
    }

    private fun hitSquareCorner(frame: RectF, x: Float, y: Float): Int {
        val r = px(22f)
        if (distance(x, y, frame.left, frame.top) <= r) return 3
        if (distance(x, y, frame.right, frame.top) <= r) return 4
        if (distance(x, y, frame.left, frame.bottom) <= r) return 2
        if (distance(x, y, frame.right, frame.bottom) <= r) return 1
        return 0
    }

    private fun hitPaletteHandle(frame: RectF, x: Float, y: Float): Int {
        val r = px(22f)
        val left = abs(x - frame.left) <= r
        val right = abs(x - frame.right) <= r
        val top = abs(y - frame.top) <= r
        val bottom = abs(y - frame.bottom) <= r
        if (left && top) return DRAG_LEFT_TOP
        if (right && top) return DRAG_RIGHT_TOP
        if (left && bottom) return DRAG_LEFT_BOTTOM
        if (right && bottom) return DRAG_RIGHT_BOTTOM
        if (left) return DRAG_LEFT
        if (right) return DRAG_RIGHT
        if (top) return DRAG_TOP
        if (bottom) return DRAG_BOTTOM
        return DRAG_NONE
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
