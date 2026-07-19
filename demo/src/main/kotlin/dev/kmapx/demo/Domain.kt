package dev.kmapx.demo

import dev.kmapx.annotations.embedded.MapTo
import java.time.Instant

/**
 * Modelo de DOMINIO — el corazón de la app. En la medida de lo posible queda libre de
 * anotaciones de mapeo: los `@MapTo` de la dirección de SALIDA viven aquí por ergonomía, pero la
 * dirección de ENTRADA (request → dominio) se declara en la capa de infraestructura (ver Dto.kt),
 * dejando ver las DOS SEDES de kmapx.
 */

/** Value classes: identidad tipada, cero overhead. kmapx las envuelve/desenvuelve solo. */
@JvmInline
value class ProductId(val value: String)

@JvmInline
value class Sku(val value: String)

enum class Category { ELECTRONICS, BOOKS, FOOD }

/**
 * La entidad de negocio. Inmutable; las actualizaciones son `copy()` (ver el PatchMapper).
 * Declara sus mapeos de SALIDA en modo A (`Product.toProductResponse()` / `.toSummary()`); la
 * dirección de entrada (request → Product) es modo B y no toca esta clase.
 */
@MapTo(ProductResponse::class)
@MapTo(ProductSummary::class, name = "toSummary")
data class Product(
    val id: ProductId,
    val sku: Sku,
    val name: String,
    val category: Category,
    val priceCents: Long,
    val createdAt: Instant,
    val tags: List<String>,
)
