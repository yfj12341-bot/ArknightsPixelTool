package com.pixelpainter.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CodecTest {

    @Test
    fun paletteJsonRoundTrip() {
        val palette = SamplePalettes.arknights40
        val decoded = PaletteCodec.decode(PaletteCodec.encode(palette))
        assertEquals(palette, decoded)
    }
}
