@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package dev.kmapx.ksp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Estructuras — sealed, value classes, PATCH por forma, enums.
 */
class StructuresTest {

    // ── Jerarquías sealed paralelas ────────────────────────────────────

    @Test
    fun `sealed interface con 3 subtipos genera when sin else`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            sealed interface EventDto {
                data class Approved(val amount: Long) : EventDto
                data class Rejected(val reason: String) : EventDto
                data object Pending : EventDto
            }

            @MapTo(EventDto::class)
            sealed interface Event {
                data class Approved(val amount: Long) : Event
                data class Rejected(val reason: String) : Event
                data object Pending : Event
            }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "EventMappings.kt" }.readText()
        assertTrue(!generated.contains("else"), generated)
        assertTrue(generated.contains("when (this)"), generated)
        assertTrue(generated.contains("is Event.Approved -> toApproved()"), generated)
        assertTrue(generated.contains("is Event.Pending -> EventDto.Pending"), generated)
        assertTrue(generated.contains("fun Event.Approved.toApproved(): EventDto.Approved"), generated)
    }

    @Test
    fun `sealed class tambien despacha`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            sealed class StateDto {
                data class Active(val since: Long) : StateDto()
                data object Idle : StateDto()
            }

            @MapTo(StateDto::class)
            sealed class State {
                data class Active(val since: Long) : State()
                data object Idle : State()
            }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "StateMappings.kt" }.readText()
        assertTrue(generated.contains("when (this)"), generated)
        assertTrue(!generated.contains("else"), generated)
    }

    @Test
    fun `MapSubtype redirige el emparejamiento`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.MapSubtype
            import dev.kmapx.annotations.embedded.MapTo

            sealed interface EventDto {
                data class Accepted(val amount: Long) : EventDto
            }

            @MapTo(EventDto::class)
            sealed interface Event {
                @MapSubtype(EventDto.Accepted::class)
                data class Approved(val amount: Long) : Event
            }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "EventMappings.kt" }.readText()
        assertTrue(generated.contains("is Event.Approved -> toAccepted()"), generated)
        assertTrue(generated.contains("fun Event.Approved.toAccepted(): EventDto.Accepted"), generated)
    }

    @Test
    fun `el pitch - agregar un subtipo al source rompe la compilacion con KMX010`() {
        KspHarness.assertFailsWithError(
            "KMX010",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            sealed interface EventDto {
                data class Approved(val amount: Long) : EventDto
            }

            @MapTo(EventDto::class)
            sealed interface Event {
                data class Approved(val amount: Long) : Event
                data class Refunded(val amount: Long) : Event
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `subtipo extra del target produce warning KMX023 y compila`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            sealed interface EventDto {
                data class Approved(val amount: Long) : EventDto
                data object Cancelled : EventDto
            }

            @MapTo(EventDto::class)
            sealed interface Event {
                data class Approved(val amount: Long) : Event
            }
            """.trimIndent(),
        )
        assertTrue(result.messages.contains("[KMX023]"), result.messages)
    }

    @Test
    fun `anidamiento sealed profundo produce KMX024`() {
        KspHarness.assertFailsWithError(
            "KMX024",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            sealed interface EventDto {
                data class A(val x: Int) : EventDto
            }

            @MapTo(EventDto::class)
            sealed interface Event {
                sealed interface Inner : Event {
                    data class A(val x: Int) : Inner
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `las reglas normales aplican dentro de cada rama`() {
        KspHarness.assertFailsWithError(
            "KMX003",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            sealed interface EventDto {
                data class Note(val text: String) : EventDto
            }

            @MapTo(EventDto::class)
            sealed interface Event {
                data class Note(val text: String?) : Event
            }
            """.trimIndent(),
        )
    }

    // ── Value classes transparentes ────────────────────────────────────

    @Test
    fun `value class nullable compone con safe-call en ambas direcciones`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            @JvmInline value class UserId(val value: String)

            data class Dto(val id: String?)

            @MapTo(Dto::class)
            data class Src(val id: UserId?)

            data class WrapDto(val id: UserId?)

            @MapTo(WrapDto::class)
            data class WrapSrc(val id: String?)
            """.trimIndent(),
        )
        val src = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(src.contains("id = id?.value"), src)
        val wrap = result.generatedFiles.first { it.name == "WrapSrcMappings.kt" }.readText()
        assertTrue(wrap.contains("id = id?.let { UserId(it) }"), wrap)
    }

    @Test
    fun `passthrough de value class es directo, sin unwrap redundante`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            @JvmInline value class UserId(val value: String)

            data class Dto(val id: UserId)

            @MapTo(Dto::class)
            data class Src(val id: UserId)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("id = id,"), generated)
        assertTrue(!generated.contains(".value"), generated)
    }

    @Test
    fun `UserId nullable a String exige estrategia y esta aplica sobre el resultado`() {
        KspHarness.assertFailsWithError(
            "KMX003",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            @JvmInline value class UserId(val value: String)

            data class Dto(val id: String)

            @MapTo(Dto::class)
            data class Src(val id: UserId?)
            """.trimIndent(),
        )
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.OnNull

            @JvmInline value class UserId(val value: String)

            data class Dto(@MapField(onNull = OnNull.THROW) val id: String)

            @MapTo(Dto::class)
            data class Src(val id: UserId?)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("id = id?.value ?: throw IllegalArgumentException"), generated)
    }

    @Test
    fun `encadenado value-de-value de dos niveles produce KMX004`() {
        KspHarness.assertFailsWithError(
            "KMX004",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            @JvmInline value class UserId(val value: String)
            @JvmInline value class Wrapper(val id: UserId)

            data class Dto(val id: String)

            @MapTo(Dto::class)
            data class Src(val id: Wrapper)
            """.trimIndent(),
        )
    }

    // ── PATCH vía copy() ───────────────────────────────────────────────

    @Test
    fun `PatchMapper genera copy con null igual a no tocar - Plan 4`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.contract.Mapper

            data class Customer(val name: String, val nickname: String, val age: Int)
            data class CustomerPatch(val name: String?, val nickname: String?)

            @Mapper
            interface CustomerPatcher { fun apply(target: Customer, patch: CustomerPatch): Customer }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "CustomerPatcherImpl.kt" }.readText()
        assertTrue(generated.contains("target.copy("), generated)
        assertTrue(generated.contains("name = patch.name ?: target.name"), generated)
        assertTrue(generated.contains("nickname = patch.nickname ?: target.nickname"), generated)
        // Campo del target ausente en el patch → se conserva (no aparece en el copy):
        assertTrue(!generated.contains("age ="), generated)
    }

    @Test
    fun `PatchMapper sobre target no data class produce KMX012`() {
        KspHarness.assertFailsWithError(
            "KMX012",
            """
            package sample
            import dev.kmapx.annotations.contract.Mapper

            class Customer(val name: String)
            data class CustomerPatch(val name: String?)

            @Mapper
            interface CustomerPatcher { fun apply(target: Customer, patch: CustomerPatch): Customer }
            """.trimIndent(),
        )
    }

    @Test
    fun `PatchMapper - campo no-nullable asignado incondicional y wrap en fallback`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.contract.Mapper

            @JvmInline value class UserId(val value: String)

            data class Customer(val id: UserId, val name: String)
            data class CustomerPatch(val id: String?, val name: String)

            @Mapper
            interface CustomerPatcher { fun apply(target: Customer, patch: CustomerPatch): Customer }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "CustomerPatcherImpl.kt" }.readText()
        assertTrue(generated.contains("id = patch.id?.let { UserId(it) } ?: target.id"), generated)
        assertTrue(generated.contains("name = patch.name,"), generated)
    }

    @Test
    fun `PatchMapper - afterApply invocada y su retorno usado`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.contract.Mapper

            data class Customer(val name: String)
            data class CustomerPatch(val name: String?)

            @Mapper
            interface CustomerPatcher {
                fun apply(target: Customer, patch: CustomerPatch): Customer
                fun afterApply(target: Customer, patch: CustomerPatch, result: Customer): Customer =
                    result.copy(name = result.name.trim())
            }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "CustomerPatcherImpl.kt" }.readText()
        assertTrue(generated.contains("afterApply(target, patch, target.copy("), generated)
    }

    @Test
    fun `PatchMapper - afterApply con firma incorrecta produce KMX014`() {
        KspHarness.assertFailsWithError(
            "KMX014",
            """
            package sample
            import dev.kmapx.annotations.contract.Mapper

            data class Customer(val name: String)
            data class CustomerPatch(val name: String?)

            @Mapper
            interface CustomerPatcher {
                fun apply(target: Customer, patch: CustomerPatch): Customer
                fun afterApply(result: Customer): Customer = result
            }
            """.trimIndent(),
        )
    }

    // ── Patch por FORMA dentro de @Mapper ────────────────────────────────────

    @Test
    fun `interfaz mixta - mapping y patch conviven en el mismo Mapper`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.contract.Mapper

            data class Customer(val name: String, val nickname: String)
            data class CustomerDto(val name: String, val nickname: String)
            data class CustomerPatch(val nickname: String?)

            @Mapper
            interface CustomerMapper {
                fun toDto(c: Customer): CustomerDto
                fun applyPatch(target: Customer, patch: CustomerPatch): Customer
            }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "CustomerMapperImpl.kt" }.readText()
        assertTrue(generated.contains("CustomerDto("), generated)
        assertTrue(generated.contains("target.copy("), generated)
        assertTrue(generated.contains("nickname = patch.nickname ?: target.nickname"), generated)
    }

    @Test
    fun `forma patch con aridad distinta de 2 produce error de forma`() {
        KspHarness.assertFailsWithError(
            "KMX015",
            """
            package sample
            import dev.kmapx.annotations.contract.Mapper

            data class Customer(val name: String)
            data class CustomerPatch(val name: String?)

            @Mapper
            interface CustomerMapper {
                fun merge(target: Customer, patch: CustomerPatch, extra: String): Customer
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `Mapper onNull TARGET_DEFAULT habilita el target default sin config global`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.contract.Mapper
            import dev.kmapx.annotations.OnNull

            data class Src(val name: String, val nickname: String?)
            data class Dto(val name: String, val nickname: String = "N/A")

            @Mapper(onNull = OnNull.TARGET_DEFAULT)
            interface M {
                fun toDto(s: Src): Dto
            }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "MImpl.kt" }.readText()
        // Construcción condicional que OMITE el argumento cuando la fuente es null.
        assertTrue(generated.contains("if (s.nickname != null)"), generated)
    }

    // ── Enums paralelos ────────────────────────────────────────────────

    @Test
    fun `enums paralelos generan when por igualdad sin else`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            enum class ColorDto { RED, GREEN }

            @MapTo(ColorDto::class)
            enum class Color { RED, GREEN }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "ColorMappings.kt" }.readText()
        assertTrue(!generated.contains("else"), generated)
        assertTrue(generated.contains("Color.RED -> ColorDto.RED"), generated)
        assertTrue(generated.contains("fun Color.toColorDto(): ColorDto"), generated)
    }

    @Test
    fun `el pitch enums - agregar un entry al source rompe con KMX026`() {
        KspHarness.assertFailsWithError(
            "KMX026",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            enum class ColorDto { RED }

            @MapTo(ColorDto::class)
            enum class Color { RED, BLUE }
            """.trimIndent(),
        )
    }

    @Test
    fun `MapEntry redirige el emparejamiento de entries`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.MapEntry
            import dev.kmapx.annotations.embedded.MapTo

            enum class ColorDto { CRIMSON }

            @MapTo(ColorDto::class)
            enum class Color { @MapEntry(target = "CRIMSON") RED }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "ColorMappings.kt" }.readText()
        assertTrue(generated.contains("Color.RED -> ColorDto.CRIMSON"), generated)
    }

    @Test
    fun `MapEntry en sede de clase es el fallback de los entries sin par`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.MapEntry
            import dev.kmapx.annotations.embedded.MapTo

            enum class StatusDto { OPEN, UNKNOWN }

            @MapTo(StatusDto::class)
            @MapEntry(target = "UNKNOWN")
            enum class Status { OPEN, ARCHIVED_V1, ARCHIVED_V2 }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "StatusMappings.kt" }.readText()
        assertTrue(!generated.contains("else"), generated)
        assertTrue(generated.contains("Status.OPEN -> StatusDto.OPEN"), generated)
        assertTrue(generated.contains("Status.ARCHIVED_V1 -> StatusDto.UNKNOWN"), generated)
        assertTrue(generated.contains("Status.ARCHIVED_V2 -> StatusDto.UNKNOWN"), generated)
    }

    @Test
    fun `el fallback de clase inexistente produce KMX047`() {
        KspHarness.assertFailsWithError(
            "KMX047",
            """
            package sample
            import dev.kmapx.annotations.MapEntry
            import dev.kmapx.annotations.embedded.MapTo

            enum class StatusDto { OPEN, UNKNOWN }

            @MapTo(StatusDto::class)
            @MapEntry(target = "UNKNWN")
            enum class Status { OPEN, ARCHIVED_V1 }
            """.trimIndent(),
        )
    }

    @Test
    fun `entry extra del target produce warning KMX023 y compila`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            enum class ColorDto { RED, CANCELLED }

            @MapTo(ColorDto::class)
            enum class Color { RED }
            """.trimIndent(),
        )
        assertTrue(result.messages.contains("[KMX023]"), result.messages)
    }

    @Test
    fun `enum como elemento de coleccion usa la funcion generada`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            enum class ColorDto { RED }

            @MapTo(ColorDto::class)
            enum class Color { RED }

            data class PaletteDto(val colors: List<ColorDto>)

            @MapTo(PaletteDto::class)
            data class Palette(val colors: List<Color>)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "PaletteMappings.kt" }.readText()
        assertTrue(generated.contains("colors = colors.map { it.toColorDto() }"), generated)
    }
}
