# Demo — CRUD de catálogo con kmapx (reemplazo de MapStruct)

Una app JVM pequeña (crear/leer/listar/actualizar/borrar productos) donde **todo el mapeo
dominio ↔ DTO lo genera kmapx en compile-time**. Cero reflection, cero strings con código, cero
mapeo escrito a mano.

```bash
./gradlew :demo:run     # recorrido CRUD ejecutable
./gradlew :demo:test    # el CRUD verificado end-to-end
```

## Qué demuestra (features en un caso real)

| En la demo | Feature | Dónde |
|---|---|---|
| `ProductId`/`Sku` → `String` y viceversa | Value classes transparentes | `id.value`, `Sku(request.sku)` |
| `Category` ↔ `CategoryDto` | Enum bidireccional desde una anotación | `@BiMapTo(Category::class)` |
| `priceCents: Long` → `"$1299.99"` | Converter **calificado** por campo | `@MapField(converter = PriceUsd::class)` |
| `createdAt: Instant` → ISO | Converter global | `@Converter fun instantToIso` |
| `priceCents` → campo `price` | Renombrado | `@MapField(from = "priceCents")` |
| entidad → respuesta / resumen | Modo embedded, `@MapTo` repetible | sobre `Product` |
| request → entidad, con `id`/`createdAt` inyectados | Modo contract + params suplementarios | `@Mapper interface ProductFactory` |
| actualización parcial (null = no tocar) | PATCH inmutable por FORMA | `fun apply(target: Product, patch: ProductPatch): Product` en un `@Mapper` |

## El código que kmapx genera

Lo que escribirías a mano — legible, depurable, sin sorpresas:

```kotlin
public fun Product.toProductResponse(): ProductResponse = ProductResponse(
  id = id.value,                        // value class desenvuelta
  sku = sku.value,
  name = name,
  category = category.toCategoryDto(),  // enum → enum (otra función generada)
  price = PriceUsd.convert(priceCents), // converter calificado (elegido por @MapField(converter=))
  createdAt = instantToIso(createdAt),  // converter global
  tags = tags,
)

public object ProductFactoryImpl : ProductFactory {          // modo contract, dominio limpio
  override fun create(request: CreateProductRequest, id: ProductId, createdAt: Instant): Product =
    Product(
      id = id,
      sku = Sku(request.sku),               // value class envuelta
      name = request.name,
      category = request.category.toCategory(),
      priceCents = request.priceCents,
      createdAt = createdAt,
      tags = request.tags,
    )
}
```

## Frente a MapStruct

- **Errores en compile-time, con `Fix:`** — un campo sin fuente, tipos incompatibles o un converter
  que no encaja fallan la compilación con `KMXnnn`, no en runtime ni en silencio.
- **Sin reflection**: el binario no carga nada de kmapx; el código generado es Kotlin normal que el
  IDE navega, autocompleta y depura (ventaja gratuita sobre las libs basadas en reflection).
- **Sin `@Named`/strings**: la elección de converter por campo es por `KClass` (`@MapField(converter=)`),
  refactor-safe. Ver [guía de migración](../docs/guia-migracion-mapstruct.md).
- **KMP-listo**: el mismo enfoque compila en `commonMain` (esta demo es JVM por simplicidad).
