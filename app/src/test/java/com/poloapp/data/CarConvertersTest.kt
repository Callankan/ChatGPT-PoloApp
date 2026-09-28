package com.poloapp.data

import org.junit.Assert.*
import org.junit.Test

class CarConvertersTest {
    @Test fun `details preserve Unicode line breaks and punctuation across database round trip`() {
        val original = mapOf("taller" to "Mecánica José · \"Polo\"", "nota" to "Línea 1\nLínea 2", "key=;:{}" to "\\valor")
        val converter = CarConverters()
        assertEquals(original, converter.decodeDetails(converter.encodeDetails(original)))
    }
    @Test fun `empty metadata round trips correctly`() {
        val converter = CarConverters()
        assertEquals(emptyMap<String, String>(), converter.decodeDetails(converter.encodeDetails(emptyMap())))
    }
    @Test fun `every record kind preserves its stable database name`() {
        val converter = CarConverters()
        RecordKind.entries.forEach { assertEquals(it, converter.decodeKind(converter.encodeKind(it))) }
    }
}
