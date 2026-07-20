# kmapx

**Mapper Kotlin-first en compile-time: todo mapeo inseguro es un error de compilación.**

kmapx genera el código de mapeo entre tus clases (dominio ↔ DTO, entidad ↔ response, etc.)
durante la compilación, usando [KSP](https://kotlinlang.org/docs/ksp-overview.html). No hay
reflection, no hay strings con código embebido y no hay overhead en runtime: lo que se genera
son extensions e implementaciones en Kotlin real, que puedes abrir, leer, navegar y depurar en
el IDE como cualquier otro archivo tuyo. Cuando un mapeo no cierra — un campo sin fuente, un
`String?` intentando llenar un `String`, tipos incompatibles — el compilador te lo dice con un
código estable (`KMXnnn`), la línea exacta del campo culpable y un "did you mean" cuando hay
un candidato parecido.

```kotlin
@MapTo(PersonDto::class)
data class Person(val name: String, val age: Int)
// → fun Person.toPersonDto(): PersonDto, generado y verificado en compile-time
```

## Requisitos

| Requisito | Versión |
|---|---|
| Kotlin | 2.1+ |
| KSP | 2.x (KSP2: `ksp.useKSP2=true` en `gradle.properties`) |
| JDK para el build | 17+ |
| Plataformas | JVM/Android, JS(IR), WasmJS, Linux x64, Windows x64, macOS, iOS |

## Instalación rápida (JVM)

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.1.21"
    id("com.google.devtools.ksp") version "2.1.21-2.0.1"
}

dependencies {
    implementation("io.github.kuroxbyte:kmapx-annotations:0.1.0")
    // Solo si usas converters calificados (Converts<A, B>) o Patch<T>:
    implementation("io.github.kuroxbyte:kmapx-runtime:0.1.0")
    ksp("io.github.kuroxbyte:kmapx-frontend-ksp:0.1.0")
}
```

```properties
# gradle.properties
ksp.useKSP2=true
```

Con eso, cada clase anotada genera su mapper al compilar. El código queda en
`build/generated/ksp/main/kotlin/`, en el mismo paquete que la clase anotada. Para
Kotlin Multiplatform, KSP se declara por target — está explicado paso a paso en la
[guía multiplataforma](guia-kmp.md).

## Dos modos, un motor

kmapx acepta dos estilos de declaración. Ambos producen exactamente el mismo plan de mapeo y
el mismo código generado; elige por arquitectura, no por funcionalidad.

**Modo embedded** (estilo JPA/Jackson): la configuración vive anotada en el modelo. Es el
camino más corto — una anotación en la clase y a compilar:

```kotlin
@MapTo(PersonDto::class)
data class Person(val name: String, val age: Int)

val dto = person.toPersonDto()   // extension generada
```

**Modo contract** (estilo MapStruct/DDD): el mapeo se declara como una interfaz en tu capa de
infraestructura y el dominio queda **sin una sola anotación** — ideal para arquitectura
hexagonal, con profiles corporativos reutilizables (`@MapperConfig`), herencia de configuración
(`inheritFrom`) e inversos declarados (`@InverseOf`):

```kotlin
@Mapper
interface PersonMapper {
    fun toDto(p: Person): PersonDto
}
// genera: object PersonMapperImpl : PersonMapper
```

## Por dónde empezar

| Quiero… | Leer |
|---|---|
| Aprenderlo de punta a punta | [Guía de referencia](referencia.md) |
| Entender los patrones de mapeo (renombres, nulls, rutas, bidireccional) | [Guía de patrones](guia-mapeo.md) |
| Ver funcionalidades combinadas en código que COMPILA | [Ejemplos avanzados](ejemplos-avanzados.md) |
| Entender un error `KMXnnn` | [Catálogo de diagnósticos](referencia/diagnosticos.md) — generado desde el código |
| Migrar desde MapStruct | [Guía de migración](guia-migracion-mapstruct.md) |
| Usarlo en KMP | [Guía multiplataforma](guia-kmp.md) |
| Ver un ejemplo completo de principio a fin | [Ejemplo end-to-end](kmapx-ejemplo-end-to-end.md) |
| Saber qué NO hace (y por qué) | [Capacidades y limitaciones](capacidades-y-limitaciones.md) |

## Cómo se ve un error

Todos los diagnósticos salen **en una sola pasada de compilación** (nunca "arreglas uno y
descubres el siguiente"), con formato canónico y acción sugerida:

```
[KMX002] com.example.PersonDto.age no source property found for constructor parameter 'age'.
Did you mean 'ageYears'? Fix: add a matching source property, or use @MapField(from = "...") to rename.
```

## Lo que lo distingue

- **Null-safety con cascada**: `T? → T` es error salvo salida declarada — por campo, por
  mapper, por profile o global, con precedencia explícita (el nivel más específico gana).
  Nunca hay un default silencioso.
- **Sin strings con código**: donde MapStruct usa `expression = "java(...)"` o
  `@Named("short")`, kmapx usa funciones y `KClass` reales — el refactor del IDE los sigue,
  y un converter que no encaja es un error de compilación, no una sorpresa en runtime.
- **Invertibilidad validada**: `@BiMapTo` y `@InverseOf` verifican en compile-time que la
  vuelta reconstruye; toda asimetría es un error con nombre (KMX028).
- **KMP real**: las clases anotadas viven en `commonMain` y el processor genera código
  idéntico por target — la misma suite de tests corre en JVM, Node y nativo.
- **Documentación que no puede mentir**: los ejemplos de esta doc los compila el build, y el
  catálogo de diagnósticos se genera desde el código fuente.
