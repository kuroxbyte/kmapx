# Guía — Migrar de MapStruct a kmapx

Esta guía está pensada para leerse con tu código MapStruct al lado: primero la tabla de
equivalencias anotación por anotación, después las tres diferencias de fondo que conviene
entender antes de tocar nada, y al final un plan de migración incremental. La buena noticia:
como kmapx reporta **todos** los problemas en una sola pasada de compilación, la migración se
autodirige — compilas, y la lista de `KMXnnn` es tu checklist.

## Equivalencias de anotaciones

| MapStruct | kmapx | Nota |
|---|---|---|
| `@Mapper` (interfaz) | `@Mapper` (interfaz) — o mejor: `@MapTo` directo | En kmapx el modo idiomático es la extension (`person.toPersonDto()`); la interfaz existe para DI y delega en las extensions |
| `@Mapper(componentModel = "spring")` | `@Mapper(componentModel = ComponentModel.SPRING)` | Genera `@Component class XImpl`; sin spring-context en el classpath → error claro `KMX030` |
| — (Koin no soportado) | `@Mapper(componentModel = ComponentModel.KOIN)` | Genera `KmapxKoinModule` por paquete con binding por interfaz; inclúyelo en `startKoin` |
| `@Mapping(target = "x", source = "y")` | `@MapField(from = "y")` sobre el parámetro del TARGET | Validado en compile-time (`KMX011` con did-you-mean); rutas `"a.b.c"` con nulabilidad por segmento |
| `@Mapping(expression = "java(...)")` | **No existe, por diseño** | Strings con código jamás; usa un `@Converter fun` (refactor-safe, testeable) |
| `@Named("short")` + `qualifiedByName` | `@MapField(converter = ShortDate::class)` | Elección de converter POR CAMPO **por KClass**, no por string; renombrar el object actualiza la referencia |
| `@Mapping(defaultValue = "x")` | `@MapField(onNull = LITERAL, default = "x")` | Parseo TIPADO en compile-time (`KMX017` si no parsea) |
| `@MapperConfig` + `@Mapper(config = ...)` | `@MapperConfig` + `@Mapper(config = ...)` | Mismo nombre a propósito; en kmapx además es un nivel de la cascada `onNull` |
| `@Mapping(target = "x", ignore = true)` | `@MapField(ignore = true)` o `@Mapper(ignore = ["x"])` | Exige default de constructor: la exclusión es OMISIÓN, nunca un valor inventado |
| `NullValuePropertyMappingStrategy` | `onNull` en cascada: `@MapField` > `@Mapper`/`@MapTo` > global | `T?→T` sin estrategia es ERROR (`KMX003`), nunca un default silencioso |
| `@MappingTarget` (update) | método con forma patch | Inmutable vía `copy()`; null = no tocar. Para SETEAR null: campo `Patch<T>` (`Keep`/`Set`) |
| `@InheritInverseConfiguration` | `@BiMapTo` (embedded) o `@InverseOf` (contract) | La invertibilidad se VALIDA en compile-time (`KMX028`): converter sin inverso, fan-out y asimetrías son errores, no herencias silenciosas |
| `@InheritConfiguration` | `@Mapper(inheritFrom = Base::class)` | Hereda la config por método; la propia gana por campo |
| `uses = [OtherMapper::class]` (composición) | Automático | Un `@MapTo`/`@Converter` del par se descubre solo; ciclos → `KMX008` |
| `uses = [...]` con **bean inyectado** (enrichment) | `@MapField(converter = Bean::class)` en modo contract | El bean (`class ... : Converts<A,B>`) se inyecta al constructor del impl. Escape hatch; el default DDD es enriquecer en el servicio |
| `@ValueMapping` (enums) | `@MapEntry(target = "X")` | Emparejamiento por nombre + `when` exhaustivo sin `else` (`KMX026` si falta un par) |
| `unmappedTargetPolicy = ERROR` | Comportamiento por defecto | `KMX002` siempre; no hay modo silencioso |

## Las tres diferencias de fondo

1. **Errores en compile-time, todos, con fix.** En MapStruct la política estricta es opt-in
   (`unmappedTargetPolicy`, `typeConversionPolicy`…) y lo no configurado degrada a warning o a
   silencio; en kmapx lo estricto ES el producto. Cada `KMXnnn` trae ubicación exacta (la línea
   del parámetro culpable, no la de la clase) y una acción concreta. En la práctica esto
   invierte el flujo de trabajo: en vez de revisar el código generado buscando sorpresas,
   compilas y el compilador te dicta la lista de decisiones pendientes.
2. **Sin reflection y sin strings con código.** `expression = "java(...)"` y
   `qualifiedByName = "short"` son strings que ningún refactor sigue y que fallan en runtime.
   Sus equivalentes kmapx son una función (`@Converter fun`) y una referencia de clase
   (`converter = ShortDate::class`): compilan, se testean solas y el IDE las renombra. El
   código generado es el Kotlin que escribirías a mano — y si quieres auditar qué decidió el
   motor campo a campo, `kmapx.report=json,html` genera un reporte de cobertura con la
   trazabilidad de cada valor.
3. **KMP real.** MapStruct es JVM (annotation processing clásico); kmapx genera por target
   desde `commonMain` con el mismo `@MapTo` — ver la [guía KMP](guia-kmp.md).

## Converters calificados: `@MapField(converter=)` vs `@Named` de MapStruct

Cuando hay VARIOS converters para el mismo par de tipos (`LocalDate → String` corto vs largo),
MapStruct los distingue con `@Named("short")` + `qualifiedByName = "short"` — dos strings que
ninguna herramienta de refactor conecta con la función real. kmapx lo resuelve con **identidad
de tipo**: el converter calificable es un `object` que implementa
`dev.kmapx.runtime.Converts<A, B>`, y se elige por `KClass`:

```kotlin
object ShortDate : Converts<LocalDate, String> { override fun convert(v: LocalDate) = v.format(SHORT) }
object LongDate  : Converts<LocalDate, String> { override fun convert(v: LocalDate) = v.format(LONG) }

data class EventDto(
    @MapField(converter = ShortDate::class) val startDate: String,
    @MapField(converter = LongDate::class)  val endDate: String,
)
```

Ventajas sobre `@Named`: renombrar el object actualiza la referencia (es `KClass`, no string);
un `Converts<A,B>` que no encaje con el par del campo es error en compile-time (`KMX027`);
referenciar algo que no es `Converts` es `KMX029`. En modo dominio limpio va sobre el método:
`@MapField(target = "startDate", converter = ShortDate::class)`, dejando el DTO sin anotaciones.

## Plan de migración incremental

No hace falta migrar todo de golpe: MapStruct y kmapx pueden convivir en el mismo módulo
mientras dure la transición (procesan anotaciones distintas).

1. **Elige un agregado pequeño** (un par Entity ↔ DTO con 1-2 mappers) como piloto.
2. **Decide el modo.** Si venías de MapStruct probablemente quieras `contract` (interfaz
   `@Mapper`, dominio limpio) — la traducción es casi mecánica. Considera `embedded` para los
   DTOs propios donde la anotación en el modelo no molesta: es menos código.
3. **Traduce las anotaciones** con la tabla de arriba. Cada `expression`/`qualifiedByName` se
   convierte en un `@Converter fun` o un `object : Converts` — este es el único paso que
   requiere escribir código nuevo, y suele ser código que ya existía dentro del string.
4. **Compila y trabaja la lista de `KMXnnn`.** Salen todos en una pasada. Los más frecuentes
   al migrar: `KMX003` (MapStruct silenciaba un `T?→T` que ahora exige estrategia — decide qué
   quieres que pase, no lo silencies por reflejo) y `KMX002` (campo sin fuente que MapStruct
   dejaba en default silencioso — decláralo `ignore` o mapéalo).
5. **Activa el reporte** (`kmapx.report=json,html`) y compara con tu mapeo mental: el reporte
   lista campo a campo de dónde salió cada valor (converter del usuario, mapper declarado,
   conversión implícita).
6. **Borra la dependencia de MapStruct** del módulo cuando la lista quede vacía.

> **Nota:** al igual que `@Mapping` de MapStruct, la config por campo de kmapx es UNA
> anotación multi-aspecto: `@MapField(target, from, converter, onNull, default, ignore)` —
> los aspectos se combinan en la misma anotación, no en anotaciones separadas.
