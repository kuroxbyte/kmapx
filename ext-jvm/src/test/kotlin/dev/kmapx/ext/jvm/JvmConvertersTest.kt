package dev.kmapx.ext.jvm

import dev.kmapx.spi.KmapxExperimentalSpi
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(KmapxExperimentalSpi::class)
class JvmConvertersTest {

    @Test
    fun `las conversiones ida y vuelta son inversas exactas`() {
        val id = UUID.fromString("00000000-0000-0000-0000-000000000001")
        assertEquals(id, uuidFromString(uuidToString(id)))

        val now = Instant.ofEpochMilli(1_700_000_000_000)
        assertEquals(now, instantFromIso(instantToIso(now)))
        assertEquals(now, instantFromEpochMillis(instantToEpochMillis(now)))
    }

    @Test
    fun `cada par contribuido apunta a una funcion que existe en el pack`() {
        val pairs = KmapxJvmExtension().contributeConverters()
        assertTrue(pairs.isNotEmpty())
        // Todas las FQN referencian funciones top-level del paquete dev.kmapx.ext.jvm.
        assertTrue(pairs.values.all { it.startsWith("dev.kmapx.ext.jvm.") }, pairs.toString())
        // El par emblema está registrado hacia la función correcta.
        assertEquals(
            "dev.kmapx.ext.jvm.uuidFromString",
            pairs.entries.first { it.key.sourceQualifiedName == "kotlin.String" && it.key.targetQualifiedName == "java.util.UUID" }.value,
        )
    }
}
