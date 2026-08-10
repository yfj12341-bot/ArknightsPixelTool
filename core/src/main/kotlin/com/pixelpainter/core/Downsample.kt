package com.pixelpainter.core

enum class DownsampleMode {
    /** Smooth area average; good for gradients. */
    AVERAGE,

    /** Majority vote over palette-mapped pixels; keeps thin lines and edges. */
    DOMINANT,

    /** Pillow-style BOX resampling: area-weighted average without edge enhancement. */
    BOX
}

object Downsample {

    private const val MAX_WORK_SIDE = 512

    fun resize(
        source: RgbImage,
        targetSize: Int = 24,
        mode: DownsampleMode = DownsampleMode.BOX,
        referencePalette: Palette? = null
    ): RgbImage {
        require(targetSize > 0) { "targetSize must be positive" }

        if (mode == DownsampleMode.AVERAGE) {
            return areaAverage(source, targetSize, targetSize)
        }
        if (mode == DownsampleMode.BOX) {
            return boxAverage(source, targetSize, targetSize)
        }

        val palette = referencePalette ?: AutoPalette.extract(source, 40)
        require(palette.colors.isNotEmpty()) { "Palette is empty" }
        val labCurve = palette.colors.map { ColorMath.srgbToLab(it) }

        val workSide = maxOf(
            targetSize,
            minOf(MAX_WORK_SIDE, maxOf(source.width, source.height))
        )
        val work = areaAverage(source, workSide, workSide)
        val mapped = IntArray(work.pixels.size) { i ->
            nearestPaletteIndex(work.pixels[i], palette.colors, labCurve)
        }
        val average = areaAverage(source, targetSize, targetSize)
        return blockDominant(work, mapped, average, targetSize, palette, labCurve)
    }

    private fun blockDominant(
        work: RgbImage,
        mapped: IntArray,
        average: RgbImage,
        targetSize: Int,
        palette: Palette,
        labCurve: List<FloatArray>
    ): RgbImage {
        val out = IntArray(targetSize * targetSize)
        val counts = IntArray(palette.size)
        for (ty in 0 until targetSize) {
            val yRange = sourceRange(ty, work.height, targetSize)
            for (tx in 0 until targetSize) {
                val xRange = sourceRange(tx, work.width, targetSize)
                var best = 0
                var tied = false
                for (y in yRange) {
                    val row = y * work.width
                    for (x in xRange) {
                        val index = mapped[row + x]
                        counts[index]++
                        if (counts[index] > counts[best]) {
                            best = index
                            tied = false
                        } else if (index != best && counts[index] == counts[best]) {
                            tied = true
                        }
                    }
                }
                out[ty * targetSize + tx] = if (tied) {
                    palette.colors[nearestPaletteIndex(
                        average.pixels[ty * targetSize + tx],
                        palette.colors,
                        labCurve
                    )]
                } else {
                    palette.colors[best]
                }
                counts.fill(0)
            }
        }
        return RgbImage(targetSize, targetSize, out)
    }

    private fun nearestPaletteIndex(
        color: Int,
        paletteColors: List<Int>,
        labCurve: List<FloatArray>
    ): Int {
        val lab = ColorMath.srgbToLab(color)
        var best = 0
        var bestDistance = Float.MAX_VALUE
        for (i in labCurve.indices) {
            val distance = ColorMath.labDeltaE(lab, labCurve[i])
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return best
    }

    fun areaAverage(
        source: RgbImage,
        targetWidth: Int,
        targetHeight: Int
    ): RgbImage {
        require(targetWidth > 0 && targetHeight > 0) { "Target dimensions must be positive" }
        val out = IntArray(targetWidth * targetHeight)
        for (ty in 0 until targetHeight) {
            val yRange = sourceRange(ty, source.height, targetHeight)
            for (tx in 0 until targetWidth) {
                val xRange = sourceRange(tx, source.width, targetWidth)
                var r = 0L
                var g = 0L
                var b = 0L
                for (y in yRange) {
                    val row = y * source.width
                    for (x in xRange) {
                        val c = source.pixels[row + x]
                        r += ColorMath.red(c)
                        g += ColorMath.green(c)
                        b += ColorMath.blue(c)
                    }
                }
                val count = yRange.count().toLong() * xRange.count()
                out[ty * targetWidth + tx] = ColorMath.argb(
                    (r / count).toInt(),
                    (g / count).toInt(),
                    (b / count).toInt()
                )
            }
        }
        return RgbImage(targetWidth, targetHeight, out)
    }

    fun boxAverage(
        source: RgbImage,
        targetWidth: Int,
        targetHeight: Int
    ): RgbImage {
        require(targetWidth > 0 && targetHeight > 0) { "Target dimensions must be positive" }
        val out = IntArray(targetWidth * targetHeight)
        val scaleX = source.width.toDouble() / targetWidth
        val scaleY = source.height.toDouble() / targetHeight

        val rowStart = IntArray(targetHeight)
        val rowEnd = IntArray(targetHeight)
        val rowWeight = DoubleArray(targetHeight)
        for (ty in 0 until targetHeight) {
            val start = ty * scaleY
            val end = (ty + 1) * scaleY
            rowStart[ty] = kotlin.math.floor(start).toInt().coerceIn(0, source.height - 1)
            rowEnd[ty] = kotlin.math.ceil(end).toInt().coerceIn(0, source.height - 1)
            rowWeight[ty] = kotlin.math.min(end, source.height.toDouble()) -
                kotlin.math.max(start, 0.0)
        }

        val colStart = IntArray(targetWidth)
        val colEnd = IntArray(targetWidth)
        val colWeight = DoubleArray(targetWidth)
        for (tx in 0 until targetWidth) {
            val start = tx * scaleX
            val end = (tx + 1) * scaleX
            colStart[tx] = kotlin.math.floor(start).toInt().coerceIn(0, source.width - 1)
            colEnd[tx] = kotlin.math.ceil(end).toInt().coerceIn(0, source.width - 1)
            colWeight[tx] = kotlin.math.min(end, source.width.toDouble()) -
                kotlin.math.max(start, 0.0)
        }

        for (ty in 0 until targetHeight) {
            for (tx in 0 until targetWidth) {
                var r = 0.0
                var g = 0.0
                var b = 0.0
                for (y in rowStart[ty]..rowEnd[ty]) {
                    val fy = kotlin.math.min((ty + 1) * scaleY, (y + 1).toDouble()) -
                        kotlin.math.max(ty * scaleY, y.toDouble())
                    val row = y * source.width
                    for (x in colStart[tx]..colEnd[tx]) {
                        val fx = kotlin.math.min((tx + 1) * scaleX, (x + 1).toDouble()) -
                            kotlin.math.max(tx * scaleX, x.toDouble())
                        val cellWeight = fx * fy
                        val c = source.pixels[row + x]
                        r += ColorMath.red(c) * cellWeight
                        g += ColorMath.green(c) * cellWeight
                        b += ColorMath.blue(c) * cellWeight
                    }
                }
                val totalWeight = rowWeight[ty] * colWeight[tx]
                out[ty * targetWidth + tx] = ColorMath.argb(
                    (r / totalWeight).toInt(),
                    (g / totalWeight).toInt(),
                    (b / totalWeight).toInt()
                )
            }
        }
        return RgbImage(targetWidth, targetHeight, out)
    }

    private fun sourceRange(index: Int, sourceSize: Int, targetSize: Int): IntRange {
        val start = index * sourceSize / targetSize
        val end = ((index + 1) * sourceSize + targetSize - 1) / targetSize
        return start until end
    }
}
