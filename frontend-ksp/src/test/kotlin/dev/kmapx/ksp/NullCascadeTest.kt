@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package dev.kmapx.ksp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Nulabilidad — estrategias, TARGET_DEFAULT, la cascada onNull y la coherencia de @MapField.
 */
class NullCascadeTest {

    // ── Estrategias T? -> T ────────────────────────────────────────────

    @Test
    fun `onNull LITERAL genera el fallback tipado`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.OnNull

            data class Dto(@MapField(onNull = OnNull.LITERAL, default = "N/A") val nickname: String, @MapField(onNull = OnNull.LITERAL, default = "42") val age: Int)

            @MapTo(Dto::class)
            data class Src(val nickname: String?, val age: Int?)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("""nickname = nickname ?: "N/A""""), generated)
        assertTrue(generated.contains("age = age ?: 42"), generated)
    }

    @Test
    fun `onNull THROW lanza con mensaje que nombra campo y par de tipos`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.OnNull

            data class Dto(@MapField(onNull = OnNull.THROW) val nickname: String)

            @MapTo(Dto::class)
            data class Src(val nickname: String?)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(
            generated.contains("nickname ?: throw IllegalArgumentException(\"nickname must not be null mapping Src -> Dto\")"),
            generated,
        )
    }

    @Test
    fun `onNull UNSAFE genera el doble bang consentido`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.OnNull

            data class Dto(@MapField(onNull = OnNull.UNSAFE) val nickname: String)

            @MapTo(Dto::class)
            data class Src(val nickname: String?)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("nickname = nickname!!"), generated)
    }

    @Test
    fun `doble MapField sobre el mismo campo produce KMX037`() {
        KspHarness.assertFailsWithError(
            "KMX037",
            """
            package sample
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.OnNull

            data class Dto(@MapField(onNull = OnNull.LITERAL, default = "x") @MapField(onNull = OnNull.THROW) val nickname: String)

            @MapTo(Dto::class)
            data class Src(val nickname: String?)
            """.trimIndent(),
        )
    }

    @Test
    fun `default no parseable produce KMX017`() {
        KspHarness.assertFailsWithError(
            "KMX017",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.OnNull

            data class Dto(@MapField(onNull = OnNull.LITERAL, default = "abc") val age: Int)

            @MapTo(Dto::class)
            data class Src(val age: Int?)
            """.trimIndent(),
        )
    }

    // ── UseTargetDefaults ────────────────────────────────────

    @Test
    fun `politica TARGET_DEFAULT de mapeo con un campo bifurca con if`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.OnNull

            data class Dto(val name: String, val nickname: String = "N/A")

            @MapTo(Dto::class, onNull = OnNull.TARGET_DEFAULT)
            data class Src(val name: String, val nickname: String?)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("if (nickname != null)"), generated)
        assertTrue(generated.contains("else"), generated)
    }

    @Test
    fun `sin opt-in el default NO se usa a escondidas - KMX003`() {
        val result = KspHarness.assertFailsWithError(
            "KMX003",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val name: String, val nickname: String = "N/A")

            @MapTo(Dto::class)
            data class Src(val name: String, val nickname: String?)
            """.trimIndent(),
        )
        assertTrue(result.messages.contains("set onNull = TARGET_DEFAULT"), result.messages)
    }

    @Test
    fun `fuente ausente con default compila con warning KMX021`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val name: String, val tags: List<String> = emptyList())

            @MapTo(Dto::class)
            data class Src(val name: String)
            """.trimIndent(),
        )
        assertTrue(result.messages.contains("[KMX021]"), result.messages)
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(!generated.contains("tags ="), generated)
    }

    @Test
    fun `tres campos omisibles superan el limite y producen KMX022`() {
        KspHarness.assertFailsWithError(
            "KMX022",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.OnNull

            data class Dto(
                val a: String = "1",
                val b: String = "2",
                val c: String = "3",
            )

            @MapTo(Dto::class, onNull = OnNull.TARGET_DEFAULT)
            data class Src(val a: String?, val b: String?, val c: String?)
            """.trimIndent(),
        )
    }

    // ── Cascada onNull───────── (campo > mapper/mapeo > global) ─────────────

    @Test
    fun `cascada - Mapper onNull THROW aplica a todos los metodos de la interfaz`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.contract.Mapper
            import dev.kmapx.annotations.OnNull

            data class Src(val nickname: String?)
            data class Dto(val nickname: String)

            @Mapper(onNull = OnNull.THROW)
            interface M {
                fun toDto(s: Src): Dto
            }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "MImpl.kt" }.readText()
        assertTrue(generated.contains("nickname ?: throw IllegalArgumentException"), generated)
    }

    @Test
    fun `cascada - STRICT explicito en el campo corta la politica del nivel superior`() {
        KspHarness.assertFailsWithError(
            "KMX003",
            """
            package sample
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.OnNull

            data class Dto(@MapField(onNull = OnNull.STRICT) val nickname: String)

            @MapTo(Dto::class, onNull = OnNull.THROW)
            data class Src(val nickname: String?)
            """.trimIndent(),
        )
    }

    @Test
    fun `cascada - LITERAL como politica de nivel produce KMX041`() {
        KspHarness.assertFailsWithError(
            "KMX041",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.OnNull

            data class Dto(val nickname: String)

            @MapTo(Dto::class, onNull = OnNull.LITERAL)
            data class Src(val nickname: String?)
            """.trimIndent(),
        )
    }

    // ── Coherencia y direccionamiento de @MapField ───────────────────────────

    @Test
    fun `target seteado en sede de campo produce KMX036`() {
        KspHarness.assertFailsWithError(
            "KMX036",
            """
            package sample
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.OnNull

            data class Dto(@MapField(target = "nickname", onNull = OnNull.THROW) val nickname: String)

            @MapTo(Dto::class)
            data class Src(val nickname: String?)
            """.trimIndent(),
        )
    }

    @Test
    fun `target faltante en sede de metodo produce KMX036`() {
        KspHarness.assertFailsWithError(
            "KMX036",
            """
            package sample
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.contract.Mapper

            data class Src(val name: String)
            data class Dto(val name: String)

            @Mapper
            interface M {
                @MapField(from = "name")
                fun toDto(s: Src): Dto
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `onNull LITERAL sin default produce KMX038`() {
        KspHarness.assertFailsWithError(
            "KMX038",
            """
            package sample
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.OnNull

            data class Dto(@MapField(onNull = OnNull.LITERAL) val nickname: String)

            @MapTo(Dto::class)
            data class Src(val nickname: String?)
            """.trimIndent(),
        )
    }

    @Test
    fun `default con onNull distinto de LITERAL produce warning KMX039 y compila`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.OnNull

            data class Dto(@MapField(onNull = OnNull.THROW, default = "x") val nickname: String)

            @MapTo(Dto::class)
            data class Src(val nickname: String?)
            """.trimIndent(),
        )
        assertTrue(result.messages.contains("[KMX039]"), result.messages)
        // El default ignorado no altera la estrategia declarada:
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("nickname ?: throw IllegalArgumentException"), generated)
    }

    @Test
    fun `onNull TARGET_DEFAULT omite el argumento y aplica el default del constructor`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.OnNull

            data class Dto(val name: String, @MapField(onNull = OnNull.TARGET_DEFAULT) val nickname: String = "N/A")

            @MapTo(Dto::class)
            data class Src(val name: String, val nickname: String?)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        // Emisión condicional: construcción condicional que OMITE el argumento cuando es null.
        assertTrue(generated.contains("if (nickname != null)"), generated)
    }

    @Test
    fun `TARGET_DEFAULT sin default en el constructor produce KMX040`() {
        KspHarness.assertFailsWithError(
            "KMX040",
            """
            package sample
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.OnNull

            data class Dto(@MapField(onNull = OnNull.TARGET_DEFAULT) val nickname: String)

            @MapTo(Dto::class)
            data class Src(val nickname: String?)
            """.trimIndent(),
        )
    }

    // NOTA (limitación del harness, no del producto): kctfork 0.7.0 + KSP2 embebido PIERDE las
    // anotaciones de propiedades de CUERPO (prop/getter/setter llegan vacíos al processor);
    // por el pipeline KSP real de Gradle funcionan. El caso "estrategia en var de cuerpo"
    // se verifica en integration-tests (Ticket/TicketSrc, runtime por target) y en EngineTest.
    // Revisitar al subir kctfork.

    @Test
    fun `estrategia muerta produce warning KMX018 y compila`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.OnNull

            data class Dto(@MapField(onNull = OnNull.LITERAL, default = "N/A") val nickname: String)

            @MapTo(Dto::class)
            data class Src(val nickname: String)
            """.trimIndent(),
        )
        assertTrue(result.messages.contains("[KMX018]"), result.messages)
        // La estrategia muerta no altera la resolución normal:
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("nickname = nickname,"), generated)
    }

    @Test
    fun `T nullable a T no-nullable produce KMX003 en compile-time`() {
        KspHarness.assertFailsWithError(
            "KMX003",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class PersonDto(val nickname: String)

            @MapTo(PersonDto::class)
            data class Person(val nickname: String?)
            """.trimIndent(),
        )
    }
}
