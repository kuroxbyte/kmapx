@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package dev.kmapx.ksp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ida y vuelta — @BiMapTo (embedded) y @InverseOf (contract): la misma inversión del motor en las dos sedes.
 */
class BidirectionalTest {

    // ── Bidireccional @BiMapTo ─────────────────────────────────────────

    @Test
    fun `BiMapTo genera ambas funciones y el renombre se invierte`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.BiMapTo
            import dev.kmapx.annotations.MapField

            data class PersonDto(@MapField(from = "firstname") val name: String, val age: Int)

            @BiMapTo(PersonDto::class)
            data class Person(val firstname: String, val age: Int)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "PersonMappings.kt" }.readText()
        assertTrue(generated.contains("fun Person.toPersonDto(): PersonDto"), generated)
        assertTrue(generated.contains("fun PersonDto.toPerson(): Person"), generated)
        assertTrue(generated.contains("name = firstname"), generated)
        assertTrue(generated.contains("firstname = name"), generated)
    }

    @Test
    fun `BiMapTo - la politica global satisface la vuelta del ensanchamiento`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.BiMapTo

            data class PersonDto(val nickname: String?)

            @BiMapTo(PersonDto::class)
            data class Person(val nickname: String)
            """.trimIndent(),
            options = mapOf("kmapx.onNull" to "throw"),
        )
        val generated = result.generatedFiles.first { it.name == "PersonMappings.kt" }.readText()
        // Ida: T -> T? silenciosa. Vuelta: la política global cierra el ensanchamiento con throw.
        assertTrue(generated.contains("fun PersonDto.toPerson(): Person"), generated)
        assertTrue(generated.contains("nickname = nickname ?: throw"), generated)
    }

    @Test
    fun `BiMapTo con converter sin inverso produce KMX028`() {
        val result = KspHarness.assertFailsWithError(
            "KMX028",
            """
            package sample
            import dev.kmapx.annotations.embedded.BiMapTo
            import dev.kmapx.annotations.Converter
            import java.time.Instant

            @Converter fun instantToIso(value: Instant): String = value.toString()

            data class EventDto(val at: String)

            @BiMapTo(EventDto::class)
            data class Event(val at: Instant)
            """.trimIndent(),
        )
        assertTrue(result.messages.contains("missing converter"), result.messages)
        assertTrue(result.messages.contains("for the reverse direction"), result.messages)
    }

    @Test
    fun `BiMapTo con ambos converters usa cada uno en su direccion`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.BiMapTo
            import dev.kmapx.annotations.Converter
            import java.time.Instant

            @Converter fun instantToIso(value: Instant): String = value.toString()
            @Converter fun isoToInstant(value: String): Instant = Instant.parse(value)

            data class EventDto(val at: String)

            @BiMapTo(EventDto::class)
            data class Event(val at: Instant)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "EventMappings.kt" }.readText()
        assertTrue(generated.contains("at = instantToIso(at)"), generated)
        assertTrue(generated.contains("at = isoToInstant(at)"), generated)
    }

    @Test
    fun `BiMapTo y MapTo del mismo par colisionan con KMX013`() {
        KspHarness.assertFailsWithError(
            "KMX013",
            """
            package sample
            import dev.kmapx.annotations.embedded.BiMapTo
            import dev.kmapx.annotations.embedded.MapTo

            data class PersonDto(val name: String)

            @BiMapTo(PersonDto::class)
            @MapTo(PersonDto::class)
            data class Person(val name: String)
            """.trimIndent(),
        )
    }

    @Test
    fun `BiMapTo anidado es invertible por construccion`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.BiMapTo

            data class AddressDto(val city: String)

            @BiMapTo(AddressDto::class)
            data class Address(val city: String)

            data class CustomerDto(val name: String, val address: AddressDto)

            @BiMapTo(CustomerDto::class)
            data class Customer(val name: String, val address: Address)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "CustomerMappings.kt" }.readText()
        assertTrue(generated.contains("address = address.toAddressDto()"), generated)
        assertTrue(generated.contains("address = address.toAddress()"), generated)
    }

    @Test
    fun `BiMapTo con value class es invertible sin declaracion extra`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.BiMapTo

            @JvmInline value class UserId(val value: String)

            data class UserDto(val id: String)

            @BiMapTo(UserDto::class)
            data class User(val id: UserId)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "UserMappings.kt" }.readText()
        assertTrue(generated.contains("id = id.value"), generated)
        assertTrue(generated.contains("id = UserId(id)"), generated)
    }

    @Test
    fun `Iterable y Sequence como fuente se materializan a List (hash5)`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class AddressDto(val city: String)
            @MapTo(AddressDto::class) data class Address(val city: String)

            data class Dto(
                val names: List<String>,          // Iterable<String> -> List<String>
                val stops: List<AddressDto>,      // Sequence<Address>  -> List<AddressDto>
            )

            @MapTo(Dto::class)
            data class Src(val names: Iterable<String>, val stops: Sequence<Address>)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        // Iterable.map ya produce List; Sequence.map es lazy → se cierra con .toList().
        assertTrue(generated.contains("names = names.map { it }"), generated)
        assertTrue(generated.contains("stops = stops.map { it.toAddressDto() }.toList()"), generated)
    }

    @Test
    fun `SuppressKmapx silencia un WARNING puntual, y sin el se emite`() {
        val con = """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.SuppressKmapx
            data class Dto(val name: String, @SuppressKmapx("KMX021") val extra: String = "x")
            @MapTo(Dto::class) data class Src(val name: String)
        """.trimIndent()
        assertFalse(KspHarness.assertCompiles(con).messages.contains("KMX021"), "debía silenciarse")

        val sin = """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            data class Dto(val name: String, val extra: String = "x")
            @MapTo(Dto::class) data class Src(val name: String)
        """.trimIndent()
        assertTrue(KspHarness.assertCompiles(sin).messages.contains("KMX021"), "debía avisar")
    }

    @Test
    fun `onNull TYPE_DEFAULT por campo cierra coleccion nullable con emptyList`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.OnNull

            data class Dto(@MapField(onNull = OnNull.TYPE_DEFAULT) val tags: List<String>, val name: String)

            @MapTo(Dto::class)
            data class Src(val tags: List<String>?, val name: String)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("tags = tags ?: emptyList()"), generated)
    }

    @Test
    fun `onNull TYPE_DEFAULT fuera de la lista cerrada produce KMX033`() {
        KspHarness.assertFailsWithError(
            "KMX033",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.OnNull

            data class Meta(val note: String)

            data class Dto(@MapField(onNull = OnNull.TYPE_DEFAULT) val meta: Meta)

            @MapTo(Dto::class)
            data class Src(val meta: Meta?)
            """.trimIndent(),
        )
    }

    @Test
    fun `onNull TYPE_DEFAULT en escalares usa el cero del tipo - lista cerrada`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.OnNull

            data class Dto(
                @MapField(onNull = OnNull.TYPE_DEFAULT) val age: Int,
                @MapField(onNull = OnNull.TYPE_DEFAULT) val nickname: String,
                @MapField(onNull = OnNull.TYPE_DEFAULT) val active: Boolean,
                @MapField(onNull = OnNull.TYPE_DEFAULT) val score: Double,
            )

            @MapTo(Dto::class)
            data class Src(val age: Int?, val nickname: String?, val active: Boolean?, val score: Double?)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("age = age ?: 0"), generated)
        assertTrue(generated.contains("nickname = nickname ?: \"\""), generated)
        assertTrue(generated.contains("active = active ?: false"), generated)
        assertTrue(generated.contains("score = score ?: 0.0"), generated)
    }

    @Test
    fun `cascada - mapeo TYPE_DEFAULT sobre global THROW - colecciones caen a empty y el resto lanza`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.OnNull

            data class Meta(val note: String)

            data class Dto(val meta: Meta, val tags: List<String>)

            @MapTo(Dto::class, onNull = OnNull.TYPE_DEFAULT)
            data class Src(val meta: Meta?, val tags: List<String>?)
            """.trimIndent(),
            options = mapOf("kmapx.onNull" to "throw"),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        // La condicional TYPE_DEFAULT aplica donde puede (colección/escalar; Meta no tiene
        // default de tipo) y donde no, CAE al global:
        assertTrue(generated.contains("tags = tags ?: emptyList()"), generated)   // nivel mapeo
        assertTrue(generated.contains("meta = meta ?: throw"), generated)         // nivel global
    }

    @Test
    fun `config global - sin opciones el mismo mapeo falla con KMX003 (default STRICT)`() {
        KspHarness.assertFailsWithError(
            "KMX003",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val name: String, val tags: List<String>)

            @MapTo(Dto::class)
            data class Src(val name: String?, val tags: List<String>?)
            """.trimIndent(),
        )
    }

    // ── @InverseOf — inverso en modo B ────────────────────────

    @Test
    fun `InverseOf hereda la config del forward INVERTIDA - el rename se voltea`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.contract.InverseOf
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.contract.Mapper

            data class Customer(val name: String, val age: Int)
            data class CustomerDto(val displayName: String, val age: Int)

            @Mapper
            interface CustomerMapper {
                @MapField(target = "displayName", from = "name")
                fun toDto(c: Customer): CustomerDto

                @InverseOf("toDto")
                fun fromDto(dto: CustomerDto): Customer
            }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "CustomerMapperImpl.kt" }.readText()
        // Ida: displayName = name. Vuelta (invertida por el motor): name = displayName.
        assertTrue(generated.contains("displayName = c.name"), generated)
        assertTrue(generated.contains("name = dto.displayName"), generated)
    }

    @Test
    fun `InverseOf sin nombre auto-detecta la unica firma inversa`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.contract.InverseOf
            import dev.kmapx.annotations.contract.Mapper

            data class A(val name: String)
            data class B(val name: String)

            @Mapper
            interface M {
                fun toB(a: A): B
                @InverseOf
                fun toA(b: B): A
            }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "MImpl.kt" }.readText()
        assertTrue(generated.contains("override fun toA(b: B): A"), generated)
    }

    @Test
    fun `InverseOf con ensanchamiento sin estrategia de vuelta produce KMX028`() {
        KspHarness.assertFailsWithError(
            "KMX028",
            """
            package sample
            import dev.kmapx.annotations.contract.InverseOf
            import dev.kmapx.annotations.contract.Mapper

            data class Customer(val nickname: String)
            data class CustomerDto(val nickname: String?)

            @Mapper
            interface M {
                fun toDto(c: Customer): CustomerDto
                @InverseOf("toDto")
                fun fromDto(dto: CustomerDto): Customer
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `InverseOf con la politica del mapper cierra la vuelta`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.contract.InverseOf
            import dev.kmapx.annotations.contract.Mapper
            import dev.kmapx.annotations.OnNull

            data class Customer(val nickname: String)
            data class CustomerDto(val nickname: String?)

            @Mapper(onNull = OnNull.THROW)
            interface M {
                fun toDto(c: Customer): CustomerDto
                @InverseOf("toDto")
                fun fromDto(dto: CustomerDto): Customer
            }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "MImpl.kt" }.readText()
        assertTrue(generated.contains("nickname ?: throw"), generated)
    }

    @Test
    fun `InverseOf con nombre inexistente produce KMX045`() {
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
                fun toB(a: A): B
                @InverseOf("toDto")
                fun toA(b: B): A
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `InverseOf con MapField propia produce KMX045`() {
        KspHarness.assertFailsWithError(
            "KMX045",
            """
            package sample
            import dev.kmapx.annotations.contract.InverseOf
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.contract.Mapper

            data class A(val name: String)
            data class B(val name: String)

            @Mapper
            interface M {
                fun toB(a: A): B
                @InverseOf("toB")
                @MapField(target = "name", from = "name")
                fun toA(b: B): A
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `los overrides MapEntry se invierten en un BiMapTo de enums`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.MapEntry
            import dev.kmapx.annotations.embedded.BiMapTo

            enum class CategoryDto { BOOKS, HOME_GARDEN }

            @BiMapTo(CategoryDto::class)
            enum class Category { BOOKS, @MapEntry(target = "HOME_GARDEN") HOME_AND_GARDEN }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "CategoryMappings.kt" }.readText()
        assertTrue(generated.contains("Category.HOME_AND_GARDEN -> CategoryDto.HOME_GARDEN"), generated)
        assertTrue(generated.contains("CategoryDto.HOME_GARDEN -> Category.HOME_AND_GARDEN"), generated)
        assertTrue(!generated.contains("else"), generated)
    }

    @Test
    fun `el fallback de clase es fan-in y BiMapTo lo rechaza con KMX028`() {
        KspHarness.assertFailsWithError(
            "KMX028",
            """
            package sample
            import dev.kmapx.annotations.MapEntry
            import dev.kmapx.annotations.embedded.BiMapTo

            enum class StatusDto { OPEN, UNKNOWN }

            @BiMapTo(StatusDto::class)
            @MapEntry(target = "UNKNOWN")
            enum class Status { OPEN, ARCHIVED_V1, ARCHIVED_V2 }
            """.trimIndent(),
        )
    }
}
