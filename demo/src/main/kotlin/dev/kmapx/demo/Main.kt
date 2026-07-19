package dev.kmapx.demo

import java.time.Instant

/**
 * Recorrido CRUD ejecutable: `./gradlew :demo:run`.
 * Muestra create → read → list → update (parcial) → delete usando SOLO mappers generados.
 */
fun main() {
    // Reloj e IDs deterministas para que la salida de la demo sea reproducible.
    var tick = 0L
    val service = ProductService(
        clock = { Instant.ofEpochSecond(1_700_000_000 + tick++) },
        ids = { "prod-%03d".format(tick) },
    )

    println("── CREATE ─────────────────────────────────────────────")
    val laptop = service.create(
        CreateProductRequest(
            sku = "LP-14",
            name = "Laptop 14\"",
            category = CategoryDto.ELECTRONICS,
            priceCents = 129_999,
            tags = listOf("portátil", "oferta"),
        ),
    )
    val novel = service.create(
        CreateProductRequest(
            sku = "BK-77",
            name = "Novela",
            category = CategoryDto.BOOKS,
            priceCents = 1_950,
            tags = listOf("ficción"),
        ),
    )
    println(laptop)
    println(novel)

    println("\n── READ ───────────────────────────────────────────────")
    println(service.get(ProductId(laptop.id)))

    println("\n── LIST (summaries) ───────────────────────────────────")
    service.list().forEach { println(it) }

    println("\n── UPDATE (patch: solo el precio; nombre y tags intactos) ──")
    val updated = service.update(
        ProductId(laptop.id),
        ProductPatch(name = null, priceCents = 99_999, tags = null),
    )
    println(updated)

    println("\n── DELETE ─────────────────────────────────────────────")
    println("borrado novela: ${service.delete(ProductId(novel.id))}")
    println("quedan: ${service.list().size} producto(s)")
}
