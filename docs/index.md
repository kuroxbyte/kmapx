# kmapx

**Mapper Kotlin-first en compile-time: todo mapeo inseguro es un error de compilación.**

Sin reflection, sin strings con código, sin overhead en runtime — kmapx genera extensions e
implementaciones en Kotlin real, navegables y depurables en el IDE. Cuando un mapeo no cierra,
el compilador te lo dice con un código estable, la línea exacta y un "did you mean".

```kotlin
@MapTo(PersonDto::class)
data class Person(val name: String, val age: Int)
// → fun Person.toPersonDto(): PersonDto, generado y verificado en compile-time
```

## Dos modos, un motor

- **embedded** (estilo JPA/Jackson): la config vive anotada en el modelo. Ergonomía primero.
- **contract** (estilo MapStruct/DDD): el mapeo es una interfaz — el dominio queda sin una sola
  anotación. Hexagonal-friendly, con profiles corporativos, herencia e inversos declarados.

## Por dónde empezar

| Quiero… | Leer |
|---|---|
| Aprenderlo de punta a punta | [Guía de referencia](referencia.md) |
| Ver funcionalidades combinadas en código que COMPILA | [Ejemplos avanzados](ejemplos-avanzados.md) |
| Entender un error `KMXnnn` | [Catálogo de diagnósticos](referencia/diagnosticos.md) — generado desde el código |
| Migrar desde MapStruct | [Guía de migración](guia-migracion-mapstruct.md) |
| Usarlo en KMP | [Guía multiplataforma](guia-kmp.md) |
| Saber qué NO hace (y por qué) | [Capacidades y limitaciones](capacidades-y-limitaciones.md) |

## Lo que lo distingue

- **Null-safety con cascada**: `T? → T` es error salvo salida declarada — por campo, por
  mapper, por profile o global, con precedencia explícita.
- **Invertibilidad validada**: `@BiMapTo` y `@InverseOf` verifican en compile-time que la
  vuelta reconstruye; toda asimetría es un error con nombre (KMX028).
- **Documentación que no puede mentir**: los ejemplos de esta doc los compila el build, y el
  catálogo de diagnósticos se genera desde el código fuente.
