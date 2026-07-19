@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package dev.kmapx.ksp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Modo CONTRACT — interfaz @Mapper, integraciones Spring/Koin/serialization y reporte de cobertura.
 */
class ContractModeTest {

    // ── Modo interfaz @Mapper ─────────────────────────────────────────

    @Test
    fun `interfaz sin deps y componentModel NONE genera object`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.contract.Mapper

            data class PersonDto(val name: String)

            @MapTo(PersonDto::class)
            data class Person(val name: String)

            @Mapper
            interface PersonMapper { fun toDto(p: Person): PersonDto }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "PersonMapperImpl.kt" }.readText()
        assertTrue(generated.contains("public object PersonMapperImpl : PersonMapper"), generated)
    }

    @Test
    fun `metodo delega en la extension existente - una sola logica`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.contract.Mapper

            data class PersonDto(val name: String)

            @MapTo(PersonDto::class)
            data class Person(val name: String)

            @Mapper
            interface PersonMapper { fun toDto(p: Person): PersonDto }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "PersonMapperImpl.kt" }.readText()
        assertTrue(generated.contains("p.toPersonDto()"), generated)
        // La lógica vive en la extension; el impl NO construye el target (sin argumentos nombrados):
        assertTrue(!generated.contains("name = "), generated)
    }

    @Test
    fun `metodo sin MapTo correspondiente genera plan propio inline`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.contract.Mapper

            data class PersonDto(val name: String)
            data class Person(val name: String)

            @Mapper
            interface PersonMapper { fun toDto(p: Person): PersonDto }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "PersonMapperImpl.kt" }.readText()
        assertTrue(generated.contains("PersonDto("), generated)
        assertTrue(generated.contains("name = p.name"), generated)
    }

    @Test
    fun `post-funcion invocada y su retorno usado`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.contract.Mapper

            data class PersonDto(val name: String)

            @MapTo(PersonDto::class)
            data class Person(val name: String)

            @Mapper
            interface PersonMapper {
                fun toDto(p: Person): PersonDto
                fun afterToDto(source: Person, result: PersonDto): PersonDto =
                    result.copy(name = result.name.trim())
            }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "PersonMapperImpl.kt" }.readText()
        assertTrue(generated.contains("afterToDto(p, p.toPersonDto())"), generated)
    }

    @Test
    fun `post-funcion con firma incorrecta produce KMX014`() {
        KspHarness.assertFailsWithError(
            "KMX014",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.contract.Mapper

            data class PersonDto(val name: String)

            @MapTo(PersonDto::class)
            data class Person(val name: String)

            @Mapper
            interface PersonMapper {
                fun toDto(p: Person): PersonDto
                fun afterToDto(result: PersonDto): PersonDto = result
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `parametro extra del metodo usado como fuente por nombre`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.contract.Mapper

            data class OrderDto(val id: String, val taxRate: Double)
            data class Order(val id: String)

            @Mapper
            interface OrderMapper { fun toDto(o: Order, taxRate: Double): OrderDto }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "OrderMapperImpl.kt" }.readText()
        assertTrue(generated.contains("id = o.id"), generated)
        assertTrue(generated.contains("taxRate = taxRate"), generated)
    }

    @Test
    fun `componentModel SPRING fuerza class`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.contract.ComponentModel
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.contract.Mapper

            data class PersonDto(val name: String)

            @MapTo(PersonDto::class)
            data class Person(val name: String)

            @Mapper(componentModel = ComponentModel.SPRING)
            interface PersonMapper { fun toDto(p: Person): PersonDto }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "PersonMapperImpl.kt" }.readText()
        assertTrue(generated.contains("public class PersonMapperImpl : PersonMapper"), generated)
    }

    @Test
    fun `interfaz generica produce KMX015`() {
        KspHarness.assertFailsWithError(
            "KMX015",
            """
            package sample
            import dev.kmapx.annotations.contract.Mapper

            @Mapper
            interface GenericMapper<T> { fun map(input: T): String }
            """.trimIndent(),
        )
    }

    @Test
    fun `herencia entre interfaces Mapper produce KMX015`() {
        KspHarness.assertFailsWithError(
            "KMX015",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.contract.Mapper

            data class PersonDto(val name: String)

            @MapTo(PersonDto::class)
            data class Person(val name: String)

            @Mapper
            interface BaseMapper { fun toDto(p: Person): PersonDto }

            @Mapper
            interface ChildMapper : BaseMapper
            """.trimIndent(),
        )
    }

    // ── Integraciones Spring/Koin/serialization ────────────────────────

    @Test
    fun `componentModel SPRING emite arroba-Component con import`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.contract.ComponentModel
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.contract.Mapper

            data class PersonDto(val name: String)

            @MapTo(PersonDto::class)
            data class Person(val name: String)

            @Mapper(componentModel = ComponentModel.SPRING)
            interface PersonMapper { fun toDto(p: Person): PersonDto }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "PersonMapperImpl.kt" }.readText()
        assertTrue(generated.contains("import org.springframework.stereotype.Component"), generated)
        assertTrue(generated.contains("@Component"), generated)
        assertTrue(generated.contains("public class PersonMapperImpl : PersonMapper"), generated)
    }

    @Test
    fun `SPRING sin spring-context en el classpath produce KMX030`() {
        val result = KspHarness.assertFailsWithError(
            "KMX030",
            """
            package sample
            import dev.kmapx.annotations.contract.ComponentModel
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.contract.Mapper

            data class PersonDto(val name: String)

            @MapTo(PersonDto::class)
            data class Person(val name: String)

            @Mapper(componentModel = ComponentModel.SPRING)
            interface PersonMapper { fun toDto(p: Person): PersonDto }
            """.trimIndent(),
            isolated = true,
        )
        assertTrue(result.messages.contains("spring-context"), result.messages)
    }

    @Test
    fun `KOIN genera un modulo por paquete con todos los mappers`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.contract.ComponentModel
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.contract.Mapper

            data class PersonDto(val name: String)
            data class OrderDto(val id: String)

            @MapTo(PersonDto::class)
            data class Person(val name: String)

            @MapTo(OrderDto::class)
            data class Order(val id: String)

            @Mapper(componentModel = ComponentModel.KOIN)
            interface PersonMapper { fun toDto(p: Person): PersonDto }

            @Mapper(componentModel = ComponentModel.KOIN)
            interface OrderMapper { fun toDto(o: Order): OrderDto }
            """.trimIndent(),
        )
        val module = result.generatedFiles.first { it.name == "KmapxKoinModule.kt" }.readText()
        assertTrue(module.contains("import org.koin.core.module.Module"), module)
        assertTrue(module.contains("val kmapxModule: Module = module {"), module)
        assertTrue(module.contains("single<OrderMapper> { OrderMapperImpl() }"), module)
        assertTrue(module.contains("single<PersonMapper> { PersonMapperImpl() }"), module)
    }

    @Test
    fun `KOIN runtime - startKoin resuelve el mapper desde el modulo generado`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.contract.ComponentModel
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.contract.Mapper

            data class PersonDto(val name: String)

            @MapTo(PersonDto::class)
            data class Person(val name: String)

            @Mapper(componentModel = ComponentModel.KOIN)
            interface PersonMapper { fun toDto(p: Person): PersonDto }
            """.trimIndent(),
        )
        val cl = result.classLoader!!
        val module = cl.loadClass("sample.KmapxKoinModuleKt")
            .getMethod("getKmapxModule").invoke(null) as org.koin.core.module.Module
        val koin = org.koin.core.context.startKoin { modules(module) }.koin
        try {
            val mapperInterface = cl.loadClass("sample.PersonMapper")
            val impl = koin.get<Any>(mapperInterface.kotlin, null, null)
            assertTrue(mapperInterface.isInstance(impl), "startKoin debe resolver PersonMapperImpl")
        } finally {
            org.koin.core.context.stopKoin()
        }
    }

    @Test
    fun `KOIN sin koin-core en el classpath produce KMX030`() {
        KspHarness.assertFailsWithError(
            "KMX030",
            """
            package sample
            import dev.kmapx.annotations.contract.ComponentModel
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.contract.Mapper

            data class PersonDto(val name: String)

            @MapTo(PersonDto::class)
            data class Person(val name: String)

            @Mapper(componentModel = ComponentModel.KOIN)
            interface PersonMapper { fun toDto(p: Person): PersonDto }
            """.trimIndent(),
            isolated = true,
        )
    }

    @Test
    fun `useSerialNames - el alias matchea solo si el nombre real no`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import kotlinx.serialization.SerialName

            data class Dto(val user_name: String, val age: Int)

            @MapTo(Dto::class, useSerialNames = true)
            data class Src(@SerialName("user_name") val userName: String, @SerialName("age") val years: Int, val age: Int)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "SrcMappings.kt" }.readText()
        // alias usado cuando el nombre real no existe:
        assertTrue(generated.contains("user_name = userName"), generated)
        // el nombre REAL gana aunque otro campo tenga @SerialName("age"):
        assertTrue(generated.contains("age = age"), generated)
    }

    @Test
    fun `sin opt-in el SerialName se ignora`() {
        KspHarness.assertFailsWithError(
            "KMX002",
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import kotlinx.serialization.SerialName

            data class Dto(val user_name: String)

            @MapTo(Dto::class)
            data class Src(@SerialName("user_name") val userName: String)
            """.trimIndent(),
        )
    }

    // ── Reporte de cobertura ───────────────────────────────────────────

    private val reportSource = """
        package sample
        import dev.kmapx.annotations.Converter
        import dev.kmapx.annotations.embedded.MapTo
        import dev.kmapx.annotations.contract.Mapper
        import java.time.Instant

        @Converter fun instantToIso(value: Instant): String = value.toString()

        data class Dto(val name: String, val createdAt: String, val tags: List<String> = emptyList())

        @MapTo(Dto::class)
        data class Src(val name: String, val createdAt: Instant, val internalFlag: Boolean)

        data class TaskPatch(val name: String?)
        data class Task(val name: String)

        @Mapper
        interface TaskPatcher { fun apply(target: Task, patch: TaskPatch): Task }
    """.trimIndent()

    private fun reportJson(result: KspHarness.Result): kotlinx.serialization.json.JsonObject {
        val file = result.generatedFiles.first { it.name == "kmapx-report.json" }
        return kotlinx.serialization.json.Json.parseToJsonElement(file.readText())
            as kotlinx.serialization.json.JsonObject
    }

    @Test
    fun `sin la opcion no se genera ningun reporte - cero costo`() {
        val result = KspHarness.assertCompiles(reportSource)
        assertTrue(result.generatedFiles.none { it.name.startsWith("kmapx-report") }, 
            result.generatedFiles.joinToString { it.name })
    }

    @Test
    fun `reporte JSON - la config global efectiva aparece en el header`() {
        val result = KspHarness.assertCompiles(
            reportSource,
            options = mapOf("kmapx.report" to "json", "kmapx.onNull" to "throw"),
        )
        val cfg = reportJson(result)["config"] as kotlinx.serialization.json.JsonObject
        assertEquals("or_throw", (cfg["onNull"] as kotlinx.serialization.json.JsonPrimitive).content)
    }

    @Test
    fun `reporte JSON - schema, origins, shapes, refs, omisiones y modos`() {
        val result = KspHarness.assertCompiles(reportSource, options = mapOf("kmapx.report" to "json"))
        // sin config global → sin bloque "config" (additivo, backward-compatible con schema 1):
        assertTrue(reportJson(result)["config"] == null, "no debería haber config sin globals")
        assertTrue(result.messages.contains("[KMX021]"), result.messages) // tags por default: warning
        val json = reportJson(result)

        // Contrato del formato: schema versionado presente.
        assertEquals(1, (json["schema"] as kotlinx.serialization.json.JsonPrimitive).content.toInt())

        val mappings = json["mappings"] as kotlinx.serialization.json.JsonArray
        val byMode = mappings.groupBy {
            ((it as kotlinx.serialization.json.JsonObject)["mode"] as kotlinx.serialization.json.JsonPrimitive).content
        }
        assertTrue("extension" in byMode && "patch" in byMode, byMode.keys.toString())

        val ext = byMode["extension"]!!.single() as kotlinx.serialization.json.JsonObject
        val fields = (ext["fields"] as kotlinx.serialization.json.JsonArray)
            .associateBy { ((it as kotlinx.serialization.json.JsonObject)["target"] as kotlinx.serialization.json.JsonPrimitive).content }
        fun field(name: String, key: String) =
            ((fields[name] as kotlinx.serialization.json.JsonObject)[key] as kotlinx.serialization.json.JsonPrimitive).content

        // Trazabilidad Resolution consumida por fin:
        assertEquals("IMPLICIT_SAFE", field("name", "origin"))
        assertEquals("direct", field("name", "shape"))
        assertEquals("USER_CONVERTER", field("createdAt", "origin"))
        assertEquals("converter", field("createdAt", "shape"))
        assertEquals("sample.instantToIso", field("createdAt", "ref"))

        // Omisiones y no-usados:
        val unused = (ext["unusedSourceProperties"] as kotlinx.serialization.json.JsonArray)
            .map { (it as kotlinx.serialization.json.JsonPrimitive).content }
        assertEquals(listOf("internalFlag"), unused)
        val warnings = (ext["warnings"] as kotlinx.serialization.json.JsonArray)
            .map { (it as kotlinx.serialization.json.JsonPrimitive).content }
        assertTrue(warnings.any { it.contains("[KMX021]") }, warnings.toString())

        // Ubicación (Q1): archivo y línea presentes.
        assertEquals("Input.kt", ((ext["file"] as kotlinx.serialization.json.JsonPrimitive)).content)
    }

    @Test
    fun `reporte HTML - autocontenido y con los pares`() {
        val result = KspHarness.assertCompiles(reportSource, options = mapOf("kmapx.report" to "json,html"))
        val html = result.generatedFiles.first { it.name == "kmapx-report.html" }.readText()
        assertTrue(html.contains("sample.Src"), html)
        assertTrue(html.contains("sample.Dto"), html)
        assertTrue(html.contains("USER_CONVERTER"), html)
        // Autocontenido: cero referencias externas (mismo espíritu que "sin java."):
        assertTrue(!html.contains("http://") && !html.contains("https://"), "el HTML debe ser autocontenido")
        assertTrue(!html.contains("<script src") && !html.contains("<link "), "sin recursos externos")
    }

    @Test
    fun `reporte agrega N clases en UN archivo y reporta modos interface y bidireccional`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.BiMapTo
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.contract.Mapper

            data class ADto(val x: Int)
            @MapTo(ADto::class) data class A(val x: Int)

            data class BDto(val y: Int)
            @BiMapTo(BDto::class) data class B(val y: Int)

            @Mapper
            interface AMapper { fun toDto(a: A): ADto }
            """.trimIndent(),
            options = mapOf("kmapx.report" to "json"),
        )
        val reports = result.generatedFiles.filter { it.name == "kmapx-report.json" }
        assertEquals(1, reports.size, "un solo archivo de reporte por módulo")
        val json = reportJson(result)
        val modes = (json["mappings"] as kotlinx.serialization.json.JsonArray).map {
            ((it as kotlinx.serialization.json.JsonObject)["mode"] as kotlinx.serialization.json.JsonPrimitive).content
        }
        assertTrue(modes.count { it == "bidirectional" } == 2, modes.toString())
        assertTrue("interface" in modes && "extension" in modes, modes.toString())
        // La delegación del modo interface queda trazada:
        val iface = (json["mappings"] as kotlinx.serialization.json.JsonArray)
            .map { it as kotlinx.serialization.json.JsonObject }
            .first { (it["mode"] as kotlinx.serialization.json.JsonPrimitive).content == "interface" }
        assertEquals("sample.toADto", (iface["delegatesTo"] as kotlinx.serialization.json.JsonPrimitive).content)
    }

    @Test
    fun `metodo de coleccion delega en el metodo hermano del mismo mapper`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.contract.Mapper

            data class Order(val id: String)
            data class OrderDto(val id: String)

            @Mapper
            interface OrderMapper {
                fun toDto(order: Order): OrderDto
                fun toDtos(orders: List<Order>): List<OrderDto>
            }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "OrderMapperImpl.kt" }.readText()
        assertTrue(generated.contains("orders.map { toDto(it) }"), generated)
    }

    @Test
    fun `metodo de coleccion delega en la extension declarada y Set materializa con mapTo`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.contract.Mapper

            data class OrderDto(val id: String)

            @MapTo(OrderDto::class)
            data class Order(val id: String)

            @Mapper
            interface OrderMapper {
                fun toDtos(orders: Set<Order>): Set<OrderDto>
            }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "OrderMapperImpl.kt" }.readText()
        assertTrue(generated.contains("orders.mapTo(mutableSetOf()) { it.toOrderDto() }"), generated)
    }

    @Test
    fun `metodo de coleccion sin mapeo del elemento produce KMX046`() {
        KspHarness.assertFailsWithError(
            "KMX046",
            """
            package sample
            import dev.kmapx.annotations.contract.Mapper

            data class Order(val id: String)
            data class OrderDto(val id: String)

            @Mapper
            interface OrderMapper {
                fun toDtos(orders: List<Order>): List<OrderDto>
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `el cruce de contenedor List a Set produce KMX046 con detalle`() {
        val result = KspHarness.assertFailsWithError(
            "KMX046",
            """
            package sample
            import dev.kmapx.annotations.contract.Mapper

            data class Order(val id: String)
            data class OrderDto(val id: String)

            @Mapper
            interface OrderMapper {
                fun toDto(order: Order): OrderDto
                fun toDtos(orders: List<Order>): Set<OrderDto>
            }
            """.trimIndent(),
        )
        assertTrue(result.messages.contains("outside the closed list"), result.messages)
    }
}
