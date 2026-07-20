# kmapx

Mapper Kotlin-first en compile-time: **todo mapeo inseguro es un error de compilación**.
Core de dominio puro (hexagonal) + frontend KSP2 + backend KotlinPoet.

**📚 Documentación completa: [kuroxbyte.github.io/kmapx](https://kuroxbyte.github.io/kmapx/)**

## Estado

**Publicado en Maven Central** (`io.github.kuroxbyte:kmapx-*`, versión `0.1.0`, Apache-2.0).
Motor completo: dos modos (embedded/contract), converters (globales, calificados, inyectados),
colecciones, value classes, sealed y enums paralelos, PATCH por forma, bidireccional validado,
rutas anidadas, profiles con herencia, integraciones Spring/Koin/serialization, reporte de
cobertura y Kotlin Multiplatform (JVM/Android, JS, WasmJS, Native). Diagnósticos KMX001–KMX047,
cada uno con factory y contrato verificado.

En curso (`0.2.0-SNAPSHOT`): SPI de extensión (`kmapx-spi`) y packs de converters (`kmapx-ext-jvm`).
Ver [CHANGELOG.md](CHANGELOG.md).

## Instalación (JVM)

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.1.21"
    id("com.google.devtools.ksp") version "2.1.21-2.0.1"
}
dependencies {
    implementation("io.github.kuroxbyte:kmapx-annotations:0.1.0")
    implementation("io.github.kuroxbyte:kmapx-runtime:0.1.0")   // solo para Converts<A,B> / Patch<T>
    ksp("io.github.kuroxbyte:kmapx-frontend-ksp:0.1.0")
}
```

```properties
# gradle.properties
ksp.useKSP2=true
```

Para Kotlin Multiplatform, KSP se declara por target — ver la
[guía multiplataforma](https://kuroxbyte.github.io/kmapx/guia-kmp/).

## Compilar desde el fuente

```bash
./gradlew build
```

Requisitos: JDK 17+.

## Módulos

| Módulo               | Rol                                                                          |
|----------------------|------------------------------------------------------------------------------|
| `annotations`        | API pública del usuario. KMP, cero dependencias.                             |
| `runtime`            | KMP, mínimo: la interfaz `Converts` (solo si se usa el aspecto `converter` de `@MapField`). |
| `core`               | Modelo (`MType`, `MappingPlan`), motor, diagnósticos. Kotlin puro. |
| `spi`                | SPI de extensión (experimental): puntos de extensión del processor, vía `ServiceLoader`. |
| `ext-jvm`            | Pack de converters JVM (`java.time`, `UUID`, `BigDecimal`, `URI`…). Consumidor del SPI. |
| `adapter-reflect`    | `mclassOf<T>()` para testear el core sin compilador. Solo tests.             |
| `backend-codegen`    | Materializa `MappingPlan` → `.kt` (KotlinPoet).                              |
| `frontend-ksp`       | Processor KSP2: traduce símbolos, invoca al motor, emite o reporta KMX.      |
| `architecture-tests` | Konsist: la frontera del core como test.                                     |
| `integration-tests`  | Proyecto KMP consumidor: consumo end-to-end en vivo.                             |
| `incremental-tests`  | Gradle TestKit: incrementalidad de KSP contra un consumidor real.     |
| `demo`               | App JVM CRUD que reemplaza a MapStruct (`./gradlew :demo:run`). Ver [demo/README](demo/README.md). |
| `gradle-plugin`      | Plugin `id("io.github.kuroxbyte.kmapx")`: aplica KSP y cablea todo. Ver [gradle-plugin/README](gradle-plugin/README.md). |
| `intellij-plugin`    | Plugin de IntelliJ: el motor real en el editor (diagnósticos KMX, quick-fixes, navegación, materializar). Build STANDALONE: `./gradlew -p intellij-plugin buildPlugin`. |

## Documentación

- Alcance actual (qué se puede y qué no, con el porqué): [capacidades-y-limitaciones.md](docs/capacidades-y-limitaciones.md).
- Ejemplos que COMBINAN funcionalidades, por modo (compilados por el harness): [ejemplos-avanzados.md](docs/ejemplos-avanzados.md).
- Guía de referencia por temas (estilo MapStruct): [referencia.md](docs/referencia.md) ·
  catálogo de diagnósticos GENERADO desde el código: [diagnosticos.md](docs/referencia/diagnosticos.md).
- Sitio: `mkdocs serve` (MkDocs Material; deploy automático a Pages) · API docs: `./gradlew :annotations:dokkaGeneratePublicationHtml`.
- Desarrollo: regenerar snapshots con `./gradlew :backend-codegen:test -Dkmapx.updateSnapshots=true` y revisar el diff.

## Uso (modo embedded)

```kotlin
@MapTo(PersonDto::class)                      // genera fun Person.toPersonDto(): PersonDto
@MapTo(PersonSummary::class, name = "asSummary") // repeatable; nombre custom opcional
data class Person(val name: String, val age: Int)
```

Todas las funciones de una clase van a `<Source>Mappings.kt`, en su mismo paquete, replicando
su visibilidad (`internal` → `internal`). Colisión de nombres de función → error `KMX013`.

## Construcción

Orden determinista: `@MapConstructor` → `@MapFactory` (companion o top-level) → constructor
primario visible. Ambigüedad → `KMX006`; nada utilizable → `KMX005`. Las `var`s públicas de
cuerpo se asignan post-construcción vía `.also { }`.

## Modo contract — la interfaz @Mapper

```kotlin
@Mapper
interface PersonMapper { fun toDto(p: Person): PersonDto }
// genera: object PersonMapperImpl : PersonMapper — delega en Person.toPersonDto() si existe,
// o materializa el plan inline si no. Post-función opcional: afterToDto(source, result): result.
```

## Null-safety

`T? → T` es error de compilación (`KMX003`) salvo estrategia explícita en el target:

```kotlin
data class Dto(
    @MapField(onNull = OnNull.LITERAL, default = "N/A") val nickname: String, // nickname ?: "N/A" (parseo tipado)
    @MapField(onNull = OnNull.THROW) val email: String,               // ?: throw IllegalArgumentException("email must not be null mapping Src -> Dto")
    @MapField(onNull = OnNull.UNSAFE) val legacy: String,          // legacy!! — el ÚNICO !! posible (test global lo garantiza)
)
```

Dos estrategias para el mismo campo son inexpresables (`onNull` es UN valor); `@MapField`
duplicada → `KMX037`; `LITERAL` sin `default` → `KMX038`; default no parseable/tipo no
soportado → `KMX017`; estrategia sin violación → warning `KMX018`.

## Errores

Todos los errores de una pasada (nunca "arregla uno, descubre el siguiente"), señalando la
línea del parámetro target, con "did you mean" (distancia ≤ 2, case-insensitive, hasta 2
candidatos) y formato canónico `[KMXnnn] <ubicación> <problema>. Fix: <acción>.` verificado
por test de contrato.

## Conversiones implícitas

Lista CERRADA: idéntico (genéricos incluidos), `T → T?`, wrap/unwrap de value class, y
colecciones elemento a elemento en una sola pasada (`addresses.map { it.toAddressDto() }`,
`Set` vía `mapTo(mutableSetOf())`, anidamiento con una lambda por nivel). Nada más:
`Long→Int` (narrowing), enum→String, `List→Set`… → `KMX004` con fix sugiriendo `@Converter`.
El widening numérico SIN PÉRDIDA es automático (`Int→Long`, `Float→Double`…)
y `stdConverters = true` suma las estándar (`String↔UUID`, `String↔BigDecimal`, `Long↔Instant`…).

## Converters

```kotlin
@Converter fun instantToIso(value: Instant): String = value.toString()
// createdAt = instantToIso(createdAt) — función Kotlin normal, refactor-safe.
// Gana sobre toda otra resolución. Dos converters del mismo par → KMX009.
// Firma inválida (2+ params, Unit, suspend, receiver, no top-level) → KMX019.
```

## Converters calificados

```kotlin
object ShortDate : Converts<LocalDate, String> { override fun convert(v: LocalDate) = v.format(SHORT) }
// elección POR CAMPO, por KClass (reemplaza @Named/qualifiedBy de MapStruct, sin strings):
data class EventDto(@MapField(converter = ShortDate::class) val startDate: String)
// emite ShortDate.convert(startDate); gana sobre @Converter global y mapper (paso 0).
// A/B no encajan → KMX027; el object no implementa Converts → KMX029; A == B → warning KMX031.
```

## Dos sedes de configuración — dominio limpio

La config por campo (`@MapField`: `from`/`converter`/`onNull`) también vive **sobre el método** de un
`@Mapper`, nombrando el destino con `target = "..."`. Así el dominio y el DTO quedan sin una sola
anotación (hexagonal/DDD). Ambas sedes producen el mismo plan; duplicar campo+método → warning KMX032.

```kotlin
@Mapper interface CustomerMapper {
    @MapField(target = "name", from = "firstName")
    @MapField(target = "registeredAt", converter = IsoInstant::class)
    fun toDto(c: Customer): CustomerDto     // Customer y CustomerDto: CERO anotaciones kmapx
}
```

## Renombrado

```kotlin
data class Dto(@MapField(from = "firstname") val name: String) // name = firstname
// from inexistente → KMX011 con "did you mean"; rutas anidadas ("a.b") → KMX020.
// Fan-out válido; el renombre explícito pisa el match homónimo sin warning.
```

## Target defaults

```kotlin
data class Dto(val name: String, val nickname: String = "N/A")
@MapTo(Dto::class, onNull = OnNull.TARGET_DEFAULT)   // opt-in doble: default + política
data class Src(val name: String, val nickname: String?)
// genera: if (nickname != null) Dto(name, nickname) else Dto(name) — la omisión aplica
// el default (KSP no expone su valor). Hasta 2 campos (when de 4 ramas); más → KMX022.
// Fuente ausente + default → compila con warning KMX021 (nunca en silencio).
```

## KMP

Generación POR target ([guía completa](docs/guia-kmp.md)).
Targets de `annotations` v1: JVM (sirve a Android), JS(IR), WasmJS, LinuxX64, MingwX64,
macOS x64/arm64, iOS arm64/simArm64 — metadata Gradle verificada publicando a repo local.
El processor es JVM-only (corre en el build). Los tests de runtime viven en `commonTest` y
corren por target (JVM/JS/Linux en CI Linux; macOS/iOS en el job macOS con
`-Pkmapx.ci.apple=true`). `expect class` anotada → `KMX025`.

## Funcionalidades

Enums (`@MapEntry`), anidados con detección de ciclos, Map/Array/Result y `Iterable`/`Sequence`
como fuente, `@BiMapTo` validado, rutas anidadas con nulabilidad por segmento (en ambas sedes),
converters calificados (`@MapField(converter=)`) —incluidos **beans inyectados en modo contract**—,
dos sedes de configuración, PATCH set-null con `Patch<T>`, config global en el
bloque `kmapx { }`, plugin de Gradle `id("io.github.kuroxbyte.kmapx")`, integraciones Spring/Koin/serialization
([guía de migración](docs/guia-migracion-mapstruct.md), [patrones de mapeo](docs/guia-mapeo.md)) y
reporte de cobertura. Diagnósticos KMX001–KMX047, todos con factory + contrato.
Hay una **demo CRUD** (módulo `demo`, reemplaza MapStruct) y un
[playground](https://kuroxbyte.github.io/kmapx/) documentado.
