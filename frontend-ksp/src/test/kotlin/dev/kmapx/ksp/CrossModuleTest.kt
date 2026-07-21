@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package dev.kmapx.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Mapeo anidado CROSS-MODULE (design/cross-module-mapping.md): un tipo anidado cuyo mapper vive
 * en OTRO módulo se resuelve sin redeclararlo, vía el marcador `@GeneratedMapping` descubierto en
 * el classpath. Se compila el módulo A (genera+compila la extensión), y luego el módulo B con A
 * en su classpath.
 */
class CrossModuleTest {

    private fun newCompilation(fileName: String, source: String, deps: List<File>): KotlinCompilation =
        KotlinCompilation().apply {
            sources = listOf(SourceFile.kotlin(fileName, source))
            useKsp2()
            symbolProcessorProviders += KmapxProcessorProvider()
            inheritClassPath = true
            classpaths = classpaths + deps
            messageOutputStream = System.out
        }

    @Test
    fun `un tipo anidado de otro modulo se resuelve por su @GeneratedMapping`() {
        // Módulo A: declara el par Address -> AddressDto (genera moda.toAddressDto con el marker).
        val moduleA = newCompilation(
            "ModuleA.kt",
            """
            package moda
            import dev.kmapx.annotations.embedded.MapTo

            data class AddressDto(val city: String)

            @MapTo(AddressDto::class)
            data class Address(val city: String)
            """.trimIndent(),
            deps = emptyList(),
        )
        val resultA = moduleA.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, resultA.exitCode, resultA.messages)

        // Módulo B: NO redeclara Address->AddressDto; lo usa anidado. Con A en el classpath.
        val moduleB = newCompilation(
            "ModuleB.kt",
            """
            package modb
            import dev.kmapx.annotations.embedded.MapTo
            import moda.Address
            import moda.AddressDto

            data class OrderDto(val address: AddressDto)

            @MapTo(OrderDto::class)
            data class Order(val address: Address)
            """.trimIndent(),
            deps = listOf(moduleA.classesDir),
        )
        val resultB = moduleB.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, resultB.exitCode, resultB.messages)

        val generated = moduleB.kspSourcesDir.walkTopDown().first { it.name == "OrderMappings.kt" }.readText()
        // El par anidado se resolvió cross-module: llama a la extensión de A, no KMX007.
        assertTrue(generated.contains("toAddressDto()"), generated)
    }

    @Test
    fun `sin el modulo A en el classpath el par anidado es KMX007`() {
        val standalone = newCompilation(
            "Standalone.kt",
            """
            package modb
            import dev.kmapx.annotations.embedded.MapTo

            class Address(val city: String)          // sin @MapTo: no hay par declarado ni cross-module
            data class AddressDto(val city: String)

            data class OrderDto(val address: AddressDto)

            @MapTo(OrderDto::class)
            data class Order(val address: Address)
            """.trimIndent(),
            deps = emptyList(),
        )
        val resultB = standalone.compile()
        assertTrue(resultB.messages.contains("[KMX007]"), resultB.messages)
    }
}
