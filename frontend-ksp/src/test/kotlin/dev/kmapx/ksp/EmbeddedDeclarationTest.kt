@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package dev.kmapx.ksp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Modo EMBEDDED — declaración y construcción: extensions, constructores/factories, KMP.
 */
class EmbeddedDeclarationTest {


    @Test
    fun `MapTo con matching homonimo genera la extension function`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class PersonDto(val name: String, val age: Int)

            @MapTo(PersonDto::class)
            data class Person(val name: String, val age: Int)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "PersonMappings.kt" }.readText()
        assertTrue(generated.contains("public fun Person.toPersonDto(): PersonDto"), generated)
        assertTrue(generated.contains("name = name"), generated)
    }

    @Test
    fun `campo sin fuente produce KMX002 en compile-time`() {
        KspHarness.assertFailsWithError(
            "KMX002",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class PersonDto(val name: String, val email: String)

            @MapTo(PersonDto::class)
            data class Person(val name: String)
            """.trimIndent(),
        )
    }

    @Test
    fun `MapTo repeatable - dos targets, dos funciones en un solo archivo`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class PersonDto(val name: String, val age: Int)
            data class PersonSummary(val name: String)

            @MapTo(PersonDto::class)
            @MapTo(PersonSummary::class)
            data class Person(val name: String, val age: Int)
            """.trimIndent(),
        )
        val mappings = result.generatedFiles.filter { it.name == "PersonMappings.kt" }
        assertEquals(1, mappings.size, "todas las funciones van al mismo archivo")
        val generated = mappings.single().readText()
        assertTrue(generated.contains("public fun Person.toPersonDto(): PersonDto"), generated)
        assertTrue(generated.contains("public fun Person.toPersonSummary(): PersonSummary"), generated)
    }

    @Test
    fun `MapTo con name explicito respeta el override`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class PersonDto(val name: String)

            @MapTo(PersonDto::class, name = "asDto")
            data class Person(val name: String)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "PersonMappings.kt" }.readText()
        assertTrue(generated.contains("public fun Person.asDto(): PersonDto"), generated)
    }

    @Test
    fun `colision de nombre de funcion produce KMX013`() {
        KspHarness.assertFailsWithError(
            "KMX013",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class PersonDto(val name: String)
            data class PersonSummary(val name: String)

            @MapTo(PersonDto::class, name = "convert")
            @MapTo(PersonSummary::class, name = "convert")
            data class Person(val name: String)
            """.trimIndent(),
        )
    }

    @Test
    fun `clase internal genera funcion internal`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class PersonDto(val name: String)

            @MapTo(PersonDto::class)
            internal data class Person(val name: String)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "PersonMappings.kt" }.readText()
        assertTrue(generated.contains("internal fun Person.toPersonDto(): PersonDto"), generated)
    }

    // ── Resolución determinista de construcción ────────────────────────

    @Test
    fun `MapConstructor en secundario gana sobre el primario privado`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapConstructor

            class Money private constructor(val cents: Long) {
                var currency: String = ""
                    private set
                @MapConstructor constructor(cents: Long, currency: String) : this(cents) {
                    this.currency = currency
                }
            }

            @MapTo(Money::class)
            data class MoneySrc(val cents: Long, val currency: String)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "MoneySrcMappings.kt" }.readText()
        assertTrue(generated.contains("cents = cents"), generated)
        assertTrue(generated.contains("currency = currency"), generated)
    }

    @Test
    fun `MapFactory de companion emite Target-funcion con argumentos nombrados`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapFactory

            class Temperature private constructor(val kelvin: Double) {
                companion object {
                    @MapFactory fun fromKelvin(kelvin: Double): Temperature = Temperature(kelvin)
                }
            }

            @MapTo(Temperature::class)
            data class Reading(val kelvin: Double)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "ReadingMappings.kt" }.readText()
        assertTrue(generated.contains("Temperature.fromKelvin("), generated)
        assertTrue(generated.contains("kelvin = kelvin"), generated)
    }

    @Test
    fun `MapFactory top-level referenciada por nombre calificado`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapFactory

            class Temperature internal constructor(val kelvin: Double)

            @MapFactory
            fun temperatureOf(kelvin: Double): Temperature = Temperature(kelvin)

            @MapTo(Temperature::class)
            data class Reading(val kelvin: Double)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "ReadingMappings.kt" }.readText()
        assertTrue(generated.contains("temperatureOf("), generated)
    }

    @Test
    fun `dos MapFactory sobre el mismo target producen KMX006`() {
        KspHarness.assertFailsWithError(
            "KMX006",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapFactory

            class Temperature private constructor(val kelvin: Double) {
                companion object {
                    @MapFactory fun fromKelvin(kelvin: Double): Temperature = Temperature(kelvin)
                    @MapFactory fun fromCelsius(kelvin: Double): Temperature = Temperature(kelvin + 273.15)
                }
            }

            @MapTo(Temperature::class)
            data class Reading(val kelvin: Double)
            """.trimIndent(),
        )
    }

    @Test
    fun `dos MapConstructor producen KMX006`() {
        KspHarness.assertFailsWithError(
            "KMX006",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapConstructor

            class Money @MapConstructor constructor(val cents: Long) {
                @MapConstructor constructor(cents: Long, currency: String) : this(cents)
            }

            @MapTo(Money::class)
            data class MoneySrc(val cents: Long, val currency: String)
            """.trimIndent(),
        )
    }

    @Test
    fun `MapConstructor y MapFactory a la vez producen KMX006`() {
        KspHarness.assertFailsWithError(
            "KMX006",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapConstructor
            import dev.kmapx.annotations.MapFactory

            class Money @MapConstructor constructor(val cents: Long) {
                companion object {
                    @MapFactory fun of(cents: Long): Money = Money(cents)
                }
            }

            @MapTo(Money::class)
            data class MoneySrc(val cents: Long)
            """.trimIndent(),
        )
    }

    @Test
    fun `primario privado sin anotaciones produce KMX005 con fix`() {
        val result = KspHarness.assertFailsWithError(
            "KMX005",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            class Locked private constructor(val id: String)

            @MapTo(Locked::class)
            data class Src(val id: String)
            """.trimIndent(),
        )
        assertTrue(result.messages.contains("@MapFactory"), result.messages)
    }

    @Test
    fun `var de cuerpo se asigna post-construccion via also`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            class Task(val id: String) { var status: String = "NEW" }

            @MapTo(Task::class)
            data class TaskSrc(val id: String, val status: String)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "TaskSrcMappings.kt" }.readText()
        assertTrue(generated.contains(".also {"), generated)
        assertTrue(generated.contains("it.status = status"), generated)
    }

    // ── KMP real ───────────────────────────────────────────────────────

    @Test
    fun `expect class anotada produce KMX025`() {
        KspHarness.assertFailsWithError(
            "KMX025",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val id: String)

            @MapTo(Dto::class)
            expect class PlatformThing {
                val id: String
            }

            actual class PlatformThing {
                actual val id: String = "x"
            }
            """.trimIndent(),
            multiplatform = true,
        )
    }

    @Test
    fun `widening implicito y conversiones estandar opt-in`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val id: java.util.UUID, val amount: Long, val score: Double)

            @MapTo(Dto::class, stdConverters = true)
            data class Src(val id: String, val amount: Int, val score: Float)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("amount = amount.toLong()"), generated)
        assertTrue(generated.contains("score = score.toDouble()"), generated)
        assertTrue(generated.contains("java.util.UUID.fromString(id)"), generated)
    }

    @Test
    fun `sin stdConverters el par estandar sigue siendo KMX004`() {
        KspHarness.assertFailsWithError(
            "KMX004",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val id: java.util.UUID)

            @MapTo(Dto::class)
            data class Src(val id: String)
            """.trimIndent(),
        )
    }

    @Test
    fun `unmapped ERROR convierte el KMX021 en bloqueo`() {
        KspHarness.assertFailsWithError(
            "KMX021",
            """
            package sample
            import dev.kmapx.annotations.Unmapped
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val name: String, val audit: String? = null)

            @MapTo(Dto::class, unmapped = Unmapped.ERROR)
            data class Src(val name: String)
            """.trimIndent(),
        )
    }

    @Test
    fun `unmapped IGNORE acalla el KMX021`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.Unmapped
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val name: String, val audit: String? = null)

            @MapTo(Dto::class, unmapped = Unmapped.IGNORE)
            data class Src(val name: String)
            """.trimIndent(),
        )
        assertFalse(result.messages.contains("KMX021"), result.messages)
    }
}
