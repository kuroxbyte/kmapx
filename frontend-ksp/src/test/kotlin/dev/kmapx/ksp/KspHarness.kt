@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package dev.kmapx.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Harness de testing de compilación sobre kctfork (kotlin-compile-testing con KSP2).
 * API de los tests de features: assertCompiles { } y assertFailsWithError("KMXnnn") { }.
 * NOTA: la API de kctfork evoluciona; si un método cambió de nombre en la versión final,
 * ajustar solo este archivo — los tests de features no tocan kctfork directamente.
 */
object KspHarness {

    data class Result(
        val exitCode: KotlinCompilation.ExitCode,
        val messages: String,
        val generatedFiles: List<File>,
        /** Para tests de RUNTIME sobre lo compilado (p. ej. startKoin resuelve el mapper). */
        val classLoader: ClassLoader? = null,
    )

    fun compile(
        source: String,
        multiplatform: Boolean = false,
        isolated: Boolean = false,
        options: Map<String, String> = emptyMap(),
    ): Result {
        val compilation = KotlinCompilation().apply {
            sources = listOf(SourceFile.kotlin("Input.kt", source))
            useKsp2()
            symbolProcessorProviders += KmapxProcessorProvider()
            messageOutputStream = System.out
            // Opciones del processor (p. ej. kmapx.report=json,html).
            kspProcessorOptions += options
            if (isolated) {
                // Classpath MÍNIMO (solo las anotaciones de kmapx) — así el resolver del
                // processor NO ve los frameworks del classpath de test (para probar KMX030).
                inheritClassPath = false
                classpaths = listOf(
                    File(dev.kmapx.annotations.embedded.MapTo::class.java.protectionDomain.codeSource.location.toURI()),
                )
            } else {
                inheritClassPath = true
            }
            // Habilita expect/actual en un mismo módulo (test de KMX025).
            if (multiplatform) kotlincArguments = kotlincArguments + "-Xmulti-platform"
        }
        val result = compilation.compile()
        val generated = compilation.kspSourcesDir.walkTopDown().filter { it.isFile }.toList()
        val classLoader = if (result.exitCode == KotlinCompilation.ExitCode.OK) result.classLoader else null
        return Result(result.exitCode, result.messages, generated, classLoader)
    }

    fun assertCompiles(source: String, options: Map<String, String> = emptyMap()): Result {
        val result = compile(source, options = options)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        return result
    }

    fun assertFailsWithError(
        code: String,
        source: String,
        multiplatform: Boolean = false,
        isolated: Boolean = false,
    ): Result {
        val result = compile(source, multiplatform, isolated)
        assertTrue(
            result.exitCode != KotlinCompilation.ExitCode.OK,
            "Se esperaba fallo de compilación con [$code] pero compiló OK",
        )
        assertTrue(result.messages.contains("[$code]"), "No se encontró [$code] en:\n${result.messages}")
        return result
    }

    fun Result.generatedFileContaining(fragment: String): File =
        generatedFiles.first { it.readText().contains(fragment) }
}
