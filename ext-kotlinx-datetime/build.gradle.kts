plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

// Pack de converters para kotlinx-datetime — el datetime MULTIPLATAFORMA que a stdConverters
// (solo JVM) le falta. Hoy JVM-first; como las funciones usan solo API de kotlinx.datetime (sin
// java.*), la expansión a KMP luego es cambiar este build a multiplatform sin tocar el código.
dependencies {
    implementation(project(":spi"))
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.kctfork.ksp)
    testImplementation(project(":annotations"))
    testImplementation(project(":frontend-ksp"))
}

tasks.test { useJUnitPlatform() }
