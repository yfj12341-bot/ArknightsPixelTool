package com.pixelpainter.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PaletteFile(
    val name: String = "",
    val colors: List<String>
)

object PaletteCodec {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun encode(palette: Palette): String =
        json.encodeToString(
            PaletteFile.serializer(),
            PaletteFile(palette.name, palette.colors.map(ColorMath::toHex))
        )

    fun decode(content: String): Palette {
        val file = json.decodeFromString(PaletteFile.serializer(), content)
        return Palette.fromHex(file.colors, file.name)
    }
}

