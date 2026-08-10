package com.pixelpainter.core

import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Color helpers used across the conversion pipeline. All pixel values are
 * stored as 32-bit ARGB ints; alpha is ignored during color distance math.
 */
object ColorMath {

    fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or
            (r.coerceIn(0, 255) shl 16) or
            (g.coerceIn(0, 255) shl 8) or
            b.coerceIn(0, 255)

    fun red(c: Int): Int = (c shr 16) and 0xFF

    fun green(c: Int): Int = (c shr 8) and 0xFF

    fun blue(c: Int): Int = c and 0xFF

    fun toHex(c: Int): String = "#%06X".format(c and 0xFFFFFF)

    fun fromHex(hex: String): Int {
        val cleaned = hex.removePrefix("#").trim()
        require(cleaned.length == 6) { "Palette colors must be #RRGGBB, got: $hex" }
        val value = cleaned.toInt(16)
        return (0xFF shl 24) or value
    }

    private fun srgbToLinear(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    fun srgbToLab(c: Int): FloatArray {
        val r = srgbToLinear(red(c))
        val g = srgbToLinear(green(c))
        val b = srgbToLinear(blue(c))

        val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
        val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
        val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883

        return floatArrayOf(
            116f * labF(y).toFloat() - 16f,
            500f * (labF(x) - labF(y)).toFloat(),
            200f * (labF(y) - labF(z)).toFloat()
        )
    }

    private fun labF(t: Double): Double {
        val epsilon = 216.0 / 24389.0
        val kappa = 24389.0 / 27.0
        return if (t > epsilon) cbrt(t) else (kappa * t + 16.0) / 116.0
    }

    fun srgbToOklab(c: Int): FloatArray {
        val r = srgbToLinear(red(c))
        val g = srgbToLinear(green(c))
        val b = srgbToLinear(blue(c))

        val l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
        val m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
        val s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b

        val lRoot = cbrt(l)
        val mRoot = cbrt(m)
        val sRoot = cbrt(s)

        return floatArrayOf(
            (0.2104542553 * lRoot + 0.7936177850 * mRoot - 0.0040720468 * sRoot).toFloat(),
            (1.9779984951 * lRoot - 2.4285922050 * mRoot + 0.4505937099 * sRoot).toFloat(),
            (0.0259040371 * lRoot + 0.7827717662 * mRoot - 0.8086757660 * sRoot).toFloat()
        )
    }

    fun labDeltaE(a: FloatArray, b: FloatArray): Float {
        val dl = a[0] - b[0]
        val da = a[1] - b[1]
        val db = a[2] - b[2]
        return sqrt((dl * dl + da * da + db * db).toDouble()).toFloat()
    }

    fun oklabDeltaE(a: FloatArray, b: FloatArray): Float {
        val dl = a[0] - b[0]
        val da = a[1] - b[1]
        val db = a[2] - b[2]
        return (dl * dl + da * da + db * db).toFloat()
    }

}
