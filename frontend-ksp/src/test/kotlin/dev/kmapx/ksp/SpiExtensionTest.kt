package dev.kmapx.ksp

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * El MECANISMO del SPI (ServiceLoader → par contribuido → llamada generada), con una extensión
 * de tipos ficticios para no colisionar con los tests que asumen KMX004 en pares reales.
 * El pack real `ext-jvm` se prueba en su propio módulo.
 */
class SpiExtensionTest {

    @Test
    fun `un par contribuido por el SPI resuelve sin Converter del usuario`() {
        val result = KspHarness.assertCompiles(
            """
            package spisample
            import dev.kmapx.annotations.embedded.MapTo

            data class LegacyId(val raw: Int)
            fun legacyToText(id: LegacyId): String = "L-" + id.raw

            data class Dto(val id: String)

            @MapTo(Dto::class)
            data class Src(val id: LegacyId)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("legacyToText("), generated)
    }

    @Test
    fun `el Converter del usuario gana sobre el contribuido para el mismo par`() {
        val result = KspHarness.assertCompiles(
            """
            package spisample
            import dev.kmapx.annotations.Converter
            import dev.kmapx.annotations.embedded.MapTo

            data class LegacyId(val raw: Int)
            fun legacyToText(id: LegacyId): String = "L-" + id.raw

            @Converter
            fun userLegacy(id: LegacyId): String = "U-" + id.raw

            data class Dto(val id: String)

            @MapTo(Dto::class)
            data class Src(val id: LegacyId)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("userLegacy("), generated)
        assertTrue(!generated.contains("legacyToText("), generated)
    }
}
