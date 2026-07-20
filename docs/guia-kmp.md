# Guía — kmapx en Kotlin Multiplatform

kmapx es multiplataforma de verdad: las clases anotadas viven en `commonMain`, y el processor
genera **una función por target** con semántica idéntica. El output es Kotlin puro — sin
reflection y sin APIs `java.*` (hay un test de contrato que lo garantiza) — así que el mismo
mapeo funciona en JVM, Android, JS, Wasm y nativo.

## Cómo funciona la generación por target

KSP no corre "una vez para common": corre **una vez por cada target** (`kspKotlinJvm`,
`kspKotlinJs`, `kspKotlinLinuxX64`, …) y el `.kt` generado aterriza en el source set generado
de ese target:

```
build/generated/ksp/
├── jvm/jvmMain/kotlin/…/PersonMappings.kt
├── js/jsMain/kotlin/…/PersonMappings.kt
└── linuxX64/linuxX64Main/kotlin/…/PersonMappings.kt
```

Las tres funciones son idénticas en comportamiento. Tu código común NO ve la función generada
(no existe en `commonMain`), pero tus tests en `commonTest` sí — porque `commonTest` compila
por target contra el código generado de cada uno. Esa es la consecuencia práctica más
importante: **escribe los tests de mapeo en `commonTest`** y la misma suite corre en JVM, Node
y nativo.

## Configuración copiable

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform") version "2.1.21"
    id("com.google.devtools.ksp") version "2.1.21-2.0.1"
}

kotlin {
    jvm(); js(IR) { nodejs() }; linuxX64() // + los targets que necesites
    sourceSets {
        commonMain {
            dependencies {
                implementation("io.github.kuroxbyte:kmapx-annotations:0.1.0")
                // solo si usas Converts<A, B> o Patch<T>:
                implementation("io.github.kuroxbyte:kmapx-runtime:0.1.0")
            }
        }
        commonTest { dependencies { implementation(kotlin("test")) } }
    }
}

// KSP se declara POR TARGET — un "ksp(...)" a secas es la configuración de JVM puro
// y en un proyecto multiplatform no engancha nada:
dependencies {
    add("kspJvm", "io.github.kuroxbyte:kmapx-frontend-ksp:0.1.0")
    add("kspJs", "io.github.kuroxbyte:kmapx-frontend-ksp:0.1.0")
    add("kspLinuxX64", "io.github.kuroxbyte:kmapx-frontend-ksp:0.1.0")
    // Apple: add("kspIosSimulatorArm64", ...) etc. — requiere host macOS con Xcode
}
```

```properties
# gradle.properties
ksp.useKSP2=true
```

## Matriz de targets de `annotations` y `runtime` (v1)

| Target | Nota |
|---|---|
| JVM | Android consume este artefacto — la librería no tiene dependencias de plataforma |
| JS (IR) | browser y nodejs |
| Wasm (JS) | |
| Linux x64 | |
| Windows x64 (mingw) | |
| macOS x64 / arm64 | |
| iOS arm64 / simulatorArm64 | |

El **processor** (`kmapx-frontend-ksp`) es JVM-only: corre dentro del build de Gradle y no
viaja a tus targets — por eso no necesita (ni tiene) variantes nativas.

## Trampas conocidas (aprendidas con tests, no con fe)

1. **Kotlin/Native rechaza `()` y `,` en nombres de test con backticks.** Un test común
   `` fun `mapea X (caso Y)`() `` compila en JVM/JS y revienta en `compileTestKotlin<Native>`
   con un error críptico. Si tu suite corre en nativo, evita paréntesis y comas en los nombres.
2. **Linkear binarios Apple exige Xcode completo.** Con solo Command Line Tools se compilan
   los klibs pero el link de los test runners falla. En CI, separa los targets Apple a un job
   macOS con Xcode (en este repo se gatean con `-Pkmapx.ci.apple=true`).
3. **`expect class` anotada → `KMX025`.** El mapeo de declaraciones `expect`/`actual` es
   no-goal en v1: KSP procesa por target y la identidad de la clase difiere entre ellos. Anota
   la `actual` de cada target, o —mejor— mapea una clase común concreta.
4. **Los tests nativos solo EJECUTAN en su host.** Linux en CI Linux, Apple en CI macOS; en
   los demás hosts solo cross-compilan. Si tu CI corre un único sistema operativo, estás
   compilando — no probando — la otra mitad de la matriz.
5. **El código generado no es una API de `commonMain`.** Si necesitas exponer un mapeo al
   código común, envuélvelo en una `expect fun`/interfaz tuya por target — o replantéate si el
   mapeo pertenece a la capa común.
