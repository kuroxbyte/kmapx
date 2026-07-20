plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

// Pack de converters JVM (java.time, java.util, java.math, java.net): consumidor REAL del SPI.
// El usuario lo añade a implementation(...) (las funciones que el código generado llama) Y a
// ksp(...) (para que el ServiceLoader descubra la extensión). Un solo artefacto, dos configs.
dependencies {
    implementation(project(":spi"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)

    // Test end-to-end AISLADO: corre el processor real con ESTE pack en el classpath del KSP.
    // Aislado del módulo frontend-ksp a propósito — el ServiceLoader es global al classpath.
    testImplementation(libs.kctfork.ksp)
    testImplementation(project(":annotations"))
    testImplementation(project(":frontend-ksp"))
}

tasks.test { useJUnitPlatform() }
