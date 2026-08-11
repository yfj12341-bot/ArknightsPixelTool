package com.pixelpainter.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelpainter.app.BuildConfig
import com.pixelpainter.core.ColorMath
import com.pixelpainter.core.CropBounds
import com.pixelpainter.core.DownsampleMode
import com.pixelpainter.core.ImageAdjustments
import com.pixelpainter.core.Palette
import com.pixelpainter.core.PaletteMode
import com.pixelpainter.core.PixelArtConverter
import com.pixelpainter.core.PixelArtOptions
import com.pixelpainter.core.PixelArtResult
import com.pixelpainter.core.RgbImage
import com.pixelpainter.core.SamplePalettes
import com.pixelpainter.app.autofill.AutoFillSettings
import com.pixelpainter.app.autofill.AutoFillStateHolder
import com.pixelpainter.app.autofill.AutoFillSupport
import com.pixelpainter.app.autofill.FillSpeedPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val MAX_HISTORY = 50
private const val PREVIEW_MAX_SIDE = 640
private const val ADJUSTMENT_PREVIEW_DEBOUNCE_MS = 24L
private const val ADJUSTMENT_FULL_DEBOUNCE_MS = 60L
private const val GITHUB_URL = "https://github.com/yfj12341-bot/ArknightsPixelTool"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelPainterApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceImage by remember { mutableStateOf<RgbImage?>(null) }
    var originalImage by remember { mutableStateOf<RgbImage?>(null) }
    var adjustmentPreviewSource by remember { mutableStateOf<RgbImage?>(null) }
    var adjustmentPreviewImage by remember { mutableStateOf<RgbImage?>(null) }
    var result by remember { mutableStateOf<PixelArtResult?>(null) }
    var paletteMode by remember { mutableStateOf(PaletteMode.AUTO) }
    var gridSize by remember { mutableStateOf(24) }
    var maxColors by remember { mutableStateOf(40) }
    var downscaleMode by remember { mutableStateOf(DownsampleMode.BOX) }
    var ditherEnabled by remember { mutableStateOf(true) }
    var processing by remember { mutableStateOf(false) }
    var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var editMode by remember { mutableStateOf(false) }
    var showColorNumbers by remember { mutableStateOf(false) }
    var undoStack by remember { mutableStateOf(emptyList<PixelArtResult>()) }
    var redoStack by remember { mutableStateOf(emptyList<PixelArtResult>()) }
    var brushColor by remember {
        mutableStateOf(SamplePalettes.arknights40.colors.first())
    }
    var cropStartX by remember { mutableStateOf(0f) }
    var cropStartY by remember { mutableStateOf(0f) }
    var cropSide by remember { mutableStateOf(0f) }
    var notice by remember { mutableStateOf<String?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    var showAutoFillSetup by remember { mutableStateOf(false) }
    var rotationDegrees by remember { mutableStateOf(0f) }
    var brightness by remember { mutableStateOf(0f) }
    var contrast by remember { mutableStateOf(0f) }
    var adjustingImage by remember { mutableStateOf(false) }
    var adjustmentPreviewJob by remember { mutableStateOf<Job?>(null) }
    var adjustmentFullJob by remember { mutableStateOf<Job?>(null) }
    var adjustmentRequestId by remember { mutableStateOf(0) }

    fun applyCropDefaults(image: RgbImage) {
        val side = minOf(image.width, image.height)
        cropStartX = ((image.width - side) / 2).toFloat()
        cropStartY = ((image.height - side) / 2).toFloat()
        cropSide = side.toFloat()
    }

    fun clampCropToImage(image: RgbImage) {
        val maxSide = minOf(image.width, image.height)
        val side = cropSide.roundToInt().coerceIn(1, maxSide).toFloat()
        val maxLeft = (image.width - side.roundToInt()).coerceAtLeast(0).toFloat()
        val maxTop = (image.height - side.roundToInt()).coerceAtLeast(0).toFloat()
        cropStartX = cropStartX.coerceIn(0f, maxLeft)
        cropStartY = cropStartY.coerceIn(0f, maxTop)
        cropSide = side
    }

    fun requestImageAdjustment(
        newRotation: Float,
        newBrightness: Float,
        newContrast: Float
    ) {
        rotationDegrees = newRotation
        brightness = newBrightness
        contrast = newContrast

        val previewBase = adjustmentPreviewSource ?: return
        val requestId = adjustmentRequestId + 1
        adjustmentRequestId = requestId
        adjustingImage = true
        adjustmentFullJob?.cancel()
        adjustmentPreviewJob?.cancel()
        adjustmentPreviewJob = scope.launch {
            delay(ADJUSTMENT_PREVIEW_DEBOUNCE_MS)
            val previewAdjusted = withContext(Dispatchers.Default) {
                ImageAdjustments.apply(
                    source = previewBase,
                    rotationDegrees = newRotation.roundToInt(),
                    brightness = newBrightness.roundToInt(),
                    contrast = newContrast.roundToInt()
                )
            }
            if (requestId == adjustmentRequestId) {
                adjustmentPreviewImage = previewAdjusted
            }
        }
    }

    fun finishImageAdjustment() {
        val base = originalImage ?: return
        val requestId = adjustmentRequestId
        adjustingImage = true
        adjustmentFullJob?.cancel()
        adjustmentFullJob = scope.launch {
            try {
                delay(ADJUSTMENT_FULL_DEBOUNCE_MS)
                val adjusted = withContext(Dispatchers.Default) {
                    ImageAdjustments.apply(
                        source = base,
                        rotationDegrees = rotationDegrees.roundToInt(),
                        brightness = brightness.roundToInt(),
                        contrast = contrast.roundToInt()
                    )
                }
                if (requestId == adjustmentRequestId) {
                    sourceImage = adjusted
                    clampCropToImage(adjusted)
                }
            } finally {
                if (requestId == adjustmentRequestId) {
                    adjustingImage = false
                }
            }
        }
    }

    fun generate() {
        val image = sourceImage ?: return
        val maxSide = minOf(image.width, image.height)
        val crop = CropBounds(
            startX = cropStartX.roundToInt(),
            startY = cropStartY.roundToInt(),
            sidePixels = cropSide.roundToInt().coerceIn(1, maxSide)
        )
        scope.launch {
            processing = true
            val generated = withContext(Dispatchers.Default) {
                PixelArtConverter.convert(
                    source = image,
                    options = PixelArtOptions(
                        paletteMode = paletteMode,
                        gridSize = if (paletteMode == PaletteMode.CUSTOM) gridSize else 24,
                        maxColors = if (paletteMode == PaletteMode.CUSTOM) maxColors else 40,
                        downscaleMode = downscaleMode,
                        dither = ditherEnabled && paletteMode != PaletteMode.FIXED
                    ),
                    fixedPalette = if (paletteMode == PaletteMode.FIXED) {
                        SamplePalettes.arknights40
                    } else {
                        null
                    },
                    crop = crop
                )
            }
            undoStack = emptyList()
            redoStack = emptyList()
            result = generated
            brushColor = generated.palette.colors.firstOrNull() ?: brushColor
            selectedCell = null
            processing = false
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val image = context.decodeRgbImage(uri)
        if (image != null) {
            originalImage = image
            sourceImage = image
            adjustmentPreviewSource = image.scaledToMaxSide(PREVIEW_MAX_SIDE)
            adjustmentPreviewImage = adjustmentPreviewSource
            rotationDegrees = 0f
            brightness = 0f
            contrast = 0f
            adjustingImage = false
            adjustmentPreviewJob?.cancel()
            adjustmentPreviewJob = null
            adjustmentFullJob?.cancel()
            adjustmentFullJob = null
            adjustmentRequestId = 0
            result = null
            undoStack = emptyList()
            redoStack = emptyList()
            selectedCell = null
            notice = null
            applyCropDefaults(image)
        } else {
            notice = "无法读取这张图片"
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("像素画助手") },
                actions = {
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Default.Info, contentDescription = "关于")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            notice?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (sourceImage == null) {
                EmptyState(
                    onPick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            } else {
                ImageAdjustPanel(
                    rotationDegrees = rotationDegrees,
                    brightness = brightness,
                    contrast = contrast,
                    enabled = !processing,
                    adjusting = adjustingImage,
                    onAdjust = { rotation, bright, cont ->
                        requestImageAdjustment(rotation, bright, cont)
                    },
                    onAdjustFinished = {
                        finishImageAdjustment()
                    }
                )

                SourceCropPanel(
                    image = sourceImage!!,
                    previewImage = adjustmentPreviewImage,
                    cropStartX = cropStartX,
                    cropStartY = cropStartY,
                    cropSide = cropSide,
                    onCropChange = { x, y, side ->
                        cropStartX = x
                        cropStartY = y
                        cropSide = side
                    },
                    onPick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )

                SettingsSection(
                    paletteMode = paletteMode,
                    onPaletteMode = {
                        paletteMode = it
                        if (it == PaletteMode.FIXED) {
                            brushColor = SamplePalettes.arknights40.colors.first()
                            ditherEnabled = false
                        }
                    },
                    gridSize = gridSize,
                    onGridSizeChange = { gridSize = ((it.coerceIn(24, 64)) / 2) * 2 },
                    maxColors = maxColors,
                    onMaxColorsChange = { maxColors = it.coerceIn(1, 256) },
                    downscaleMode = downscaleMode,
                    onDownscaleMode = { downscaleMode = it },
                    ditherEnabled = ditherEnabled,
                    onDitherChange = { ditherEnabled = it }
                )

                OutlinedButton(
                    onClick = { generate() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !processing && !adjustingImage
                ) {
                    if (processing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("正在生成")
                    } else {
                        Text(
                            if (paletteMode == PaletteMode.CUSTOM) {
                                "生成 ${gridSize}×${gridSize} 像素画"
                            } else {
                                "生成 24×24 像素画"
                            }
                        )
                    }
                }

                result?.let { art ->
                    ResultSection(
                        art = art,
                        paletteMode = paletteMode,
                        editMode = editMode,
                        onEditModeChange = { editMode = it },
                        showColorNumbers = showColorNumbers,
                        onShowColorNumbersChange = { showColorNumbers = it },
                        selectedCell = selectedCell,
                        onSelectCell = { selectedCell = it },
                        brushColor = brushColor,
                        onBrushColor = { brushColor = it },
                        onEditPixel = { x, y ->
                            val current = result ?: return@ResultSection
                            val cellIndex = y * current.gridSize + x
                            val existing = current.palette.colors.indexOf(brushColor)
                            if (existing < 0 && paletteMode == PaletteMode.FIXED) return@ResultSection
                            val palette = if (existing >= 0) {
                                current.palette
                            } else {
                                current.palette.copy(
                                    colors = current.palette.colors + brushColor
                                )
                            }
                            val index = if (existing >= 0) existing else palette.size - 1
                            if (current.indices[cellIndex] == index) return@ResultSection
                            val indices = current.indices.copyOf()
                            indices[cellIndex] = index
                            val previewPixels = IntArray(indices.size) {
                                palette.colors[indices[it]]
                            }
                            undoStack = (undoStack + current).takeLast(MAX_HISTORY)
                            redoStack = emptyList()
                            result = current.copy(
                                palette = palette,
                                indices = indices,
                                preview = RgbImage(
                                    width = current.gridSize,
                                    height = current.gridSize,
                                    pixels = previewPixels
                                )
                            )
                            selectedCell = x to y
                        },
                        canUndo = undoStack.isNotEmpty(),
                        onUndo = {
                            val previous = undoStack.lastOrNull() ?: return@ResultSection
                            val current = result ?: return@ResultSection
                            undoStack = undoStack.dropLast(1)
                            redoStack = redoStack + current
                            result = previous
                            selectedCell = null
                        },
                        canRedo = redoStack.isNotEmpty(),
                        onRedo = {
                            val next = redoStack.lastOrNull() ?: return@ResultSection
                            val current = result ?: return@ResultSection
                            redoStack = redoStack.dropLast(1)
                            undoStack = (undoStack + current).takeLast(MAX_HISTORY)
                            result = next
                            selectedCell = null
                        },
                        onSharePng = { sharePixelPng(context, result ?: return@ResultSection) },
                        onAutoFillRequest = { showAutoFillSetup = true },
                    )
                }
            }
        }
    }
    if (showAutoFillSetup) {
        AutoFillSetupDialog(
            onDismiss = { showAutoFillSetup = false },
            onConfirm = { settings ->
                val art = result ?: return@AutoFillSetupDialog
                showAutoFillSetup = false
                AutoFillStateHolder.setPending(art, settings)
                context.sendBroadcast(
                    android.content.Intent(AutoFillSupport.ACTION_START_SETUP).apply {
                        setPackage(context.packageName)
                        putExtra(AutoFillSupport.EXTRA_HAS_ART, true)
                        putExtra(AutoFillSupport.EXTRA_PALETTE_SIZE, art.palette.colors.size)
                        putExtra(AutoFillSupport.EXTRA_PALETTE_COLORS, art.palette.colors.toIntArray())
                        putExtra(AutoFillSupport.EXTRA_INDICES, art.indices)
                        putExtra(AutoFillSupport.EXTRA_GRID_SIZE, settings.gridSize)
                        putExtra(AutoFillSupport.EXTRA_PALETTE_COLUMNS, settings.paletteColumns)
                        putExtra(AutoFillSupport.EXTRA_PALETTE_ROWS, settings.paletteRows)
                        putExtra(AutoFillSupport.EXTRA_VISIBLE_COLORS, settings.visibleColors)
                        putExtra(AutoFillSupport.EXTRA_PAGE_OVERLAP, settings.pageOverlapColors)
                        putExtra(AutoFillSupport.EXTRA_SWIPE_UP_NEXT, settings.swipeUpToNextPage)
                        putExtra(AutoFillSupport.EXTRA_TAP_DELAY, settings.tapDelayMs)
                        putExtra(AutoFillSupport.EXTRA_PALETTE_DELAY, settings.paletteDelayMs)
                        putExtra(AutoFillSupport.EXTRA_SWIPE_DELAY, settings.swipeDelayMs)
                        putExtra(AutoFillSupport.EXTRA_COUNTDOWN, settings.countdownSeconds)
                    }
                )
                if (!AutoFillSupport.isAccessibilityServiceEnabled(context)) {
                    AutoFillSupport.notifyOpenSettings(context)
                    AutoFillSupport.openAccessibilitySettings(context)
                } else {
                    Toast.makeText(context, "请切回明日方舟界面，稍候将出现框选浮层", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关于") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("像素画助手", style = MaterialTheme.typography.titleMedium)
                Text(
                    "ArknightsPixelTool v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium
                )
                HorizontalDivider()
                Text(
                    "为《明日方舟》奇象巡展活动准备的 Android 像素画生成器，支持导入图片生成 24×24 ~ 64×64 像素画。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "玩家自制工具，与游戏官方无关；请勿用于任何违法用途。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                Text("GitHub：", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "github.com/yfj12341-bot/ArknightsPixelTool",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(GITHUB_URL)
                            )
                            context.startActivity(intent)
                        }
                        .padding(2.dp)
                )
                HorizontalDivider()
                Text("更新日志", style = MaterialTheme.typography.titleSmall)
                Text(
                    "v0.2.0",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "• 新增自动填充：框选游戏内画布与调色盘位置后自动点击填充",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "• 提供非常快 / 快 / 中等 / 慢四档填充速度",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "v0.1.0",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "• 初始版本：图片转像素画、调色板、手动编辑与 PNG 分享",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("确定") }
        }
    )
}
@Composable
private fun SourceCropPanel(
    image: RgbImage,
    previewImage: RgbImage?,
    cropStartX: Float,
    cropStartY: Float,
    cropSide: Float,
    onCropChange: (Float, Float, Float) -> Unit,
    onPick: () -> Unit
) {
    val drawImage = previewImage ?: image
    val bitmap = remember(drawImage) { drawImage.toBitmap() }
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val maxSide = minOf(image.width, image.height)
    val currentX by rememberUpdatedState(cropStartX)
    val currentY by rememberUpdatedState(cropStartY)
    val currentSide by rememberUpdatedState(cropSide)

    Card {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "裁剪区域 1:1",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall
                )
                TextButton(onClick = onPick) {
                    Text("重新选图")
                }
            }
            Text(
                text = "${image.width}×${image.height}，裁剪 " +
                    "${currentSide.roundToInt()}×${currentSide.roundToInt()}，双指缩放",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF10141B))
                    .clipToBounds()
                    .pointerInput(image.width, image.height) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val oldSide = currentSide.coerceIn(1f, maxSide.toFloat())
                            val newSide = (oldSide / zoom).coerceIn(1f, maxSide.toFloat())
                            val oldScale = size.width.toFloat() / oldSide
                            val newScale = size.width.toFloat() / newSide
                            val anchorImageX = currentX + centroid.x / oldScale
                            val anchorImageY = currentY + centroid.y / oldScale
                            val maxLeft = (image.width - newSide).coerceAtLeast(0f)
                            val maxTop = (image.height - newSide).coerceAtLeast(0f)
                            val nextX = (anchorImageX - (centroid.x + pan.x) / newScale)
                                .coerceIn(0f, maxLeft)
                            val nextY = (anchorImageY - (centroid.y + pan.y) / newScale)
                                .coerceIn(0f, maxTop)
                            onCropChange(nextX, nextY, newSide)
                        }
                    }
            ) {
                val scale = size.width / currentSide.coerceIn(1f, maxSide.toFloat())
                val contentWidth = image.width * scale
                val contentHeight = image.height * scale
                val panX = -currentX * scale
                val panY = -currentY * scale
                drawImage(
                    image = imageBitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(drawImage.width, drawImage.height),
                    dstOffset = IntOffset(panX.roundToInt(), panY.roundToInt()),
                    dstSize = IntSize(contentWidth.roundToInt(), contentHeight.roundToInt())
                )
                val frame = Stroke(2.dp.toPx())
                drawRect(
                    color = Color(0xFFFFFFFF),
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height),
                    style = frame
                )
            }
        }
    }
}

@Composable
private fun ImageAdjustPanel(
    rotationDegrees: Float,
    brightness: Float,
    contrast: Float,
    enabled: Boolean,
    adjusting: Boolean,
    onAdjust: (Float, Float, Float) -> Unit,
    onAdjustFinished: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "图片调整",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall
                )
                if (adjusting) {
                    Text(
                        text = "处理中",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(
                    onClick = {
                        onAdjust(0f, 0f, 0f)
                        onAdjustFinished()
                    },
                    enabled = enabled && !adjusting
                ) {
                    Text("重置")
                }
            }

            AdjustSlider(
                label = "旋转",
                value = rotationDegrees,
                valueRange = 0f..360f,
                enabled = enabled,
                onValueChange = { onAdjust(it, brightness, contrast) },
                onValueChangeFinished = onAdjustFinished
            )

            AdjustSlider(
                label = "亮度",
                value = brightness,
                valueRange = -100f..100f,
                enabled = enabled,
                onValueChange = {
                    onAdjust(rotationDegrees, it, contrast)
                },
                onValueChangeFinished = onAdjustFinished
            )
            AdjustSlider(
                label = "对比度",
                value = contrast,
                valueRange = -100f..100f,
                enabled = enabled,
                onValueChange = {
                    onAdjust(rotationDegrees, brightness, it)
                },
                onValueChangeFinished = onAdjustFinished
            )
        }
    }
}

@Composable
private fun AdjustSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.width(56.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value.roundToInt().toString(),
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.End,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SettingsSection(
    paletteMode: PaletteMode,
    onPaletteMode: (PaletteMode) -> Unit,
    gridSize: Int,
    onGridSizeChange: (Int) -> Unit,
    maxColors: Int,
    onMaxColorsChange: (Int) -> Unit,
    downscaleMode: DownsampleMode,
    onDownscaleMode: (DownsampleMode) -> Unit,
    ditherEnabled: Boolean,
    onDitherChange: (Boolean) -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("颜色模式", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip(
                    selected = paletteMode == PaletteMode.AUTO,
                    label = "自动 40 色",
                    onClick = { onPaletteMode(PaletteMode.AUTO) }
                )
                ModeChip(
                    selected = paletteMode == PaletteMode.FIXED,
                    label = "固定调色板",
                    onClick = { onPaletteMode(PaletteMode.FIXED) }
                )
                ModeChip(
                    selected = paletteMode == PaletteMode.CUSTOM,
                    label = "自定义",
                    onClick = { onPaletteMode(PaletteMode.CUSTOM) }
                )
            }
            if (paletteMode == PaletteMode.FIXED) {
                Text(
                    "当前使用活动 40 色，编号 1-40，顺序固定不调整",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (paletteMode == PaletteMode.CUSTOM) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("画幅", modifier = Modifier.weight(1f))
                    Text("${gridSize}×${gridSize}")
                }
                Slider(
                    value = gridSize.toFloat(),
                    onValueChange = { onGridSizeChange((it.roundToInt() / 2) * 2) },
                    valueRange = 24f..64f,
                    steps = 19,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("颜色上限", modifier = Modifier.weight(1f))
                    Text("$maxColors")
                }
                Slider(
                    value = maxColors.toFloat(),
                    onValueChange = { onMaxColorsChange(it.roundToInt()) },
                    valueRange = 1f..256f,
                    steps = 254,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "自定义模式按所选颜色上限自动取色",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text("降采样", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = downscaleMode == DownsampleMode.AVERAGE,
                    onClick = { onDownscaleMode(DownsampleMode.AVERAGE) },
                    label = { Text("平滑平均") }
                )
                FilterChip(
                    selected = downscaleMode == DownsampleMode.DOMINANT,
                    onClick = { onDownscaleMode(DownsampleMode.DOMINANT) },
                    label = { Text("主体色") }
                )
                FilterChip(
                    selected = downscaleMode == DownsampleMode.BOX,
                    onClick = { onDownscaleMode(DownsampleMode.BOX) },
                    label = { Text("Box 平均") }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (paletteMode == PaletteMode.FIXED) 0f else 1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("抖动", modifier = Modifier.weight(1f))
                Switch(
                    checked = ditherEnabled,
                    onCheckedChange = onDitherChange,
                    enabled = paletteMode != PaletteMode.FIXED
                )
            }
        }
    }
}

@Composable
private fun ModeChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun ResultSection(
    art: PixelArtResult,
    paletteMode: PaletteMode,
    editMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    showColorNumbers: Boolean,
    onShowColorNumbersChange: (Boolean) -> Unit,
    selectedCell: Pair<Int, Int>?,
    onSelectCell: (Pair<Int, Int>) -> Unit,
    brushColor: Int,
    onBrushColor: (Int) -> Unit,
    onEditPixel: (Int, Int) -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    canRedo: Boolean,
    onRedo: () -> Unit,
    onSharePng: () -> Unit,
    onAutoFillRequest: () -> Unit
) {
    var showGrid by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "生成结果：${art.gridSize}×${art.gridSize} · ${art.palette.size} 色",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("编辑")
                        Switch(checked = editMode, onCheckedChange = onEditModeChange)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("网格")
                        Switch(checked = showGrid, onCheckedChange = { showGrid = it })
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("色号")
                        Switch(
                            checked = showColorNumbers,
                            onCheckedChange = onShowColorNumbersChange
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onUndo,
                        enabled = canUndo,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("撤销")
                    }
                    OutlinedButton(
                        onClick = onRedo,
                        enabled = canRedo,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("重做")
                    }
                }
                PixelGridPreview(
                    grid = art.preview,
                    palette = art.palette,
                    showNumbers = showColorNumbers,
                    showGrid = showGrid,
                    selected = selectedCell,
                    onCellClick = { cell ->
                        if (editMode) onEditPixel(cell.first, cell.second)
                        else onSelectCell(cell)
                    }
                )
                selectedCell?.let { (x, y) ->
                    val index = art.indices[y * art.gridSize + x]
                    Text(
                        "选中 $x,$y → 色号 ${index + 1}  ${ColorMath.toHex(art.palette.colors[index])}",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("当前画笔", modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(brushColor))
                            .border(
                                width = 1.dp,
                                color = Color(0x55000000),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
                PaletteStrip(
                    palette = art.palette,
                    brushColor = brushColor,
                    onSelect = onBrushColor
                )
                if (paletteMode != PaletteMode.FIXED) {
                    CustomColorPicker(
                        selectedColor = brushColor,
                        onColorChange = onBrushColor
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (paletteMode == PaletteMode.FIXED) {
                OutlinedButton(onClick = onAutoFillRequest, modifier = Modifier.weight(1f)) {
                    Text("自动填充到游戏")
                }
            }
            OutlinedButton(onClick = onSharePng, modifier = Modifier.weight(1f)) {
                Text("分享 PNG")
            }
        }
    }
}

@Composable
private fun PixelGridPreview(
    grid: RgbImage,
    palette: Palette,
    showNumbers: Boolean,
    showGrid: Boolean,
    selected: Pair<Int, Int>?,
    onCellClick: (Pair<Int, Int>) -> Unit
) {
    var zoom by remember(grid.width, grid.height) { mutableStateOf(1f) }
    var pan by remember(grid.width, grid.height) { mutableStateOf(Offset.Zero) }
    val currentZoom by rememberUpdatedState(zoom)
    val currentPan by rememberUpdatedState(pan)
    val currentOnCellClick by rememberUpdatedState(onCellClick)
    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
        }
    }
    val previewBitmap = remember(grid) {
        Bitmap.createBitmap(grid.width, grid.height, Bitmap.Config.ARGB_8888).apply {
            setPixels(grid.pixels, 0, grid.width, 0, 0, grid.width, grid.height)
        }
    }
    val previewImage = remember(previewBitmap) { previewBitmap.asImageBitmap() }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .clipToBounds()
            .pointerInput(grid.width, grid.height) {
                detectTapGestures { offset ->
                    val viewport = Size(size.width.toFloat(), size.height.toFloat())
                    val baseCell = viewport.minDimension / grid.width
                    val cellSize = baseCell * currentZoom
                    val contentWidth = cellSize * grid.width
                    val contentHeight = cellSize * grid.height
                    val panX = currentPan.x
                    val panY = currentPan.y
                    val localX = offset.x - panX
                    val localY = offset.y - panY
                    if (localX < 0f || localY < 0f ||
                        localX >= contentWidth || localY >= contentHeight
                    ) {
                        return@detectTapGestures
                    }
                    val x = (localX / cellSize).toInt().coerceIn(0, grid.width - 1)
                    val y = (localY / cellSize).toInt().coerceIn(0, grid.height - 1)
                    currentOnCellClick(x to y)
                }
            }
            .pointerInput(grid.width, grid.height) {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    val oldZoom = currentZoom
                    val newZoom = (oldZoom * zoomChange).coerceIn(1f, 8f)
                    val viewportWidth = size.width.toFloat()
                    val viewportHeight = size.height.toFloat()
                    val oldContentWidth = viewportWidth * oldZoom
                    val oldContentHeight = viewportHeight * oldZoom
                    val newContentWidth = viewportWidth * newZoom
                    val newContentHeight = viewportHeight * newZoom
                    pan = Offset(
                        x = zoomAnchoredPan(
                            currentPan.x, viewportWidth, oldContentWidth,
                            newContentWidth, centroid.x, panChange.x
                        ),
                        y = zoomAnchoredPan(
                            currentPan.y, viewportHeight, oldContentHeight,
                            newContentHeight, centroid.y, panChange.y
                        )
                    )
                    zoom = newZoom
                }
            }
    ) {
        val baseCell = size.minDimension / grid.width
        val cellSize = baseCell * zoom
        val contentWidth = cellSize * grid.width
        val contentHeight = cellSize * grid.height
        val panX = pan.x
        val panY = pan.y

        drawRect(color = Color(0xFF0E0E12))
        drawImage(
            image = previewImage,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(grid.width, grid.height),
            dstOffset = IntOffset(panX.roundToInt(), panY.roundToInt()),
            dstSize = IntSize(contentWidth.roundToInt(), contentHeight.roundToInt()),
            filterQuality = FilterQuality.None
        )
        if (showNumbers) {
            for (y in 0 until grid.height) {
                for (x in 0 until grid.width) {
                    val left = panX + x * cellSize
                    val top = panY + y * cellSize
                    if (left + cellSize < 0f || left > size.width) continue
                    if (top + cellSize < 0f || top > size.height) continue
                    val color = grid.pixels[y * grid.width + x]
                    val index = palette.colors.indexOf(color)
                    if (index >= 0) {
                        textPaint.textSize = (cellSize * 0.36f).coerceAtLeast(6.sp.toPx())
                        val luma = ColorMath.red(color) * 299 +
                            ColorMath.green(color) * 587 +
                            ColorMath.blue(color) * 114
                        textPaint.color = if (luma > 150_000) {
                            0xCC000000.toInt()
                        } else {
                            0xCCFFFFFF.toInt()
                        }
                        val centerX = left + cellSize / 2f
                        val baseline = top + cellSize / 2f -
                            (textPaint.ascent() + textPaint.descent()) / 2f
                        drawContext.canvas.nativeCanvas.drawText(
                            (index + 1).toString(),
                            centerX,
                            baseline,
                            textPaint
                        )
                    }
                }
            }
        }
        if (showGrid && (grid.width > 8 || zoom > 1f)) {
            for (i in 0..grid.width) {
                val x = panX + i * cellSize
                drawLine(
                    color = Color(0x33000000),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
            }
            for (j in 0..grid.height) {
                val y = panY + j * cellSize
                drawLine(
                    color = Color(0x33000000),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }
            val centerX = panX + (grid.width / 2f) * cellSize
            val centerY = panY + (grid.height / 2f) * cellSize
            drawLine(
                color = Color(0xCC000000),
                start = Offset(centerX, 0f),
                end = Offset(centerX, size.height),
                strokeWidth = 3.dp.toPx()
            )
            drawLine(
                color = Color(0xCC000000),
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = 3.dp.toPx()
            )
        }
        selected?.let { (x, y) ->
            drawRect(
                color = Color.White,
                topLeft = Offset(panX + x * cellSize, panY + y * cellSize),
                size = Size(cellSize, cellSize),
                style = Stroke(2.dp.toPx())
            )
        }
    }
}

private fun clampPanOffset(value: Float, viewport: Float, content: Float): Float =
    if (content <= viewport) 0f else value.coerceIn(viewport - content, 0f)

private fun RgbImage.scaledToMaxSide(maxSide: Int): RgbImage {
    if (maxOf(width, height) <= maxSide) return this
    val scale = maxSide.toFloat() / maxOf(width, height)
    val previewWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val previewHeight = (height * scale).roundToInt().coerceAtLeast(1)
    val source = toBitmap()
    val scaled = Bitmap.createScaledBitmap(source, previewWidth, previewHeight, true)
    source.recycle()
    val pixels = IntArray(scaled.width * scaled.height)
    scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
    scaled.recycle()
    return RgbImage(previewWidth, previewHeight, pixels)
}

private fun zoomAnchoredPan(
    panValue: Float,
    viewport: Float,
    oldContent: Float,
    newContent: Float,
    centroid: Float,
    panChange: Float
): Float {
    val anchorInContent = centroid - panValue
    val newTopLeft = centroid + panChange - anchorInContent * (newContent / oldContent)
    return clampPanOffset(newTopLeft, viewport, newContent)
}

private fun colorToHsl(color: Int): FloatArray {
    val r = ColorMath.red(color) / 255f
    val g = ColorMath.green(color) / 255f
    val b = ColorMath.blue(color) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    val delta = max - min
    val h = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    val s = if (delta == 0f) {
        0f
    } else {
        delta / (1f - kotlin.math.abs(2f * l - 1f))
    }
    return floatArrayOf(
        if (h < 0f) h + 360f else h,
        s.coerceIn(0f, 1f),
        l
    )
}

private fun hslToColor(h: Float, s: Float, l: Float): Int {
    val hue = ((h % 360f) + 360f) % 360f
    val saturation = s.coerceIn(0f, 1f)
    val lightness = l.coerceIn(0f, 1f)
    val c = (1f - kotlin.math.abs(2f * lightness - 1f)) * saturation
    val x = c * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
    val m = lightness - c / 2f
    val rgb = when {
        hue < 60f -> floatArrayOf(c, x, 0f)
        hue < 120f -> floatArrayOf(x, c, 0f)
        hue < 180f -> floatArrayOf(0f, c, x)
        hue < 240f -> floatArrayOf(0f, x, c)
        hue < 300f -> floatArrayOf(x, 0f, c)
        else -> floatArrayOf(c, 0f, x)
    }
    return ColorMath.argb(
        ((rgb[0] + m) * 255f).roundToInt(),
        ((rgb[1] + m) * 255f).roundToInt(),
        ((rgb[2] + m) * 255f).roundToInt()
    )
}

@Composable
private fun PaletteStrip(
    palette: Palette,
    brushColor: Int,
    onSelect: (Int) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(palette.colors) { index, color ->
            val selected = color == brushColor
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(color))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color(0x55000000)
                            },
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { onSelect(color) }
                )
                Text(text = "${index + 1}", fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun CustomColorPicker(
    selectedColor: Int,
    onColorChange: (Int) -> Unit
) {
    var useHsl by remember { mutableStateOf(false) }
    var r by remember(selectedColor) { mutableStateOf(ColorMath.red(selectedColor)) }
    var g by remember(selectedColor) { mutableStateOf(ColorMath.green(selectedColor)) }
    var b by remember(selectedColor) { mutableStateOf(ColorMath.blue(selectedColor)) }
    var hue by remember { mutableStateOf(0f) }
    var saturation by remember { mutableStateOf(0f) }
    var lightness by remember { mutableStateOf(0f) }
    var lastEmitted by remember { mutableStateOf<Int?>(null) }

    fun syncHslFromColor(color: Int) {
        val hsl = colorToHsl(color)
        hue = hsl[0]
        saturation = hsl[1] * 255f
        lightness = hsl[2] * 255f
    }

    LaunchedEffect(selectedColor) {
        if (lastEmitted != selectedColor) {
            syncHslFromColor(selectedColor)
        }
    }

    fun emit() {
        val color = ColorMath.argb(r, g, b)
        lastEmitted = color
        syncHslFromColor(color)
        onColorChange(color)
    }

    fun emitHsl() {
        val color = hslToColor(hue, saturation / 255f, lightness / 255f)
        lastEmitted = color
        onColorChange(color)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("自定义颜色", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !useHsl,
                onClick = { useHsl = false },
                label = { Text("RGB") }
            )
            FilterChip(
                selected = useHsl,
                onClick = { useHsl = true },
                label = { Text("HSL") }
            )
        }
        if (useHsl) {
            ChannelSlider(
                label = "色相",
                value = hue.roundToInt(),
                valueRange = 0f..360f
            ) {
                hue = it.toFloat()
                emitHsl()
            }
            ChannelSlider(label = "饱和度", value = saturation.roundToInt()) {
                saturation = it.toFloat()
                emitHsl()
            }
            ChannelSlider(label = "明暗", value = lightness.roundToInt()) {
                lightness = it.toFloat()
                emitHsl()
            }
        } else {
            ChannelSlider(label = "R", value = r) {
                r = it
                emit()
            }
            ChannelSlider(label = "G", value = g) {
                g = it
                emit()
            }
            ChannelSlider(label = "B", value = b) {
                b = it
                emit()
            }
        }
    }
}

@Composable
private fun ChannelSlider(
    label: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float> = 0f..255f,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.width(24.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = valueRange,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value.toString(),
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun EmptyState(onPick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "选择一张图片，裁剪为 1:1 后生成 24×24、最多 40 色的像素画",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            OutlinedButton(onClick = onPick) {
                Text("选择图片")
            }
        }
    }
}

@Composable
private fun AutoFillSetupDialog(
    onDismiss: () -> Unit,
    onConfirm: (AutoFillSettings) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("autofill", Context.MODE_PRIVATE)
    }
    var showAdvanced by remember { mutableStateOf(false) }
    var selectedPreset by remember {
        mutableStateOf(
            runCatching {
                FillSpeedPreset.valueOf(
                    prefs.getString("speed_preset", FillSpeedPreset.MEDIUM.name)
                        ?: FillSpeedPreset.MEDIUM.name
                )
            }.getOrDefault(FillSpeedPreset.MEDIUM)
        )
    }

    fun choosePreset(preset: FillSpeedPreset) {
        selectedPreset = preset
        prefs.edit().putString("speed_preset", preset.name).apply()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自动填充到明日方舟") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "仅支持固定调色板生成的 24×24 像素画。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "点击「准备填充」后请在 5 秒内切换到明日方舟，倒计时结束后自动弹出框选界面。",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "填充期间请勿触碰屏幕，左上角有「中止填充」按钮可随时停止。",
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(if (showAdvanced) "收起高级选项" else "高级选项")
                }
                if (showAdvanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("填充速度", style = MaterialTheme.typography.labelLarge)
                        FillSpeedPreset.values().toList().chunked(2).forEach { rowPresets ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowPresets.forEach { preset ->
                                    FilterChip(
                                        selected = preset == selectedPreset,
                                        onClick = { choosePreset(preset) },
                                        label = { Text(preset.label) }
                                    )
                                }
                            }
                        }
                        Text(
                            "更快的填充速度可能导致误触，请按实际情况选择。",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        AutoFillSettings(
                            tapDelayMs = selectedPreset.tapDelayMs,
                            paletteDelayMs = selectedPreset.paletteDelayMs,
                            swipeDelayMs = selectedPreset.swipeDelayMs
                        )
                    )
                }
            ) { Text("准备填充") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
