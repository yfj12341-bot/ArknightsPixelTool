package com.pixelpainter.core

enum class PaletteMode {
    /** Use the exact event palette supplied by the user. */
    FIXED,

    /** Pick the best N colors from the source image itself. */
    AUTO,

    /** Pick the best colors automatically, but let the user choose size and limit. */
    CUSTOM
}

data class PixelArtOptions(
    val gridSize: Int = 24,
    val maxColors: Int = 40,
    val paletteMode: PaletteMode = PaletteMode.AUTO,
    val downscaleMode: DownsampleMode = DownsampleMode.BOX,
    val dither: Boolean = false
) {
    init {
        require(gridSize in 1..128) { "gridSize must be between 1 and 128" }
        require(maxColors in 1..256) { "maxColors must be between 1 and 256" }
    }
}

data class PixelArtResult(
    val gridSize: Int,
    val palette: Palette,
    val indices: IntArray,
    val preview: RgbImage
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PixelArtResult) return false
        return gridSize == other.gridSize &&
            palette == other.palette &&
            indices.contentEquals(other.indices) &&
            preview == other.preview
    }

    override fun hashCode(): Int {
        var result = gridSize
        result = 31 * result + palette.hashCode()
        result = 31 * result + indices.contentHashCode()
        result = 31 * result + preview.hashCode()
        return result
    }
}

object PixelArtConverter {

    fun convert(
        source: RgbImage,
        options: PixelArtOptions = PixelArtOptions(),
        fixedPalette: Palette? = null,
        crop: CropBounds? = null
    ): PixelArtResult {
        require(options.gridSize in 1..128) { "gridSize must be between 1 and 128" }
        require(options.paletteMode != PaletteMode.FIXED || fixedPalette != null) {
            "fixedPalette is required in FIXED mode"
        }

        val square = if (crop != null) source.squareCrop(crop) else source.centeredSquareCrop()
        val palette = when (options.paletteMode) {
            PaletteMode.AUTO, PaletteMode.CUSTOM ->
                AutoPalette.extract(square, options.maxColors)
            PaletteMode.FIXED -> fixedPalette!!.copy(
                colors = fixedPalette.colors.take(options.maxColors)
            )
        }
        require(palette.colors.isNotEmpty()) { "Palette is empty" }

        val downscaled = Downsample.resize(
            source = square,
            targetSize = options.gridSize,
            mode = options.downscaleMode,
            referencePalette = palette
        )
        val indices = NearestColorMapper.mapToIndices(downscaled, palette, options.dither)
        val previewPixels = IntArray(indices.size) { palette.colors[indices[it]] }
        return PixelArtResult(
            gridSize = options.gridSize,
            palette = palette,
            indices = indices,
            preview = RgbImage(options.gridSize, options.gridSize, previewPixels)
        )
    }
}
