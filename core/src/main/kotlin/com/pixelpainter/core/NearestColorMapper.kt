package com.pixelpainter.core

/**
 * Maps an image onto a palette, optionally with Floyd-Steinberg error
 * diffusion to fake gradients using only palette colors.
 */
object NearestColorMapper {

    fun mapToIndices(
        image: RgbImage,
        palette: Palette,
        dither: Boolean = false
    ): IntArray {
        require(palette.colors.isNotEmpty()) { "Palette cannot be empty" }
        val oklab = palette.colors.map { ColorMath.srgbToOklab(it) }
        val pixels = if (dither) image.pixels.copyOf() else image.pixels
        val out = IntArray(image.pixels.size)

        if (!dither) {
            for (i in pixels.indices) {
                out[i] = PaletteMatcher.nearestIndex(pixels[i], palette.colors, oklab)
            }
            return out
        }

        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val i = y * image.width + x
                val best = PaletteMatcher.nearestIndex(pixels[i], palette.colors, oklab)
                out[i] = best
                val chosen = palette.colors[best]
                val errR = ColorMath.red(pixels[i]) - ColorMath.red(chosen)
                val errG = ColorMath.green(pixels[i]) - ColorMath.green(chosen)
                val errB = ColorMath.blue(pixels[i]) - ColorMath.blue(chosen)

                diffuse(pixels, image.width, image.height, x, y, errR, errG, errB)
            }
        }
        return out
    }

    private fun diffuse(
        pixels: IntArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        errR: Int,
        errG: Int,
        errB: Int
    ) {
        fun add(px: Int, py: Int, weight: Int) {
            if (px !in 0 until width || py !in 0 until height) return
            val index = py * width + px
            val r = (ColorMath.red(pixels[index]) + errR * weight / 16).coerceIn(0, 255)
            val g = (ColorMath.green(pixels[index]) + errG * weight / 16).coerceIn(0, 255)
            val b = (ColorMath.blue(pixels[index]) + errB * weight / 16).coerceIn(0, 255)
            pixels[index] = ColorMath.argb(r, g, b)
        }

        add(x + 1, y, 7)
        add(x - 1, y + 1, 3)
        add(x, y + 1, 5)
        add(x + 1, y + 1, 1)
    }
}
