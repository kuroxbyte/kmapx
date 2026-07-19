@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package dev.kmapx.ksp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Valores compuestos — conversiones implícitas, anidados + ciclos KMX008, Map/arrays/Result.
 */
class ContainersAndNestedTest {

    // ── Conversiones implícitas (lista cerrada) ────────────────────────

    @Test
    fun `lista con mapper de elemento declarado - un solo map`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class AddressDto(val city: String)

            @MapTo(AddressDto::class)
            data class Address(val city: String)

            data class Dto(val addresses: List<AddressDto>)

            @MapTo(Dto::class)
            data class Src(val addresses: List<Address>)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("addresses = addresses.map { it.toAddressDto() }"), generated)
    }

    @Test
    fun `lista identica es referencia directa, sin map ni copia`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val tags: List<String>)

            @MapTo(Dto::class)
            data class Src(val tags: List<String>)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("tags = tags,"), generated)
        assertTrue(!generated.contains(".map"), generated)
    }

    @Test
    fun `estrategia del parametro aplica al elemento de la coleccion`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.OnNull

            data class Dto(@MapField(onNull = OnNull.LITERAL, default = "N/A") val tags: List<String>)

            @MapTo(Dto::class)
            data class Src(val tags: List<String?>)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("""tags = tags.map { it ?: "N/A" }"""), generated)
    }

    @Test
    fun `lista de nullables a lista de no-nullables sin estrategia produce KMX003`() {
        KspHarness.assertFailsWithError(
            "KMX003",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val tags: List<String>)

            @MapTo(Dto::class)
            data class Src(val tags: List<String?>)
            """.trimIndent(),
        )
    }

    @Test
    fun `Long a Int (narrowing) NO es implicito - KMX004, el caso emblema`() {
        // Int→Long pasó a ser widening implícito; el emblema de la lista
        // cerrada ahora es el NARROWING: perder bits jamás será silencioso.
        val result = KspHarness.assertFailsWithError(
            "KMX004",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val total: Int)

            @MapTo(Dto::class)
            data class Src(val total: Long)
            """.trimIndent(),
        )
        assertTrue(result.messages.contains("cannot convert kotlin.Long to kotlin.Int"), result.messages)
    }

    @Test
    fun `enum a String NO es implicito - KMX004`() {
        KspHarness.assertFailsWithError(
            "KMX004",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            enum class Color { RED }

            data class Dto(val color: String)

            @MapTo(Dto::class)
            data class Src(val color: Color)
            """.trimIndent(),
        )
    }

    @Test
    fun `anidamiento List de List con una lambda por nivel compila`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class AddressDto(val city: String)

            @MapTo(AddressDto::class)
            data class Address(val city: String)

            data class Dto(val grid: List<List<AddressDto>>)

            @MapTo(Dto::class)
            data class Src(val grid: List<List<Address>>)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("grid = grid.map { it.map { it.toAddressDto() } }"), generated)
    }

    // ── Anidados automáticos ───────────────────────────────────────────

    @Test
    fun `anidado top-level usa el mapper declarado por referencia`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class AddressDto(val city: String)

            @MapTo(AddressDto::class)
            data class Address(val city: String)

            data class CustomerDto(val name: String, val address: AddressDto)

            @MapTo(CustomerDto::class)
            data class Customer(val name: String, val address: Address)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "CustomerMappings.kt" }.readText()
        assertTrue(generated.contains("address = address.toAddressDto()"), generated)
    }

    @Test
    fun `anidado sin declaracion produce KMX007 verbatim (ejemplo maestro)`() {
        val result = KspHarness.assertFailsWithError(
            "KMX007",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class AddressDto(val city: String)
            data class Address(val city: String)

            data class CustomerDto(val address: AddressDto)

            @MapTo(CustomerDto::class)
            data class Customer(val address: Address)
            """.trimIndent(),
        )
        assertTrue(result.messages.contains("no mapping found for Address -> AddressDto"), result.messages)
        assertTrue(result.messages.contains("annotate Address with @MapTo(AddressDto::class)"), result.messages)
    }

    @Test
    fun `anidado nullable compone con interrogacion-punto`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class AddressDto(val city: String)

            @MapTo(AddressDto::class)
            data class Address(val city: String)

            data class CustomerDto(val address: AddressDto?)

            @MapTo(CustomerDto::class)
            data class Customer(val address: Address?)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "CustomerMappings.kt" }.readText()
        assertTrue(generated.contains("address = address?.toAddressDto()"), generated)
    }

    @Test
    fun `ciclo directo produce KMX008 con camino completo`() {
        val result = KspHarness.assertFailsWithError(
            "KMX008",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class PersonDto(val name: String, val address: AddressDto)
            data class AddressDto(val city: String, val owner: PersonDto)

            @MapTo(PersonDto::class)
            data class Person(val name: String, val address: Address)

            @MapTo(AddressDto::class)
            data class Address(val city: String, val owner: Person)
            """.trimIndent(),
        )
        assertTrue(result.messages.contains("mapping cycle detected"), result.messages)
        assertTrue(
            result.messages.contains("Person -> Address -> Person") ||
                result.messages.contains("Address -> Person -> Address"),
            result.messages,
        )
    }

    @Test
    fun `ciclo indirecto A-B-C-A tambien se detecta`() {
        KspHarness.assertFailsWithError(
            "KMX008",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class ADto(val b: BDto)
            data class BDto(val c: CDto)
            data class CDto(val a: ADto)

            @MapTo(ADto::class) data class A(val b: B)
            @MapTo(BDto::class) data class B(val c: C)
            @MapTo(CDto::class) data class C(val a: A)
            """.trimIndent(),
        )
    }

    @Test
    fun `anidado en PATCH con fallback`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.contract.Mapper

            data class Address(val city: String)

            @MapTo(Address::class)
            data class AddressDto(val city: String)

            data class Customer(val name: String, val address: Address)
            data class CustomerPatch(val name: String?, val address: AddressDto?)

            @Mapper
            interface CustomerPatcher { fun apply(target: Customer, patch: CustomerPatch): Customer }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "CustomerPatcherImpl.kt" }.readText()
        assertTrue(generated.contains("address = patch.address?.toAddress() ?: target.address"), generated)
    }

    @Test
    fun `PATCH con Patch tri-estado permite setear null (hash6)`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.contract.Mapper
            import dev.kmapx.runtime.Patch

            data class Product(val name: String, val note: String?)
            data class ProductPatch(val name: String?, val note: Patch<String?> = Patch.Keep)

            @Mapper
            interface ProductPatcher { fun apply(target: Product, patch: ProductPatch): Product }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "ProductPatcherImpl.kt" }.readText()
        // name: semántica Merge-Patch de siempre (null = no tocar).
        assertTrue(generated.contains("name = patch.name ?: target.name"), generated)
        // note: tri-estado — Keep conserva, Set (incluido Set(null)) asigna.
        assertTrue(generated.contains("when (val p = patch.note)"), generated)
        assertTrue(generated.contains("Keep -> target.note"), generated)
        assertTrue(generated.contains("is") && generated.contains("Set -> p.value"), generated)
    }

    @Test
    fun `PATCH tri-estado - runtime - Set(null) borra, Keep conserva (hash6)`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.contract.Mapper
            import dev.kmapx.runtime.Patch

            data class Product(val name: String, val note: String?)
            data class ProductPatch(val note: Patch<String?> = Patch.Keep)

            @Mapper
            interface ProductPatcher { fun apply(target: Product, patch: ProductPatch): Product }

            fun check(): String {
                val p = ProductPatcherImpl
                val base = Product("x", "old")
                val kept = p.apply(base, ProductPatch(Patch.Keep)).note
                val cleared = p.apply(base, ProductPatch(Patch.Set(null))).note
                val set = p.apply(base, ProductPatch(Patch.Set("new"))).note
                return "kept=" + kept + " cleared=" + cleared + " set=" + set
            }
            """.trimIndent(),
        )
        val cls = result.classLoader!!.loadClass("sample.InputKt")
        val out = cls.getMethod("check").invoke(null) as String
        assertEquals("kept=old cleared=null set=new", out)
    }

    @Test
    fun `enum en PATCH con fallback - cierra`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.contract.Mapper

            enum class Status { OPEN, CLOSED }

            @MapTo(Status::class)
            enum class StatusDto { OPEN, CLOSED }

            data class Task(val id: String, val status: Status)
            data class TaskPatch(val status: StatusDto?)

            @Mapper
            interface TaskPatcher { fun apply(target: Task, patch: TaskPatch): Task }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "TaskPatcherImpl.kt" }.readText()
        assertTrue(generated.contains("status = patch.status?.toStatus() ?: target.status"), generated)
    }

    // ── Map, arrays, Result ────────────────────────────────────────────

    @Test
    fun `Map con valor mapeado emite mapValues y clave+valor emite buildMap`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            @JvmInline value class Sku(val value: String)

            data class AmountDto(val cents: Long)

            @MapTo(AmountDto::class)
            data class Amount(val cents: Long)

            data class Dto(val prices: Map<String, AmountDto>, val bySku: Map<String, AmountDto>)

            @MapTo(Dto::class)
            data class Src(val prices: Map<String, Amount>, val bySku: Map<Sku, Amount>)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("prices = prices.mapValues { (_, v) -> v.toAmountDto() }"), generated)
        assertTrue(generated.contains("bySku = buildMap { for ((k, v) in bySku) put(k.value, v.toAmountDto()) }"), generated)
    }

    @Test
    fun `Map identico es referencia directa`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val tags: Map<String, Int>)

            @MapTo(Dto::class)
            data class Src(val tags: Map<String, Int>)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("tags = tags,"), generated)
        assertTrue(!generated.contains("mapValues"), generated)
    }

    @Test
    fun `Array con mapper de elemento y IntArray es KMX004 - el caso emblema`() {
        val ok = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class AddressDto(val city: String)

            @MapTo(AddressDto::class)
            data class Address(val city: String)

            data class Dto(val stops: Array<AddressDto>)

            @MapTo(Dto::class)
            data class Src(val stops: Array<Address>)
            """.trimIndent(),
        )
        val generated = ok.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("stops = stops.map { it.toAddressDto() }.toTypedArray()"), generated)

        KspHarness.assertFailsWithError(
            "KMX004",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val values: LongArray)

            @MapTo(Dto::class)
            data class Src(val values: IntArray)
            """.trimIndent(),
        )
    }

    @Test
    fun `Result con converter de elemento emite map`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.Converter
            import dev.kmapx.annotations.embedded.MapTo
            import java.time.Instant

            @Converter fun instantToIso(value: Instant): String = value.toString()

            data class Dto(val at: Result<String>)

            @MapTo(Dto::class)
            data class Src(val at: Result<Instant>)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(generated.contains("at = at.map { instantToIso(it) }"), generated)
    }

    @Test
    fun `Map anidado - una lambda por nivel`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class AddressDto(val city: String)

            @MapTo(AddressDto::class)
            data class Address(val city: String)

            data class Dto(val routes: Map<String, List<AddressDto>>)

            @MapTo(Dto::class)
            data class Src(val routes: Map<String, List<Address>>)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        assertTrue(
            generated.contains("routes = routes.mapValues { (_, v) -> v.map { it.toAddressDto() } }"),
            generated,
        )
    }

    @Test
    fun `Map a List NO es implicito - KMX004 (cierre de la lista)`() {
        KspHarness.assertFailsWithError(
            "KMX004",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo

            data class Dto(val entries: List<String>)

            @MapTo(Dto::class)
            data class Src(val entries: Map<String, String>)
            """.trimIndent(),
        )
    }
}
