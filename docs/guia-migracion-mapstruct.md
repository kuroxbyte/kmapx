# Guía — Migrar de MapStruct a kmapx

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
| `@InheritInverseConfiguration` | `@InverseOf("toDto")` | Invertibilidad VALIDADA en compile-time (KMX028), no solo heredada |
| `@MapperConfig` + `@Mapper(config = ...)` | `@MapperConfig` + `@Mapper(config = ...)` | Mismo nombre a propósito; en kmapx además es un nivel de la cascada `onNull` |
| `@Mapping(target = "x", ignore = true)` | `@MapField(ignore = true)` o `@Mapper(ignore = ["x"])` | Exige default de constructor: la exclusión es OMISIÓN, nunca un valor inventado |
| `NullValuePropertyMappingStrategy` | `onNull` en cascada: `@MapField` > `@Mapper`/`@MapTo` > global | `T?→T` sin estrategia es ERROR (`KMX003`), nunca un default silencioso |
| `@MappingTarget` (update) | `@PatchMapper` | Inmutable vía `copy()`; null = no tocar. Para SETEAR null: campo `Patch<T>` (`Keep`/`Set`) |
| `@InheritInverseConfiguration` | `@BiMapTo` | La invertibilidad se VALIDA (`KMX028`): converter sin inverso, fan-out y asimetrías son errores |
| `@InheritConfiguration` | `@Mapper(inheritFrom = Base::class)` | Hereda la config por método; la propia gana por campo |
| `uses = [OtherMapper::class]` (composición) | Automático | Un `@MapTo`/`@Converter` del par se descubre solo; ciclos → `KMX008` |
| `uses = [...]` con **bean inyectado** (enrichment) | `@MapField(converter = Bean::class)` en modo B | El bean (`class ... : Converts<A,B>`) se inyecta al constructor del impl. Escape hatch; el default DDD es enriquecer en el servicio |
| `@ValueMapping` (enums) | `@MapEntry(target = "X")` | Emparejamiento por nombre + `when` exhaustivo sin `else` (`KMX026` si falta un par) |
| `unmappedTargetPolicy = ERROR` | Comportamiento por defecto | `KMX002` siempre; no hay modo silencioso |

## Las tres diferencias de fondo

1. **Errores en compile-time, todos, con fix**: en MapStruct la política estricta es opt-in;
   en kmapx es el producto. Cada `KMXnnn` trae ubicación exacta (línea del parámetro) y acción.
2. **Sin reflection y sin strings con código**: el generado es el Kotlin que escribirías a mano
   (hay un reporte de cobertura que muestra qué decidió el motor: `kmapx.report=json,html`).
3. **KMP real**: mismo `@MapTo` en `commonMain`, generación por target (ver `guia-kmp.md`).

## Converters calificados: `@MapField(converter=)` vs `@Named` de MapStruct

Cuando hay VARIOS converters para el mismo par de tipos (`LocalDate → String` corto vs largo),
MapStruct los distingue con `@Named("short")` + `qualifiedByName = "short"` — dos strings que
ninguna herramienta de refactor conecta con la función real. kmapx lo resuelve con **identidad de
tipo**: el converter calificable es un `object` que implementa `dev.kmapx.runtime.Converts<A, B>`,
y se elige por `KClass`:

```kotlin
object ShortDate : Converts<LocalDate, String> { override fun convert(v: LocalDate) = v.format(SHORT) }
object LongDate  : Converts<LocalDate, String> { override fun convert(v: LocalDate) = v.format(LONG) }

data class EventDto(
    @MapField(converter = ShortDate::class) val startDate: String,
    @MapField(converter = LongDate::class)  val endDate: String,
)
```

Ventajas sobre `@Named`: renombrar el object actualiza la referencia (es `KClass`, no string); un
`Converts<A,B>` que no encaje con el par del campo es error en compile-time (`KMX027`); referenciar
algo que no es `Converts` es `KMX029`. En modo dominio limpio va sobre el método:
`@MapField(target = "startDate", converter = ShortDate::class)`, dejando el DTO sin anotaciones.

## Pasos sugeridos

1. Reemplaza cada interfaz `@Mapper` de MapStruct por `@MapTo` en las clases source
   (o mantén la interfaz kmapx si inyectas).
2. Convierte cada `expression`/`qualifiedByName` en un `@Converter fun` top-level.
3. Compila: los `KMXnnn` son tu checklist de migración (salen TODOS en una pasada).
4. Activa `kmapx.report` y compara el reporte con tu mapeo mental.

> **Nota:** al igual que `@Mapping` de MapStruct, la config por campo de kmapx es UNA
> anotación multi-aspecto: `@MapField(target, from, converter, onNull, default)`.
