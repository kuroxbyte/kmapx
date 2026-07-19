# kmapx — Guía de referencia

La guía por TEMAS, al estilo de la reference guide de MapStruct: de cero a todas las
funcionalidades, en el orden en que las vas a necesitar.
Los bloques grandes de código salen de [ejemplos-avanzados.md](ejemplos-avanzados.md), que el
build **compila** — esta documentación no puede quedar por detrás del código sin que un test falle.

1. [Empezar](#1-empezar)
2. [Los dos modos: embedded y contract](#2-los-dos-modos-embedded-y-contract)
3. [Mapeo básico y construcción](#3-mapeo-básico-y-construcción)
4. [Config por campo: @MapField](#4-config-por-campo-mapfield)
5. [Null-safety y la cascada onNull](#5-null-safety-y-la-cascada-onnull)
6. [Converters](#6-converters)
7. [Colecciones y contenedores](#7-colecciones-y-contenedores)
8. [Sealed, enums y value classes](#8-sealed-enums-y-value-classes)
9. [Patch: actualización parcial inmutable](#9-patch-actualización-parcial-inmutable)
10. [Bidireccional e inversos](#10-bidireccional-e-inversos)
11. [Reutilización: profiles, herencia e ignore](#11-reutilización-profiles-herencia-e-ignore)
12. [Diagnósticos](#12-diagnósticos)
13. [Plugin de Gradle y config global](#13-plugin-de-gradle-y-config-global)
14. [Kotlin Multiplatform](#14-kotlin-multiplatform)
15. [Viniendo de MapStruct](#15-viniendo-de-mapstruct)

---

## 1. Empezar

```kotlin
// build.gradle.kts — la vía corta: el plugin aplica KSP y cablea todo
plugins { id("io.github.kuroxbyte.kmapx") }

// …o la vía manual, con las coordenadas de Maven Central:
dependencies {
    implementation("io.github.kuroxbyte:kmapx-annotations:0.1.0")
    implementation("io.github.kuroxbyte:kmapx-runtime:0.1.0")
    ksp("io.github.kuroxbyte:kmapx-frontend-ksp:0.1.0")
}
```

```kotlin
import dev.kmapx.annotations.embedded.MapTo

@MapTo(PersonDto::class)
data class Person(val name: String, val age: Int)

// genera: fun Person.toPersonDto(): PersonDto — código Kotlin real en <Source>Mappings.kt,
// navegable y depurable en el IDE, sin reflection ni overhead en runtime.
```

El pitch en una línea: **todo mapeo inseguro es un error de compilación**, con código KMX
estable, ubicación exacta y "did you mean". Ver [el catálogo completo](referencia/diagnosticos.md).

## 2. Los dos modos: embedded y contract

Un solo motor, dos superficies. Los paquetes de las anotaciones siguen el corte:

| | `embedded` (estilo JPA/Jackson) | `contract` (estilo MapStruct/DDD) |
|---|---|---|
| Declaración | `@MapTo`/`@BiMapTo` **sobre el modelo** | interfaz `@Mapper` — el dominio queda limpio |
| Config por campo | `@MapField` en el campo | `@MapField(target = "...")` en el método |
| Paquete | `dev.kmapx.annotations.embedded` | `dev.kmapx.annotations.contract` |
| Cuándo | DTOs propios, ergonomía primero | hexagonal/DDD, modelos de terceros |

```kotlin
import dev.kmapx.annotations.contract.Mapper

@Mapper
interface PersonMapper { fun toDto(p: Person): PersonDto }
// genera object PersonMapperImpl : PersonMapper. Si el par exacto ya tiene extension embedded,
// el método DELEGA en ella (la extension es la fuente de verdad).
```

Los métodos de un `@Mapper` aceptan **parámetros suplementarios** (se emparejan por nombre):
`fun create(request: CreateReq, id: ProductId, createdAt: Instant): Product`.

## 3. Mapeo básico y construcción

- Matching por nombre; renombre con `@MapField(from = "...")` (§4).
- Construcción determinista:
  `@MapConstructor` → `@MapFactory` (companion o top-level) → constructor primario visible.
  Ambigüedad = KMX006; nada utilizable = KMX005.
- Las `var`s públicas de cuerpo se asignan post-construcción vía `.also { }`.
- Anidados: un par declarado resuelve por REFERENCIA a
  su función generada (nunca inline); los ciclos de declaración son KMX008.
- Post-función opcional: un método default
  `after<Método>(source, result): result` — el único "callback", explícito y tipado.

## 4. Config por campo: @MapField

UNA anotación con los aspectos de un campo destino,
válida en ambas sedes:

```kotlin
@MapField(
    target = "price",              // SOLO en sede de método (en el campo está prohibido: KMX036)
    from = "supplier.address.city",// renombre plano o ruta anidada con ?. por segmento
    converter = PriceTag::class,   // converter calificado por campo — object o bean
    onNull = OnNull.LITERAL,       // estrategia de nulabilidad (§5) — exclusiva por construcción
    default = "N/A",               // solo con LITERAL (KMX038/KMX039)
    ignore = true,                 // exclusión deliberada — excluyente con lo demás (KMX043)
)
```

Reglas: una `@MapField` por campo (repetida = KMX037); método gana sobre campo con warning KMX032.

## 5. Null-safety y la cascada onNull

`T? → T` es **error de compilación** (KMX003) salvo
salida declarada. La salida se resuelve por CASCADA
:

```
@MapField(onNull=…)  >  @Mapper / @MapTo(onNull=…)  >  @MapperConfig(onNull=…)  >  kmapx.onNull global
```

| `OnNull` | Efecto | Ámbito |
|---|---|---|
| `INHERIT` | el default en TODAS las sedes: cae al siguiente nivel | todos |
| `STRICT` | corta la cascada: KMX003 aunque un nivel superior declare salida | todos |
| `LITERAL` + `default` | `?: literal` con parseo TIPADO (KMX017 si no parsea) | solo campo |
| `TYPE_DEFAULT` | el cero/vacío de la lista CERRADA: `emptyList()/emptySet()/emptyMap()`, `0`, `0L`, `0.0`, `0.0f`, `false`, `""` | todos* |
| `TARGET_DEFAULT` | OMITE el argumento → aplica el default del constructor | todos* |
| `THROW` | `?: throw IllegalArgumentException(campo + par de tipos)` | todos |
| `UNSAFE` | `!!` consentido — el ÚNICO `!!` posible (un test global lo garantiza) | solo campo |

\* Como política de NIVEL, las condicionales aplican donde pueden y la violación **cae al
siguiente nivel**; declaradas explícitas en un campo inaplicable son error (KMX033/KMX040).

**Política `unmapped`** — la omisión ("campo sin fuente llenado por su
default", KMX021) gradúa su severidad con la MISMA cascada: `@MapTo`/`@Mapper`/`@MapperConfig`
`(unmapped = Unmapped.IGNORE|WARN|ERROR)` o `kmapx.unmapped` global. El default es `WARN` (el
histórico); `ERROR` bloquea el build (auditoría estricta); `IGNORE` la acalla.

## 6. Converters

```kotlin
@Converter                                  // GLOBAL: aplica a todo par Instant→String
fun instantToIso(value: Instant): String = value.toString()

object ShortDate : Converts<LocalDate, String> {   // CALIFICADO: se elige POR CAMPO
    override fun convert(value: LocalDate) = value.format(SHORT)
}
data class EventDto(@MapField(converter = ShortDate::class) val startDate: String)
```

Orden de resolución: calificado (paso 0)
→ global → value class → idéntico → colecciones → mapper declarado. Dos globales del mismo par =
KMX009. En modo contract, un converter puede ser una **class con dependencias** — se inyecta al
constructor del impl (en modo embedded es KMX034).

## 7. Colecciones y contenedores

Lista CERRADA de conversiones implícitas: idéntico, `T → T?`, colecciones elemento a elemento
en UNA pasada (`items.map { it.toDto() }`), `Map<K,V>` (clave invariante, valor por la cadena),
`Array`, `Result<T>`, fuentes `Iterable`/`Sequence` que materializan a `List`. Nada más:
`Long→Int` (narrowing) o `List→Set` piden `@Converter` (KMX004).

**Implícitos ampliados**: el widening numérico SIN PÉRDIDA es automático (`Int→Long`,
`Float→Double`, `Byte/Short→Int/Long/Float/Double` — emite `x.toLong()`, elementos de colección
incluidos), y con `stdConverters = true` (en `@MapTo`/`@Mapper`/`@MapperConfig` o
`kmapx.stdConverters` global) se suman las conversiones estándar `String↔UUID`,
`String↔BigDecimal`, `String↔BigInteger`, `String↔Instant` y `Long↔Instant` (epoch millis).
Un `@Converter` tuyo para el par siempre gana.

**Métodos de colección en contract**: un `@Mapper` puede declarar
`fun toDtos(orders: List<Order>): List<OrderDto>` — el elemento DELEGA en el método hermano del
par exacto o en la extension declarada (`orders.map { toDto(it) }`); sin ninguno, KMX046.
Lista cerrada: `List→List`, `Set→Set`, `List/Set→Collection/Iterable`.

## 8. Sealed, enums y value classes

- **Sealed paralelas**: `when` exhaustivo SIN `else`;
  pareo por `simpleName` con override `@MapSubtype`; source sin par = KMX010.
- **Enums**: `when` por igualdad; override `@MapEntry`; entry sin
  par = KMX026 con did-you-mean. **Fallback de clase**: `@MapEntry("UNKNOWN")`
  sobre el enum SOURCE manda todo entry sin par a ese destino — rama explícita por entry,
  jamás un `else`; fallback inexistente = KMX047.
- **Value classes**: wrap/unwrap transparente — ojo: el
  `init` validador SÍ se ejecuta al envolver.

## 9. Patch: actualización parcial inmutable

No hay anotación: la **FORMA** del método lo declara —
retorno == tipo del PRIMER parámetro, aridad 2:

```kotlin
@Mapper
interface CustomerMapper {
    fun toDto(c: Customer): CustomerDto                          // mapping
    fun applyPatch(target: Customer, patch: CustomerPatch): Customer   // PATCH → target.copy(...)
}
data class CustomerPatch(
    val name: String?,          // null = NO tocar
    val email: Patch<String>,   // tri-estado: Keep / Set(v) / Set(null) — el when es exhaustivo
)
```

El target debe ser data class (KMX012). Post-función: `after<Método>(target, patch, result)`.

## 10. Bidireccional e inversos

La misma inversión del motor en las dos sedes — renombres se voltean solos, un converter exige
su inverso registrado, y toda asimetría es **KMX028** en compile-time
:

```kotlin
@BiMapTo(WarehouseDto::class)      // embedded: A.toB() Y B.toA() desde UNA declaración
class Warehouse private constructor(...) { ... }

@Mapper interface M {
    @MapField(target = "displayName", from = "name")
    fun toDto(c: Customer): CustomerDto
    @InverseOf                     // contract: "" auto-detecta la única firma inversa
    fun fromDto(dto: CustomerDto): Customer   // genera name = dto.displayName
}
```

Una política de la cascada cuenta como estrategia de vuelta (el widening `T → T?` se cierra con
el `THROW` del mapper/profile/global). Nota v1: los overrides `@MapEntry` no participan — un
enum bidireccional exige nombres idénticos.

## 11. Reutilización: profiles, herencia e ignore

```kotlin
@MapperConfig(componentModel = SPRING, onNull = OnNull.THROW, ignore = ["createdAt", "updatedAt"])
interface CompanyMapperConfig                       // settings corporativos: NO genera código

@Mapper(config = CompanyMapperConfig::class,        // hereda settings (KMX044 si no es profile)
        inheritFrom = BaseCustomerMapper::class)    // hereda config POR MÉTODO; la propia gana por campo
interface CustomerMapper { ... }
```

**Ignore**: `@MapField(ignore = true)` per-field o listas
`ignore = [...]` en `@MapTo`/`@Mapper`/`@MapperConfig` (se UNEN, nunca des-ignoran). Exige
default de constructor (`= null` para nullables) — la exclusión es OMISIÓN, nunca un valor
inventado (KMX042). Silencia KMX002/KMX021: es el consentimiento explícito.

## 12. Diagnósticos

Todos los errores en UNA pasada, señalando la línea del parámetro target, con did-you-mean y
formato canónico verificado por contrato. El catálogo completo (KMX001–KMX045) está
**[generado desde el código](referencia/diagnosticos.md)** — mensaje real, fix y severidad por
código. Los WARNING se silencian con `@SuppressKmapx("KMXnnn")`; los errores, nunca.
`kmapx.warningsAsErrors = true` para CI duro.

## 13. Plugin de Gradle y config global

```kotlin
plugins { id("io.github.kuroxbyte.kmapx") }        // JVM y KMP: aplica KSP y cablea todo por target

kmapx {
    onNull.set(OnNull.THROW)       // el nivel GLOBAL de la cascada (§5)
    useSerialNames.set(true)       // @SerialName como alias de matching (aditivo)
    warningsAsErrors.set(true)
    report("json", "html")         // Reporte de cobertura de mapeo con trazabilidad por campo
    moduleName.set("orders")
}
```

## 14. Kotlin Multiplatform

`annotations` y `runtime` publican para todos los targets; el output es válido en `commonMain`
([guía KMP](guia-kmp.md)). El processor corre por target
; `expect`/`actual` no se mapean en v1 (KMX025).

## 15. Viniendo de MapStruct

La [guía de migración](guia-migracion-mapstruct.md) mapea anotación por anotación
(`@Mapping`→`@MapField`, `@Named/qualifiedBy`→`converter =`, `@MappingTarget`→patch por forma,
`@InheritInverseConfiguration`→`@InverseOf`, `@MapperConfig`→`@MapperConfig`). Las diferencias
de fondo: kmapx genera **extensions Kotlin** además de impls, valida invertibilidad y rutas en
compile-time, y no tiene strings con código (`expression = "java(...)"` no existe a propósito).
