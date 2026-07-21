package dev.kmapx.ext.datetime

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DateTimeConvertersTest {

    @Test
    fun `ida y vuelta Instant es inversa (ISO y epoch)`() {
        val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
        assertEquals(now, instantFromIso(instantToIso(now)))
        assertEquals(now, instantFromEpochMillis(instantToEpochMillis(now)))
    }

    @Test
    fun `todos los pares apuntan a funciones del pack`() {
        val pairs = KmapxDateTimeExtension().contributeConverters()
        assertTrue(pairs.isNotEmpty())
        assertTrue(pairs.values.all { it.startsWith("dev.kmapx.ext.datetime.") }, pairs.toString())
        assertEquals(
            "dev.kmapx.ext.datetime.instantFromIso",
            pairs.entries.first { it.key.sourceQualifiedName == "kotlin.String" && it.key.targetQualifiedName == "kotlinx.datetime.Instant" }.value,
        )
    }
}
