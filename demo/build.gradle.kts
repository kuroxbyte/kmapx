plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    application
}

kotlin { jvmToolchain(17) }

// Demo CRUD JVM: kmapx reemplazando a MapStruct en un caso real (catálogo de productos).
// Consume el processor como cualquier usuario final: annotations + runtime en el classpath,
// frontend-ksp como procesador KSP. Cero dependencias de compilador en el binario resultante.
dependencies {
    implementation(project(":annotations"))
    implementation(project(":runtime"))
    ksp(project(":frontend-ksp"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

application {
    mainClass.set("dev.kmapx.demo.MainKt")
}

tasks.test { useJUnitPlatform() }
