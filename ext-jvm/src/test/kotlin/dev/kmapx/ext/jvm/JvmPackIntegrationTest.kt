@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package dev.kmapx.ext.jvm

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import dev.kmapx.ksp.KmapxProcessorProvider
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El pack `ext-jvm` de EXTREMO A EXTREMO: compila un consumidor con el processor real y ESTE
 * módulo en el classpath (ServiceLoader lo descubre) y verifica que el código generado llama a
 * las funciones del pack. Vive aquí, no en frontend-ksp, para no contaminar el ServiceLoader
 * global de aquellos tests (que asumen pares reales sin resolver).
 */
class JvmPackIntegrationTest {

    private fun generate(source: String, generatedFileName: String): String {
        val compilation = KotlinCompilation().apply {
            sources = listOf(SourceFile.kotlin("Input.kt", source))
            useKsp2()
            symbolProcessorProviders += KmapxProcessorProvider()
            inheritClassPath = true
            messageOutputStream = System.out
        }
        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        return compilation.kspSourcesDir.walkTopDown()
            .first { it.name == generatedFileName }.readText()
    }

    @Test
    fun `String a UUID resuelve por el pack sin Converter del usuario`() {
        val generated = generate(
            """
            package consumer
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val id: java.util.UUID)

            @MapTo(Dto::class)
            data class Src(val id: String)
            """.trimIndent(),
            "SrcMappings.kt",
        )
        assertTrue(generated.contains("uuidFromString("), generated)
    }

    @Test
    fun `Instant a String y BigDecimal a String los resuelve el pack`() {
        val generated = generate(
            """
            package consumer
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val at: String, val amount: String)

            @MapTo(Dto::class)
            data class Src(val at: java.time.Instant, val amount: java.math.BigDecimal)
            """.trimIndent(),
            "SrcMappings.kt",
        )
        assertTrue(generated.contains("instantToIso("), generated)
        assertTrue(generated.contains("bigDecimalToString("), generated)
    }
}
