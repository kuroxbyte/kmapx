# Ejemplos avanzados — modo embedded y modo contract

Dos ejemplos grandes que **combinan** las funcionalidades, uno por modo. No son
pseudocódigo: el harness los compila tal cual en los tests
`los ejemplos avanzados del modo embedded compilan` y `... del modo contract compilan`
(`AdvancedExamplesGuideTest.kt`) — si esta guía se desactualiza, el build lo dice.

- **Modo embedded** (ex modo A, estilo JPA/Jackson): la config vive *embebida* en el modelo.
- **Modo contract** (ex modo B, estilo MapStruct/DDD): el mapeo es un *contrato* — una interfaz
  en la capa de infraestructura; el dominio queda sin una sola anotación kmapx.

---

## Modo embedded — catálogo de e-commerce

Combina: `@MapTo` repetible con `name`, política de mapeo `onNull = TYPE_DEFAULT` (colecciones
**y** escalares), lista `ignore` de mapeo + `ignore` per-field, `@MapField` con sus
tres aspectos (ruta anidada, converter calificado, `LITERAL`), `TARGET_DEFAULT`,
value classes, `@Converter` global vs calificado (paso 0 del orden de resolución), `@MapFactory`,
sealed con `@MapSubtype`, enums con `@MapEntry`, `@BiMapTo`, colecciones y
`Map` en una pasada.

```kotlin
import dev.kmapx.annotations.*
import dev.kmapx.annotations.contract.*
import dev.kmapx.annotations.embedded.*
import dev.kmapx.runtime.Converts

// ── Value classes — se (des)envuelven solas ──
@JvmInline value class ProductId(val value: String)

// ── Converter GLOBAL — cualquier Long→String sin calificar pasa por aquí ──
@Converter
fun centsToDisplay(cents: Long): String =
    "$" + (cents / 100) + "." + (cents % 100).toString().padStart(2, '0')

// ── Converter CALIFICADO — elegido POR CAMPO; gana sobre el global (paso 0) ──
object PriceTag : Converts<Long, String> {
    override fun convert(value: Long): String = "PRICE:" + value
}

// ── Enums paralelos BIDIRECCIONALES — la vuelta invierte los @MapEntry sola ──
enum class CategoryDto { ELECTRONICS, BOOKS, HOME_GARDEN }

@BiMapTo(CategoryDto::class)               // ida y vuelta: HOME_GARDEN → HOME_AND_GARDEN se deriva
enum class Category {
    ELECTRONICS,
    BOOKS,
    @MapEntry(target = "HOME_GARDEN") HOME_AND_GARDEN,
}

// ── Factory privada Y bidireccional ──
data class WarehouseDto(val code: String, val capacity: Int)

@BiMapTo(WarehouseDto::class)              // Ida y vuelta; ambas construyen vía of(...)
class Warehouse private constructor(val code: String, val capacity: Int) {
    companion object {
        @MapFactory
        fun of(code: String, capacity: Int) = Warehouse(code.uppercase(), capacity)
    }
}

// ── Sealed paralelas — when exhaustivo SIN else; @MapSubtype redirige nombres distintos ──
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

// ── Anidados: el par declarado resuelve dentro de colecciones ──
data class AddressDto(val city: String)

@MapTo(AddressDto::class)
data class Address(val city: String)

data class Supplier(val name: String, val address: Address)

// ── El DTO: @MapField concentra los tres aspectos de cada campo ──
data class ProductDto(
    val id: String,                                        // ProductId desenvuelta
    val name: String,

    // Rename + converter calificado en UNA anotación
    @MapField(from = "priceCents", converter = PriceTag::class) val price: String,

    // Este Long usa el @Converter GLOBAL (no está calificado)
    @MapField(from = "priceCents") val priceDisplay: String,

    // Ruta anidada — se navega y valida en compile-time
    @MapField(from = "supplier.address.city") val supplierCity: String,

    // onNull per-field: LITERAL con parseo tipado
    @MapField(onNull = OnNull.LITERAL, default = "N/A") val notes: String,

    // Si la fuente es null se OMITE el argumento → aplica el default
    @MapField(onNull = OnNull.TARGET_DEFAULT) val discount: Int = 0,

    // Excluido deliberadamente — exige default; silencia KMX002/KMX021
    @MapField(ignore = true) val internalNotes: String? = null,

    // Estos dos NO declaran nada: los cierra la política de MAPEO de abajo (TYPE_DEFAULT)
    val tags: List<String>,                                //  ?: emptyList()
    val rating: Double,                                    //  ?: 0.0

    val category: CategoryDto,                             // enum dispatch
    val restock: StockEventDto,                            // sealed dispatch
    val stops: List<AddressDto>,                           // elemento a elemento
    val stockByCode: Map<String, Int>,                     // Map passthrough

    val internalCode: String? = null,                      // lo excluye la LISTA ignore del @MapTo
)

data class ProductSummary(
    @MapField(from = "supplier.address.city") val supplierCity: String,   // cada mapeo declara su ruta
    val name: String,
)

// ── La entidad: DOS mapeos desde una clase (repeatable + name), con política e ignore de MAPEO ──
@MapTo(ProductDto::class, onNull = OnNull.TYPE_DEFAULT, ignore = ["internalCode"])
@MapTo(ProductSummary::class, name = "asSummary")
data class Product(
    val id: ProductId,
    val name: String,
    val priceCents: Long,
    val supplier: Supplier,
    val notes: String?,
    val discount: Int?,
    val tags: List<String>?,        // → emptyList() por la política del mapeo
    val rating: Double?,            // → 0.0 (escalares en la lista cerrada)
    val category: Category,
    val restock: StockEvent,
    val stops: List<Address>,
    val stockByCode: Map<String, Int>,
)
```

Genera `Product.toProductDto()` y `Product.asSummary()` en `ProductMappings.kt`, `when`
exhaustivos para sealed/enum, y `Warehouse.toWarehouseDto()` **más** `WarehouseDto.toWarehouse()`
por el `@BiMapTo` — ambas direcciones construyendo vía la factory, con invertibilidad validada
(KMX028 si no cerrara). Los overrides `@MapEntry` también participan de `@BiMapTo`: la vuelta
los invierte sola (`CategoryDto.HOME_GARDEN → Category.HOME_AND_GARDEN`); el fallback de CLASE
 es fan-in — varios entries hacia uno — y ahí `@BiMapTo` sigue siendo KMX028.

**La cascada en acción** (`campo > mapeo > global`): `notes`/`discount` declaran su
salida per-field y GANAN; `tags`/`rating` no declaran nada y caen a la política del mapeo
(`TYPE_DEFAULT`); si tampoco existiera, caerían al `kmapx.onNull` global del bloque `kmapx { }`;
agotada la cascada, `T? → T` vuelve a ser el error KMX003. Un
`@MapField(onNull = OnNull.STRICT)` explícito CORTA la cascada para ese campo.

---

## Modo contract — banca con dominio 100% limpio

Combina: `@MapperConfig` (profile: `componentModel`, política y `ignore` corporativos),
`@Mapper(config, inheritFrom)` (herencia de config por método), interfaz MIXTA con patch
por FORMA y `Patch<T>` tri-estado, `@InverseOf` con auto-detección,
converter INYECTADO como bean, parámetros suplementarios, post-funciones `after<Método>`
 y `@MapField` en sede de método.

```kotlin
import dev.kmapx.annotations.*
import dev.kmapx.annotations.contract.*
import dev.kmapx.annotations.embedded.*
import dev.kmapx.runtime.Converts
import dev.kmapx.runtime.Patch

// ── DOMINIO: ni una anotación kmapx (el punto del modo contract) ──
data class Customer(
    val id: String,
    val name: String,
    val nickname: String?,
    val email: String,
    val riskScore: Int,
)

// ── DTOs / requests: también limpios ──
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
    val name: String?,              // null = no tocar
    val email: Patch<String>,       // tri-estado: Keep / Set(v) / Set(null)
)

// ── Converter inyectado — una CLASS con dependencias, no un object ──
@org.springframework.stereotype.Component
class RiskLabeler : Converts<Int, String> {
    override fun convert(value: Int): String = if (value > 70) "HIGH" else "LOW"
}

// ── El profile corporativo — settings en UN lugar, no genera código ──
@MapperConfig(
    componentModel = ComponentModel.SPRING,   // todos los mappers del profile son @Component
    onNull = OnNull.THROW,                    // nivel PROFILE de la cascada
    ignore = ["audit"],                       // Campos de auditoría fuera, en toda la empresa
)
interface CompanyMapperConfig

// ── Mapper base — su config por método se hereda por NOMBRE ──
@Mapper(onNull = OnNull.THROW)             // el base es válido POR SÍ MISMO (genera su propio impl)
interface BaseCustomerMapper {
    @MapField(target = "displayName", from = "name")
    @MapField(target = "nickname", onNull = OnNull.LITERAL, default = "-")
    fun toDto(c: Customer): CustomerDto
}

// ── El mapper: interfaz MIXTA — mapping + patch + inverso conviven ──
@Mapper(config = CompanyMapperConfig::class, inheritFrom = BaseCustomerMapper::class)
interface CustomerMapper {

    // MAPPING — hereda el rename displayName del base; su @MapField de nickname
    // PISA al heredado ("N/A" gana sobre "-": la config propia gana por campo destino).
    @MapField(target = "nickname", onNull = OnNull.LITERAL, default = "N/A")
    fun toDto(c: Customer): CustomerDto

    // MAPPING con converter INYECTADO: la impl SPRING lo recibe por constructor.
    @MapField(target = "riskLabel", from = "riskScore", converter = RiskLabeler::class)
    fun toView(c: Customer): CustomerView

    // MAPPING con targets heterogéneos: aquí SÍ existe "audit" → el ignore del profile aplica
    // (en los targets sin ese campo, simplemente no hace nada).
    fun toSummary(c: Customer): AccountSummary

    // MAPPING con parámetros SUPLEMENTARIOS: id lo genera el servicio, se empareja por nombre.
    fun create(request: CreateCustomerRequest, id: String): Customer

    // PATCH por FORMA — retorno == tipo del PRIMER parámetro (@PatchMapper no existe):
    // name String? = no tocar; email Patch<String> = tri-estado con when exhaustivo.
    fun applyPatch(target: Customer, patch: CustomerPatch): Customer

    // El INVERSO de toDto, auto-detectado (única firma inversa). Renombres se invierten
    // (name = displayName); el widening email String? → String lo cierra el THROW del profile.
    @InverseOf
    fun fromDto(dto: CustomerDto): Customer

    // Post-funciones — (source, result) para mapping, (target, patch, result) para patch.
    fun afterToDto(source: Customer, result: CustomerDto): CustomerDto =
        result.copy(displayName = result.displayName.trim())
    fun afterApplyPatch(target: Customer, patch: CustomerPatch, result: Customer): Customer = result
}
```

Genera `class CustomerMapperImpl(riskLabeler: RiskLabeler) : CustomerMapper` anotada
`@Component` (el profile lo decidió), con el `copy()` del patch, el `when` del tri-estado, la
vuelta invertida de `fromDto` y las post-funciones aplicadas sobre cada resultado.

**Qué valida el compilador aquí**: `config` sin `@MapperConfig` = KMX044; dos `@InverseOf`
mutuos o firma no-inversa = KMX045; `ignore` inexistente en TODOS los targets = KMX011 con
did-you-mean; el inverso de un campo con converter sin inverso registrado = KMX028; `LITERAL`
sin `default` = KMX038; patch sobre un target que no es data class = KMX012.

---

## Los dos modos conviven

Si el par exacto ya tiene extension embedded declarada, el método contract **delega** en ella
(la extension es la fuente de verdad): declarar `@MapTo(CustomerDto::class)` sobre
`Customer` haría que `CustomerMapperImpl.toDto` emita `return c.toCustomerDto()` en vez de
materializar el plan inline. Un solo motor, dos superficies.

| Decisión | embedded | contract |
|---|---|---|
| ¿Dónde vive la config? | En el modelo (`@MapTo`, `@MapField` en el campo) | En la interfaz (`@MapField(target=)` en el método) |
| ¿Cuándo elegirlo? | DTOs propios, ergonomía primero | DDD/hexagonal, modelos de terceros, dominio limpio |
| Analogía | JPA / Jackson / kotlinx.serialization | MapStruct (`@Mapper`, `@MapperConfig`, inversos) |
| Bidireccional | `@BiMapTo` | `@InverseOf` |
| Reutilización | política e `ignore` por `@MapTo` | `@MapperConfig` (profiles) + `inheritFrom` |
