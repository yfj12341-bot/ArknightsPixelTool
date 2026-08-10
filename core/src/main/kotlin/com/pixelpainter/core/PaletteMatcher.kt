package com.pixelpainter.core

/** Plain OKLab nearest-color lookup used by final palette quantization. */
object PaletteMatcher {

    fun nearestIndex(color: Int, paletteColors: List<Int>): Int =
        nearestIndex(color, paletteColors, paletteColors.map { ColorMath.srgbToOklab(it) })

    fun nearestIndex(
        color: Int,
        paletteColors: List<Int>,
        oklabCurve: List<FloatArray>
    ): Int {
        val target = ColorMath.srgbToOklab(color)
        var best = 0
        var bestDistance = Float.MAX_VALUE
        for (i in oklabCurve.indices) {
            val distance = ColorMath.oklabDeltaE(target, oklabCurve[i])
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return best
    }
}
