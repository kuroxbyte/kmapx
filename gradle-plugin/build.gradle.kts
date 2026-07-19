plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    `maven-publish`
}

kotlin { jvmToolchain(17) }

// Plugin de Gradle `id("io.github.kuroxbyte.kmapx")`: aplica KSP y cablea annotations+runtime+processor
// (por target en KMP), en vez de que el usuario lo haga a mano. Depende de las APIs Gradle de KSP
// y Kotlin en compile-only: el build del consumidor ya las trae en runtime.
dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:${libs.versions.ksp.get()}")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    // ProjectBuilder aplica los plugins de Kotlin/KSP: sus clases deben estar en runtime de test.
    testImplementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
}

gradlePlugin {
    plugins {
        create("kmapx") {
            // El id vive bajo el namespace verificable (Portal/Central exigen dominio propio
            // para ids como `dev.kmapx`) — pre-1.0, sin deprecación.
            id = "io.github.kuroxbyte.kmapx"
            implementationClass = "dev.kmapx.gradle.KmapxPlugin"
            displayName = "kmapx"
            description = "Compile-time Kotlin mapper: applies KSP and wires the kmapx processor."
        }
    }
}

tasks.test { useJUnitPlatform() }
