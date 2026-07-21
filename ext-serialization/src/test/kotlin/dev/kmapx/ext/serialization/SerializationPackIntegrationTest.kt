@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package dev.kmapx.ext.serialization

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import dev.kmapx.ksp.KmapxProcessorProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** El pack de serialización de EXTREMO A EXTREMO (ServiceLoader lo descubre en este classpath). */
class SerializationPackIntegrationTest {

    @Test
    fun `JsonElement a String lo resuelve el pack sin Converter del usuario`() {
        val compilation = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin(
                    "Input.kt",
                    """
                    package consumer
                    import dev.kmapx.annotations.embedded.MapTo
                    import kotlinx.serialization.json.JsonElement

                    data class Dto(val payload: String)

                    @MapTo(Dto::class)
                    data class Src(val payload: JsonElement)
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
        assertTrue(generated.contains("jsonElementToString("), generated)
    }
}
