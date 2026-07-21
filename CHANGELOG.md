# Changelog

Todos los cambios notables de kmapx. El formato sigue
[Keep a Changelog](https://keepachangelog.com/es/1.1.0/) y el proyecto usa
[versionado semántico](https://semver.org/lang/es/).

## [0.2.0] — 2026-07

### Añadido

- **Mapeo anidado cross-module** (embedded `@MapTo`): un tipo anidado cuyo mapper vive en otra
  biblioteca/módulo se resuelve sin redeclararlo. Cada extensión generada lleva un marcador
  `@GeneratedMapping(source, target)` (retención BINARY) que el processor del consumidor descubre
  en el classpath. El modo contract cross-module queda para una versión futura.
- **SPI de extensión** (`kmapx-spi`, experimental hasta 1.0): interfaz `KmapxExtension`
  descubierta por `ServiceLoader` para contribuir converters sin modificar la librería.
  Marcada `@KmapxExperimentalSpi` (opt-in obligatorio).
- **Packs de converters** (consumidores del SPI; se añaden a `implementation(...)` y `ksp(...)`):
  - `kmapx-ext-jvm`: `java.time` (`Instant`, `LocalDate`, `LocalDateTime`, `Duration`),
    `java.util.UUID`, `java.math` (`BigDecimal`, `BigInteger`) y `java.net.URI` ↔ `String`.
  - `kmapx-ext-serialization`: `JsonElement ↔ String` (kotlinx.serialization).
  - `kmapx-ext-kotlinx-datetime`: `Instant`/`LocalDate`/`LocalDateTime`/`LocalTime` ↔ `String`.
  - Un `@Converter` del usuario siempre gana sobre el del pack; un pack nunca introduce
    ambigüedad ni suprime errores.
- **Converter calificado en PATCH**: `@MapField(converter=)` sobre el campo target ahora se
  aplica al `Set(v)` del tri-estado y al fallback (antes se ignoraba en silencio).
- **Plugin de IntelliJ** — nuevas capacidades en el editor, todas con el motor real del compilador:
  - Diagnósticos que faltaban: KMX010 (sealed), KMX013 (colisión de nombre), KMX028
    (invertibilidad de `@BiMapTo`/`@InverseOf`), KMX030 (framework ausente), KMX044 (config
    inválida).
  - Multi-fuente en contract, converters calificados, rutas anidadas, `useSerialNames` y
    subtipos sealed cross-file — ahora resueltos (antes: falsos positivos o abstención).
  - Intención **"materializar el mapeo como código fuente"** (el "delombok" de kmapx): escribe
    el mapeo generado como fuente, quita la anotación, limpia imports y el generado stale.
  - Referencia de `@InverseOf` (ctrl+click, rename-safe, completado) y completado sobre string
    vacío en todos los sitios (`@MapEntry`, `from`, `ignore`).
  - `@SuppressKmapx` respetado en el editor.
  - Iconos propios (gutter y Marketplace).

### Corregido

- Los diagnósticos de converter calificado (KMX027/029/031/034) nombraban la anotación
  histórica `@UseConverter`; ahora dicen `@MapField(converter=)`. Igual "modo A/B" → "embedded/
  contract" en los mensajes.
- Falsos positivos del plugin de IntelliJ: value classes, `Patch<T>` del runtime y tipos del
  runtime degradaban a KMX004 en el editor.
- El KMX004 de un cruce de contenedor (`List → Set`, `Map → List`) ahora explica el porqué
  (dedup, falta de clave) en vez del mensaje genérico.

### Documentación

- Guía de patrones ampliada: "Fechas, JSON y tipos de plataforma", "Packs de converters",
  "Anidados de otro módulo" y particularidades KMP.
- Nota de que `stdConverters` es solo JVM.
- `capacidades-y-limitaciones` actualizada; dos limitaciones obsoletas corregidas (el widening
  numérico ya es automático; los overrides `@MapEntry` de `@BiMapTo` ya se invierten).

## [0.1.0] — 2026-07

Primera versión publicada en Maven Central (`io.github.kuroxbyte:kmapx-*`).

### Añadido

- Mapper Kotlin-first en compile-time sobre KSP2: todo mapeo inseguro es un error de
  compilación con código estable, ubicación exacta y "did you mean".
- Dos modos: **embedded** (`@MapTo`/`@BiMapTo` sobre el modelo) y **contract** (interfaz
  `@Mapper`, dominio limpio).
- Config por campo unificada (`@MapField`: `from`, `converter`, `onNull`, `default`, `ignore`).
- Null-safety con cascada `onNull` (campo > mapper > profile > global).
- Converters globales (`@Converter`), calificados (`Converts<A,B>`) e inyectados (modo contract).
- Colecciones, `Map`/`Array`/`Result`, value classes, sealed y enums paralelos, PATCH por forma
  con `Patch<T>`, bidireccional validado, rutas anidadas, profiles `@MapperConfig` con herencia.
- Integraciones Spring/Koin/serialization, reporte de cobertura, plugin de Gradle.
- Kotlin Multiplatform: generación por target (JVM/Android, JS, WasmJS, Native).
- Diagnósticos KMX001–KMX047, todos con factory y contrato verificado.
