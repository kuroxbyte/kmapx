@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package dev.kmapx.ksp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Converters calificados `@MapField(converter=)` + módulo `runtime` (`Converts`), y la
 * sede por método (modo contract, dominio limpio).
 */
class QualifiedConverterTest {

    private val shortDate = """
        package sample
        import dev.kmapx.runtime.Converts
        import java.time.LocalDate

        object ShortDate : Converts<LocalDate, String> {
            override fun convert(value: LocalDate): String = value.toString()
        }
    """.trimIndent()

    @Test
    fun `modo A - MapField converter sobre el campo emite Object convert`() {
        val result = KspHarness.assertCompiles(
            """
            $shortDate

            data class EventDto(@dev.kmapx.annotations.MapField(converter = ShortDate::class) val startDate: String)

            @dev.kmapx.annotations.embedded.MapTo(EventDto::class)
            data class Event(val startDate: java.time.LocalDate)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "EventMappings.kt" }.readText()
        assertTrue(generated.contains("startDate = ShortDate.convert(startDate)"), generated)
    }

    @Test
    fun `modo B - dominio 100 por ciento limpio - config solo en el metodo`() {
        // Source y Target SIN una sola anotación kmapx; todo vive en la interfaz @Mapper.
        val result = KspHarness.assertCompiles(
            """
            $shortDate

            data class Customer(val firstName: String, val startDate: LocalDate)
            data class CustomerDto(val name: String, val startDate: String)

            @dev.kmapx.annotations.contract.Mapper
            interface CustomerMapper {
                @dev.kmapx.annotations.MapField(target = "name", from = "firstName")
                @dev.kmapx.annotations.MapField(target = "startDate", converter = ShortDate::class)
                fun toDto(c: Customer): CustomerDto
            }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "CustomerMapperImpl.kt" }.readText()
        assertTrue(generated.contains("name = c.firstName"), generated)
        assertTrue(generated.contains("startDate = ShortDate.convert(c.startDate)"), generated)
    }

    @Test
    fun `modo B - from con ruta anidada aplana desde el metodo`() {
        // Dominio limpio: Customer/CustomerDto sin anotaciones; el aplanado a.b vive en el @Mapper.
        val result = KspHarness.assertCompiles(
            """
            package sample

            data class Country(val code: String)
            data class Address(val city: String, val country: Country?)
            data class Customer(val name: String, val address: Address)
            data class CustomerDto(val name: String, val city: String, val countryCode: String?)

            @dev.kmapx.annotations.contract.Mapper
            interface CustomerMapper {
                @dev.kmapx.annotations.MapField(target = "city", from = "address.city")
                @dev.kmapx.annotations.MapField(target = "countryCode", from = "address.country.code")
                fun toDto(c: Customer): CustomerDto
            }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "CustomerMapperImpl.kt" }.readText()
        assertTrue(generated.contains("city = c.address.city"), generated)
        assertTrue(generated.contains("countryCode = c.address.country?.code"), generated)
    }

    @Test
    fun `MapField converter con object anidado bajo un namespace resuelve`() {
        // Cierra la pregunta abierta: agrupar converters bajo un objeto namespace.
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.annotations.MapField
            import dev.kmapx.runtime.Converts
            import java.time.LocalDate

            object DateFormats {
                object Short : Converts<LocalDate, String> { override fun convert(value: LocalDate) = value.toString() }
            }

            data class EventDto(@MapField(converter = DateFormats.Short::class) val start: String)

            @MapTo(EventDto::class)
            data class Event(val start: LocalDate)
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "EventMappings.kt" }.readText()
        // El nombre calificado del object anidado se referencia correctamente (compila = válido).
        assertTrue(generated.contains("Short.convert(start)"), generated)
    }

    @Test
    fun `modo B - inheritFrom hereda la config por metodo de otro Mapper`() {
        // El derivado NO declara config: solo compila porque HEREDA name<-firstName del base.
        val result = KspHarness.assertCompiles(
            """
            package sample

            data class Customer(val firstName: String)
            data class CustomerDto(val name: String)

            @dev.kmapx.annotations.contract.Mapper
            interface BaseMapper {
                @dev.kmapx.annotations.MapField(target = "name", from = "firstName")
                fun toDto(c: Customer): CustomerDto
            }

            @dev.kmapx.annotations.contract.Mapper(inheritFrom = BaseMapper::class)
            interface DerivedMapper {
                fun toDto(c: Customer): CustomerDto
            }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "DerivedMapperImpl.kt" }.readText()
        assertTrue(generated.contains("name = c.firstName"), generated)
    }

    private val injectedConverter = """
        package sample
        import dev.kmapx.annotations.contract.ComponentModel
        import dev.kmapx.annotations.MapField
        import dev.kmapx.annotations.contract.Mapper
        import dev.kmapx.runtime.Converts
        import org.springframework.stereotype.Component

        interface CustomerRepo { fun findName(id: Long): String }

        @Component
        class CustomerName(private val repo: CustomerRepo) : Converts<Long, String> {
            override fun convert(value: Long): String = repo.findName(value)
        }

        data class Order(val customerId: Long)
        data class OrderDto(@MapField(from = "customerId", converter = CustomerName::class) val customerName: String)
    """.trimIndent()

    @Test
    fun `converter-class inyectado en modo B Spring - constructor + instancia`() {
        val result = KspHarness.assertCompiles(
            "$injectedConverter\n\n@Mapper(componentModel = ComponentModel.SPRING)\ninterface OrderMapper { fun toDto(o: Order): OrderDto }",
        )
        val gen = result.generatedFiles.first { it.name == "OrderMapperImpl.kt" }.readText()
        assertTrue(gen.contains("public class OrderMapperImpl("), gen)
        assertTrue(gen.contains("customerName: CustomerName"), gen)                  // inyectado por constructor
        assertTrue(gen.contains("customerName = customerName.convert(o.customerId)"), gen)  // instancia, no estático
    }

    @Test
    fun `Koin emite el binding con get() por dependencia inyectada`() {
        val result = KspHarness.assertCompiles(
            "$injectedConverter\n\n@Mapper(componentModel = ComponentModel.KOIN)\ninterface OrderMapper { fun toDto(o: Order): OrderDto }",
        )
        val module = result.generatedFiles.first { it.name == "KmapxKoinModule.kt" }.readText()
        assertTrue(module.contains("single<OrderMapper> { OrderMapperImpl(get()) }"), module)
    }

    @Test
    fun `converter-class en modo A (extension) produce KMX034`() {
        KspHarness.assertFailsWithError(
            "KMX034",
            """
            package sample
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.embedded.MapTo
            import dev.kmapx.runtime.Converts

            class Twice : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

            data class Dto(@MapField(from = "n", converter = Twice::class) val s: String)
            @MapTo(Dto::class) data class Src(val n: Long)
            """.trimIndent(),
        )
    }

    @Test
    fun `Spring + converter-class sin @Component produce KMX035`() {
        KspHarness.assertFailsWithError(
            "KMX035",
            """
            package sample
            import dev.kmapx.annotations.contract.ComponentModel
            import dev.kmapx.annotations.MapField
            import dev.kmapx.annotations.contract.Mapper
            import dev.kmapx.runtime.Converts

            class CustomerName : Converts<Long, String> { override fun convert(value: Long) = value.toString() }

            data class Order(val customerId: Long)
            data class OrderDto(@MapField(from = "customerId", converter = CustomerName::class) val customerName: String)

            @Mapper(componentModel = ComponentModel.SPRING)
            interface OrderMapper { fun toDto(o: Order): OrderDto }
            """.trimIndent(),
        )
    }

    @Test
    fun `modo B - target inexistente en el metodo produce KMX011`() {
        KspHarness.assertFailsWithError(
            "KMX011",
            """
            package sample

            data class Customer(val firstName: String)
            data class CustomerDto(val name: String)

            @dev.kmapx.annotations.contract.Mapper
            interface CustomerMapper {
                @dev.kmapx.annotations.MapField(target = "nam", from = "firstName")
                fun toDto(c: Customer): CustomerDto
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `tipos que no encajan con el Converts producen KMX027`() {
        KspHarness.assertFailsWithError(
            "KMX027",
            """
            $shortDate

            data class Src(val startDate: Int)
            data class Dto(val startDate: String)

            @dev.kmapx.annotations.contract.Mapper
            interface M {
                @dev.kmapx.annotations.MapField(target = "startDate", converter = ShortDate::class)
                fun toDto(s: Src): Dto
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `object que no implementa Converts produce KMX029`() {
        KspHarness.assertFailsWithError(
            "KMX029",
            """
            package sample

            object NotAConverter

            data class Src(val startDate: java.time.LocalDate)
            data class Dto(val startDate: String)

            @dev.kmapx.annotations.contract.Mapper
            interface M {
                @dev.kmapx.annotations.MapField(target = "startDate", converter = NotAConverter::class)
                fun toDto(s: Src): Dto
            }
            """.trimIndent(),
        )
    }
}
