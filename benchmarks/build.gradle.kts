plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.jmh)
    kotlin("kapt")
    application
}

application { mainClass.set("dev.kmapx.bench.VerifyKt") }

kotlin { jvmToolchain(17) }

// Benchmarks JMH: kmapx vs escrito-a-mano (y MapStruct). Mide el RUNTIME del código generado.
// No se publica; no participa del release.
dependencies {
    implementation(project(":annotations"))
    implementation(project(":runtime"))
    ksp(project(":frontend-ksp"))

    // MapStruct (comparación): processor Java vía kapt.
    implementation(libs.mapstruct)
    kapt(libs.mapstruct.processor)
}

// El source set jmh ve el main (modelos + mappers generados).
jmh {
    warmupIterations.set(3)
    iterations.set(5)
    fork.set(2)
    benchmarkMode.set(listOf("thrpt"))
}
