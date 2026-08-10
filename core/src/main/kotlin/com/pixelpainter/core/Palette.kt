package com.pixelpainter.core

/**
 * A palette is a fixed, ordered list of colors. Index 0..n-1 is the color
 * number shown in the painting guide / export files.
 */
data class Palette(
    val colors: List<Int>,
    val name: String = ""
) {
    val size: Int get() = colors.size

    fun reorderedByLuma(): Palette {
        val ordered = colors.sortedByDescending { ColorMath.srgbToLab(it)[0] }
        return copy(colors = ordered)
    }

    companion object {
        fun fromHexStrings(hexColors: List<String>, name: String = ""): Palette {
            val colors = hexColors.map(ColorMath::fromHex)
            require(colors.distinct().size == colors.size) { "Palette contains duplicate colors" }
            return Palette(colors, name)
        }

        fun fromHex(hexes: List<String>, name: String = ""): Palette =
            fromHexStrings(hexes, name)
    }
}

/**
 * The exact 40 color event palette, numbered 1-40 in the order supplied by the
 * user. The app displays these as 色号 1..40 and must not reorder them.
 */
object SamplePalettes {
    val arknights40: Palette = Palette(
        name = "活动 40 色",
        colors = listOf(
            ColorMath.argb(34, 34, 34),      // 1
            ColorMath.argb(180, 180, 180),   // 2
            ColorMath.argb(234, 231, 223),   // 3
            ColorMath.argb(255, 255, 255),   // 4
            ColorMath.argb(211, 47, 54),     // 5
            ColorMath.argb(156, 10, 0),      // 6
            ColorMath.argb(214, 12, 74),     // 7
            ColorMath.argb(230, 150, 141),   // 8
            ColorMath.argb(254, 152, 117),   // 9
            ColorMath.argb(247, 208, 192),   // 10
            ColorMath.argb(252, 239, 234),   // 11
            ColorMath.argb(251, 246, 232),   // 12
            ColorMath.argb(220, 210, 200),   // 13
            ColorMath.argb(226, 206, 171),   // 14
            ColorMath.argb(213, 99, 34),     // 15
            ColorMath.argb(212, 140, 66),    // 16
            ColorMath.argb(242, 153, 0),     // 17
            ColorMath.argb(249, 201, 51),    // 18
            ColorMath.argb(252, 228, 153),   // 19
            ColorMath.argb(179, 180, 122),   // 20
            ColorMath.argb(194, 218, 114),   // 21
            ColorMath.argb(108, 110, 0),     // 22
            ColorMath.argb(170, 139, 82),    // 23
            ColorMath.argb(169, 143, 116),   // 24
            ColorMath.argb(170, 146, 40),    // 25
            ColorMath.argb(63, 43, 18),      // 26
            ColorMath.argb(116, 73, 31),     // 27
            ColorMath.argb(83, 70, 88),      // 28
            ColorMath.argb(42, 36, 70),      // 29
            ColorMath.argb(57, 69, 153),     // 30
            ColorMath.argb(90, 69, 157),     // 31
            ColorMath.argb(186, 163, 215),   // 32
            ColorMath.argb(182, 188, 223),   // 33
            ColorMath.argb(169, 172, 190),   // 34
            ColorMath.argb(99, 171, 185),    // 35
            ColorMath.argb(180, 210, 220),   // 36
            ColorMath.argb(145, 216, 230),   // 37
            ColorMath.argb(71, 174, 160),    // 38
            ColorMath.argb(182, 211, 200),   // 39
            ColorMath.argb(39, 56, 100)      // 40
        )
    )
}
