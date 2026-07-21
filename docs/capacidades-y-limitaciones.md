# kmapx — qué se puede y qué no (con el porqué)

Referencia honesta del alcance actual. Los límites están clasificados en tres tipos:
**(D) por diseño** (no va a cambiar), **(V1) alcance de v1** (podría crecer), **(A) arquitectónico**
(límite de KSP, difícil de cambiar sin volverse compiler-plugin).

---

## Lo que SE PUEDE hacer

### Declaración
```kotlin
@MapTo(PersonDto::class)                          // modo embedded: fun Person.toPersonDto()
@MapTo(Summary::class, name = "asSummary")        // repetible + nombre custom
data class Person(val name: String, val age: Int)

@Mapper interface PersonMapper {                  // modo contract: object/class PersonMapperImpl
    fun toDto(p: Person): PersonDto               // dominio limpio (sin anotar Person/PersonDto)
}
```

### Construcción
- Orden determinista: `@MapConstructor` → `@MapFactory` → constructor primario.
- `var`s de cuerpo post-construcción vía `.also { }`.
- Defaults del target: `onNull = TARGET_DEFAULT` (campo, mapper/mapeo o global) omite el argumento (hasta 2 campos).

### Null-safety (`T? → T` es error salvo estrategia)
```kotlin
data class Dto(
    @MapField(onNull = OnNull.LITERAL, default = "N/A") val nick: String,  // ?: "N/A" (parseo tipado)
    @MapField(onNull = OnNull.THROW)          val email: String,            // ?: throw
    @MapField(onNull = OnNull.TYPE_DEFAULT)   val tags: List<String>,       // ?: emptyList() (escalares → 0/""/false)
    @MapField(onNull = OnNull.UNSAFE)         val legacy: String,           // !! (el único posible)
@MapField(ignore = true)                  val createdAt: Instant? = null, // Excluido (exige default)
)
```

### Conversiones estructurales
```kotlin
@JvmInline value class UserId(val v: String)      // id.value / UserId(s)
List<Address> → List<AddressDto>                  // items.map { it.toAddressDto() }
Set / Array<T> / Result<T> / Map<K,V>             // una pasada, sin copias
Iterable / Collection / Sequence  → List          // fuentes lazy/genéricas (Sequence: + .toList())
```

### Converters
```kotlin
@Converter fun isoInstant(v: Instant): String = v.toString()        // global, por tipo
object Cents : Converts<Long,String> { ... }                        // calificado, object
@MapField(converter = Cents::class) val price: String

@Component class CustomerName(repo: CustomerRepo) : Converts<Long,String> { ... }  // bean
@Mapper(componentModel = SPRING) interface M { @MapField(target = "x", converter = CustomerName::class) ... }
@MapperConfig(componentModel = SPRING, onNull = THROW) interface Cfg   // Profile reutilizable
@Mapper(config = Cfg::class) interface N { ... }                      //   → hereda settings (cascada)
@InverseOf("toDto") fun fromDto(d: Dto): Entity                        // La vuelta, invertida y validada (KMX028)
```
- **`stdConverters = true`** (`@MapTo`/`@Mapper`/`@MapperConfig` o `kmapx.stdConverters` global):
  suma las conversiones estándar `String↔UUID/BigDecimal/BigInteger/Instant`, `Long↔Instant`
  (epoch millis). **Solo JVM** (usa tipos `java.*`).

### Extensibilidad (0.2)
```kotlin
// Pack de converters listos — sin escribir ni un @Converter (ver guia-mapeo):
dependencies {
    implementation("io.github.kuroxbyte:kmapx-ext-jvm:<v>")   // java.time, UUID, BigDecimal, URI…
    ksp("io.github.kuroxbyte:kmapx-ext-jvm:<v>")
}
// Tu propio pack: implementá KmapxExtension (kmapx-spi) y registralo en META-INF/services.
```
- Un `@Converter` **tuyo** para un par siempre gana sobre el del pack; un pack jamás introduce
  ambigüedad (KMX009) ni suprime un error — solo añade caminos válidos.
- El SPI (`kmapx-spi`) es **experimental** hasta 1.0 (`@KmapxExperimentalSpi`).

### Renombrado y aplanado (en ambas sedes: campo y método)
```kotlin
@MapField(from = "firstName")            val name: String
@MapField(from = "address.country.code") val code: String       // ruta con nulabilidad por segmento
```

### Enums, sealed, bidireccional, patch
```kotlin
@MapEntry(target = "PENDING") CREATED                       // enums con nombres distintos
@MapSubtype(target = Approved::class)                       // sealed paralelos
@BiMapTo(Dto::class)                                        // ambas direcciones, invertibilidad VALIDADA
@Mapper interface P { fun apply(t: T, patch: Pt): T }  // PATCH por forma: retorno == 1er param
val note: Patch<String?> = Patch.Keep                       // set-null explícito
```

### Integraciones y config
- `@Mapper(componentModel = SPRING | KOIN)` → `@Component` / módulo Koin.
- `@Mapper(inheritFrom = Base::class)` — hereda config por método.
- `@SuppressKmapx("KMX021")` — silencia un WARNING puntual.
- Bloque `kmapx { }`: `useSerialNames`, `onNull` (cascada: campo > mapper/mapeo > global), `warningsAsErrors`, `report`.
- KMP: generación por target (JVM/JS/Wasm/Native).
- Reporte de cobertura JSON/HTML (`kmapx.report`).

---

## Lo que NO se puede (y por qué)

### Conversiones
| No se puede | Resultado | Por qué |
|---|---|---|
| `Long → Int` (narrowing con pérdida), `enum → String` | KMX004 | **(D)** lista CERRADA; nada de conversiones implícitas silenciosas. Usá `@Converter`. El widening SIN pérdida (`Int→Long`, `Float→Double`…) SÍ es automático. |
| Cruce de contenedor: `List → Set`, `Map → List` | KMX004 | **(D)** la lista de contenedores es cerrada. |
| `IntArray`/`LongArray` (arrays primitivos) elemento a elemento | KMX004 (passthrough solo si idéntico) | **(D)** solo `Array<T>`; los primitivos son passthrough. |
| `Iterable`/`Sequence` → **Set/Array/Result** | KMX004 | **(V1)** solo → `List`/`Collection`/`Iterable`. |
| Mapeo con `@Mapping(expression = "java(...)")` (strings con código) | — | **(D)** jamás strings con código; usá un `@Converter` (refactor-safe). |
| **Serializar** un objeto a JSON/XML automáticamente (`Meta → String`) | KMX004 | **(D)** kmapx mapea ESTRUCTURA, no serializa: es específico del tipo (necesita su serializador) y una responsabilidad de tu librería de serialización. Escribí un `@Converter fun (m: Meta): String = Json.encodeToString(m)` — de una línea, explícito y testeable. |

### Anidados y composición
| No se puede | Resultado | Por qué |
|---|---|---|
| Auto-mapear un anidado cuyo mapper vive en **otro módulo** | KMX007 | **(V1)** `declaredMappings` es por-módulo (KSP solo ve la ronda actual). Declará el par en el consumidor o usá `@Converter`. |
| Auto-generar el mapper anidado sin declararlo | KMX007 | **(D)** resolución por referencia: el par tiene que existir (`@MapTo`/`@Converter`). |
| **Des-aplanar** (construir anidado desde campos planos) | — | **(V1)** las rutas solo LEEN (aplanan), no construyen. |
| Ciclo de mapeo (`A→B→A`) | KMX008 | **(D)** se detecta y se corta (no recursión infinita). |
| Jerarquías sealed anidadas (>1 nivel) | KMX024 | **(V1)** un nivel en v1. |

### Modo interfaz / DI
| No se puede | Resultado | Por qué |
|---|---|---|
| `@MapField(converter=)` con **bean (class)** en modo embedded (extension) | KMX034 | **(A)** una función top-level no tiene dónde inyectar; solo modo contract. |
| Inyectar un bean que **no** tenga forma `Converts<A,B>` (un service cualquiera) | — | **(D)** guardrail: mantiene al mapper como traducción, no application-service. |
| Enriquecer un campo con un repo dentro del mapper (lógica que no es `A→B`) | — | **(D)** va en la capa de aplicación (mapper puro); el converter inyectado solo cubre el caso `A→B`. |
| Verificar en compile-time que el bean esté registrado en **Koin** | falla en runtime | **(A)** el registro vive fuera de la unidad de compilación. (En **Spring** SÍ se valida: KMX035.) |
| `@Qualifier`/scopes/prototype para el bean inyectado | — | **(V1)** se asume singleton. |
| Interfaz `@Mapper` **genérica** o herencia entre `@Mapper` | KMX015 | **(V1)** no soportado en v1. |

### PATCH
| No se puede | Resultado | Por qué |
|---|---|---|
| Setear `null` en un PATCH normal (campo `T?`) | null = conservar | **(D)** semántica JSON-Merge-Patch; para borrar usá `Patch<T>` (`Set(null)`). |
| `onNull = LITERAL/THROW/…` de `@MapField` en PATCH | inaplicable | **(D)** en un PATCH el `null` del patch significa "conservar el target" (no es una violación `T?→T`), así que la estrategia no tiene dónde actuar. El `converter` calificado SÍ se aplica (a `Patch<T>` y al fallback). |
| Config **por método** (`@MapField`) en métodos PATCH | — | **(V1)** el patch no consume la sede de método; su config vive en la clase target. |
| `afterApply` distinto por método en un patcher multi-método | — | **(V1)** un solo hook `afterApply` por interfaz. |
| Merge de colecciones (patch de una lista fusiona elementos) | reemplazo completo | **(V1)** null = no tocar; si viene, reemplaza toda la colección. |

### Enums / bidireccional
| No se puede | Resultado | Por qué |
|---|---|---|
| `@BiMapTo` con **fallback de clase** (`@MapEntry` catch-all sobre el enum) | KMX028 | **(D)** un fallback es fan-in (varios entries → uno): la vuelta no sabe a cuál volver. Los overrides POR ENTRY sí se invierten solos. |
| Entry del source sin par (ni por nombre ni `@MapEntry`) | KMX026 | **(D)** exhaustividad total, sin `else`. |

### Arquitectura / DX
| No se puede | Resultado | Por qué |
|---|---|---|
| DSL tipo Mappie (`to::x fromProperty from::y`) | — | **(A)** KSP no lee el cuerpo de lambdas; requeriría ser compiler-plugin. |
| Referencia de propiedad type-safe en anotación (`from = Src::name`) | — | **(A)** las anotaciones de Kotlin no aceptan `KProperty` (solo `String`/`KClass`/enum). |
| Mezclar cuerpo escrito a mano + generado en el mismo mapper (abstract class mapper) | — | **(A)** KSP no ve/edita cuerpos; solo genera archivos nuevos. |
| `expect`/`actual` anotadas | KMX025 | **(V1)** no-goal v1; anotá la `actual` por target o mapeá una clase común. |
| `@Converter` con 2+ params, `suspend`, receiver, o no-top-level | KMX019 | **(D)** debe ser función pura `(A) -> B`. |
| Más de 2 defaults con omisión condicional | KMX022 | **(D)** el `when` explota; anotá los extra con estrategia. |

---

## Cómo leer los "por qué"

- **(D) Por diseño:** son los principios que fundan el proyecto (cero conversiones implícitas,
  cero strings con código, cero reflection, cero DI entre mappers). No van a cambiar — son la
  garantía de "todo mapeo inseguro es error de compilación".
- **(V1) Alcance de v1:** decisiones de acotar, no de imposibilidad. Pueden crecer con demanda
  (cross-module, sede de método en PATCH, `converter` en PATCH, Sequence→Set, des-aplanar…).
  Muchas "conversiones que faltan" no esperan a una release: un **pack** (`kmapx-ext-*`) o tu
  propio `KmapxExtension` (SPI) las añade desde fuera, sin tocar la librería.
- **(A) Arquitectónico:** límites reales de KSP (no lee lambdas/expresiones; anotaciones sin
  `KProperty`). Cambiarlos implicaría abandonar KSP por un compiler-plugin — otra tecnología.

Ante la duda, el compilador te dice qué falta: cada `KMXnnn` trae ubicación exacta y `Fix:`.
