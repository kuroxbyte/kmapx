package dev.kmapx.demo

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CRUD end-to-end sobre mappers generados por kmapx. Reloj/IDs fijos para asserts estables.
 */
class ProductServiceTest {

    private fun service() = ProductService(
        clock = { Instant.parse("2023-11-14T22:13:20Z") },
        ids = { "prod-1" },
    )

    private val request = CreateProductRequest(
        sku = "LP-14",
        name = "Laptop",
        category = CategoryDto.ELECTRONICS,
        priceCents = 129_999,
        tags = listOf("portátil"),
    )

    @Test
    fun `create mapea request a entidad (modo B) y entidad a respuesta (modo A)`() {
        val response = service().create(request)

        assertEquals("prod-1", response.id)                // ProductId desenvuelta
        assertEquals("LP-14", response.sku)                // Sku desenvuelta
        assertEquals(CategoryDto.ELECTRONICS, response.category) // enum dispatch
        assertEquals("$1299.99", response.price)           // converter calificado PriceUsd
        assertEquals("2023-11-14T22:13:20Z", response.createdAt) // @Converter instantToIso
        assertEquals(listOf("portátil"), response.tags)
    }

    @Test
    fun `list produce la vista resumida (segundo MapTo con name)`() {
        val svc = service()
        svc.create(request)
        val summaries = svc.list()

        assertEquals(1, summaries.size)
        assertEquals(ProductSummary(id = "prod-1", name = "Laptop", price = "$1299.99"), summaries.single())
    }

    @Test
    fun `update parcial - null conserva el valor, no-null reemplaza (PATCH)`() {
        val svc = service()
        svc.create(request)

        val updated = svc.update(
            ProductId("prod-1"),
            ProductPatch(name = null, priceCents = 99_999, tags = null),
        )!!

        assertEquals("Laptop", updated.name)   // conservado (patch.name == null)
        assertEquals("$999.99", updated.price)  // reemplazado
        assertEquals(listOf("portátil"), updated.tags) // conservado
    }

    @Test
    fun `DIP - el mapper es inyectable, se puede sustituir por un doble en test`() {
        // La interfaz @Mapper permite pasar un doble donde iría ProductFactoryImpl: los mappers
        // de kmapx son inyectables/mockeables (a diferencia de los mappers estáticos de MapStruct).
        val fakeFactory = object : ProductFactory {
            override fun create(request: CreateProductRequest, id: ProductId, createdAt: Instant) =
                Product(id, Sku("FAKE"), "fijo", Category.FOOD, 0, createdAt, emptyList())
        }
        val response = ProductService(factory = fakeFactory, ids = { "x" }, clock = { Instant.EPOCH })
            .create(request)

        assertEquals("FAKE", response.sku)
        assertEquals("fijo", response.name)
    }

    @Test
    fun `delete elimina y get devuelve null`() {
        val svc = service()
        svc.create(request)

        assertTrue(svc.delete(ProductId("prod-1")))
        assertNull(svc.get(ProductId("prod-1")))
    }
}
