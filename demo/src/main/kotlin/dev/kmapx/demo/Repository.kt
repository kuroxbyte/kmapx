package dev.kmapx.demo

import java.util.concurrent.ConcurrentHashMap

/**
 * Puerto de persistencia (DIP): el servicio depende de esta ABSTRACCIÓN, no de una implementación.
 * Cambiar a JDBC/Exposed/etc. no toca el servicio.
 */
interface ProductRepository {
    fun save(product: Product): Product
    fun findById(id: ProductId): Product?
    fun findAll(): List<Product>
    fun delete(id: ProductId): Boolean
}

/** Adaptador trivial en memoria — el foco de la demo es el mapeo, no el almacenamiento. */
class InMemoryProductRepository : ProductRepository {
    private val store = ConcurrentHashMap<String, Product>()

    override fun save(product: Product): Product {
        store[product.id.value] = product
        return product
    }

    override fun findById(id: ProductId): Product? = store[id.value]

    override fun findAll(): List<Product> = store.values.sortedBy { it.name }

    override fun delete(id: ProductId): Boolean = store.remove(id.value) != null
}
