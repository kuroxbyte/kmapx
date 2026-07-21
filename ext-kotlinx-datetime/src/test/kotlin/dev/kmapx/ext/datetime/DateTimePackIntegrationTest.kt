@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package dev.kmapx.ext.datetime

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import dev.kmapx.ksp.KmapxProcessorProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** El pack de kotlinx-datetime de EXTREMO A EXTREMO (ServiceLoader lo descubre en este classpath). */
class DateTimePackIntegrationTest {

    @Test
    fun `Instant y LocalDate a String los resuelve el pack sin Converter del usuario`() {
        val compilation = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin(
                    "Input.kt",
                    """
                    package consumer
                    import dev.kmapx.annotations.embedded.MapTo

                    data class Dto(val at: String, val day: String)

                    @MapTo(Dto::class)
                    data class Src(val at: kotlinx.datetime.Instant, val day: kotlinx.datetime.LocalDate)
                    """.trimIndent(),
                ),
            )
            useKsp2()
            symbolProcessorProviders += KmapxProcessorProvider()
            inheritClassPath = true
            messageOutputStream = System.out
        }
        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val generated = compilation.kspSourcesDir.walkTopDown().first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("instantToIso("), generated)
        assertTrue(generated.contains("localDateToIso("), generated)
    }
}
