# Guía — Patrones de mapeo

Esta guía explica **cómo pensar los mapeos** con kmapx: cómo decide el motor el valor de cada
campo, qué estrategia de nulabilidad elegir según el caso, cuándo un mapeo debe ser
bidireccional y cuándo no, y los patrones de aplanado y actualización parcial. Para la
descripción exhaustiva de cada funcionalidad, la [guía de referencia](referencia.md); para
código completo que compila, los [ejemplos avanzados](ejemplos-avanzados.md).

## Cómo resuelve kmapx cada campo

Para cada parámetro del constructor del target, el motor busca un valor en este orden — el
primer paso que aplica, gana:

1. **Converter calificado** (`@MapField(converter = X::class)`): elección explícita por campo.
   Gana sobre todo lo demás.
2. **Converter global** (`@Converter fun`): si hay uno registrado para el par de tipos exacto.
3. **Wrap/unwrap de value class**: `ProductId → String` y viceversa, transparente.
4. **Identidad**: mismo tipo (genéricos incluidos), o `T → T?`.
5. **Colecciones**: elemento a elemento, aplicando esta misma cadena al elemento.
6. **Mapper declarado**: si el par anidado tiene su propio `@MapTo`/`@Mapper`, se llama a esa
   función generada **por referencia** (nunca se duplica el código inline).

Si ningún paso aplica, el resultado es un error con nombre: `KMX002` si no hay fuente,
`KMX004` si los tipos no convierten, `KMX007` si falta declarar el mapper anidado. El matching
de nombres es exacto (con `useSerialNames = true`, el alias `@SerialName` también cuenta);
un renombre se declara con `@MapField(from = "...")` y jamás se adivina.

```kotlin
// Todo junto — lo que el motor decide por ti, visible en el código generado:
fun Order.toOrderDto(): OrderDto = OrderDto(
    id = id.value,                            // paso 3: unwrap de value class
    status = status,                          // paso 4: identidad
    items = items.map { it.toItemDto() },     // pasos 5+6: colección delegando al par declarado
    total = formatMoney(total),               // paso 2: @Converter global
)
```

## Elegir estrategia de null: caso por caso

`T? → T` nunca compila en silencio. La pregunta correcta no es "¿cómo lo silencio?" sino
"¿qué debe pasar cuando la fuente venga null?" — y cada respuesta tiene su estrategia:

| Cuando la fuente es null, quiero… | Estrategia | Código generado |
|---|---|---|
| que sea imposible: arregla el modelo | (nada — el default) | error `KMX003` |
| un valor fijo de negocio | `onNull = LITERAL, default = "N/A"` | `nickname ?: "N/A"` |
| el cero/vacío del tipo | `onNull = TYPE_DEFAULT` | `tags ?: emptyList()` |
| el default que ya declara el constructor | `onNull = TARGET_DEFAULT` | omite el argumento |
| que explote con contexto | `onNull = THROW` | `?: throw IllegalArgumentException("email must not be null mapping Src -> Dto")` |
| asumir el riesgo conscientemente | `onNull = UNSAFE` | `legacy!!` — el único `!!` que kmapx emite |

Dos consejos de diseño:

- **Declara la política al nivel más alto que sea verdad.** Si "todo null lanza" es la regla
  del módulo, ponla en el bloque `kmapx { }` global o en tu `@MapperConfig` corporativo, y deja
  `@MapField` solo para las excepciones. La cascada (`campo > mapper > profile > global`)
  existe exactamente para eso, y `STRICT` en un campo corta la herencia cuando un campo
  concreto NO debe aceptar la política general.
- **`TARGET_DEFAULT` es el más declarativo**: el valor de respaldo vive en el constructor del
  target (donde cualquier lector lo espera), no en una anotación. Requiere que el parámetro
  tenga default, y kmapx genera la construcción condicional que lo respeta.

## Renombres y aplanado con rutas

El renombre plano es el caso simple: `@MapField(from = "firstname") val name: String`. Para
**aplanar** un objeto anidado en un DTO plano, `from` acepta rutas con puntos, y la nulabilidad
se analiza **por segmento**:

`@MapField(from = "address.country.code")` — la matriz de nulabilidad:

| Segmentos intermedios | Target | Resultado |
|---|---|---|
| todos no-nullables | `T` | `address.country.code` — acceso directo |
| alguno nullable | `T?` | `address.country?.code` — `?.` DESDE el primer segmento nullable |
| alguno nullable | `T` | `KMX003` **nombrando el segmento culpable** — salvo estrategia |
| alguno nullable | `T` + `onNull = THROW` | `address?.city ?: throw IllegalArgumentException(...)` |

Detalles que te ahorran sorpresas:

- El did-you-mean de un segmento inexistente se calcula sobre el TIPO de ese segmento
  (`'cty' does not exist on Address. Did you mean 'city'?`) — no sobre la clase raíz.
- Tras resolver la ruta aplican TODAS las reglas sobre el tipo final: converters, wrap/unwrap
  (`address.zip.value`), estrategias de null.
- En PATCH la ruta lee del patch: `note = patch.meta?.note ?: target.note`.
- Solo lectura de propiedades: sin llamadas a métodos, sin índices, y sin des-aplanar
  (construir un objeto anidado desde campos planos no existe en v1 — hazlo con un
  `@Converter` o una factory).

## Anidados: declarar, no anidar a mano

Cuando `Order` tiene un `Address` y `OrderDto` un `AddressDto`, no escribas nada en `Order`:
**declara el par anidado** (`@MapTo(AddressDto::class)` sobre `Address`, o un método más en tu
`@Mapper`) y kmapx lo resuelve por referencia. Ventajas de este diseño frente a la generación
implícita:

- El mapeo anidado existe como función propia — testeable y reutilizable por separado.
- Un par NO declarado es `KMX007` (con el fix exacto), nunca código sorpresa que no revisaste.
- Los ciclos de declaración (`Person → Address → Person`) se detectan y reportan con el camino
  completo (`KMX008`), en vez de recursión infinita.

## Converters: global, calificado o inyectado

- **Global** (`@Converter fun instantToIso(v: Instant): String`): la conversión canónica de un
  par de tipos en todo el módulo. Función top-level pura; dos globales del mismo par = `KMX009`
  (la ambigüedad es tuya de resolver, no del motor).
- **Calificado** (`object ShortDate : Converts<LocalDate, String>` + `@MapField(converter =
  ShortDate::class)`): cuando el MISMO par de tipos convierte distinto según el campo (fecha
  corta vs larga). Identidad por `KClass` — el refactor lo sigue, y un converter que no encaja
  con el par del campo es `KMX027`.
- **Inyectado** (una `class` que implementa `Converts` con dependencias, solo modo contract):
  el escape hatch para conversiones que necesitan colaboradores (un formateador con locale, un
  catálogo). Se inyecta al constructor del impl. Si la "conversión" necesita un repositorio,
  probablemente no es un mapeo — enriquece en la capa de servicio y deja el mapper puro.

## Patch: null significa "no tocar"… hasta que necesitas setear null

Para actualización parcial, la forma del método lo declara (retorno == tipo del primer
parámetro): `fun apply(target: Product, patch: ProductPatch): Product` genera un
`target.copy(...)` inmutable. En el patch:

- Un campo `T?` con null significa **no tocar** — el semántico JSON-Merge-Patch clásico.
- Cuando necesitas distinguir "no tocar" de "poner null" (limpiar una nota, quitar un
  descuento), el campo del patch se declara `Patch<T>`: tri-estado `Keep` / `Set(valor)` /
  `Set(null)`, y el `when` generado es exhaustivo — no hay cuarta opción posible.

Regla práctica: empieza con `T?`; introduce `Patch<T>` campo a campo solo donde el negocio
realmente borra valores. Son componibles en el mismo patch.

## Cuándo NO usar bidireccional

`@BiMapTo` (embedded) y `@InverseOf` (contract) son para pares SIMÉTRICOS: la vuelta debe poder
reconstruir el original. Usa dos `@MapTo` unidireccionales cuando la asimetría es legítima —
y `KMX028` es precisamente la señal de que estás en ese caso:

- **Campos de un solo lado** (auditoría, flags internos): el round-trip no puede reconstruirlos.
- **Converter sin inverso razonable** (`Instant → "hace 3 días"`): no fuerces un inverso falso.
- **Fan-out** (un campo fuente alimenta dos del target): la vuelta no puede dividir un valor.
- **Defaults del target como fallback**: la omisión pierde información a propósito.

Regla práctica: si para silenciar un KMX028 tendrías que INVENTAR datos, la relación es
unidireccional — declárala como tal y el modelo queda honesto. kmapx valida la estructura
(cada campo tiene camino de vuelta); que tus converters sean inversos exactos
(`isoToInstant(instantToIso(x)) == x`) es responsabilidad tuya, y un test de round-trip de una
línea la cubre.

## Fechas, JSON y tipos de plataforma

Las tres preguntas que todo usuario hace en su primera hora — y el criterio común detrás de
las tres respuestas: donde otra librería pondría un default silencioso, kmapx te frena con un
error y te pide **una decisión tuya, una vez, en una función con nombre**.

### Fechas

kmapx se niega a propósito a convertir fechas implícitamente: `Instant → String` no tiene un
formato "obvio" (¿ISO? ¿epoch? ¿qué zona horaria?). La escalera:

```kotlin
// 1. El formato canónico del módulo — UNA vez, para todo par Instant→String:
@Converter fun instantToIso(v: Instant): String = v.toString()

// 2. Varios formatos según el campo — calificados por KClass:
object ShortDate : Converts<LocalDate, String> { override fun convert(v: LocalDate) = v.format(SHORT) }
object LongDate  : Converts<LocalDate, String> { override fun convert(v: LocalDate) = v.format(LONG) }
data class EventDto(
    @MapField(converter = ShortDate::class) val startDate: String,
    @MapField(converter = LongDate::class)  val endDate: String,
)

// 3. stdConverters = true regala String↔Instant (ISO) y Long↔Instant (epoch millis) —
//    opt-in porque ASUMEN un formato; tu @Converter siempre les gana. Solo JVM (ver abajo).
```

La trampa clásica que este diseño te obliga a decidir: la **zona horaria vive en el
converter**, a la vista y testeable — no enterrada en un `toString()` implícito que en
producción corre con otro locale.

### JSON

kmapx no es una librería de serialización, y no intenta serlo. El diseño es por capas, y cada
una hace lo suyo:

```
dominio ──kmapx──▶ DTO @Serializable ──kotlinx.serialization──▶ JSON
```

Los puntos de contacto: `useSerialNames = true` hace que el matching respete los alias
`@SerialName` del DTO (una sola fuente de nombres para mapeo y serialización). Y si de verdad
necesitas `String(JSON) → Objeto` como conversión de un campo, un
`@Converter fun metaFromJson(raw: String): Meta = Json.decodeFromString(raw)` es una función
pura legítima. Lo que NO conviene: serializar objetos enteros dentro de converters — eso es
esconder la capa de serialización dentro del mapper.

### Objetos propios

El caso central de kmapx, en tres niveles según cuánto control necesitas: el **par declarado**
(`@MapTo` en el anidado o método hermano del `@Mapper` — se resuelve por referencia en campos,
colecciones y rutas), la **construcción especial** (`@MapConstructor`/`@MapFactory` para
factories validadas), y **`Converts<A,B>`** cuando la conversión no es estructural (un `Money`
con aritmética, un tipo de terceros que no puedes anotar).

### Packs de converters (extensiones)

Los pares de conversión más comunes vienen empaquetados — no hace falta escribirlos. Dos packs
oficiales, ambos por SPI (se añaden a `implementation(...)` **y** `ksp(...)`):

- **`kmapx-ext-jvm`**: `java.time` (`Instant`, `LocalDate`, `LocalDateTime`, `Duration`),
  `java.util.UUID`, `java.math` (`BigDecimal`, `BigInteger`) y `java.net.URI`, en ambas
  direcciones contra `String` (y `Instant↔Long` epoch millis).
- **`kmapx-ext-serialization`**: `JsonElement ↔ String` (kotlinx.serialization). El JSON
  POR-TIPO (`Meta ↔ String`) sigue siendo un `@Converter` tuyo — específico del tipo, por diseño.
- **`kmapx-ext-kotlinx-datetime`**: `Instant`/`LocalDate`/`LocalDateTime`/`LocalTime` de
  kotlinx-datetime ↔ `String` (ISO) y `Instant↔Long` (epoch millis) — el datetime que
  `stdConverters` (solo JVM) no cubre.

Ejemplo con `kmapx-ext-jvm`:

```kotlin
// build.gradle.kts — el pack va en DOS configuraciones:
dependencies {
    implementation("io.github.kuroxbyte:kmapx-ext-jvm:<v>")  // las funciones que el codigo llama
    ksp("io.github.kuroxbyte:kmapx-ext-jvm:<v>")             // el descubrimiento (SPI/ServiceLoader)
}
```

```kotlin
// Con el pack en el classpath, esto compila SIN un solo @Converter:
data class AuditDto(val id: String, val at: String)

@MapTo(AuditDto::class)
data class Audit(val id: java.util.UUID, val at: java.time.Instant)
// genera: AuditDto(id = uuidToString(id), at = instantToIso(at))
```

Dos garantías del diseño: un `@Converter` **tuyo** para cualquiera de esos pares **gana** sobre
el del pack (misma precedencia que siempre — el calificado y el global del usuario mandan), y un
pack **jamás** introduce ambigüedad KMX009 ni suprime un error; solo añade caminos válidos. Con
el pack ya no necesitas `stdConverters = true` para esos pares.

Los packs son consumidores del **SPI** de kmapx (`kmapx-spi`, experimental hasta 1.0): puedes
escribir el tuyo — un `KmapxExtension` que implemente `contributeConverters()`, registrado en
`META-INF/services` — para paquetes de conversión propios (kotlinx-datetime, tipos de tu
dominio) sin tocar la librería.

### Particularidades por plataforma (KMP)

1. **`stdConverters` es JVM-only por construcción**: emite `java.util.UUID`,
   `java.time.Instant`, `java.math.BigDecimal` — tipos que no existen en `commonMain`. Para
   fechas multiplataforma: [kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime) +
   tus `@Converter` en común.
2. **Los converters deben vivir donde el target los vea**: el processor corre una vez por
   target — un `@Converter` en `jvmMain` solo existe para JVM; el mismo mapeo en JS dará
   KMX004. Regla: converters en `commonMain` con tipos comunes, salvo que el mapeo sea de un
   solo target.
3. **El código generado es Kotlin común puro** (sin `java.*`, garantizado por test): las
   semánticas son idénticas en JVM, JS, Wasm y Native.
4. **`expect class` no se mapea** (KMX025): mapea clases comunes concretas.
5. En Kotlin/JS el `Long` es boxed — si tu DTO alimenta JSON en JS, plantéate `String` para
   ids grandes (decisión tuya, no del mapper).

## Recetario rápido

| Necesito… | Patrón |
|---|---|
| renombrar un campo | `@MapField(from = "firstname")` |
| aplanar `a.b.c` | `@MapField(from = "a.b.c")` — `?.` automático por segmento |
| formatear una fecha de DOS formas | dos `object : Converts<...>` + `converter =` por campo |
| excluir `createdAt` de todos los mapeos | `@MapperConfig(ignore = ["createdAt"])` + `config =` |
| DTO de creación sin `id` (lo pone el servicio) | parámetro suplementario en el método del `@Mapper` |
| update parcial estilo PATCH HTTP | método con forma patch + `Patch<T>` donde se borre |
| ida y vuelta Entity ↔ DTO | `@BiMapTo` / `@InverseOf` — si sale KMX028, revisa la simetría |
| valor por defecto cuando la fuente es null | `TARGET_DEFAULT` (default en el constructor) o `LITERAL` |
