@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package dev.kmapx.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Config por campo — renombres y rutas, converters, ignore, @MapperConfig y errores exhaustivos.
 */
class FieldConfigTest {

    // ── Renombrado plano @MapField(from = "...") ───────────────────────────

    @Test
    fun `renombre basico redirige el matching`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapField

            data class Dto(@MapField(from = "firstname") val name: String)

            @MapTo(Dto::class)
            data class Src(val firstname: String)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("name = firstname"), generated)
    }

    @Test
    fun `from inexistente produce KMX011 con did-you-mean`() {
        val result = KspHarness.assertFailsWithError(
            "KMX011",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapField

            data class Dto(@MapField(from = "firstnme") val name: String)

            @MapTo(Dto::class)
            data class Src(val firstname: String)
            """.trimIndent(),
        )
        assertTrue(result.messages.contains("Did you mean 'firstname'?"), result.messages)
    }

    @Test
    fun `sintaxis de ruta malformada produce KMX020`() {
        KspHarness.assertFailsWithError(
            "KMX020",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapField

            data class Dto(@MapField(from = "address..city") val city: String)

            @MapTo(Dto::class)
            data class Src(val address: String)
            """.trimIndent(),
        )
    }

    // ── Rutas anidadas ────────────────────────────────────────────────

    @Test
    fun `aplanado basico y profundo con nullable intermedio`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapField

            data class Country(val code: String)
            data class Address(val city: String, val country: Country?)
            data class Customer(val name: String, val address: Address)

            data class Dto(
                @MapField(from = "address.city") val city: String,
                @MapField(from = "address.country.code") val countryCode: String?,
            )

            @MapTo(Dto::class)
            data class Src(val name: String, val address: Address)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("city = address.city"), generated)
        assertTrue(generated.contains("countryCode = address.country?.code"), generated)
    }

    @Test
    fun `segmento nullable hacia target no-nullable exige estrategia y la aplica`() {
        val fails = KspHarness.assertFailsWithError(
            "KMX003",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapField

            data class Address(val city: String)
            data class Dto(@MapField(from = "address.city") val city: String)

            @MapTo(Dto::class)
            data class Src(val address: Address?)
            """.trimIndent(),
        )
        assertTrue(fails.messages.contains("segment 'address' is nullable"), fails.messages)

        val ok = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.OnNull

            data class Address(val city: String)
            data class Dto(@MapField(from = "address.city", onNull = OnNull.THROW) val city: String)

            @MapTo(Dto::class)
            data class Src(val address: Address?)
            """.trimIndent(),
        )
        val generated = ok.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("city = address?.city ?: throw IllegalArgumentException"), generated)
    }

    @Test
    fun `segmento inexistente produce KMX011 con did-you-mean del tipo correcto`() {
        val result = KspHarness.assertFailsWithError(
            "KMX011",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapField

            data class Address(val city: String)
            data class Dto(@MapField(from = "address.cty") val city: String)

            @MapTo(Dto::class)
            data class Src(val address: Address)
            """.trimIndent(),
        )
        assertTrue(result.messages.contains("'cty' does not exist on Address"), result.messages)
        assertTrue(result.messages.contains("Did you mean 'city'?"), result.messages)
    }

    @Test
    fun `ruta con conversion al final y fan-out de rutas`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapField

            @JvmInline value class Zip(val value: String)

            data class Address(val zip: Zip)
            data class Dto(
                @MapField(from = "address.zip") val zip: String,
                @MapField(from = "address.zip") val zipCopy: String,
            )

            @MapTo(Dto::class)
            data class Src(val address: Address)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("zip = address.zip.value"), generated)
        assertTrue(generated.contains("zipCopy = address.zip.value"), generated)
    }

    @Test
    fun `ruta en PATCH lee del patch con fallback plano`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.contract.Mapper

            data class Meta(val note: String)
            data class TaskPatch(val meta: Meta?)
            data class Task(val id: String, @MapField(from = "meta.note") val note: String)

            @Mapper
            interface TaskPatcher { fun apply(target: Task, patch: TaskPatch): Task }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "TaskPatcherImpl.kt" }.readText()
        assertTrue(generated.contains("note = patch.meta?.note ?: target.note"), generated)
    }

    @Test
    fun `fan-out - dos params desde el mismo from`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapField

            data class Dto(
                @MapField(from = "firstname") val display: String,
                @MapField(from = "firstname") val sortKey: String,
            )

            @MapTo(Dto::class)
            data class Src(val firstname: String)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("display = firstname"), generated)
        assertTrue(generated.contains("sortKey = firstname"), generated)
    }

    @Test
    fun `renombre + converter`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.Converter
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapField
            import java.time.Instant

            @Converter fun instantToIso(value: Instant): String = value.toString()

            data class Dto(@MapField(from = "created") val createdAt: String)

            @MapTo(Dto::class)
            data class Src(val created: Instant)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("createdAt = instantToIso(created)"), generated)
    }

    // ── Converters ─────────────────────────────────────────────────────

    @Test
    fun `Converter registrado y usado - Plan 1 del ejemplo end-to-end`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.Converter
            import dev.kmapx.annotations.embedded.MapTo
            import java.time.Instant

            @Converter fun instantToIso(value: Instant): String = value.toString()

            data class Dto(val createdAt: String)

            @MapTo(Dto::class)
            data class Src(val createdAt: Instant)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("createdAt = instantToIso(createdAt)"), generated)
    }

    @Test
    fun `dos converters del mismo par producen KMX009`() {
        val result = KspHarness.assertFailsWithError(
            "KMX009",
            """
            package sample
            import dev.kmapx.annotations.Converter
            import dev.kmapx.annotations.embedded.MapTo
            import java.time.Instant

            @Converter fun instantToIso(value: Instant): String = value.toString()
            @Converter fun instantToEpoch(value: Instant): String = value.epochSecond.toString()

            data class Dto(val createdAt: String)

            @MapTo(Dto::class)
            data class Src(val createdAt: Instant)
            """.trimIndent(),
        )
        assertTrue(result.messages.contains("instantToIso"), result.messages)
        assertTrue(result.messages.contains("instantToEpoch"), result.messages)
    }

    @Test
    fun `firmas invalidas de Converter producen KMX019`() {
        // 2 parámetros:
        KspHarness.assertFailsWithError(
            "KMX019",
            """
            package sample
            import dev.kmapx.annotations.Converter
            import dev.kmapx.annotations.embedded.MapTo

            @Converter fun bad(a: Int, b: Int): String = "x"

            data class Dto(val code: String)
            @MapTo(Dto::class) data class Src(val code: String)
            """.trimIndent(),
        )
        // Retorno Unit:
        KspHarness.assertFailsWithError(
            "KMX019",
            """
            package sample
            import dev.kmapx.annotations.Converter
            import dev.kmapx.annotations.embedded.MapTo

            @Converter fun bad(a: Int) { }

            data class Dto(val code: String)
            @MapTo(Dto::class) data class Src(val code: String)
            """.trimIndent(),
        )
        // suspend:
        KspHarness.assertFailsWithError(
            "KMX019",
            """
            package sample
            import dev.kmapx.annotations.Converter
            import dev.kmapx.annotations.embedded.MapTo

            @Converter suspend fun bad(a: Int): String = "x"

            data class Dto(val code: String)
            @MapTo(Dto::class) data class Src(val code: String)
            """.trimIndent(),
        )
    }

    @Test
    fun `converter con par nullable se envuelve con let`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.Converter
            import dev.kmapx.annotations.embedded.MapTo
            import java.time.Instant

            @Converter fun instantToIso(value: Instant): String = value.toString()

            data class Dto(val createdAt: String?)

            @MapTo(Dto::class)
            data class Src(val createdAt: Instant?)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("createdAt = createdAt?.let { instantToIso(it) }"), generated)
    }

    @Test
    fun `elemento de coleccion convertido via converter`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.Converter
            import dev.kmapx.annotations.embedded.MapTo
            import java.time.Instant

            @Converter fun instantToIso(value: Instant): String = value.toString()

            data class Dto(val stamps: List<String>)

            @MapTo(Dto::class)
            data class Src(val stamps: List<Instant>)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("stamps = stamps.map { instantToIso(it) }"), generated)
    }

    // ── Aspecto ignore ────────────────────────────────────────

    @Test
    fun `ignore por campo omite el argumento en silencio - sin KMX021 ni KMX002`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val name: String, @MapField(ignore = true) val createdAt: String? = null)

            @MapTo(Dto::class)
            data class Src(val name: String)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(!generated.contains("createdAt"), generated)
        // El ignore ES el consentimiento: ni KMX002 (sin fuente) ni el warning KMX021.
        assertTrue(!result.messages.contains("[KMX021]"), result.messages)
    }

    @Test
    fun `ignore sin default de constructor produce KMX042`() {
        KspHarness.assertFailsWithError(
            "KMX042",
            """
            package sample
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(@MapField(ignore = true) val createdAt: String)

            @MapTo(Dto::class)
            data class Src(val createdAt: String)
            """.trimIndent(),
        )
    }

    @Test
    fun `ignore junto a otros aspectos produce KMX043`() {
        KspHarness.assertFailsWithError(
            "KMX043",
            """
            package sample
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(@MapField(ignore = true, from = "other") val createdAt: String? = null)

            @MapTo(Dto::class)
            data class Src(val other: String)
            """.trimIndent(),
        )
    }

    @Test
    fun `lista ignore de MapTo excluye en bulk y valida nombres`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val name: String, val createdAt: String? = null, val updatedAt: String? = null)

            @MapTo(Dto::class, ignore = ["createdAt", "updatedAt"])
            data class Src(val name: String, val createdAt: String, val updatedAt: String)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(!generated.contains("createdAt"), generated)
        assertTrue(!generated.contains("updatedAt"), generated)
    }

    @Test
    fun `lista ignore con nombre inexistente produce KMX011 con did-you-mean`() {
        val result = KspHarness.assertFailsWithError(
            "KMX011",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val name: String, val createdAt: String? = null)

            @MapTo(Dto::class, ignore = ["createdAtt"])
            data class Src(val name: String, val createdAt: String)
            """.trimIndent(),
        )
        assertTrue(result.messages.contains("createdAt"), result.messages)
    }

    @Test
    fun `lista ignore de Mapper aplica a todos los metodos del modo B`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.contract.Mapper

            data class Src(val name: String, val createdAt: String)
            data class Dto(val name: String, val createdAt: String? = null)

            @Mapper(ignore = ["createdAt"])
            interface M {
                fun toDto(s: Src): Dto
            }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "MImpl.kt" }.readText()
        assertTrue(!generated.contains("createdAt"), generated)
    }

    @Test
    fun `lista ignore de Mapper con targets heterogeneos - basta que exista en UN target (revisión)`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.contract.Mapper

            data class Src(val name: String, val audit: String)
            data class Dto(val name: String, val audit: String? = null)
            data class Summary(val name: String)

            @Mapper(ignore = ["audit"])
            interface M {
                fun toDto(s: Src): Dto          // audit existe aquí
                fun toSummary(s: Src): Summary  // y aquí NO — no debe ser KMX011
            }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "MImpl.kt" }.readText()
        assertTrue(!generated.contains("audit"), generated)
    }

    @Test
    fun `dos InverseOf apuntandose mutuamente produce KMX045 (revisión)`() {
        KspHarness.assertFailsWithError(
            "KMX045",
            """
            package sample
            import dev.kmapx.annotations.contract.InverseOf
            import dev.kmapx.annotations.contract.Mapper

            data class A(val name: String)
            data class B(val name: String)

            @Mapper
            interface M {
                @InverseOf("toA")
                fun toB(a: A): B
                @InverseOf("toB")
                fun toA(b: B): A
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `ciclo de mapeo a traves de valores de Map produce KMX008 (revisión)`() {
        KspHarness.assertFailsWithError(
            "KMX008",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class ADto(val items: Map<String, BDto>)
            data class BDto(val items: Map<String, ADto>)

            @MapTo(ADto::class)
            data class A(val items: Map<String, B>)

            @MapTo(BDto::class)
            data class B(val items: Map<String, A>)
            """.trimIndent(),
        )
    }

    @Test
    fun `PATCH honra el renombre plano declarado en el val de constructor (revisión)`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.contract.Mapper

            data class Vehicle(@MapField(from = "plate") val plateNumber: String, val year: Int)
            data class VehiclePatch(val plate: String?, val year: Int?)

            @Mapper
            interface P {
                fun apply(target: Vehicle, patch: VehiclePatch): Vehicle
            }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "PImpl.kt" }.readText()
        assertTrue(generated.contains("plateNumber = patch.plate ?: target.plateNumber"), generated)
    }

    @Test
    fun `BiMapTo con campo ignorado produce KMX028`() {
        KspHarness.assertFailsWithError(
            "KMX028",
            """
            package sample
            import dev.kmapx.annotations.embedded.BiMapTo
            import dev.kmapx.annotations.MapField

            data class PersonDto(val name: String, @MapField(ignore = true) val audit: String? = null)

            @BiMapTo(PersonDto::class)
            data class Person(val name: String, val audit: String?)
            """.trimIndent(),
        )
    }

    // ── Profiles @MapperConfig ─────────────────────────────────────────────

    @Test
    fun `MapperConfig aporta componentModel, onNull e ignore al mapper`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.contract.Mapper
            import dev.kmapx.annotations.contract.MapperConfig
            import dev.kmapx.annotations.contract.ComponentModel
            import dev.kmapx.annotations.OnNull

            @MapperConfig(
                componentModel = ComponentModel.SPRING,
                onNull = OnNull.THROW,
                ignore = ["audit"],
            )
            interface CompanyConfig

            data class Src(val name: String, val nickname: String?, val audit: String)
            data class Dto(val name: String, val nickname: String, val audit: String? = null)

            @Mapper(config = CompanyConfig::class)
            interface M {
                fun toDto(s: Src): Dto
            }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "MImpl.kt" }.readText()
        // componentModel del profile → class @Component (no object):
        assertTrue(generated.contains("public class MImpl : M"), generated)
        assertTrue(generated.contains("@Component"), generated)
        // onNull del profile (nivel entre mapper y global) → throw:
        assertTrue(generated.contains("nickname ?: throw"), generated)
        // ignore del profile → excluido:
        assertTrue(!generated.contains("audit"), generated)
    }

    @Test
    fun `el mapper gana sobre el profile - NONE explicito sobre SPRING`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.contract.Mapper
            import dev.kmapx.annotations.contract.MapperConfig
            import dev.kmapx.annotations.contract.ComponentModel

            @MapperConfig(componentModel = ComponentModel.SPRING)
            interface CompanyConfig

            data class Src(val name: String)
            data class Dto(val name: String)

            @Mapper(config = CompanyConfig::class, componentModel = ComponentModel.NONE)
            interface M {
                fun toDto(s: Src): Dto
            }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "MImpl.kt" }.readText()
        // NONE EXPLÍCITO (≠ INHERIT) pisa al profile — la lección de ComponentModel.INHERIT:
        assertTrue(generated.contains("public object MImpl : M"), generated)
        assertTrue(!generated.contains("@Component"), generated)
    }

    @Test
    fun `STRICT en el mapper corta el THROW del profile`() {
        KspHarness.assertFailsWithError(
            "KMX003",
            """
            package sample
            import dev.kmapx.annotations.contract.Mapper
            import dev.kmapx.annotations.contract.MapperConfig
            import dev.kmapx.annotations.OnNull

            @MapperConfig(onNull = OnNull.THROW)
            interface CompanyConfig

            data class Src(val nickname: String?)
            data class Dto(val nickname: String)

            @Mapper(config = CompanyConfig::class, onNull = OnNull.STRICT)
            interface M {
                fun toDto(s: Src): Dto
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `config que no es MapperConfig produce KMX044`() {
        KspHarness.assertFailsWithError(
            "KMX044",
            """
            package sample
            import dev.kmapx.annotations.contract.Mapper

            interface NotAProfile

            data class Src(val name: String)
            data class Dto(val name: String)

            @Mapper(config = NotAProfile::class)
            interface M {
                fun toDto(s: Src): Dto
            }
            """.trimIndent(),
        )
    }

    // ── Errores exhaustivos ────────────────────────────────────────────

    @Test
    fun `tres errores distintos reportados en una sola compilacion`() {
        val result = KspHarness.compile(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val fullName: String, val age: Int, val email: String)

            @MapTo(Dto::class)
            data class Src(val fulName: String, val age: String)
            """.trimIndent(),
        )
        assertTrue(result.exitCode != KotlinCompilation.ExitCode.OK)
        // Nunca "arregla uno, descubre el siguiente": los 3 en la misma ronda.
        assertTrue(result.messages.contains("Dto.fullName"), result.messages)
        assertTrue(result.messages.contains("Did you mean 'fulName'?"), result.messages)
        assertTrue(result.messages.contains("[KMX004]"), result.messages)
        assertTrue(result.messages.contains("Dto.email"), result.messages)
    }

    @Test
    fun `la ubicacion del error es la linea del parametro target, no la clase source`() {
        val result = KspHarness.assertFailsWithError(
            "KMX002",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(
                val name: String,
                val age: Int,
            )

            @MapTo(Dto::class)
            data class Src(val name: String)
            """.trimIndent(),
        )
        // `age` está declarado en la línea 6 de Input.kt; el diagnóstico debe señalarla:
        assertTrue(
            Regex("""Input\.kt:6""").containsMatchIn(result.messages),
            "el error no señala la línea del parámetro target:\n${result.messages}",
        )
    }
}
