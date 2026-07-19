plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core"))
    // El backend expone GeneratedFile; este módulo NO conoce KotlinPoet (regla Konsist).
    implementation(project(":backend-codegen"))
    // Las anotaciones se leen por nombre calificado (el processor no las necesita en classpath,
    // pero tenerlas evita strings mágicos):
    compileOnly(project(":annotations"))
    implementation(libs.ksp.api)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.kctfork.ksp)
    testImplementation(project(":annotations"))
    // El harness compila código de usuario que referencia `dev.kmapx.runtime.Converts`.
    testImplementation(project(":runtime"))
    // Los frameworks entran SOLO al classpath de compile-testing (regla cero, Q2 de la spec):
    testImplementation(libs.spring.context)
    testImplementation(libs.koin.core)
    testImplementation(libs.kotlinx.serialization.core)
    testImplementation(libs.kotlinx.serialization.json) // Parsear el reporte en tests
}

tasks.test { useJUnitPlatform() }
