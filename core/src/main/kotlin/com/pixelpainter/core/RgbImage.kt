package com.pixelpainter.core

class RgbImage(
    val width: Int,
    val height: Int,
    val pixels: IntArray
) {
    init {
        require(width > 0 && height > 0) { "Image dimensions must be positive" }
        require(pixels.size == width * height) {
            "Pixel count ${pixels.size} does not match ${width}x$height"
        }
    }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x]

    fun copy(): RgbImage = RgbImage(width, height, pixels.copyOf())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RgbImage) return false
        return width == other.width && height == other.height && pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + pixels.contentHashCode()
        return result
    }
}

fun RgbImage.centeredSquareCrop(): RgbImage {
    val side = minOf(width, height)
    val startX = (width - side) / 2
    val startY = (height - side) / 2
    return squareCrop(CropBounds(startX, startY, side))
}

/**
 * A square region in source pixel coordinates. Values are clamped when the
 * crop is applied, so callers may keep normalized UI state and let the crop
 * snap to the nearest valid position.
 */
data class CropBounds(
    val startX: Int,
    val startY: Int,
    val sidePixels: Int
) {
    init {
        require(sidePixels > 0) { "Crop side must be positive" }
    }
}

fun RgbImage.squareCrop(bounds: CropBounds): RgbImage {
    val side = bounds.sidePixels.coerceIn(1, minOf(width, height))
    val maxLeft = (width - side).coerceAtLeast(0)
    val maxTop = (height - side).coerceAtLeast(0)
    val startX = bounds.startX.coerceIn(0, maxLeft)
    val startY = bounds.startY.coerceIn(0, maxTop)
    val out = IntArray(side * side)
    for (y in 0 until side) {
        System.arraycopy(pixels, (startY + y) * width + startX, out, y * side, side)
    }
    return RgbImage(side, side, out)
}
