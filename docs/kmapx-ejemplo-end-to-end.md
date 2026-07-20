# Ejemplo end-to-end — kmapx

Recorrido completo por las 3 etapas con un caso que integra todas las decisiones tomadas:
value classes, null-safety con default, anidado por referencia,
sealed paralelas, converter en Kotlin, modo interfaz + Spring,
PATCH con `copy()` y post-función en vez de lifecycle.

---

## ETAPA 0 — Código de entrada (lo que escribe el usuario)

```kotlin
// ── Dominio ────────────────────────────────────────────────────────────
@JvmInline value class OrderId(val value: String)

data class Address(val street: String, val city: String)

data class Customer(
    val id: OrderId,
    val name: String,
    val nickname: String?,           // nullable en el source
    val address: Address,            // requiere mapeo anidado
    val registeredAt: Instant,       // requiere converter
)

sealed interface PaymentEvent {
    data class Approved(val amount: BigDecimal) : PaymentEvent
    data class Rejected(val reason: String) : PaymentEvent
    data object Pending : PaymentEvent
}

// ── DTOs ───────────────────────────────────────────────────────────────
data class AddressDto(val street: String, val city: String)

data class CustomerDto(
    val id: String,                  // ← unwrap de OrderId
    val name: String,
    val nickname: String = "N/A",    // ← T? → T resuelto por default (opt-in)
    val address: AddressDto,         // ← anidado: referencia a toAddressDto()
    val registeredAt: String,        // ← Instant → String: converter
)

sealed interface PaymentEventDto {
    data class Approved(val amount: BigDecimal) : PaymentEventDto
    data class Rejected(val reason: String) : PaymentEventDto
    data object Pending : PaymentEventDto
}

data class CustomerPatchDto(val name: String?, val nickname: String?)

// ── Declaraciones de mapeo ─────────────────────────────────────────────

// Modo embedded: extension functions (el default idiomático)
@MapTo(AddressDto::class)
private annotation class AddressMapping   // ilustrativo; en la práctica @MapTo va sobre Address

@MapTo(CustomerDto::class, onNull = OnNull.TARGET_DEFAULT)   // opt-in del target default
// (anotación colocada sobre Customer)

@MapTo(PaymentEventDto::class)
// (anotación colocada sobre PaymentEvent; subtipos emparejados por nombre)

// Converter registrado como función Kotlin normal — nunca strings
@Converter
fun instantToIso(value: Instant): String = value.toString()

// Modo contract: interfaz + Spring, con post-función explícita
@Mapper(componentModel = ComponentModel.SPRING)
interface CustomerMapper {
    fun toDto(customer: Customer): CustomerDto

    // Reemplazo de @AfterMapping: explícito, tipado, devuelve el resultado
    fun afterToDto(source: Customer, result: CustomerDto): CustomerDto =
        result.copy(name = result.name.trim())
}

// Caso PATCH: solo válido porque Customer es data class
@Mapper
interface CustomerPatcher {
    fun apply(target: Customer, patch: CustomerPatchDto): Customer
}
```

---

## ETAPA 1-2 — Modelo de dominio y MappingPlan (interno, Kotlin puro, cero KSP)

El frontend KSP tradujo los símbolos a `MClass`/`MType` (omitido por brevedad).
El motor resolvió y produjo estos planes — **puro dato, testeable con assertEquals**:

```kotlin
// Plan 1: Customer → CustomerDto (modo extension)
MappingPlan(
    source = mtype("com.app.Customer"),
    target = mtype("com.app.CustomerDto"),
    emission = Emission.ExtensionFunction(name = "toCustomerDto"),
    construction = Construction.PrimaryConstructor(arguments = listOf(
        Argument("id",           UnwrapValueClass(ref("id"))),
        Argument("name",         Direct(ref("name"))),
        Argument("nickname",     NullFallbackToDefault(ref("nickname"))),
        Argument("address",      ViaMapper(ref("address"),
                                     MapperRef.GeneratedExtension("com.app.toAddressDto"),
                                     origin = Resolution.DECLARED_MAPTO)),         // referencia, nunca inline
        Argument("registeredAt", ViaConverter(ref("registeredAt"),
                                     ConverterRef("com.app.instantToIso"),
                                     origin = Resolution.USER_CONVERTER)),         // Gana sobre cualquier otra regla
    )),
    diagnostics = emptyList(),
)

// Plan 2: PaymentEvent → PaymentEventDto (sealed)
MappingPlan(
    source = mtype("com.app.PaymentEvent", kind = SEALED_INTERFACE),
    target = mtype("com.app.PaymentEventDto", kind = SEALED_INTERFACE),
    emission = Emission.ExtensionFunction(name = "toPaymentEventDto"),
    construction = Construction.SealedDispatch(branches = listOf(
        Branch(sourceSubtype = "Approved", plan = /* sub-plan Approved→Approved */),
        Branch(sourceSubtype = "Rejected", plan = /* sub-plan Rejected→Rejected */),
        Branch(sourceSubtype = "Pending",  plan = Construction.ObjectReference("PaymentEventDto.Pending")),
    )),
    // Si mañana agregan PaymentEvent.Refunded sin par en el DTO:
    // diagnostics = [KMX010: "PaymentEvent.Refunded has no counterpart in PaymentEventDto"]
    diagnostics = emptyList(),
)

// Plan 3: CustomerMapper (modo interfaz, Spring)
MapperPlan(
    interfaceName = "com.app.CustomerMapper",
    componentModel = ComponentModel.SPRING,          // el core lo guarda como dato; solo el emisor lo interpreta
    methods = listOf(
        MethodPlan(
            signature = "toDto(Customer): CustomerDto",
            body = /* mismo plan que Plan 1 */,
            postFunction = PostFunction("afterToDto"),
        ),
    ),
    dependencies = emptyList(),                       // sin colaboradores → podría ser object; SPRING fuerza class
)

// Plan 4: CustomerPatcher
PatchPlan(
    target = mtype("com.app.Customer", kind = DATA_CLASS),  // validado: si no fuera data class → KMX012
    patch  = mtype("com.app.CustomerPatchDto"),
    fields = listOf(
        PatchField("name",     semantics = NullMeansKeep),
        PatchField("nickname", semantics = NullMeansKeep),
    ),
)
```

---

## ETAPA 3 — Código de salida (lo que ve el usuario en build/generated/ksp/)

```kotlin
// Generated by kmapx 0.1.0. Do not edit.
package com.app

// ── Plan de Address (referenciado por los demás, generado una sola vez) ─
public fun Address.toAddressDto(): AddressDto = AddressDto(
    street = street,
    city = city,
)

// ── Plan 1: extension function de Customer ─────────────────────────────
public fun Customer.toCustomerDto(): CustomerDto = if (nickname != null) CustomerDto(
    id = id.value,                              // unwrap value class
    name = name,
    nickname = nickname,                        // smart-cast a String
    address = address.toAddressDto(),           // referencia, no inline
    registeredAt = instantToIso(registeredAt),  // converter del usuario
) else CustomerDto(
    id = id.value,
    name = name,
    // nickname omitido → el constructor aplica "N/A" 
    address = address.toAddressDto(),
    registeredAt = instantToIso(registeredAt),
)

// ── Plan 2: sealed dispatch con when exhaustivo (sin else) ─────────────
public fun PaymentEvent.toPaymentEventDto(): PaymentEventDto = when (this) {
    is PaymentEvent.Approved -> PaymentEventDto.Approved(amount = amount)
    is PaymentEvent.Rejected -> PaymentEventDto.Rejected(reason = reason)
    is PaymentEvent.Pending  -> PaymentEventDto.Pending
}

// ── Plan 3: implementación de la interfaz, modo Spring ─────────────────
@Component
public class CustomerMapperImpl : CustomerMapper {
    override fun toDto(customer: Customer): CustomerDto {
        val result = customer.toCustomerDto()   // reutiliza la extension: una sola fuente de verdad
        return afterToDto(customer, result)     // post-función explícita
    }
}

// ── Plan 4: PATCH inmutable vía copy() ─────────────────────────────────
public class CustomerPatcherImpl : CustomerPatcher {
    override fun apply(target: Customer, patch: CustomerPatchDto): Customer = target.copy(
        name = patch.name ?: target.name,           // null = no tocar
        nickname = patch.nickname ?: target.nickname,
    )
}
```

---

## Los errores que este mismo código produciría si algo estuviera mal

```text
# Si CustomerDto.nickname no tuviera default ni estrategia:
e: [KMX003] CustomerDto.kt:5 CustomerDto.nickname is non-nullable but source
   Customer.nickname is nullable.
   Fix: add a default parameter (with onNull = TARGET_DEFAULT), or annotate with @MapField(onNull = LITERAL/THROW/UNSAFE).

# Si Address no tuviera @MapTo(AddressDto::class):
e: [KMX007] Customer.kt:6 No mapping found for Address -> AddressDto required by CustomerDto.address.
   Fix: annotate Address with @MapTo(AddressDto::class) or register a @Converter.

# Si se agrega PaymentEvent.Refunded sin par en el DTO:
e: [KMX010] PaymentEvent.kt:4 PaymentEvent.Refunded has no counterpart in PaymentEventDto.
   Fix: add PaymentEventDto.Refunded or declare an explicit branch with @MapSubtype.

# Si Customer fuera una clase regular en el método PATCH:
e: [KMX012] CustomerPatcher.kt:2 Patch mapping requires the target to be a data class
   (copy() is needed for immutable update).
   Fix: make Customer a data class, or declare a regular mapping that constructs a new Customer.
```

## Qué demuestra este ejemplo (checklist de decisiones)

- Modo embedded y modo contract conviven; la implementación de interfaz **delega en la extension** — una sola lógica generada.
- El anidado siempre es una **referencia** a una función nombrada, jamás inline.
- El converter del usuario es una función Kotlin refactorizable, sin `expression = "..."`.
- `T? → T` solo compila porque hay estrategia (default + opt-in); el default se usa **omitiendo el argumento**.
- El `when` sealed no tiene `else`: agregar un subtipo rompe la compilación, no la producción.
- `componentModel` vive como dato en el plan; solo el emisor conoce `@Component` — el core no importa Spring.
- PATCH es `copy()` y exige data class; la mutación estilo `@MappingTarget` no existe.
- Todo el output es código que un desarrollador habría escrito a mano: auditable, depurable, overhead cero.
