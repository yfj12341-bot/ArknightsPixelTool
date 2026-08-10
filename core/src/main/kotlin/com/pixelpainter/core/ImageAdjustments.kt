package com.pixelpainter.core

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pure image adjustments used before the crop step. Rotation is discrete,
 * color adjustments are applied in RGB then HSV space.
 */
object ImageAdjustments {

    fun apply(
        source: RgbImage,
        rotationDegrees: Int = 0,
        brightness: Int = 0,
        contrast: Int = 0,
        hueDegrees: Int = 0,
        saturation: Int = 0
    ): RgbImage {
        val rotated = rotate(source, rotationDegrees)
        if (brightness == 0 && contrast == 0 && hueDegrees == 0 && saturation == 0) {
            return rotated
        }

        val brightnessDelta = brightness.toFloat()
        val contrastParam = contrast.toFloat()
        val contrastFactor =
            (259f * (contrastParam + 255f)) / (255f * (259f - contrastParam))
        val saturationFactor = (100 + saturation) / 100f
        val out = IntArray(rotated.pixels.size)

        for (i in rotated.pixels.indices) {
            var r = ColorMath.red(rotated.pixels[i]).toFloat() + brightnessDelta
            var g = ColorMath.green(rotated.pixels[i]).toFloat() + brightnessDelta
            var b = ColorMath.blue(rotated.pixels[i]).toFloat() + brightnessDelta

            if (contrast != 0) {
                r = contrastFactor * (r - 128f) + 128f
                g = contrastFactor * (g - 128f) + 128f
                b = contrastFactor * (b - 128f) + 128f
            }

            if (hueDegrees != 0 || saturation != 0) {
                val hsv = rgbToHsv(
                    r.coerceIn(0f, 255f),
                    g.coerceIn(0f, 255f),
                    b.coerceIn(0f, 255f)
                )
                if (saturation != 0) {
                    hsv[1] = (hsv[1] * saturationFactor).coerceIn(0f, 1f)
                }
                if (hueDegrees != 0) {
                    hsv[0] = (hsv[0] + hueDegrees).mod(360f)
                }
                val rgb = hsvToRgb(hsv[0], hsv[1], hsv[2])
                r = rgb[0]
                g = rgb[1]
                b = rgb[2]
            }

            out[i] = ColorMath.argb(r.roundToInt(), g.roundToInt(), b.roundToInt())
        }
        return RgbImage(rotated.width, rotated.height, out)
    }

    fun rotate(source: RgbImage, degrees: Int): RgbImage {
        val normalized = ((degrees % 360) + 360) % 360
        return when (normalized) {
            0 -> source.copy()
            90 -> rotate90(source)
            180 -> rotate180(source)
            270 -> rotate270(source)
            else -> rotateArbitrary(source, normalized)
        }
    }

    private fun rotate90(source: RgbImage): RgbImage {
        val out = IntArray(source.pixels.size)
        for (y in 0 until source.height) {
            val srcRow = y * source.width
            for (x in 0 until source.width) {
                val dstX = source.height - 1 - y
                val dstY = x
                out[dstY * source.height + dstX] = source.pixels[srcRow + x]
            }
        }
        return RgbImage(source.height, source.width, out)
    }

    private fun rotate180(source: RgbImage): RgbImage {
        val out = IntArray(source.pixels.size)
        for (y in 0 until source.height) {
            val srcRow = y * source.width
            val dstRow = (source.height - 1 - y) * source.width
            for (x in 0 until source.width) {
                out[dstRow + source.width - 1 - x] = source.pixels[srcRow + x]
            }
        }
        return RgbImage(source.width, source.height, out)
    }

    private fun rotate270(source: RgbImage): RgbImage {
        val out = IntArray(source.pixels.size)
        for (y in 0 until source.height) {
            val srcRow = y * source.width
            for (x in 0 until source.width) {
                val dstX = y
                val dstY = source.width - 1 - x
                out[dstY * source.height + dstX] = source.pixels[srcRow + x]
            }
        }
        return RgbImage(source.height, source.width, out)
    }

    private fun rotateArbitrary(source: RgbImage, degrees: Int): RgbImage {
        val radians = Math.toRadians(degrees.toDouble())
        val cosA = cos(radians)
        val sinA = sin(radians)
        val centerX = (source.width - 1) / 2.0
        val centerY = (source.height - 1) / 2.0
        val out = IntArray(source.pixels.size)
        for (y in 0 until source.height) {
            val destRow = y * source.width
            val dy = y - centerY
            for (x in 0 until source.width) {
                val dx = x - centerX
                val srcX = cosA * dx + sinA * dy + centerX
                val srcY = -sinA * dx + cosA * dy + centerY
                out[destRow + x] = bilinearSample(source, srcX, srcY)
            }
        }
        return RgbImage(source.width, source.height, out)
    }

    private fun bilinearSample(source: RgbImage, x: Double, y: Double): Int {
        if (x < 0.0 || y < 0.0 || x > source.width - 1 || y > source.height - 1) {
            return ColorMath.argb(0, 0, 0)
        }
        val x0 = floor(x).toInt().coerceIn(0, source.width - 1)
        val y0 = floor(y).toInt().coerceIn(0, source.height - 1)
        val x1 = (x0 + 1).coerceAtMost(source.width - 1)
        val y1 = (y0 + 1).coerceAtMost(source.height - 1)
        val fx = (x - x0).toFloat().coerceIn(0f, 1f)
        val fy = (y - y0).toFloat().coerceIn(0f, 1f)
        val r = interpolateChannel(
            ColorMath.red(source[x0, y0]),
            ColorMath.red(source[x1, y0]),
            ColorMath.red(source[x0, y1]),
            ColorMath.red(source[x1, y1]),
            fx,
            fy
        )
        val g = interpolateChannel(
            ColorMath.green(source[x0, y0]),
            ColorMath.green(source[x1, y0]),
            ColorMath.green(source[x0, y1]),
            ColorMath.green(source[x1, y1]),
            fx,
            fy
        )
        val b = interpolateChannel(
            ColorMath.blue(source[x0, y0]),
            ColorMath.blue(source[x1, y0]),
            ColorMath.blue(source[x0, y1]),
            ColorMath.blue(source[x1, y1]),
            fx,
            fy
        )
        return ColorMath.argb(r, g, b)
    }

    private fun interpolateChannel(
        topLeft: Int,
        topRight: Int,
        bottomLeft: Int,
        bottomRight: Int,
        fx: Float,
        fy: Float
    ): Int {
        val top = topLeft + (topRight - topLeft) * fx
        val bottom = bottomLeft + (bottomRight - bottomLeft) * fx
        return (top + (bottom - top) * fy).roundToInt()
    }

    private fun rgbToHsv(r: Float, g: Float, b: Float): FloatArray {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val v = max
        val s = if (max == 0f) 0f else delta / max
        var h = 0f
        if (delta > 0f) {
            when (max) {
                r -> h = 60f * (((g - b) / delta) % 6f)
                g -> h = 60f * (((b - r) / delta) + 2f)
                else -> h = 60f * (((r - g) / delta) + 4f)
            }
            if (h < 0f) h += 360f
        }
        return floatArrayOf(h, s, v)
    }

    private fun hsvToRgb(h: Float, s: Float, v: Float): FloatArray {
        val c = v * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = v - c
        val rgb = when {
            h < 60f -> floatArrayOf(c, x, 0f)
            h < 120f -> floatArrayOf(x, c, 0f)
            h < 180f -> floatArrayOf(0f, c, x)
            h < 240f -> floatArrayOf(0f, x, c)
            h < 300f -> floatArrayOf(x, 0f, c)
            else -> floatArrayOf(c, 0f, x)
        }
        return floatArrayOf(
            (rgb[0] + m) * 255f,
            (rgb[1] + m) * 255f,
            (rgb[2] + m) * 255f
        )
    }
}
