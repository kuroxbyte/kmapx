package dev.kmapx.demo

import dev.kmapx.annotations.embedded.BiMapTo
import dev.kmapx.annotations.Converter
import dev.kmapx.annotations.MapField
import dev.kmapx.annotations.embedded.MapTo
import dev.kmapx.annotations.contract.Mapper
import dev.kmapx.runtime.Converts
import java.time.Instant

/**
 * Capa de INFRAESTRUCTURA — DTOs, converters y mappers. Todo lo que MapStruct pondría en un
 * `@Mapper` con `@Mapping(expression=...)`/`@Named` aquí es Kotlin normal y verificado en
 * compile-time. Sin reflection, sin strings con código, sin overhead en runtime.
 */

// ── Converters ───────────────────────────────────────────────────────────────

/** Converter global: función Kotlin normal. Reemplaza `@Mapping(expression="java(...)")`. */
@Converter
fun instantToIso(value: Instant): String = value.toString()

/**
 * Converter CALIFICADO: elegido por campo vía `@MapField(converter = PriceUsd::class)`.
 * Es el reemplazo type-safe de `@Named("priceUsd")` + `qualifiedByName` de MapStruct: se
 * referencia por `KClass`, así que renombrarlo actualiza los usos y un desajuste de tipos es KMX027.
 */
object PriceUsd : Converts<Long, String> {
    override fun convert(value: Long): String =
        "$" + (value / 100) + "." + (value % 100).toString().padStart(2, '0')
}

// ── Enum del borde (mapeado en AMBAS direcciones desde una sola declaración) ──

/** `@BiMapTo` genera `CategoryDto.toCategory()` y `Category.toCategoryDto()`. Enum → enum. */
@BiMapTo(Category::class)
enum class CategoryDto { ELECTRONICS, BOOKS, FOOD }

// ── Salida: entidad → DTOs de respuesta (modo A, `@MapTo` sobre la entidad) ──

data class ProductResponse(
    val id: String,                 // Product.id.value — value class desenvuelta
    val sku: String,                // Product.sku.value
    val name: String,
    val category: CategoryDto,      // enum dispatch (Category → CategoryDto)
    @MapField(from = "priceCents", converter = PriceUsd::class) val price: String, // rename + converter, UNA anotación
    val createdAt: String,          // Instant → String vía el @Converter global
    val tags: List<String>,
)

data class ProductSummary(
    val id: String,
    val name: String,
    @MapField(from = "priceCents", converter = PriceUsd::class) val price: String,
)

// ── Entrada: request → entidad (modo B, dominio limpio — la config vive en el método) ──

data class CreateProductRequest(
    val sku: String,                // String → Sku, envuelto
    val name: String,
    val category: CategoryDto,      // CategoryDto → Category (dirección inversa del @BiMapTo)
    val priceCents: Long,
    val tags: List<String>,
)

/**
 * Modo B: el mapeo se declara por la EXISTENCIA del método. `id` y `createdAt` son
 * parámetros suplementarios (los genera el servicio) y se emparejan por nombre. Ni
 * [CreateProductRequest] ni [Product] llevan anotaciones para esta dirección: dominio limpio.
 */
@Mapper
interface ProductFactory {
    fun create(request: CreateProductRequest, id: ProductId, createdAt: Instant): Product
}

// ── Actualización parcial inmutable (PATCH: null = no tocar) ─────────────────

data class ProductPatch(
    val name: String?,
    val priceCents: Long?,
    val tags: List<String>?,
)

/** PATCH por FORMA — `(target, patch): target` → `copy(name = patch.name ?: target.name, ...)`. */
@Mapper
interface ProductPatcher {
    fun apply(target: Product, patch: ProductPatch): Product
}
