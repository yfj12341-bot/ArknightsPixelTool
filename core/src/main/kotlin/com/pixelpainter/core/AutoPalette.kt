package com.pixelpainter.core

/**
 * Builds an optimal small palette from the source image. Median cut provides
 * deterministic initial centers, then Lloyd iterations refine them in Lab
 * space. The result is capped at [maxColors].
 */
object AutoPalette {

    fun extract(source: RgbImage, maxColors: Int): Palette {
        require(maxColors > 0) { "maxColors must be positive" }
        val samples = samplePixels(source, sampleLimit = 8192)
        if (samples.isEmpty()) return Palette(emptyList())

        val distinct = samples.distinct()
        if (distinct.size <= maxColors) {
            return Palette(distinct.sortedBy { ColorMath.srgbToLab(it)[0] }, "自动")
        }

        var centers = medianCutCenters(samples, maxColors)
        for (iteration in 1..20) {
            val next = refineOnce(samples, centers)
            if (next.isEmpty()) break
            val changed = next.size != centers.size ||
                next.indices.any { it !in centers.indices || centers[it] != next[it] }
            centers = next
            if (!changed) break
        }

        return Palette(centers.distinct().sortedColors(), "自动")
    }

    private fun samplePixels(source: RgbImage, sampleLimit: Int): IntArray {
        val total = source.pixels.size
        if (total <= sampleLimit) return source.pixels.copyOf()
        val step = total.toDouble() / sampleLimit
        val out = IntArray(sampleLimit)
        for (i in 0 until sampleLimit) {
            out[i] = source.pixels[(i * step).toInt()]
        }
        return out
    }

    private fun medianCutCenters(samples: IntArray, maxColors: Int): List<Int> {
        val buckets = mutableListOf(samples.toList())
        while (buckets.size < maxColors) {
            val bucket = buckets.maxByOrNull { channelRange(it) } ?: break
            if (channelRange(bucket) == 0) break
            buckets.remove(bucket)
            if (buckets.size + 2 > maxColors) {
                buckets.add(bucket)
                break
            }
            val split = splitBucket(bucket)
            buckets.add(split.first)
            buckets.add(split.second)
        }
        return buckets.map { averageColor(it) }
    }

    private fun splitBucket(bucket: List<Int>): Pair<List<Int>, List<Int>> {
        data class Range(val channel: Int, val size: Int)
        val ranges = listOf(
            Range(0, bucket.maxOf { ColorMath.red(it) } - bucket.minOf { ColorMath.red(it) }),
            Range(1, bucket.maxOf { ColorMath.green(it) } - bucket.minOf { ColorMath.green(it) }),
            Range(2, bucket.maxOf { ColorMath.blue(it) } - bucket.minOf { ColorMath.blue(it) })
        )
        val channel = ranges.maxBy { it.size }.channel
        val sorted = when (channel) {
            0 -> bucket.sortedBy { ColorMath.red(it) }
            1 -> bucket.sortedBy { ColorMath.green(it) }
            else -> bucket.sortedBy { ColorMath.blue(it) }
        }
        val mid = sorted.size / 2
        return sorted.take(mid) to sorted.drop(mid)
    }

    private fun channelRange(bucket: List<Int>): Int {
        val r = bucket.maxOf { ColorMath.red(it) } - bucket.minOf { ColorMath.red(it) }
        val g = bucket.maxOf { ColorMath.green(it) } - bucket.minOf { ColorMath.green(it) }
        val b = bucket.maxOf { ColorMath.blue(it) } - bucket.minOf { ColorMath.blue(it) }
        return maxOf(r, g, b)
    }

    private fun refineOnce(samples: IntArray, centers: List<Int>): List<Int> {
        val lab = centers.map { ColorMath.srgbToLab(it) }
        val sums = Array(centers.size) { LongArray(3) }
        val counts = IntArray(centers.size)
        for (color in samples) {
            val target = ColorMath.srgbToLab(color)
            var best = 0
            var bestDistance = Float.MAX_VALUE
            for (i in centers.indices) {
                val distance = ColorMath.labDeltaE(target, lab[i])
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = i
                }
            }
            sums[best][0] += ColorMath.red(color)
            sums[best][1] += ColorMath.green(color)
            sums[best][2] += ColorMath.blue(color)
            counts[best]++
        }

        val next = mutableListOf<Int>()
        for (i in centers.indices) {
            if (counts[i] == 0) continue
            next += ColorMath.argb(
                (sums[i][0] / counts[i]).toInt(),
                (sums[i][1] / counts[i]).toInt(),
                (sums[i][2] / counts[i]).toInt()
            )
        }
        return next
    }

    private fun averageColor(colors: List<Int>): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        for (c in colors) {
            r += ColorMath.red(c)
            g += ColorMath.green(c)
            b += ColorMath.blue(c)
        }
        return ColorMath.argb(
            (r / colors.size).toInt(),
            (g / colors.size).toInt(),
            (b / colors.size).toInt()
        )
    }

    private fun List<Int>.sortedColors(): List<Int> = sortedByDescending { ColorMath.srgbToLab(it)[0] }
}
