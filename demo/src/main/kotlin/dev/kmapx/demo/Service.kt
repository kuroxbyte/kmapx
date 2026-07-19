package dev.kmapx.demo

import java.time.Instant
import java.util.UUID

/**
 * Servicio CRUD. TODA la traducción dominio ↔ DTO usa funciones GENERADAS por kmapx:
 *  - `ProductFactoryImpl.create(...)`  request → entidad (modo B, con params suplementarios)
 *  - `Product.toProductResponse()`     entidad → respuesta (modo A + converters + value classes)
 *  - `Product.toSummary()`             segunda vista de la misma entidad
 *  - `ProductPatcherImpl.apply(...)`   actualización parcial inmutable (null = no tocar)
 *
 * No hay ni una línea de mapeo escrita a mano, ni reflection: es Kotlin que compila y depura.
 *
 * DIP: el servicio depende de ABSTRACCIONES — el puerto [ProductRepository] y las interfaces de
 * mapeo [ProductFactory]/[ProductPatcher]. kmapx genera los `*Impl` (que se inyectan por defecto),
 * pero un test puede pasar dobles: los mappers son inyectables y mockeables, a diferencia de los
 * mappers estáticos de MapStruct.
 */
class ProductService(
    private val repo: ProductRepository = InMemoryProductRepository(),
    private val factory: ProductFactory = ProductFactoryImpl,
    private val patcher: ProductPatcher = ProductPatcherImpl,
    private val clock: () -> Instant = Instant::now,
    private val ids: () -> String = { UUID.randomUUID().toString() },
) {
    fun create(request: CreateProductRequest): ProductResponse {
        val entity = factory.create(
            request = request,
            id = ProductId(ids()),
            createdAt = clock(),
        )
        return repo.save(entity).toProductResponse()
    }

    fun get(id: ProductId): ProductResponse? = repo.findById(id)?.toProductResponse()

    fun list(): List<ProductSummary> = repo.findAll().map { it.toSummary() }

    /** Actualización parcial: los campos null del patch conservan el valor actual. */
    fun update(id: ProductId, patch: ProductPatch): ProductResponse? {
        val current = repo.findById(id) ?: return null
        val updated = patcher.apply(current, patch)
        return repo.save(updated).toProductResponse()
    }

    fun delete(id: ProductId): Boolean = repo.delete(id)
}
