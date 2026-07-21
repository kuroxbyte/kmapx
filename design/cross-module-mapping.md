# Diseño — Mapeo anidado cross-module

> Documento de diseño interno (no publicado en el sitio).
> Estado: **Fase 1 (embedded `@MapTo`) IMPLEMENTADA** — la técnica quedó validada con un test de
> dos módulos (`CrossModuleTest`). Fase 2 (contract `@Mapper`) pendiente.

## Problema

Hoy, un mapper anidado declarado en **otro módulo** no se resuelve automáticamente:

```
:domain   →  data class Address(...)          @MapTo(AddressDto::class)  → genera Address.toAddressDto()
:api      →  data class OrderDto(val address: AddressDto, ...)
             @MapTo(OrderDto::class) data class Order(val address: Address, ...)
```

Al compilar `:api`, el motor busca el par `(Address, AddressDto)` en su `declaredMappings` —
que solo contiene lo declarado en la ronda ACTUAL de `:api` — y no lo encuentra: **KMX007**.
El usuario debe redeclarar el `@MapTo` en `:api` o escribir un `@Converter`. Es la fricción #1
en proyectos multi-módulo.

Nota importante: la extensión `Address.toAddressDto()` generada por `:domain` **sí está** en el
classpath de `:api` como función pública compilada. El código generado de `:api` PODRÍA llamarla
sin problema — lo único que falta es que el **processor** de `:api` SEPA que existe, para emitir
la llamada en vez de KMX007.

## Por qué es difícil en KSP

KSP procesa por ronda de compilación: `getSymbolsWithAnnotation()` solo devuelve símbolos de las
FUENTES actuales (y generados), no de dependencias compiladas. Así que el `@MapTo` de `:domain`
es invisible para el processor de `:api` por esa vía.

**El grounding que lo desbloquea**: el `Resolver` de KSP **sí ve las clases de dependencias**.
- `resolver.getClassDeclarationByName(fqn)` resuelve clases compiladas del classpath (ya lo
  usamos para el probe de frameworks, `MapperHandler`).
- `resolver.getDeclarationsFromPackage(pkg)` devuelve las declaraciones de un paquete
  **incluyendo las de dependencias** (funciones top-level entre ellas).

Es decir: el processor de `:api` puede DESCUBRIR la extensión generada por `:domain` si sabe
dónde mirar — sin leer recursos del classpath (que KSP no expone) ni acoplar al plugin de Gradle.

## Aproximación recomendada: marcador `@GeneratedMapping` + scan por paquete

1. **Marcar** cada extensión generada con una anotación de retención `BINARY` (sobrevive al jar):

   ```kotlin
   @GeneratedMapping(source = "com.acme.Address", target = "com.acme.AddressDto")
   public fun Address.toAddressDto(): AddressDto = ...
   ```

   Va en un módulo liviano (`annotations` o uno nuevo `runtime`-adyacente), `@Retention(BINARY)`,
   `@Target(FUNCTION)`.

2. **Descubrir** en el consumidor: cuando el motor no encuentra el par `(S, T)` en el
   `declaredMappings` LOCAL, antes de emitir KMX007 el frontend consulta
   `resolver.getDeclarationsFromPackage(paqueteDe S)` y busca una función con
   `@GeneratedMapping(source = S.qn, target = T.qn)`. Si existe, registra el par con el FQN de esa
   función → el motor emite `ViaMapper(GeneratedExtension(fqn))`, idéntico a un par local.

Por qué esta forma:
- **Cero acople a Gradle** — usa solo el `Resolver`, que ya ve el classpath.
- **Correcto con nombres custom** (`@MapTo(name = ...)`): el par viaja en la anotación, no en una
  convención de nombre frágil.
- **Costo acotado**: se escanea solo el paquete del tipo fuente anidado, y solo cuando el par no
  resolvió localmente (camino de fallo, no el feliz).

## Alternativas consideradas (y por qué no)

- **Índice como recurso** (`META-INF/kmapx/mappings.index`): emitir un recurso y leerlo en el
  consumidor. Problema: KSP no expone lectura de recursos del classpath de compilación desde el
  processor (el classloader del processor ≠ classpath de compilación). Requeriría cablear los
  índices al classpath del processor vía el plugin de Gradle → acople y fragilidad. Rechazado.
- **Convención pura** (`buscar una función llamada to<Target>`): frágil ante `@MapTo(name=...)` y
  colisiones. Rechazado a favor del marcador explícito.

## Alcance por fases

- **Fase 1 — embedded `@MapTo`**: las extensiones top-level. Es el 80% del caso y lo más limpio
  (funciones con el marcador, descubiertas por paquete).
- **Fase 2 — contract `@Mapper`**: los `XImpl` son objects/classes, no funciones top-level; la
  resolución cross-module de un `@Mapper` de otro módulo es una historia aparte (¿inyectar el
  impl? ¿llamar su método?). Se pospone.
- **Fuera de alcance v1**: converters `@Converter` cross-module (mismo problema, misma técnica si
  se decide; los converters ya se pueden compartir vía un pack/SPI, que es la vía recomendada).

## Incrementalidad

El archivo generado del consumidor pasa a depender de la extensión de la dependencia. A nivel
Gradle esto ya lo cubre el up-to-date por módulo (si `:domain` cambia, `:api` recompila). Dentro
de KSP, el `Dependencies` del archivo generado del consumidor debe marcarse `aggregating = true`
para el caso cross-module (ya soportado en `GeneratedOutput`), porque su validez depende de
símbolos fuera de su archivo fuente. A validar con `incremental-tests` (un segundo módulo).

## Mientras tanto (workarounds vigentes, documentar)

El usuario NO está bloqueado hoy:
1. Redeclarar el `@MapTo(AddressDto::class)` sobre `Address` en el módulo consumidor (si el tipo
   es accesible), o
2. un `@Converter fun (Address) -> AddressDto = { it.toAddressDto() }` en el consumidor que delega
   en la extensión de la dependencia (que sí está en el classpath).

## Plan de implementación (cuando se retome)

1. `@GeneratedMapping(source, target)` en `annotations` (`@Retention(BINARY)`).
2. El backend emite el marcador sobre cada extensión `@MapTo`/`@BiMapTo`.
3. `DeclarationIndex`/`PathReferences`: al no resolver un par localmente, `crossModuleLookup(S, T)`
   vía `getDeclarationsFromPackage`.
4. Tests: nuevo módulo consumidor en `incremental-tests` (o `integration-tests`) que mapee un tipo
   anidado de otro módulo sin redeclararlo.
5. Doc de usuario: sección "Multi-módulo" en la guía de patrones.
6. Diagnóstico: si el par existe cross-module pero el marcador falta (versión vieja), un hint.
