# Plugin de Gradle `id("io.github.kuroxbyte.kmapx")`

Reemplaza el cableado manual de KSP + dependencias por una línea.

> **Estado**: el plugin vive en este repo y funciona vía composite build, pero **aún no está
> publicado** en el Gradle Plugin Portal ni en Maven Central — mientras tanto, usa el cableado
> manual de abajo.

## Antes (manual)

```kotlin
plugins { id("com.google.devtools.ksp") version "2.1.21-2.0.1" }
dependencies {
    implementation("io.github.kuroxbyte:kmapx-annotations:0.1.0")
    implementation("io.github.kuroxbyte:kmapx-runtime:0.1.0")
    ksp("io.github.kuroxbyte:kmapx-frontend-ksp:0.1.0")
    // en KMP: repetir kspJvm(...), kspJs(...), kspLinuxX64(...) por cada target
}
```

## Con el plugin

```kotlin
plugins { id("io.github.kuroxbyte.kmapx") version "0.1.0" }

kmapx {
    report("json", "html")             // Reporte de cobertura
    moduleName.set("orders")

    // Config global — defaults para TODO el módulo (una anotación explícita ENCIENDE; opción A):
    useSerialNames.set(true)           // @SerialName como alias en todos los @MapTo
    onNull.set(OnNull.THROW)           // nivel GLOBAL de la cascada (default: STRICT/KMX003)
    warningsAsErrors.set(true)         // CI duro: los KMXnnn WARNING fallan el build
}
```

Cada opción se traduce a una opción del processor KSP (`kmapx.useSerialNames`, `kmapx.onNull`,
…). **Precedencia:** anotación explícita > config global > default. Los opt-in booleanos son
aditivos (la config los enciende para todo el módulo; una anotación puede encender uno puntual,
no apagarlo). La cascada resuelve la precedencia: `@MapField(onNull=)` > `@Mapper`/`@MapTo`
`(onNull=)` > `kmapx.onNull` global; las políticas condicionales (`TYPE_DEFAULT`/`TARGET_DEFAULT`)
aplican donde pueden y donde no, la violación cae al siguiente nivel.

El plugin:
- aplica KSP;
- en un módulo **JVM** agrega `implementation` de `annotations`+`runtime` y `ksp` del processor;
- en un módulo **Multiplatform** pone `annotations`+`runtime` en `commonMain` y cablea un
  `ksp<Target>` por cada target no-común;
- traduce el bloque `kmapx { }` a opciones del processor (`kmapx.report`, `kmapx.module`).

## Estado

El plugin compila y sus tests (ProjectBuilder) verifican que aplica KSP y cablea las dependencias.
La verificación funcional end-to-end contra un consumidor real llega con la **publicación** de los
artefactos (`0.1.0`): hoy `KmapxPlugin.KMAPX_VERSION = "0.1.0-SNAPSHOT"` apunta a coordenadas aún
no publicadas (por eso el módulo `demo` sigue consumiendo el processor por `includeBuild`).
