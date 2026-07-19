# Guía — kmapx en Kotlin Multiplatform

kmapx genera **por target**: el processor corre una vez por cada uno (`kspKotlinJvm`,
`kspKotlinJs`, …) y el `.kt` generado aterriza en el source set generado de ese target.
Las clases anotadas viven en `commonMain`; el código generado no — una función por target,
mismas semánticas (el output es Kotlin puro, sin reflection y sin APIs `java.*`; hay un test
de contrato que lo garantiza).

## Configuración copiable

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp")
}

kotlin {
    jvm(); js(IR) { nodejs() }; linuxX64() // + los targets que necesites
    sourceSets {
        commonMain { dependencies { implementation("dev.kmapx:annotations:<v>") } }
        commonTest { dependencies { implementation(kotlin("test")) } }
    }
}

// KSP se declara POR TARGET — "ksp(...)" a secas es la config de jvm puro:
dependencies {
    add("kspJvm", "dev.kmapx:frontend-ksp:<v>")
    add("kspJs", "dev.kmapx:frontend-ksp:<v>")
    add("kspLinuxX64", "dev.kmapx:frontend-ksp:<v>")
    // Apple: add("kspIosSimulatorArm64", ...) etc. — requiere host macOS con Xcode
}
```

```properties
# gradle.properties
ksp.useKSP2=true
```

## Matriz de targets de `annotations` (v1)

JVM (Android consume este artefacto: la librería no tiene dependencias de plataforma),
JS (IR), Wasm (JS), Linux x64, Windows x64 (mingw), macOS x64/arm64, iOS arm64/simArm64.
El **processor** (`frontend-ksp`) es JVM-only: corre en el build, no viaja a tus targets.

## Trampas conocidas (aprendidas con tests, no con fe)

1. **Kotlin/Native rechaza `()` y `,` en nombres de test con backticks.** Un test común
   `` fun `hace X`() `` compila en JVM/JS y revienta en `compileTestKotlin<Native>`.
2. **Linkear binarios Apple exige Xcode completo** — con solo Command Line Tools se compilan
   los klibs pero no los test runners. Por eso los targets Apple de un módulo CON tests se
   gatean (aquí: `-Pkmapx.ci.apple=true`, activado en el job macOS de CI).
3. **`commonTest` compila por target** contra el código generado de cada uno — es el lugar
   correcto para los tests de mapeo: la misma suite corre en JVM, Node y nativo.
4. **`expect class` anotada → `KMX025`**: expect/actual mapping es no-goal en v1; anota la
   `actual` por target o mapea una clase común concreta.
5. Los tests nativos solo EJECUTAN en su host (linux en CI Linux, Apple en CI macOS);
   en los demás hosts cross-compilan. La matriz de CI cubre ambos jobs.
