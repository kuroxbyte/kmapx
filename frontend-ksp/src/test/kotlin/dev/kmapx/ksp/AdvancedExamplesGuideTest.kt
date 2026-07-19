@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package dev.kmapx.ksp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * docs/ejemplos-avanzados.md — la guía COMPILA de verdad: si se desactualiza, este test lo dice.
 */
class AdvancedExamplesGuideTest {

    // ── docs/ejemplos-avanzados.md: la guía compila DE VERDAD ────────────────

    @Test
    fun `los ejemplos avanzados del modo embedded compilan (docs)`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.*
import dev.kmapx.annotations.contract.*
import dev.kmapx.annotations.embedded.*
            import dev.kmapx.runtime.Converts

            @JvmInline value class ProductId(val value: String)

            @Converter
            fun centsToDisplay(cents: Long): String =
                "$" + (cents / 100) + "." + (cents % 100).toString().padStart(2, '0')

            object PriceTag : Converts<Long, String> {
                override fun convert(value: Long): String = "PRICE:" + value
            }

            enum class CategoryDto { ELECTRONICS, BOOKS, HOME_GARDEN }

            @MapTo(CategoryDto::class)
            enum class Category {
                ELECTRONICS,
                BOOKS,
                @MapEntry(target = "HOME_GARDEN") HOME_AND_GARDEN,
            }

            data class WarehouseDto(val code: String, val capacity: Int)

            @BiMapTo(WarehouseDto::class)
            class Warehouse private constructor(val code: String, val capacity: Int) {
                companion object {
                    @MapFactory
                    fun of(code: String, capacity: Int) = Warehouse(code.uppercase(), capacity)
                }
            }

            sealed interface StockEventDto {
                data class Restocked(val units: Int) : StockEventDto
                data object OutOfStock : StockEventDto
            }

            @MapTo(StockEventDto::class)
            sealed interface StockEvent {
                @MapSubtype(StockEventDto.Restocked::class)
                data class Replenished(val units: Int) : StockEvent
                data object OutOfStock : StockEvent
            }

            data class AddressDto(val city: String)

            @MapTo(AddressDto::class)
            data class Address(val city: String)

            data class Supplier(val name: String, val address: Address)

            data class ProductDto(
                val id: String,
                val name: String,
                @MapField(from = "priceCents", converter = PriceTag::class) val price: String,
                @MapField(from = "priceCents") val priceDisplay: String,
                @MapField(from = "supplier.address.city") val supplierCity: String,
                @MapField(onNull = OnNull.LITERAL, default = "N/A") val notes: String,
                @MapField(onNull = OnNull.TARGET_DEFAULT) val discount: Int = 0,
                @MapField(ignore = true) val internalNotes: String? = null,
                val tags: List<String>,
                val rating: Double,
                val category: CategoryDto,
                val restock: StockEventDto,
                val stops: List<AddressDto>,
                val stockByCode: Map<String, Int>,
                val internalCode: String? = null,
            )

            data class ProductSummary(
                @MapField(from = "supplier.address.city") val supplierCity: String,
                val name: String,
            )

            @MapTo(ProductDto::class, onNull = OnNull.TYPE_DEFAULT, ignore = ["internalCode"])
            @MapTo(ProductSummary::class, name = "asSummary")
            data class Product(
                val id: ProductId,
                val name: String,
                val priceCents: Long,
                val supplier: Supplier,
                val notes: String?,
                val discount: Int?,
                val tags: List<String>?,
                val rating: Double?,
                val category: Category,
                val restock: StockEvent,
                val stops: List<Address>,
                val stockByCode: Map<String, Int>,
            )
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "ProductMappings.kt" }.readText()
        assertTrue(generated.contains("price = PriceTag.convert(priceCents)"), generated)
        assertTrue(generated.contains("priceDisplay = centsToDisplay(priceCents)"), generated)
        assertTrue(generated.contains("supplierCity = supplier.address.city"), generated)
        assertTrue(generated.contains("tags = tags ?: emptyList()"), generated)
        assertTrue(generated.contains("rating = rating ?: 0.0"), generated)
        assertTrue(!generated.contains("internalCode"), generated)
        assertTrue(!generated.contains("internalNotes"), generated)
        assertTrue(generated.contains("fun Product.asSummary(): ProductSummary"), generated)
    }

    @Test
    fun `los ejemplos avanzados del modo contract compilan (docs)`() {
        val result = KspHarness.assertCompiles(
            """
            package sample
            import dev.kmapx.annotations.*
import dev.kmapx.annotations.contract.*
import dev.kmapx.annotations.embedded.*
            import dev.kmapx.runtime.Converts
            import dev.kmapx.runtime.Patch

            data class Customer(
                val id: String,
                val name: String,
                val nickname: String?,
                val email: String,
                val riskScore: Int,
            )

            data class CustomerDto(
                val id: String,
                val displayName: String,
                val nickname: String,
                val email: String?,
                val riskScore: Int,
            )
            data class CustomerView(val id: String, val riskLabel: String)
            data class AccountSummary(val name: String, val audit: String? = null)
            data class CreateCustomerRequest(val name: String, val nickname: String?, val riskScore: Int, val email: String)
            data class CustomerPatch(
                val name: String?,
                val email: Patch<String>,
            )

            @org.springframework.stereotype.Component
            class RiskLabeler : Converts<Int, String> {
                override fun convert(value: Int): String = if (value > 70) "HIGH" else "LOW"
            }

            @MapperConfig(
                componentModel = ComponentModel.SPRING,
                onNull = OnNull.THROW,
                ignore = ["audit"],
            )
            interface CompanyMapperConfig

            @Mapper(onNull = OnNull.THROW)
            interface BaseCustomerMapper {
                @MapField(target = "displayName", from = "name")
                @MapField(target = "nickname", onNull = OnNull.LITERAL, default = "-")
                fun toDto(c: Customer): CustomerDto
            }

            @Mapper(config = CompanyMapperConfig::class, inheritFrom = BaseCustomerMapper::class)
            interface CustomerMapper {

                @MapField(target = "nickname", onNull = OnNull.LITERAL, default = "N/A")
                fun toDto(c: Customer): CustomerDto

                @MapField(target = "riskLabel", from = "riskScore", converter = RiskLabeler::class)
                fun toView(c: Customer): CustomerView

                fun toSummary(c: Customer): AccountSummary

                fun create(request: CreateCustomerRequest, id: String): Customer

                fun applyPatch(target: Customer, patch: CustomerPatch): Customer

                @InverseOf
                fun fromDto(dto: CustomerDto): Customer

                fun afterToDto(source: Customer, result: CustomerDto): CustomerDto =
                    result.copy(displayName = result.displayName.trim())
                fun afterApplyPatch(target: Customer, patch: CustomerPatch, result: Customer): Customer = result
            }
            """.trimIndent(),
        )
        val generated = result.generatedFiles.first { it.name == "CustomerMapperImpl.kt" }.readText()
        // El profile decide SPRING → class @Component con el converter inyectado:
        assertTrue(generated.contains("@Component"), generated)
        assertTrue(generated.contains("class CustomerMapperImpl("), generated)
        // herencia de config (rename del base) + LITERAL propio + after aplicado:
        assertTrue(generated.contains("displayName = c.name"), generated)
        assertTrue(generated.contains("""nickname = c.nickname ?: "N/A""""), generated)
        assertTrue(generated.contains("afterToDto(c,"), generated)
        // Patch por forma + tri-estado:
        assertTrue(generated.contains("target.copy("), generated)
        assertTrue(generated.contains("Patch.Keep -> target.email"), generated)
        // Inverso auto-detectado: rename invertido y THROW del profile cerrando el widening:
        assertTrue(generated.contains("name = dto.displayName"), generated)
        assertTrue(generated.contains("email = dto.email ?: throw"), generated)
        // El ignore del profile aplica donde existe:
        assertTrue(!generated.contains("audit"), generated)
    }
}
